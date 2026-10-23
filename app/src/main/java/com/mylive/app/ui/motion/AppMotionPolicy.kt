package com.mylive.app.ui.motion

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.SpringSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

@Immutable
data class AppMotionPolicy(
    val reducedMotion: Boolean
) {
    fun <T> routeSpec(): SpringSpec<T> = AppMotion.routeSpec(reducedMotion)

    fun <T> contentSpec(): SpringSpec<T> = AppMotion.contentSpec(reducedMotion)

    fun <T> effectsSpec(): SpringSpec<T> = AppMotion.contentSpec(reducedMotion)

    companion object {
        val Default = AppMotionPolicy(reducedMotion = false)
    }
}

val LocalAppMotionPolicy = staticCompositionLocalOf { AppMotionPolicy.Default }

/**
 * App-wide interpretation of Android's animator scale.
 *
 * Compose already scales its clocks with this setting. The explicit policy additionally lets
 * spatial components replace large movement with non-spatial feedback instead of merely making
 * the same slide instantaneous.
 */
@Composable
fun ProvideAppMotionPolicy(content: @Composable () -> Unit) {
    val applicationContext = LocalContext.current.applicationContext
    val resolver = applicationContext.contentResolver
    var animatorScale by remember(resolver) {
        mutableFloatStateOf(readAnimatorDurationScale(resolver))
    }

    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                animatorScale = readAnimatorDurationScale(resolver)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer
        )
        onDispose {
            resolver.unregisterContentObserver(observer)
        }
    }

    val policy = remember(animatorScale) {
        AppMotionPolicy(reducedMotion = isReducedMotionScale(animatorScale))
    }
    CompositionLocalProvider(LocalAppMotionPolicy provides policy, content = content)
}

internal fun isReducedMotionScale(scale: Float): Boolean {
    return scale.isFinite() && scale <= 0f
}

private fun readAnimatorDurationScale(
    resolver: android.content.ContentResolver
): Float {
    return runCatching {
        Settings.Global.getFloat(
            resolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
    }.getOrDefault(1f)
}
