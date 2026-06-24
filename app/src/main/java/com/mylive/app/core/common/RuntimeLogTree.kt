package com.mylive.app.core.common

import timber.log.Timber

class RuntimeLogTree(
    private val logToLogcat: Boolean
) : Timber.DebugTree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        CoreLog.recordFromTimber(priority, tag, message)
        if (logToLogcat) {
            super.log(priority, tag, message, t)
        }
    }
}
