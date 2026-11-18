package com.mylive.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BilibiliQrControllerTest {
    private val challenge = BilibiliQrChallenge("fixture", "https://passport.bilibili.com/fixture")
    @Test fun scanConfirmationPersistsOnlyAfterServerSuccess() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val codes = ArrayDeque(listOf(BilibiliQrPoll(86101), BilibiliQrPoll(86090), BilibiliQrPoll(0, "SESSDATA=fixture")))
        var saved = ""
        val api = object : BilibiliQrApi {
            override suspend fun challenge() = this@BilibiliQrControllerTest.challenge
            override suspend fun poll(key: String) = codes.removeFirst()
        }
        val controller = BilibiliQrController(api, { saved = it; true }, dispatcher, 100)
        controller.load(); runCurrent()
        advanceTimeBy(200); runCurrent(); assertEquals(DesktopQrStatus.Scanned, controller.state.value.status); assertEquals("", saved)
        advanceTimeBy(100); runCurrent(); assertEquals("SESSDATA=fixture", saved)
        assertEquals(DesktopQrStatus.Success, controller.state.value.status)
        controller.close()
    }
    @Test fun expiredQrNeverSavesACredential() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var saves = 0
        val api = object : BilibiliQrApi {
            override suspend fun challenge() = this@BilibiliQrControllerTest.challenge
            override suspend fun poll(key: String) = BilibiliQrPoll(86038)
        }
        val controller = BilibiliQrController(api, { saves++; true }, dispatcher, 100)
        controller.load(); runCurrent(); advanceTimeBy(100); runCurrent()
        assertEquals(DesktopQrStatus.Expired, controller.state.value.status); assertEquals(0, saves)
        controller.close()
    }
    @Test fun olderChallengeAndPollResponsesCannotSaveAfterRefreshingQr() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gate = CompletableDeferred<Unit>()
        var generation = 0; var saved = ""
        val api = object : BilibiliQrApi {
            override suspend fun challenge() = BilibiliQrChallenge("${++generation}", challenge.url)
            override suspend fun poll(key: String): BilibiliQrPoll {
                if (key == "1") withContext(NonCancellable) { gate.await() }
                return BilibiliQrPoll(0, "SESSDATA=$key")
            }
        }
        val controller = BilibiliQrController(api, { saved = it; true }, dispatcher, 100)
        controller.load(); runCurrent(); advanceTimeBy(100); runCurrent()
        controller.load(); runCurrent(); advanceTimeBy(100); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals("SESSDATA=2", saved)
        assertEquals(DesktopQrStatus.Success, controller.state.value.status)
        controller.close()
    }
    @Test fun closingQrCancelsPollingAndCannotAuthenticateLater() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gate = CompletableDeferred<Unit>(); var saves = 0
        val api = object : BilibiliQrApi {
            override suspend fun challenge() = this@BilibiliQrControllerTest.challenge
            override suspend fun poll(key: String): BilibiliQrPoll {
                withContext(NonCancellable) { gate.await() }; return BilibiliQrPoll(0, "SESSDATA=late")
            }
        }
        val controller = BilibiliQrController(api, { saves++; true }, dispatcher, 100)
        controller.load(); runCurrent(); advanceTimeBy(100); runCurrent()
        controller.close(); gate.complete(Unit); runCurrent()
        assertEquals(0, saves)
    }
    @Test fun localLifetimeExpiresButSuccessfulLoginPersistenceHasItsOwnLifetime() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val api = object : BilibiliQrApi {
            override suspend fun challenge() = this@BilibiliQrControllerTest.challenge
            override suspend fun poll(key: String) = BilibiliQrPoll(0, "SESSDATA=fixture")
        }
        val controller = BilibiliQrController(api, { delay(500); true }, dispatcher, 100, 200)
        controller.load(); runCurrent(); advanceTimeBy(700); runCurrent()
        assertEquals(DesktopQrStatus.Success, controller.state.value.status)
        controller.close()
        val waiting = object : BilibiliQrApi {
            override suspend fun challenge() = this@BilibiliQrControllerTest.challenge
            override suspend fun poll(key: String) = BilibiliQrPoll(86101)
        }
        val expired = BilibiliQrController(waiting, { true }, dispatcher, 100, 200)
        expired.load(); runCurrent(); advanceTimeBy(200); runCurrent()
        assertEquals(DesktopQrStatus.Expired, expired.state.value.status)
        expired.close()
    }
    @Test fun transientPollFailureRetriesAndMissingSessionDataCannotReportSuccess() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var attempts = 0; var saved = false
        val api = object : BilibiliQrApi {
            override suspend fun challenge() = this@BilibiliQrControllerTest.challenge
            override suspend fun poll(key: String): BilibiliQrPoll {
                if (++attempts == 1) error("temporary network failure")
                return BilibiliQrPoll(0, "visitor=only")
            }
        }
        val controller = BilibiliQrController(api, { saved = true; true }, dispatcher, 100)
        controller.load(); runCurrent(); advanceTimeBy(300); runCurrent()
        assertEquals(2, attempts); assertFalse(saved)
        assertEquals(DesktopQrStatus.Failed, controller.state.value.status)
        controller.close()
    }
}
