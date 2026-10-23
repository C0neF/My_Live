package com.mylive.app.ui.screen.settings

import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.mylive.app.data.local.datastore.SettingsDataStore
import com.mylive.app.data.local.secure.CredentialCipher
import com.mylive.app.data.local.secure.SensitiveCredentialStore
import com.mylive.app.data.repository.AccountRepository
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Main, ViewModel and audit DataStore; all HTTP terminates in the local interceptor. */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BiliBiliQRLoginConcurrencyAndroidTest {
    private val store = ViewModelStore()
    private val network = ControlledNetwork()
    private val cipher = MemoryCipher()
    private lateinit var settings: SettingsDataStore
    private lateinit var repository: AccountRepository
    private var original: CredentialSnapshot? = null
    private var scopeJob: Job? = null

    @Before
    fun prepareAuditCredentials() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("Build with -PisolatedDeviceTests=true; never run against QA or production",
            "com.mylive.app.audit", target.packageName)
        settings = SettingsDataStore(target)
        withTimeout(10_000) {
            val preferences = settings.preferencesFlow().first()
            original = CredentialSnapshot(
                preferences[SettingsDataStore.EncryptedBilibiliCookie],
                preferences[SettingsDataStore.BilibiliCookie]
            )
            settings.edit {
                it.remove(SettingsDataStore.EncryptedBilibiliCookie)
                it.remove(SettingsDataStore.BilibiliCookie)
            }
            repository = AccountRepository(SensitiveCredentialStore(settings, cipher))
            assertEquals("The isolated test starts with no Bilibili account", "", repository.bilibiliCookie.first())
        }
    }

    @After
    fun clearViewModelAndRestoreAuditCredentials() = runBlocking {
        try {
            onMain { store.clear() }
        } finally {
            // Also cancel the root directly if Main was unavailable during a failing fixture.
            scopeJob?.cancel()
            network.client.dispatcher.cancelAll()
            network.releaseAll()
            try {
                scopeJob?.let { awaitJobs(listOf(it)) }
            } finally {
                try {
                    network.close()
                    assertEquals("Fixture failures must be distinct from product regression assertions",
                        emptyList<String>(), network.failures.toList())
                } finally {
                    original?.let { snapshot ->
                        withTimeout(10_000) {
                            settings.edit { preferences ->
                                if (snapshot.encrypted == null) preferences.remove(SettingsDataStore.EncryptedBilibiliCookie)
                                else preferences[SettingsDataStore.EncryptedBilibiliCookie] = snapshot.encrypted
                                if (snapshot.legacy == null) preferences.remove(SettingsDataStore.BilibiliCookie)
                                else preferences[SettingsDataStore.BilibiliCookie] = snapshot.legacy
                            }
                        }
                    }
                    cipher.clear()
                }
            }
        }
    }

    @Test
    fun anObsoleteGenerateFailureCannotReplaceTheFreshQrOnRealMain() = runBlocking {
        val oldGenerate = network.generate(Reply("controlled old failure", status = 500))
        val freshGenerate = network.generate(qrReply("fresh"))
        val unscanned = network.immediate(pollReply(86101))
        network.polls("fresh") { unscanned }
        val model = createModel()
        oldGenerate.awaitEntered()
        val oldJobs = onMain { children(model) }

        onMain { model.loadQRCode() }
        finishGeneration(model, freshGenerate, oldJobs)
        onMain { assertFreshQr(model) }
        oldGenerate.release()
        awaitJobs(oldJobs)

        onMain { assertFreshQr(model) }
        assertEquals("", currentCookie())
    }

    @Test
    fun aSlowPollCannotOverlapTheNextRealThreeSecondTick() = runBlocking {
        val generate = network.generate(qrReply("fresh"))
        val slowPoll = network.gate(pollReply(86101))
        val laterPoll = network.immediate(pollReply(86101))
        val route = network.polls("fresh") { index -> if (index == 1) slowPoll else laterPoll }
        val model = createModel()
        finishGeneration(model, generate)
        slowPoll.awaitEntered()

        assertFalse("A pending poll must finish before another request for this QR starts",
            route.secondRequest.await(4_500, TimeUnit.MILLISECONDS))
        assertEquals("Only one poll may be in flight", 1, route.maxActive.get())
        onMain { assertFreshQr(model) }
    }

    @Test
    fun refreshingRejectsAnOldLoginBeforeItCanWriteRealCredentials() = runBlocking {
        val generate = network.generate(qrReply("old"))
        val freshGenerate = network.generate(qrReply("fresh"))
        val oldLogin = network.gate(loginReply("stale"))
        val unscanned = network.immediate(pollReply(86101))
        network.polls("old") { oldLogin }
        network.polls("fresh") { unscanned }
        val model = createModel()
        finishGeneration(model, generate)
        oldLogin.awaitEntered()
        val oldJobs = onMain { children(model) }

        onMain { model.loadQRCode() }
        finishGeneration(model, freshGenerate, oldJobs)
        onMain { assertFreshQr(model) }
        oldLogin.release()
        awaitJobs(oldJobs)

        assertEquals("A refreshed-away QR must not save its cookie through AccountRepository", "", currentCookie())
        assertEquals("Stale login must never enter credential encryption", emptyList<String>(), cipher.encryptions.toList())
        val preferences = withTimeout(10_000) { settings.preferencesFlow().first() }
        assertNull(preferences[SettingsDataStore.EncryptedBilibiliCookie])
        assertNull(preferences[SettingsDataStore.BilibiliCookie])
        onMain { assertFreshQr(model) }
    }

    @Test
    fun aCurrentLoginPersistsItsCookieAndStopsPolling() = runBlocking {
        val generate = network.generate(qrReply("fresh"))
        val login = network.gate(loginReply("current"))
        val route = network.polls("fresh") { login }
        val model = createModel()
        finishGeneration(model, generate)
        login.awaitEntered()
        val loginJobs = onMain { children(model) }

        login.release()
        awaitJobs(loginJobs)

        val expected = "SESSDATA=current-session; bili_jct=current-csrf"
        assertEquals(expected, currentCookie())
        assertEquals(listOf(expected), cipher.encryptions.toList())
        assertTrue(withTimeout(10_000) { repository.isLoggedInBiliBili.first() })
        val preferences = withTimeout(10_000) { settings.preferencesFlow().first() }
        val encrypted = preferences[SettingsDataStore.EncryptedBilibiliCookie]
        assertTrue("The actual store must persist the cipher output", encrypted?.startsWith("qr-device-token-") == true)
        assertFalse("The persisted credential value must not contain the test cookie", encrypted.orEmpty().contains("current-session"))
        assertNull("Plaintext legacy credentials must be removed", preferences[SettingsDataStore.BilibiliCookie])
        onMain {
            assertTrue(model.uiState.value.loginSuccess)
            assertEquals(qrUrl("fresh"), model.uiState.value.qrCodeUrl)
            assertEquals("Success must finish the actual polling jobs", emptyList<Job>(), children(model))
        }
        assertEquals(1, route.requests.get())
    }

    private suspend fun createModel(): BiliBiliQRLoginViewModel = onMain {
        BiliBiliQRLoginViewModel(repository, network.client).also { model ->
            store.put("qr-device-test", model)
            scopeJob = model.viewModelScope.coroutineContext[Job]!!
        }
    }

    private suspend fun finishGeneration(
        model: BiliBiliQRLoginViewModel,
        gate: Gate,
        previousJobs: List<Job> = emptyList()
    ) {
        gate.awaitEntered()
        val generationJobs = onMain { children(model).filterNot { it in previousJobs } }
        assertEquals("The blocked generate request must belong to one actual job", 1, generationJobs.size)
        gate.release()
        awaitJobs(generationJobs)
        onMain {
            assertEquals(QRStatus.Unscanned, model.uiState.value.status)
            assertNotNull("Android must create the real QR Bitmap", model.uiState.value.qrCodeBitmap)
        }
    }

    private fun assertFreshQr(model: BiliBiliQRLoginViewModel) {
        val state = model.uiState.value
        assertEquals("An obsolete response must not alter fresh QR status", QRStatus.Unscanned, state.status)
        assertEquals("An obsolete response must not replace the fresh QR", qrUrl("fresh"), state.qrCodeUrl)
        assertNotNull(state.qrCodeBitmap)
        assertFalse("An obsolete response must not report login success", state.loginSuccess)
        assertNull(state.errorMsg)
    }

    private suspend fun currentCookie(): String = withTimeout(10_000) { repository.bilibiliCookie.first() }

    private suspend fun awaitJobs(jobs: List<Job>) {
        withTimeout(10_000) { jobs.joinAll() }
    }

    private suspend fun <T> onMain(block: () -> T): T = withTimeout(10_000) {
        withContext(Dispatchers.Main.immediate) {
            assertEquals("Use Android's actual Main looper", Looper.getMainLooper(), Looper.myLooper())
            block()
        }
    }

    private fun children(model: BiliBiliQRLoginViewModel): List<Job> =
        model.viewModelScope.coroutineContext[Job]!!.children.toList()

    private data class CredentialSnapshot(val encrypted: String?, val legacy: String?)

    /** Only the cryptographic boundary is substituted; persistence and account flows are real. */
    private class MemoryCipher : CredentialCipher {
        val encryptions = ConcurrentLinkedQueue<String>()
        private val values = ConcurrentHashMap<String, String>()
        private val nextToken = AtomicInteger()

        override fun encrypt(plainText: String): String {
            encryptions.add(plainText)
            return "qr-device-token-${nextToken.incrementAndGet()}".also { values[it] = plainText }
        }

        override fun decrypt(cipherText: String): String =
            checkNotNull(values[cipherText]) { "Only credentials generated in this test may be decrypted" }

        fun clear() {
            values.clear()
            encryptions.clear()
        }
    }

    private data class Reply(val body: String, val status: Int = 200, val cookies: List<String> = emptyList())

    private class Gate(
        private val reply: Reply,
        private val failures: ConcurrentLinkedQueue<String>,
        released: Boolean = false
    ) {
        private val entered = CountDownLatch(1)
        private val resume = CountDownLatch(if (released) 0 else 1)

        fun awaitEntered() {
            assertTrue("The real ViewModel must reach the controlled request", entered.await(10, TimeUnit.SECONDS))
        }

        fun release() = resume.countDown()

        fun respond(request: Request): Response {
            entered.countDown()
            if (!resume.await(30, TimeUnit.SECONDS)) {
                val message = "Fixture timed out releasing ${request.url.encodedPath}"
                failures.add(message)
                throw IOException(message)
            }
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(reply.status).message("Controlled device response")
                .body(reply.body.toResponseBody("application/json".toMediaType()))
                .apply { reply.cookies.forEach { addHeader("Set-Cookie", it) } }.build()
        }
    }

    private class PollRoute(private val respond: (Int) -> Gate) {
        val requests = AtomicInteger()
        val maxActive = AtomicInteger()
        val secondRequest = CountDownLatch(1)
        private val active = AtomicInteger()

        fun execute(request: Request): Response {
            val count = active.incrementAndGet()
            maxActive.accumulateAndGet(count) { old, current -> maxOf(old, current) }
            val index = requests.incrementAndGet()
            if (index == 2) secondRequest.countDown()
            return try {
                respond(index).respond(request)
            } finally {
                active.decrementAndGet()
            }
        }
    }

    private class ControlledNetwork {
        val failures = ConcurrentLinkedQueue<String>()
        private val gates = ConcurrentLinkedQueue<Gate>()
        private val generateReplies = ConcurrentLinkedQueue<Gate>()
        private val pollRoutes = ConcurrentHashMap<String, PollRoute>()
        val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val request = chain.request()
            // No chain.proceed: this fixture cannot send a request to Bilibili or any other host.
            when (request.url.encodedPath) {
                "/x/passport-login/web/qrcode/generate" ->
                    (generateReplies.poll() ?: unexpected(request)).respond(request)
                "/x/passport-login/web/qrcode/poll" ->
                    (pollRoutes[request.url.queryParameter("qrcode_key")] ?: unexpected(request)).execute(request)
                else -> unexpected(request)
            }
        }).build()

        fun gate(reply: Reply): Gate = Gate(reply, failures).also(gates::add)

        fun immediate(reply: Reply): Gate = Gate(reply, failures, released = true).also(gates::add)

        fun generate(reply: Reply): Gate = gate(reply).also(generateReplies::add)

        fun polls(key: String, respond: (Int) -> Gate): PollRoute =
            PollRoute(respond).also { pollRoutes[key] = it }

        fun releaseAll() = gates.forEach(Gate::release)

        suspend fun close() {
            client.connectionPool.evictAll()
            val executor = client.dispatcher.executorService
            executor.shutdown()
            if (!withContext(Dispatchers.IO) { executor.awaitTermination(5, TimeUnit.SECONDS) }) {
                executor.shutdownNow()
                assertTrue("The fixture's OkHttp executor must terminate",
                    withContext(Dispatchers.IO) { executor.awaitTermination(5, TimeUnit.SECONDS) })
            }
        }

        private fun unexpected(request: Request): Nothing {
            val message = "Unexpected fixture request: ${request.url}"
            failures.add(message)
            throw IOException(message)
        }
    }

    private companion object {
        fun qrUrl(key: String) = "https://example.invalid/qr/$key"

        fun qrReply(key: String) = Reply(
            """{"code":0,"data":{"qrcode_key":"$key","url":"${qrUrl(key)}"}}"""
        )

        fun pollReply(code: Int) = Reply("""{"code":0,"data":{"code":$code}}""")

        fun loginReply(prefix: String) = Reply(
            pollReply(0).body,
            cookies = listOf("SESSDATA=$prefix-session; Path=/; HttpOnly", "bili_jct=$prefix-csrf; Path=/")
        )
    }
}
