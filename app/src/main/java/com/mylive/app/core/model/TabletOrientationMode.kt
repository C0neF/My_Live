package com.mylive.app.core.model

/**
 * User-owned orientation policy for tablet layouts.
 *
 * New installations follow the system. Existing explicit portrait/landscape choices retain their
 * persisted values; [Uninitialized] preserves migration of the legacy first-launch setting.
 */
enum class TabletOrientationMode(val persistedValue: Int) {
    Uninitialized(0),
    Portrait(1),
    Landscape(2),
    FollowSystem(3);

    companion object {
        fun fromPersistedValue(value: Int): TabletOrientationMode {
            return entries.firstOrNull { it.persistedValue == value } ?: Uninitialized
        }
    }
}
