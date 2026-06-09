package com.mylive.app.ui.screen.room.player

import com.mylive.app.core.model.LiveMessage

internal class DanmakuDedupeWindow {
    private val recentFingerprints = ArrayDeque<String>()
    private val recentCounts = HashMap<String, Int>()
    private var enabled = false
    private var windowSize = 20
    private var stepSize = 2
    private var strictMode = false
    private var eventsSincePrune = 0

    fun configure(
        enabled: Boolean,
        windowSize: Int,
        stepSize: Int,
        strictMode: Boolean
    ) {
        this.enabled = enabled
        this.windowSize = windowSize.coerceIn(1, 100)
        this.stepSize = stepSize.coerceIn(1, 20)
        if (this.strictMode != strictMode) {
            clear()
        }
        this.strictMode = strictMode
    }

    fun shouldDrop(message: LiveMessage): Boolean {
        if (!enabled) return false
        val fingerprint = message.fingerprint()
        if (fingerprint.isEmpty()) return false

        val duplicate = recentCounts.containsKey(fingerprint)
        recentFingerprints.addLast(fingerprint)
        recentCounts[fingerprint] = (recentCounts[fingerprint] ?: 0) + 1
        eventsSincePrune += 1

        val shouldPrune = eventsSincePrune >= stepSize ||
            recentFingerprints.size > windowSize + stepSize - 1
        if (shouldPrune) {
            eventsSincePrune = 0
            while (recentFingerprints.size > windowSize) {
                val removed = recentFingerprints.removeFirst()
                val nextCount = (recentCounts[removed] ?: 0) - 1
                if (nextCount <= 0) {
                    recentCounts.remove(removed)
                } else {
                    recentCounts[removed] = nextCount
                }
            }
        }

        return duplicate
    }

    fun clear() {
        recentFingerprints.clear()
        recentCounts.clear()
        eventsSincePrune = 0
    }

    private fun LiveMessage.fingerprint(): String {
        val normalizedMessage = message.trim().lowercase()
        if (normalizedMessage.isEmpty()) return ""
        if (strictMode) return normalizedMessage
        val normalizedUser = userName.trim().lowercase()
        return "$normalizedUser:$normalizedMessage"
    }
}
