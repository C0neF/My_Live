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
}
