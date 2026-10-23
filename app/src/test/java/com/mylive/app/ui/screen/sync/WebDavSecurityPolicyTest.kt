package com.mylive.app.ui.screen.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WebDavSecurityPolicyTest {

    @Test
    fun buildWebDavBackupUrlRejectsCleartextHttp() {
        assertThrows(IllegalArgumentException::class.java) {
            buildWebDavBackupUrl("http://dav.example.com/backups")
        }
    }

    @Test
    fun buildWebDavBackupUrlAppendsDefaultBackupFileToHttpsDirectory() {
        val url = buildWebDavBackupUrl(" https://dav.example.com/backups/ ")

        assertEquals("https://dav.example.com/backups/mylive_profile_backup.json", url)
    }

    @Test
    fun buildWebDavBackupUrlKeepsHttpsJsonFileUrl() {
        val url = buildWebDavBackupUrl("https://dav.example.com/backups/profile.json")

        assertEquals("https://dav.example.com/backups/profile.json", url)
    }

    @Test
    fun nutstoreRootUsesAnApplicationCollection() {
        listOf(
            "https://dav.jianguoyun.com/dav/",
            "https://dav.jianguoyun.com/dav",
            "https://dav.jianguoyun.com/"
        ).forEach { root ->
            assertEquals(
                "https://dav.jianguoyun.com/dav/MyLive/mylive_profile_backup.json",
                buildWebDavBackupUrl(root)
            )
        }
    }

    @Test
    fun explicitNutstoreFolderAndFileArePreserved() {
        assertEquals(
            "https://dav.jianguoyun.com/dav/custom/mylive_profile_backup.json",
            buildWebDavBackupUrl("https://dav.jianguoyun.com/dav/custom/")
        )
        assertEquals(
            "https://dav.jianguoyun.com/dav/custom/profile.json",
            buildWebDavBackupUrl("https://dav.jianguoyun.com/dav/custom/profile.json")
        )
    }

    @Test
    fun anotherServersDavRootKeepsItsExistingBackupLocation() {
        assertEquals(
            "https://dav.example.com/dav/mylive_profile_backup.json",
            buildWebDavBackupUrl("https://dav.example.com/dav/")
        )
    }

    @Test
    fun fileNameIsAppendedToThePathBeforeTheQuery() {
        assertEquals(
            "https://dav.example.com/backups/mylive_profile_backup.json?token=test",
            buildWebDavBackupUrl("https://dav.example.com/backups/?token=test")
        )
        assertEquals(
            "https://dav.example.com/backups/profile.json?token=test",
            buildWebDavBackupUrl("https://dav.example.com/backups/profile.json?token=test")
        )
    }
}
