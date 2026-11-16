package com.mylive.app.core.sync

/**
 * Identity of the JSON profile backup both Android and desktop read and write.
 * The document type and schema range are shared. Each app still maps rows onto its own storage.
 */
object ProfileBackupFormat {
    const val TYPE = "my_live_profile"
    const val SCHEMA_VERSION = 3
    const val MIN_SCHEMA_VERSION = 1

    fun accepts(version: Int): Boolean = version in MIN_SCHEMA_VERSION..SCHEMA_VERSION
}
