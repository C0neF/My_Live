package com.mylive.app.ui.screen.room.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioural cover for the PlayerView contract.
 *
 * This used to assert on the text of `PlayerView.kt` — it matched the parameter list and counted
 * `PlayerView(` occurrences, so reformatting broke it while a real behaviour change (say, drawing
 * controls over the PiP window) sailed through. The chrome-suppression rules are now pure
 * functions, so they can be checked directly.
 */
class PlayerViewContractPolicyTest {

    @Test
    fun controlsFollowTheAutoHideTimerOutsidePictureInPicture() {
        assertTrue(shouldShowPlayerControls(controlsVisible = true, isInPictureInPicture = false))
        assertFalse(shouldShowPlayerControls(controlsVisible = false, isInPictureInPicture = false))
    }

    @Test
    fun pictureInPictureSuppressesControlsEvenWhileTheyAreDue() {
        assertFalse(shouldShowPlayerControls(controlsVisible = true, isInPictureInPicture = true))
    }

    @Test
    fun loadingSpinnerNeedsAControllerAndNoError() {
        assertTrue(
            shouldShowPlayerLoading(
                isLoading = true,
                hasError = false,
                hasController = true,
                isInPictureInPicture = false
            )
        )
        assertFalse(
            shouldShowPlayerLoading(
                isLoading = true,
                hasError = true,
                hasController = true,
                isInPictureInPicture = false
            )
        )
        assertFalse(
            shouldShowPlayerLoading(
                isLoading = true,
                hasError = false,
                hasController = false,
                isInPictureInPicture = false
            )
        )
    }

    @Test
    fun pictureInPictureSuppressesTheLoadingSpinner() {
        assertFalse(
            shouldShowPlayerLoading(
                isLoading = true,
                hasError = false,
                hasController = true,
                isInPictureInPicture = true
            )
        )
    }

    @Test
    fun aReadOnlySurfaceNeedsNoActionWiring() {
        // The whole point of the defaults: the PiP call site supplies state only, and every
        // interaction is inert rather than wired to an empty lambda at each call site.
        val actions = PlayerViewActions()

        assertNull(actions.onBack)
        assertNull(actions.onQualityClick)
        assertNull(actions.onRefreshClick)
        assertNull(actions.onFullscreenClick)
        assertNull(actions.onControlsLockChange)
        assertNull(actions.danmaku.onEnabledChange)
        assertNull(actions.danmaku.onSizeChange)
        assertNull(actions.danmaku.onControllerCreated)
    }

    @Test
    fun uiStateIsValueComparableSoRecompositionCanSkip() {
        // PlayerViewUiState is rebuilt inline on every recomposition of the room; it has to
        // compare equal when nothing changed, or PlayerView can never skip.
        val base = PlayerViewUiState(title = "room", danmaku = DanmakuConfig(size = 18.0))
        val same = PlayerViewUiState(title = "room", danmaku = DanmakuConfig(size = 18.0))
        val different = PlayerViewUiState(title = "room", danmaku = DanmakuConfig(size = 20.0))

        assertTrue(base == same)
        assertFalse(base == different)
    }
}
