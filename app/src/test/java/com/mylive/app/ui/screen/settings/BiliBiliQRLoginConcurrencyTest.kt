package com.mylive.app.ui.screen.settings

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.mylive.app.data.repository.AccountRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real ViewModel and real three-second polling; every HTTP request terminates in the fixture. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BiliBiliQRLoginConcurrencyTest {
    private val repository = mockk<AccountRepository>()
    private val savedCookies = ConcurrentLinkedQueue<String>()
    private val network = ControlledNetwork()
    private val store = ViewModelStore()
    private var scopeJob: Job? = null

    @Before
    fun prepare() {
        // IO work and its delays keep their real dispatchers and clocks.
        Dispatchers.setMain(Dispatchers.Unconfined)
        coEvery { repository.setBilibiliCookie(any()) } answers {
            savedCookies.add(firstArg())
            Unit
        }
    }

    @After
    fun close() {
        try {
            store.clear()
            network.client.dispatcher.cancelAll()
            network.releaseAll()
            scopeJob?.let { job -> awaitJobs(listOf(job)) }
        } finally {
            network.client.connectionPool.evictAll()
            network.client.dispatcher.executorService.shutdownNow()
            try {
                assertTrue("The fixture's OkHttp executor must terminate",
                    network.client.dispatcher.executorService.awaitTermination(5, TimeUnit.SECONDS))
                assertEquals("Network fixture failures must not be mistaken for a product failure",
                    emptyList<String>(), network.failures.toList())
            } finally {
                Dispatchers.resetMain()
            }
        }
    }

    @Test
    fun anOlderGenerateFailureCannotReplaceTheRefreshedQrCode() {
        val oldGenerate = network.generate(Reply("controlled old failure", status = 500))
        val freshGenerate = network.generate(qrReply("fresh"))
        network.polls("fresh") { network.immediate(pollReply(86101)) }
        val model = createModel()
        oldGenerate.awaitEntered()
        val oldJobs = children(model)

        refreshAndFinishGeneration(model, freshGenerate, oldJobs)
        oldGenerate.release()
        awaitJobs(oldJobs)

        assertFreshQr(model)
    }

    @Test
    fun anOlderGenerateSuccessCannotReplaceTheRefreshedQrCode() {
        val oldGenerate = network.generate(qrReply("old"))
        val freshGenerate = network.generate(qrReply("fresh"))
        network.polls("old") { network.immediate(pollReply(86101)) }
        network.polls("fresh") { network.immediate(pollReply(86101)) }
        val model = createModel()
        oldGenerate.awaitEntered()
        val oldJobs = children(model)

        refreshAndFinishGeneration(model, freshGenerate, oldJobs)
        oldGenerate.release()
        awaitJobs(oldJobs)

        assertFreshQr(model)
    }

    @Test
    fun aSlowPollCannotStartAnotherRequestForTheSameQrCode() {
        val generate = network.generate(qrReply("fresh"))
        val slowPoll = network.gate(pollReply(86101))
        val laterPoll = network.immediate(pollReply(86101))
        val route = network.polls("fresh") { index -> if (index == 1) slowPoll else laterPoll }
        val model = createModel()
        finishGeneration(model, generate)
        slowPoll.awaitEntered()

        // Hold the first real request across the next real three-second polling tick.
        assertFalse("A pending poll must finish before the next request starts",
            route.secondRequest.await(4_500, TimeUnit.MILLISECONDS))
        assertEquals("At most one poll may be in flight for a QR code", 1, route.maxActive.get())
    }

    @Test
    fun refreshingDiscardsAnOldScannedResponse() {
        assertOldStatusIsDiscarded(pollCode = 86090, verifyFreshPolling = false)
    }

    @Test
    fun refreshingDiscardsAnOldExpiredResponseAndKeepsTheFreshPollRunning() {
        assertOldStatusIsDiscarded(pollCode = 86038, verifyFreshPolling = true)
    }

    @Test
    fun refreshingDiscardsAnOldLoginResponseWithoutSavingItsCookies() {
        val generate = network.generate(qrReply("old"))
        val freshGenerate = network.generate(qrReply("fresh"))
        val oldLogin = network.gate(loginReply("stale"))
        network.polls("old") { oldLogin }
        network.polls("fresh") { network.immediate(pollReply(86101)) }
        val model = createModel()
        finishGeneration(model, generate)
        oldLogin.awaitEntered()
        val oldJobs = children(model)

        refreshAndFinishGeneration(model, freshGenerate, oldJobs)
        oldLogin.release()
        awaitJobs(oldJobs)

        assertEquals("Credentials from a refreshed-away QR code must not be persisted",
            emptyList<String>(), savedCookies.toList())
        coVerify(exactly = 0) { repository.setBilibiliCookie(any()) }
        assertFreshQr(model)
    }

    @Test
    fun aCurrentSuccessfulLoginSavesCookiesAndStopsItsPoll() {
        val generate = network.generate(qrReply("fresh"))
        val login = network.gate(loginReply("current"))
        val route = network.polls("fresh") { login }
        val model = createModel()
        finishGeneration(model, generate)
        login.awaitEntered()
        val loginJobs = children(model)

        login.release()
        awaitJobs(loginJobs)

        val expected = "SESSDATA=current-session; bili_jct=current-csrf"
        assertEquals(listOf(expected), savedCookies.toList())
        coVerify(exactly = 1) { repository.setBilibiliCookie(expected) }
        assertTrue("The current QR must complete login", model.uiState.value.loginSuccess)
        assertEquals(qrUrl("fresh"), model.uiState.value.qrCodeUrl)
        assertEquals("Completing login must stop the polling coroutine", emptyList<Job>(), children(model))
        assertEquals(1, route.requests.get())
    }

    private fun assertOldStatusIsDiscarded(pollCode: Int, verifyFreshPolling: Boolean) {
        val generate = network.generate(qrReply("old"))
        val freshGenerate = network.generate(qrReply("fresh"))
        val oldPoll = network.gate(pollReply(pollCode))
        val freshPoll = network.gate(pollReply(86101))
        network.polls("old") { oldPoll }
        network.polls("fresh") { freshPoll }
        val model = createModel()
        finishGeneration(model, generate)
        oldPoll.awaitEntered()
        val oldJobs = children(model)

        refreshAndFinishGeneration(model, freshGenerate, oldJobs)
        oldPoll.release()
        awaitJobs(oldJobs)

        assertFreshQr(model)
        if (verifyFreshPolling) {
            freshPoll.awaitEntered("The new QR must keep polling after an obsolete expiration arrives")
        }
        assertTrue(savedCookies.isEmpty())
    }

    private fun createModel(): BiliBiliQRLoginViewModel =
        BiliBiliQRLoginViewModel(repository, network.client).also { model ->
            store.put("qr-login", model)
            scopeJob = model.viewModelScope.coroutineContext[Job]!!
        }

    private fun refreshAndFinishGeneration(
        model: BiliBiliQRLoginViewModel,
        generate: Gate,
        previousJobs: List<Job>
    ) {
        model.loadQRCode()
        finishGeneration(model, generate, previousJobs)
        assertFreshQr(model)
    }

    private fun finishGeneration(
        model: BiliBiliQRLoginViewModel,
        generate: Gate,
        previousJobs: List<Job> = emptyList()
    ) {
        generate.awaitEntered()
        // This response is still blocked, so the only new child is the actual generation job.
        val generationJobs = children(model).filterNot { it in previousJobs }
        assertEquals("The controlled generate response must belong to one real coroutine", 1, generationJobs.size)
        generate.release()
        awaitJobs(generationJobs)
        assertEquals(QRStatus.Unscanned, model.uiState.value.status)
        assertNotNull("The real Bitmap generator must have succeeded", model.uiState.value.qrCodeBitmap)
    }

    private fun assertFreshQr(model: BiliBiliQRLoginViewModel) {
        val state = model.uiState.value
        assertEquals("An obsolete request must not alter the new QR status", QRStatus.Unscanned, state.status)
        assertEquals("An obsolete request must not replace the new QR URL", qrUrl("fresh"), state.qrCodeUrl)
        assertNotNull(state.qrCodeBitmap)
        assertFalse("An obsolete request must not complete login", state.loginSuccess)
        assertEquals(null, state.errorMsg)
    }

    private fun children(model: BiliBiliQRLoginViewModel): List<Job> =
        model.viewModelScope.coroutineContext[Job]!!.children.toList()

    private fun awaitJobs(jobs: List<Job>) = runBlocking {
        withTimeout(10_000) { jobs.joinAll() }
    }

    private data class Reply(val body: String, val status: Int = 200, val cookies: List<String> = emptyList())

    private class Gate(
        private val reply: Reply,
        private val failures: ConcurrentLinkedQueue<String>,
        released: Boolean = false
    ) {
        private val entered = CountDownLatch(1)
        private val resume = CountDownLatch(if (released) 0 else 1)

        fun awaitEntered(message: String = "The real ViewModel must reach the controlled HTTP request") {
            assertTrue(message, entered.await(10, TimeUnit.SECONDS))
        }

        fun release() = resume.countDown()

        fun respond(request: Request): Response {
            entered.countDown()
            if (!resume.await(30, TimeUnit.SECONDS)) {
                val message = "Fixture timed out releasing ${request.url.encodedPath}"
                failures.add(message)
                throw IOException(message)
            }
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(reply.status)
                .message("Controlled response")
                .body(reply.body.toResponseBody("application/json".toMediaType()))
                .apply { reply.cookies.forEach { addHeader("Set-Cookie", it) } }
                .build()
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
        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                // No chain.proceed: neither Bilibili nor any other host can be contacted.
                when (request.url.encodedPath) {
                    "/x/passport-login/web/qrcode/generate" -> {
                        val gate = generateReplies.poll() ?: unexpected(request)
                        gate.respond(request)
                    }
                    "/x/passport-login/web/qrcode/poll" -> {
                        val route = pollRoutes[request.url.queryParameter("qrcode_key")]
                            ?: unexpected(request)
                        route.execute(request)
                    }
                    else -> unexpected(request)
                }
            })
            .build()

        fun gate(reply: Reply): Gate = Gate(reply, failures).also(gates::add)

        fun immediate(reply: Reply): Gate = Gate(reply, failures, released = true).also(gates::add)

        fun generate(reply: Reply): Gate = gate(reply).also(generateReplies::add)

        fun polls(key: String, respond: (Int) -> Gate): PollRoute =
            PollRoute(respond).also { pollRoutes[key] = it }

        fun releaseAll() = gates.forEach(Gate::release)

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
            body = pollReply(0).body,
            cookies = listOf(
                "SESSDATA=$prefix-session; Path=/; HttpOnly",
                "bili_jct=$prefix-csrf; Path=/"
            )
        )
    }
}
