package com.mylive.desktop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MesaPackagingPolicyTest {
    @Test
    fun mesaIsInstalledOnlyIntoTheNativeTestRuntime() {
        val gradle = File("build.gradle.kts").readText()
        val install = gradle.substringAfter("val installMesaOpenGl").substringBefore("tasks.register<Test>(\"nativeSmokeTest\")")
        assertTrue(install.contains("nativeTestRuntime"))
        assertFalse(install.contains("windowsVideoResources"))
        val smoke = gradle.substringAfter("tasks.register<Test>(\"nativeSmokeTest\")").substringBefore("abstract class RejectMesaFromPackagedResources")
        assertTrue(smoke.contains("nativeTestRuntime.get().dir(\"windows/mpv\")"))
        assertFalse(smoke.contains("windowsVideoResources.get()"))
        val packaged = gradle.substringAfter("val prepareDesktopResources").substringBefore("val rejectMesaFromPackagedResources")
        assertTrue(packaged.contains("exclude(mesaDriverDlls)"))
    }

    @Test
    fun syncAfterTheNativeTestDoesNotPackageMesaDlls() {
        val root = File("build/tmp/mesa-packaging-policy").apply {
            deleteRecursively()
            mkdirs()
        }
        val video = File(root, "videoResources/windows/mpv").apply { mkdirs() }
        File(video, "libmpv-2.dll").writeText("official")
        val mesaNames = listOf("opengl32.dll", "libgallium_wgl.dll", "dxil.dll")
        val mesaBin = File(root, "mesa").apply { mkdirs() }
        mesaNames.forEach { File(mesaBin, it).writeText("llvmpipe") }

        val testRuntime = File(root, "nativeTestRuntime/windows/mpv").apply { mkdirs() }
        video.copyRecursively(testRuntime, overwrite = true)
        mesaNames.forEach { name -> File(mesaBin, name).copyTo(File(testRuntime, name), overwrite = true) }

        // A later package sync reads videoResources, which the test driver must not have changed.
        val packaged = File(root, "desktopResources/windows/mpv").apply { mkdirs() }
        video.listFiles()
            ?.filter { it.isFile && it.name !in mesaNames }
            ?.forEach { it.copyTo(File(packaged, it.name), overwrite = true) }
        mesaNames.forEach { name -> File(video, name).writeText("polluted") }
        packaged.listFiles()?.forEach { if (it.name in mesaNames) it.delete() }
        video.listFiles()
            ?.filter { it.isFile && it.name !in mesaNames }
            ?.forEach { it.copyTo(File(packaged, it.name), overwrite = true) }

        assertTrue(mesaNames.all { File(testRuntime, it).readText() == "llvmpipe" })
        assertTrue(File(video, "opengl32.dll").exists())
        assertTrue(packaged.listFiles().orEmpty().none { it.name in mesaNames })
        assertTrue(File(packaged, "libmpv-2.dll").readText() == "official")
    }
}
