package com.mylive.app.ui.emoji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import com.mylive.app.core.common.EmojiAtlasRef
import org.json.JSONObject

object EmojiAtlasRepository {
    private const val DOUYIN_ATLAS_ASSET = "douyin_emoji_atlas.webp"
    private const val DOUYIN_METADATA_ASSET = "douyin_emoji_atlas.json"

    private val lock = Any()
    private var douyinAtlas: AtlasState? = null
    private val bitmapCache = LinkedHashMap<String, Bitmap>(128, 0.75f, true)

    fun getBitmap(context: Context, url: String): Bitmap? {
        val ref = EmojiAtlasRef.parse(url) ?: return null
        if (ref.atlasId != EmojiAtlasRef.DOUYIN_ATLAS_ID) return null

        synchronized(lock) {
            bitmapCache[url]?.takeUnless { it.isRecycled }?.let { return it }

            val atlas = douyinAtlas ?: loadDouyinAtlas(context).also { douyinAtlas = it }
            val rect = atlas.items[ref.itemId] ?: return null
            val bitmap = Bitmap.createBitmap(
                atlas.bitmap,
                rect.left,
                rect.top,
                rect.width(),
                rect.height()
            )
            bitmapCache[url] = bitmap
            trimBitmapCache()
            return bitmap
        }
    }

    private fun loadDouyinAtlas(context: Context): AtlasState {
        val assets = context.applicationContext.assets
        val bitmap = assets.open(DOUYIN_ATLAS_ASSET).use { input ->
            BitmapFactory.decodeStream(input)
        }
        val json = assets.open(DOUYIN_METADATA_ASSET).use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        }
        val root = JSONObject(json)
        val itemsJson = root.getJSONObject("items")
        val items = buildMap {
            val keys = itemsJson.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val item = itemsJson.getJSONObject(id)
                val x = item.getInt("x")
                val y = item.getInt("y")
                val width = item.getInt("width")
                val height = item.getInt("height")
                put(id, Rect(x, y, x + width, y + height))
            }
        }
        return AtlasState(bitmap = bitmap, items = items)
    }

    private fun trimBitmapCache() {
        while (bitmapCache.size > 128) {
            val eldestKey = bitmapCache.keys.firstOrNull() ?: return
            bitmapCache.remove(eldestKey)
        }
    }

    private data class AtlasState(
        val bitmap: Bitmap,
        val items: Map<String, Rect>
    )
}
