package com.mylive.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mylive.app.data.local.datastore.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsDataStoreTest {

    private lateinit var settingsDataStore: SettingsDataStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        settingsDataStore = SettingsDataStore(context)
    }

    @After
    fun tearDown() = runBlocking {
        settingsDataStore.clearAll()
    }

    @Test
    fun testBooleanPreferences() = runBlocking {
        // Default value
        val defaultVal = settingsDataStore.getFlow(SettingsDataStore.DanmuEnable, true).first()
        assertTrue(defaultVal)

        // Write value
        settingsDataStore.setValue(SettingsDataStore.DanmuEnable, false)
        val writtenVal = settingsDataStore.getFlow(SettingsDataStore.DanmuEnable, true).first()
        assertFalse(writtenVal)
    }

    @Test
    fun testDoublePreferences() = runBlocking {
        // Default value
        val defaultVal = settingsDataStore.getFlow(SettingsDataStore.DanmuSize, 16.0).first()
        assertEquals(16.0, defaultVal, 0.001)

        // Write value
        settingsDataStore.setValue(SettingsDataStore.DanmuSize, 20.5)
        val writtenVal = settingsDataStore.getFlow(SettingsDataStore.DanmuSize, 16.0).first()
        assertEquals(20.5, writtenVal, 0.001)
    }

    @Test
    fun testStringPreferences() = runBlocking {
        // Default value
        val defaultVal = settingsDataStore.getFlow(SettingsDataStore.SiteSort, "").first()
        assertEquals("", defaultVal)

        // Write value
        settingsDataStore.setValue(SettingsDataStore.SiteSort, "bilibili,douyu")
        val writtenVal = settingsDataStore.getFlow(SettingsDataStore.SiteSort, "").first()
        assertEquals("bilibili,douyu", writtenVal)

        // Remove value
        settingsDataStore.removeValue(SettingsDataStore.SiteSort)
        val removedVal = settingsDataStore.getFlow(SettingsDataStore.SiteSort, "default").first()
        assertEquals("default", removedVal)
    }
}
