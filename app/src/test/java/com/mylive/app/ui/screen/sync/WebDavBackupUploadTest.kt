package com.mylive.app.ui.screen.sync

import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class WebDavBackupUploadTest {

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
