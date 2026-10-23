package com.mylive.app.ui.screen.settings

import android.app.Application
import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.mylive.app.core.common.HttpClient
import com.mylive.app.update.AppUpdateRepository
import java.io.File
import java.io.IOException
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
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real Repository, HTTP calls, files and per-entry ViewModel lifecycles; no external requests. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AppUpdateDownloadLifecycleTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val network = ControlledNetwork()
    private val pages = mutableListOf<Page>()
    private lateinit var cacheDirectory: File
    private lateinit var repository: AppUpdateRepository

    @Before
    fun prepare() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        cacheDirectory = temporaryFolder.newFolder("cache")
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getCacheDir(): File = cacheDirectory
        }
        repository = AppUpdateRepository(HttpClient(network.client), network.client, context)
    }

    @After
    fun close() {
        try {
            pages.forEach { it.store.clear() }
            network.client.dispatcher.cancelAll()
            network.releaseAll()
            awaitJobs(pages.map { it.scopeJob })
        } finally {
            network.client.connectionPool.evictAll()
            network.client.dispatcher.executorService.shutdownNow()
            try {
                assertTrue("The fixture's OkHttp executor must terminate",
                    network.client.dispatcher.executorService.awaitTermination(5, TimeUnit.SECONDS))
                assertEquals("Fixture routing/timeouts must not count as a product failure",
                    emptyList<String>(), network.failures.toList())
            } finally {
                Dispatchers.resetMain()
            }
        }
    }

    @Test
    fun aCurrentPageDownloadsTheCompleteApkNormally() {
        val transfer = network.download(holdAfterFirstChunk = false)
        val page = openUpdatePage()

        page.model.downloadUpdate()
        awaitWork(page)

        assertDownloadedApk(page)
        assertTrue("Finishing a download must close its HTTP body", transfer.isClosed)
        assertEquals(1, network.downloadRequests.get())
        assertEquals(1L, page.model.uiState.value.installRequestId)
    }

    @Test
    fun leavingThePageCancelsTheStreamingCallAndClosesItsBody() {
        val transfer = network.download(holdAfterFirstChunk = true)
        val page = openUpdatePage()
        startAndHoldAfterFirstWrite(page, transfer)

        // The NavEntry ViewModelStore is cleared when the popped page leaves composition.
        page.store.clear()

        assertTrue("Leaving the update page must close the blocked old HTTP body",
            transfer.awaitClosed(2, TimeUnit.SECONDS))
        assertTrue("Lifecycle cancellation must cancel the actual OkHttp Call", transfer.isCancelled)
        awaitJobs(listOf(page.scopeJob))
    }

    @Test
    fun anOldCancelledDownloadCannotBreakTheReenteredPagesDownloadOfTheSameApk() {
        val previousTransfer = network.download(holdAfterFirstChunk = true)
        val previousPage = openUpdatePage()
        startAndHoldAfterFirstWrite(previousPage, previousTransfer)
        previousPage.store.clear()

        // A fresh navigation entry has a fresh ViewModel but shares the injected Repository/cache.
        network.download(holdAfterFirstChunk = false)
        val currentPage = openUpdatePage()
        currentPage.model.downloadUpdate()
        awaitWork(currentPage)

        // Releasing the old response must not alter a newer download's result. On Windows the
        // old open handle can instead make the newer rename fail; both violate the same contract.
        // All deletes/renames below are the production File operations on the host filesystem.
        previousTransfer.release()
        awaitJobs(listOf(previousPage.scopeJob))

        assertDownloadedApk(currentPage)
        assertEquals(2, network.downloadRequests.get())
    }

    private fun openUpdatePage(): Page {
        val model = AppUpdateViewModel(repository)
        val store = ViewModelStore().also { it.put("update", model) }
        val page = Page(model, store, model.viewModelScope.coroutineContext[Job]!!)
        pages.add(page)
        model.checkForUpdate()
        awaitWork(page)
        assertNotNull("The real release parser must make the controlled update available",
            model.uiState.value.updateInfo)
        assertNull(model.uiState.value.error)
        return page
    }

    private fun startAndHoldAfterFirstWrite(page: Page, transfer: Transfer) {
        page.model.downloadUpdate()
        transfer.awaitHeld()
        val part = File(cacheDirectory, "updates").listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".part") }
            .single()
        assertTrue("The old download must have opened its real temporary file", part.isFile)
        assertEquals("The HTTP boundary must be after the first real file write",
            FIRST_CHUNK_SIZE.toLong(), part.length())
    }

    private fun assertDownloadedApk(page: Page) {
        val state = page.model.uiState.value
        assertNull("The current page's download must succeed; actual error=${state.error}", state.error)
        assertNotNull("The current page must retain its completed APK", state.downloadedFile)
        val apk = state.downloadedFile!!
        assertTrue("The completed APK must remain present after the old request finishes", apk.isFile)
        assertArrayEquals("The retained APK must contain the complete download", APK_BYTES, apk.readBytes())
        assertEquals(100, state.downloadProgress)
    }

    private fun awaitWork(page: Page) = awaitJobs(page.scopeJob.children.toList())

    private fun awaitJobs(jobs: List<Job>) = runBlocking {
        withTimeout(10_000) { jobs.joinAll() }
    }

    private data class Page(
        val model: AppUpdateViewModel,
        val store: ViewModelStore,
        val scopeJob: Job
    )

    /** A response stream whose held read behaves like a socket read when its Call is cancelled. */
    private class Transfer(
        private val holdAfterFirstChunk: Boolean,
        private val failures: ConcurrentLinkedQueue<String>
    ) {
        private val held = CountDownLatch(1)
        private val released = CountDownLatch(if (holdAfterFirstChunk) 1 else 0)
        private val closed = CountDownLatch(1)
        @Volatile private var call: Call? = null

        val isClosed: Boolean get() = closed.count == 0L
        val isCancelled: Boolean get() = call?.isCanceled() == true

        fun awaitHeld() {
            assertTrue("The old HTTP stream must reach the controlled read after writing one chunk",
                held.await(10, TimeUnit.SECONDS))
        }

        fun awaitClosed(timeout: Long, unit: TimeUnit): Boolean = closed.await(timeout, unit)

        fun release() = released.countDown()

        fun respond(request: Request, actualCall: Call): Response {
            call = actualCall
            val source: BufferedSource = object : Source {
                private var offset = 0

                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (byteCount == 0L) return 0L
                    if (holdAfterFirstChunk && offset >= FIRST_CHUNK_SIZE) {
                        held.countDown()
                        var waitedMillis = 0
                        while (!released.await(20, TimeUnit.MILLISECONDS)) {
                            if (actualCall.isCanceled()) throw IOException("Controlled socket cancelled")
                            waitedMillis += 20
                            if (waitedMillis >= 30_000) {
                                val failure = "Fixture timed out releasing the old APK stream"
                                failures.add(failure)
                                throw IOException(failure)
                            }
                        }
                    }
                    if (actualCall.isCanceled()) throw IOException("Controlled socket cancelled")
                    if (offset == APK_BYTES.size) return -1L
                    val remaining = if (offset < FIRST_CHUNK_SIZE) FIRST_CHUNK_SIZE - offset
                        else APK_BYTES.size - offset
                    val count = minOf(byteCount, remaining.toLong()).toInt()
                    sink.write(APK_BYTES, offset, count)
                    offset += count
                    return count.toLong()
                }

                override fun timeout(): Timeout = Timeout.NONE

                override fun close() {
                    closed.countDown()
                    release()
                }
            }.buffer()
            val body = object : ResponseBody() {
                override fun contentType(): MediaType = "application/vnd.android.package-archive".toMediaType()
                override fun contentLength(): Long = APK_BYTES.size.toLong()
                override fun source(): BufferedSource = source
            }
            return response(request, body)
        }
    }

    private class ControlledNetwork {
        val failures = ConcurrentLinkedQueue<String>()
        val downloadRequests = AtomicInteger()
        private val pendingTransfers = ConcurrentLinkedQueue<Transfer>()
        private val allTransfers = ConcurrentLinkedQueue<Transfer>()
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                // No chain.proceed: GitHub and every other external address are unreachable.
                when {
                    request.url.host == "api.github.com" &&
                        request.url.encodedPath == "/repos/C0neF/My_Live/releases" ->
                        response(request, RELEASE_JSON.toResponseBody("application/json".toMediaType()))
                    request.url.host == "update.example.invalid" && request.url.encodedPath == "/update.apk" -> {
                        downloadRequests.incrementAndGet()
                        val transfer = pendingTransfers.poll() ?: unexpected(request)
                        transfer.respond(request, chain.call())
                    }
                    else -> unexpected(request)
                }
            })
            .build()

        fun download(holdAfterFirstChunk: Boolean): Transfer =
            Transfer(holdAfterFirstChunk, failures).also {
                pendingTransfers.add(it)
                allTransfers.add(it)
            }

        fun releaseAll() = allTransfers.forEach { it.release() }

        private fun unexpected(request: Request): Nothing {
            val failure = "Unexpected fixture request: ${request.url}"
            failures.add(failure)
            throw IOException(failure)
        }
    }

    private companion object {
        const val APK_NAME = "mylive-controlled.apk"
        const val FIRST_CHUNK_SIZE = 4_096
        val APK_BYTES = ByteArray(32_768) { (it % 251).toByte() }
        val RELEASE_JSON = """[{"tag_name":"v2.99.0","name":"Controlled update","draft":false,"prerelease":false,"assets":[{"name":"$APK_NAME","browser_download_url":"https://update.example.invalid/update.apk","size":${APK_BYTES.size}}]}]"""

        fun response(request: Request, body: ResponseBody): Response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("Controlled response")
            .body(body)
            .build()
    }
}
