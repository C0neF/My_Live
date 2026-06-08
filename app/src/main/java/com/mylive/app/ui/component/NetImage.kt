package com.mylive.app.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun NetImage(
    url: String,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    isCircle: Boolean = false
) {
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .then(
                if (isCircle) Modifier.clip(CircleShape) else Modifier
            )
    )
}
