package com.mylive.app.core.model

/** A device-local override; selecting Phone also disables automatic tablet layout. */
enum class AppLayoutMode(val persistedValue: Int) {
    Auto(0),
    Phone(1),
    Tablet(2);

    companion object {
        fun fromPersistedValue(value: Int): AppLayoutMode =
            entries.firstOrNull { it.persistedValue == value } ?: Auto
    }
}