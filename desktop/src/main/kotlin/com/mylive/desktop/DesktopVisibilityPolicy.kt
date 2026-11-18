package com.mylive.desktop

internal enum class VisibilityPlaybackAction { None, Pause, Resume }

/** Resume only playback that this policy paused, rather than overriding a user's pause. */
internal class DesktopVisibilityPolicy {
    private var resumeAfterHide = false
    fun update(visible: Boolean, backgroundAllowed: Boolean, playing: Boolean, hasMedia: Boolean): VisibilityPlaybackAction {
        if (!hasMedia) { resumeAfterHide = false; return VisibilityPlaybackAction.None }
        if (!visible && !backgroundAllowed) {
            resumeAfterHide = resumeAfterHide || playing
            return if (playing) VisibilityPlaybackAction.Pause else VisibilityPlaybackAction.None
        }
        if (resumeAfterHide) { resumeAfterHide = false; return VisibilityPlaybackAction.Resume }
        return VisibilityPlaybackAction.None
    }
    fun manuallyChanged() { resumeAfterHide = false }
}
