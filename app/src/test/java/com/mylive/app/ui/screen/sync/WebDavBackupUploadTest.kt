package com.mylive.app.ui.screen.sync

import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class WebDavBackupUploadTest {

    @Test
    fun nutstoreRootBackupCreatesTheApplicationFolderBeforeRetryingUpload() {
        val server = MockWebServer()
        var folderCreated = false
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val code = when (request.method to request.path) {
                    "PUT" to "/dav/mylive_profile_backup.json" -> 403
                    "MKCOL" to "/dav/" -> 405
                    "PUT" to "/dav/MyLive/mylive_profile_backup.json" -> if (folderCreated) 201 else 403
                    "MKCOL" to "/dav/MyLive/" -> {
                        folderCreated = true
                        201
                    }
                    else -> 404
                }
                return MockResponse().setResponseCode(code)
            }
        }
        server.start()
        try {
            val resolvedPath = buildWebDavBackupUrl("https://dav.jianguoyun.com/dav/")
                .toHttpUrl().encodedPath
            uploadWebDavBackup(
                OkHttpClient(), server.url(resolvedPath).toString(), "alice", "app-password", "{}"
            )

            val firstPut = server.takeRequest(1, TimeUnit.SECONDS)!!
            val create = server.takeRequest(1, TimeUnit.SECONDS)!!
            val retry = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("/dav/MyLive/mylive_profile_backup.json", firstPut.path)
            assertEquals("MKCOL", create.method)
            assertEquals("/dav/MyLive/", create.path)
            assertEquals("PUT", retry.method)
            assertEquals(firstPut.path, retry.path)
            listOf(firstPut, create, retry).forEach {
                assertEquals(Credentials.basic("alice", "app-password"), it.getHeader("Authorization"))
            }
            assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun uploadSendsSinglePutWhenServerAcceptsBackup() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(204))
        server.start()

        try {
            val backupUrl = server.url("/backups/mylive_profile_backup.json").toString()

            uploadWebDavBackup(
                okHttpClient = OkHttpClient(),
                backupUrl = backupUrl,
                username = "",
                password = "",
                backupJson = """{"ok":true}"""
            )

            val put = server.takeRequest()
            assertEquals("PUT", put.method)
            assertEquals("/backups/mylive_profile_backup.json", put.path)
            assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun uploadRetriesAfterCreatingParentCollectionWhenInitialPutReturnsNotFound() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(201))
        server.start()

        try {
            val backupUrl = server.url("/backups/mylive_profile_backup.json").toString()

            uploadWebDavBackup(
                okHttpClient = OkHttpClient(),
                backupUrl = backupUrl,
                username = "alice",
                password = "secret",
                backupJson = """{"ok":true}"""
            )

            val expectedAuth = Credentials.basic("alice", "secret")
            val firstPut = server.takeRequest()
            val mkcol = server.takeRequest()
            val retryPut = server.takeRequest()

            assertEquals("PUT", firstPut.method)
            assertEquals("/backups/mylive_profile_backup.json", firstPut.path)
            assertEquals(expectedAuth, firstPut.getHeader("Authorization"))
            assertEquals("""{"ok":true}""", firstPut.body.readUtf8())

            assertEquals("MKCOL", mkcol.method)
            assertEquals("/backups/", mkcol.path)
            assertEquals(expectedAuth, mkcol.getHeader("Authorization"))
            assertEquals("0", mkcol.getHeader("Content-Length"))

            assertEquals("PUT", retryPut.method)
            assertEquals("/backups/mylive_profile_backup.json", retryPut.path)
            assertEquals(expectedAuth, retryPut.getHeader("Authorization"))
            assertEquals("""{"ok":true}""", retryPut.body.readUtf8())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun uploadRepairsMissingParentWhenServerSignalsForbiddenOnInitialPut() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(204))
        server.start()

        try {
            val backupUrl = server.url("/backups/mylive_profile_backup.json").toString()

            uploadWebDavBackup(
                okHttpClient = OkHttpClient(),
                backupUrl = backupUrl,
                username = "alice",
                password = "secret",
                backupJson = """{"ok":true}"""
            )

            assertEquals("PUT", server.takeRequest().method)
            assertEquals("MKCOL", server.takeRequest().method)
            assertEquals("PUT", server.takeRequest().method)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun genuineForbiddenCollectionStillFailsWithoutReportingBackupSuccess() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(MockResponse().setResponseCode(403))
        server.start()

        try {
            val backupUrl = server.url("/private/mylive_profile_backup.json").toString()

            val error = assertThrows(IOException::class.java) {
                uploadWebDavBackup(
                    okHttpClient = OkHttpClient(),
                    backupUrl = backupUrl,
                    username = "alice",
                    password = "wrong",
                    backupJson = """{"ok":true}"""
                )
            }

            assertEquals("HTTP 403: Client Error", error.message)
            assertEquals("PUT", server.takeRequest().method)
            assertEquals("MKCOL", server.takeRequest(1, TimeUnit.SECONDS)?.method)
            assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun uploadRetriesWhenParentCollectionAlreadyExists() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(409))
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(MockResponse().setResponseCode(204))
        server.start()

        try {
            val backupUrl = server.url("/backups/mylive_profile_backup.json").toString()

            uploadWebDavBackup(
                okHttpClient = OkHttpClient(),
                backupUrl = backupUrl,
                username = "",
                password = "",
                backupJson = """{"ok":true}"""
            )

            assertEquals("PUT", server.takeRequest().method)
            assertEquals("MKCOL", server.takeRequest().method)
            assertEquals("PUT", server.takeRequest().method)
        } finally {
            server.shutdown()
        }
    }
}
