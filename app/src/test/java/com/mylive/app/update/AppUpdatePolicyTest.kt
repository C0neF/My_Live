package com.mylive.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppUpdatePolicyTest {

    @Test
    fun releaseParserSelectsHttpsApkAndStripsVersionPrefix() {
        val release = parseGitHubReleaseForUpdate(
            """
            {
              "tag_name": "v1.1.3",
              "name": "Release 1.1.3",
              "body": "修复播放问题",
              "html_url": "https://github.com/C0neF/My_Live/releases/tag/v1.1.3",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "notes.txt",
                  "size": 12,
                  "browser_download_url": "https://github.com/C0neF/My_Live/releases/download/v1.1.3/notes.txt"
                },
                {
                  "name": "MyLive-v1.1.3.apk",
                  "size": 42424242,
                  "browser_download_url": "https://github.com/C0neF/My_Live/releases/download/v1.1.3/MyLive-v1.1.3.apk"
                }
              ]
            }
            """.trimIndent()
        )

        assertNotNull(release)
        requireNotNull(release)
        assertEquals("v1.1.3", release.tagName)
        assertEquals("1.1.3", release.versionName)
        assertEquals("MyLive-v1.1.3.apk", release.apkName)
        assertEquals(42424242L, release.apkSizeBytes)
    }

    @Test
    fun v1ChannelSelectsHighestNewerStableReleaseAndIgnoresV2() {
        val release = selectLatestStableReleaseForMajor(
            releasesJson = """
                [
                  {
                    "tag_name": "v2.0.0",
                    "draft": false,
                    "prerelease": false,
                    "assets": [
                      {
                        "name": "MyLive-v2.0.0.apk",
                        "browser_download_url": "https://example.com/MyLive-v2.0.0.apk"
                      }
                    ]
                  },
                  {
                    "tag_name": "v1.2.0",
                    "draft": false,
                    "prerelease": false,
                    "assets": [
                      {
                        "name": "MyLive-v1.2.0.apk",
                        "browser_download_url": "https://example.com/MyLive-v1.2.0.apk"
                      }
                    ]
                  },
                  {
                    "tag_name": "v1.10.0",
                    "draft": false,
                    "prerelease": false,
                    "assets": [
                      {
                        "name": "MyLive-v1.10.0.apk",
                        "browser_download_url": "https://example.com/MyLive-v1.10.0.apk"
                      }
                    ]
                  }
                ]
            """.trimIndent(),
            currentVersionName = "1.1.2",
            majorVersion = 1
        )

        assertNotNull(release)
        assertEquals("1.10.0", release?.versionName)
    }

    @Test
    fun currentMajorChannelSelectsV2UpdatesForV2BuildsAndIgnoresV1() {
        val release = selectLatestStableReleaseForCurrentMajor(
            releasesJson = """
                [
                  {
                    "tag_name": "v1.10.0",
                    "draft": false,
                    "prerelease": false,
                    "assets": [
                      {
                        "name": "MyLive-v1.10.0.apk",
                        "browser_download_url": "https://example.com/MyLive-v1.10.0.apk"
                      }
                    ]
                  },
                  {
                    "tag_name": "v2.0.1",
                    "draft": false,
                    "prerelease": false,
                    "assets": [
                      {
                        "name": "MyLive-v2.0.1.apk",
                        "browser_download_url": "https://example.com/MyLive-v2.0.1.apk"
                      }
                    ]
                  }
                ]
            """.trimIndent(),
            currentVersionName = "2.0.0"
        )

        assertNotNull(release)
        assertEquals("2.0.1", release?.versionName)
    }

    @Test
    fun v1ChannelRejectsDraftPrereleaseInvalidAssetsAndInstalledVersion() {
        val release = selectLatestStableReleaseForMajor(
            releasesJson = """
                [
                  {
                    "tag_name": "v1.3.0",
                    "draft": true,
                    "prerelease": false,
                    "assets": [
                      {
                        "name": "draft.apk",
                        "browser_download_url": "https://example.com/draft.apk"
                      }
                    ]
                  },
                  {
                    "tag_name": "v1.2.0",
                    "draft": false,
                    "prerelease": true,
                    "assets": [
                      {
                        "name": "preview.apk",
                        "browser_download_url": "https://example.com/preview.apk"
                      }
                    ]
                  },
                  {
                    "tag_name": "v1.1.4",
                    "draft": false,
                    "prerelease": false,
                    "assets": [
                      {
                        "name": "cleartext.apk",
                        "browser_download_url": "http://example.com/cleartext.apk"
                      }
                    ]
                  },
                  {
                    "tag_name": "v1.1.2",
                    "draft": false,
                    "prerelease": false,
                    "assets": [
                      {
                        "name": "installed.apk",
                        "browser_download_url": "https://example.com/installed.apk"
                      }
                    ]
                  }
                ]
            """.trimIndent(),
            currentVersionName = "1.1.2",
            majorVersion = 1
        )

        assertEquals(null, release)
    }

    @Test
    fun versionComparisonUsesNumericSegments() {
        assertTrue(isReleaseNewer(candidateVersionName = "1.1.3", currentVersionName = "1.1.2"))
        assertTrue(isReleaseNewer(candidateVersionName = "1.10.0", currentVersionName = "1.9.9"))
        assertTrue(isReleaseNewer(candidateVersionName = "v1.2.0", currentVersionName = "V1.1.2"))
        assertFalse(isReleaseNewer(candidateVersionName = "1.1.2", currentVersionName = "1.1.2"))
        assertFalse(isReleaseNewer(candidateVersionName = "1.0.9", currentVersionName = "1.1.2"))
    }

    @Test
    fun releaseParserTreatsNullableMetadataAsEmpty() {
        val release = parseGitHubReleaseForUpdate(
            """
            {
              "tag_name": "v1.1.3",
              "name": null,
              "body": null,
              "html_url": null,
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "MyLive-v1.1.3.apk",
                  "browser_download_url": "https://example.com/MyLive-v1.1.3.apk"
                }
              ]
            }
            """.trimIndent()
        )

        assertNotNull(release)
        assertEquals("v1.1.3", release?.releaseName)
        assertEquals("", release?.releaseNotes)
        assertEquals("", release?.releasePageUrl)
    }

    @Test
    fun repositoryUsesReleaseListAndCurrentMajorStableChannel() {
        val source = File("src/main/java/com/mylive/app/update/AppUpdateRepository.kt").readText()

        assertTrue(source.contains("/releases"))
        assertFalse(source.contains("/releases/latest"))
        assertFalse(source.contains("majorVersion = 1"))
        assertTrue(source.contains("selectLatestStableReleaseForCurrentMajor("))
    }

    @Test
    fun repositoryDownloadsToBoundedTemporaryFileAndCleansFailures() {
        val source = File("src/main/java/com/mylive/app/update/AppUpdateRepository.kt").readText()

        assertTrue(source.contains("MAX_APK_SIZE_BYTES"))
        assertTrue(source.contains("\"${'$'}{targetFile.name}.part\""))
        assertTrue(source.contains("downloadedBytes <= MAX_APK_SIZE_BYTES"))
        assertTrue(source.contains("temporaryFile.delete()"))
        assertTrue(source.contains("temporaryFile.renameTo(targetFile)"))
        assertFalse(source.contains("targetFile.outputStream()"))
    }

    @Test
    fun installIntentUsesContentUriAndApkMimeType() {
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
    fun settingsScreenExposesCurrentMajorUpdateEntry() {
        val routeSource = File("src/main/java/com/mylive/app/ui/navigation/Route.kt").readText()
        val navSource = File("src/main/java/com/mylive/app/ui/navigation/AppNavGraph.kt").readText()
        val settingsSource = File("src/main/java/com/mylive/app/ui/screen/settings/SettingsScreen.kt").readText()
        val updateSource = File("src/main/java/com/mylive/app/ui/screen/settings/AppUpdateScreen.kt").readText()
        val viewModelSource = File("src/main/java/com/mylive/app/ui/screen/settings/AppUpdateViewModel.kt").readText()

        assertTrue(routeSource.contains("data object SettingsUpdate : Route"))
        assertTrue(navSource.contains("entry<Route.SettingsUpdate>"))
        assertTrue(navSource.contains("AppUpdateScreen(navigator = navigator)"))
        assertTrue(settingsSource.contains("settings_update"))
        assertTrue(settingsSource.contains("navigator.navigate(Route.SettingsUpdate)"))
        assertTrue(updateSource.contains("BuildConfig.VERSION_NAME.substringBefore('.')"))
        assertTrue(updateSource.contains("\"v${'$'}updateMajorVersion 稳定版\""))
        assertTrue(updateSource.contains("更新通道：${'$'}updateChannelName"))
        assertTrue(updateSource.contains("GitHub ${'$'}updateChannelName"))
        assertTrue(viewModelSource.contains("BuildConfig.VERSION_NAME.substringBefore('.')"))
        assertFalse(updateSource.contains("v1.x 稳定版"))
        assertFalse(viewModelSource.contains("v1.x 稳定版"))
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
