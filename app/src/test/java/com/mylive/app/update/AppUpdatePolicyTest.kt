package com.mylive.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppUpdatePolicyTest {

    @Test
    fun releaseParserSelectsFirstHttpsApkAssetAndStripsVersionPrefix() {
        val release = parseGitHubReleaseForUpdate(
            """
            {
              "tag_name": "v2.0.1",
              "name": "Release 2.0.1",
              "body": "修复播放和搜索问题",
              "html_url": "https://github.com/C0neF/My_Live/releases/tag/v2.0.1",
              "prerelease": false,
              "assets": [
                {
                  "name": "notes.txt",
                  "size": 12,
                  "browser_download_url": "https://github.com/C0neF/My_Live/releases/download/v2.0.1/notes.txt"
                },
                {
                  "name": "MyLive-v2.0.1.apk",
                  "size": 42424242,
                  "browser_download_url": "https://github.com/C0neF/My_Live/releases/download/v2.0.1/MyLive-v2.0.1.apk"
                }
              ]
            }
            """.trimIndent()
        )

        assertNotNull(release)
        requireNotNull(release)
        assertEquals("v2.0.1", release.tagName)
        assertEquals("2.0.1", release.versionName)
        assertEquals("MyLive-v2.0.1.apk", release.apkName)
        assertEquals(42424242L, release.apkSizeBytes)
        assertEquals("https://github.com/C0neF/My_Live/releases/download/v2.0.1/MyLive-v2.0.1.apk", release.apkDownloadUrl)
    }

    @Test
    fun releaseParserRejectsPrereleaseMissingApkAndCleartextDownloadUrl() {
        assertEquals(
            null,
            parseGitHubReleaseForUpdate(
                """{"tag_name":"v2.0.1","prerelease":true,"assets":[{"name":"MyLive.apk","browser_download_url":"https://example.com/MyLive.apk"}]}"""
            )
        )
        assertEquals(
            null,
            parseGitHubReleaseForUpdate(
                """{"tag_name":"v2.0.1","prerelease":false,"assets":[{"name":"notes.txt","browser_download_url":"https://example.com/notes.txt"}]}"""
            )
        )
        assertEquals(
            null,
            parseGitHubReleaseForUpdate(
                """{"tag_name":"v2.0.1","prerelease":false,"assets":[{"name":"MyLive.apk","browser_download_url":"http://example.com/MyLive.apk"}]}"""
            )
        )
    }

    @Test
    fun versionComparisonUsesNumericSegments() {
        assertTrue(isReleaseNewer(candidateVersionName = "2.0.1", currentVersionName = "2.0.0"))
        assertTrue(isReleaseNewer(candidateVersionName = "2.10.0", currentVersionName = "2.9.9"))
        assertFalse(isReleaseNewer(candidateVersionName = "2.0.0", currentVersionName = "2.0.0"))
        assertFalse(isReleaseNewer(candidateVersionName = "1.9.9", currentVersionName = "2.0.0"))
    }

    @Test
    fun installIntentPolicyUsesContentUriAndApkMimeType() {
        val source = File("src/main/java/com/mylive/app/update/AppUpdateInstaller.kt").readText()

        assertTrue(source.contains("FileProvider.getUriForFile("))
        assertTrue(source.contains("BuildConfig.APPLICATION_ID + \".fileprovider\""))
        assertTrue(source.contains("Intent.ACTION_VIEW"))
        assertTrue(source.contains("application/vnd.android.package-archive"))
        assertTrue(source.contains("Intent.FLAG_GRANT_READ_URI_PERMISSION"))
        assertFalse(source.contains("Uri.fromFile("))
    }

    @Test
    fun manifestDeclaresInstallPermissionAndNonExportedFileProvider() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val paths = File("src/main/res/xml/app_update_file_paths.xml").readText()

        assertTrue(manifest.contains("android.permission.REQUEST_INSTALL_PACKAGES"))
        assertTrue(manifest.contains("android:name=\"androidx.core.content.FileProvider\""))
        assertTrue(manifest.contains("android:authorities=\"\${applicationId}.fileprovider\""))
        assertTrue(manifest.contains("android:exported=\"false\""))
        assertTrue(manifest.contains("android:grantUriPermissions=\"true\""))
        assertTrue(manifest.contains("@xml/app_update_file_paths"))
        assertTrue(paths.contains("<cache-path"))
        assertTrue(paths.contains("path=\"updates/\""))
    }

    @Test
    fun settingsScreenExposesManualUpdateEntry() {
        val routeSource = File("src/main/java/com/mylive/app/ui/navigation/Route.kt").readText()
        val navSource = File("src/main/java/com/mylive/app/ui/navigation/AppNavGraph.kt").readText()
        val settingsSource = File("src/main/java/com/mylive/app/ui/screen/settings/SettingsScreen.kt").readText()

        assertTrue(routeSource.contains("data object SettingsUpdate : Route"))
        assertTrue(navSource.contains("entry<Route.SettingsUpdate>"))
        assertTrue(navSource.contains("AppUpdateScreen(navigator = navigator)"))
        assertTrue(settingsSource.contains("settings_update"))
        assertTrue(settingsSource.contains("navigator.navigate(Route.SettingsUpdate)"))
    }

    @Test
    fun updateScreenRetriesInstallAfterUnknownSourcePermissionIsGranted() {
        val source = File("src/main/java/com/mylive/app/ui/screen/settings/AppUpdateScreen.kt").readText()
        val launchBlock = source.substringAfter("val launchDownloadedUpdate: (File) -> Unit = { file ->")
            .substringBefore("LaunchedEffect(uiState.downloadedFile)")
        val resumeBlock = source.substringAfter("Lifecycle.Event.ON_RESUME ->")
            .substringBefore("else ->")

        assertTrue(source.contains("LifecycleEventObserver"))
        assertTrue(source.contains("LocalLifecycleOwner.current"))
        assertTrue(launchBlock.contains("AppUpdateInstaller.canRequestPackageInstalls(context)"))
        assertTrue(launchBlock.contains("launchedInstallFile = file"))
        assertTrue(launchBlock.contains("AppUpdateInstaller.createInstallIntent(context, file)"))
        assertTrue(launchBlock.contains("AppUpdateInstaller.createInstallPermissionIntent(context)"))
        assertTrue(resumeBlock.contains("val file = uiState.downloadedFile"))
        assertTrue(resumeBlock.contains("AppUpdateInstaller.canRequestPackageInstalls(context)"))
        assertTrue(resumeBlock.contains("launchDownloadedUpdate(file)"))
    }
}
