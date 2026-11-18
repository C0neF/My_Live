package com.mylive.app.ui.emoji

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import com.mylive.app.core.common.EmojiAtlasRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun EmojiImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val atlasRef = remember(url) { EmojiAtlasRef.parse(url) }
    if (atlasRef != null) {
        val bitmapState = produceState<Bitmap?>(
            initialValue = EmojiAtlasRepository.getCachedBitmap(url),
            key1 = context,
            key2 = url
        ) {
            if (value == null) {
                value = withContext(Dispatchers.IO) {
                    EmojiAtlasRepository.getBitmap(context, url)
                }
            }
        }
        val bitmap = bitmapState.value
        if (bitmap != null) {
            val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = ContentScale.Fit
            )
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    }
}
