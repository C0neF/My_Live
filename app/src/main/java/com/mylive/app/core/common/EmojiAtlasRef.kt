package com.mylive.app.core.common

data class EmojiAtlasRef(
    val atlasId: String,
    val itemId: String
) {
    fun toUrl(): String = "$SCHEME://$atlasId/$itemId"

    companion object {
        const val SCHEME = "atlas"
        const val DOUYIN_ATLAS_ID = "douyin"

        fun douyin(itemId: String): EmojiAtlasRef = EmojiAtlasRef(DOUYIN_ATLAS_ID, itemId)

        fun parse(url: String): EmojiAtlasRef? {
            val prefix = "$SCHEME://"
            if (!url.startsWith(prefix)) return null

            val body = url.removePrefix(prefix)
            val separator = body.indexOf('/')
            if (separator <= 0 || separator == body.lastIndex) return null

            val atlasId = body.substring(0, separator)
            val itemId = body.substring(separator + 1)
            if (itemId.contains('/')) return null

            return EmojiAtlasRef(atlasId = atlasId, itemId = itemId)
        }
    }
}
