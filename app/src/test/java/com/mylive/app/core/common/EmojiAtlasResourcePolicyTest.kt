package com.mylive.app.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmojiAtlasResourcePolicyTest {

    @Test
    fun douyinEmojiAssetsAreConsolidatedIntoOneAtlas() {
        val root = findProjectRoot()
        val assets = root.resolve("app/src/main/assets")

        assertTrue(assets.resolve("douyin_emoji_atlas.webp").isFile)
        assertTrue(assets.resolve("douyin_emoji_atlas.json").isFile)
        assertFalse(assets.resolve("douyin_emoji").exists())
    }

    @Test
    fun douyinEmojiAtlasMetadataCoversAllEmojiIds() {
        val root = findProjectRoot()
        val metadata = root.resolve("app/src/main/assets/douyin_emoji_atlas.json").readText()

        assertTrue(metadata.contains("\"asset\":\"douyin_emoji_atlas.webp\""))
        assertEquals(141, Regex("\"x\":").findAll(metadata).count())
        assertTrue(metadata.contains("\"1f642\""))
        assertTrue(metadata.contains("\"cpc\""))
    }

    private fun findProjectRoot(): File {
        return generateSequence(File(".").canonicalFile) { it.parentFile }
            .firstOrNull { candidate ->
                candidate.resolve("settings.gradle.kts").isFile &&
                    candidate.resolve("app/build.gradle.kts").isFile
            }
            ?: error("Cannot locate My_Live project root")
    }
}
