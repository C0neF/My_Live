package com.mylive.app.ui.theme

import androidx.compose.ui.tooling.preview.Preview

/**
 * Preview annotations for different form factors.
 * Use these to verify UI on phone, foldable, tablet, and desktop.
 */
@Preview(name = "Phone", device = "id:pixel_7", showBackground = true)
@Preview(name = "Foldable", device = "id:pixel_fold", showBackground = true)
@Preview(name = "Tablet", device = "id:pixel_tablet", showBackground = true)
annotation class FormFactorPreviews

@Preview(name = "Phone Dark", device = "id:pixel_7", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Tablet Dark", device = "id:pixel_tablet", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
annotation class DarkFormFactorPreviews

@Preview(name = "Phone", device = "id:pixel_7", showBackground = true)
@Preview(name = "Phone - Font Scale 1.5", device = "id:pixel_7", showBackground = true, fontScale = 1.5f)
annotation class FontScalePreviews
