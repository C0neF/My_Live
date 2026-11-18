package com.mylive.desktop

import com.mylive.app.core.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopMessagePolicyTest {
    @get:Rule val folder = TemporaryFolder()
    private fun message(user: String = "user", text: String = "message") = LiveMessage(LiveMessageType.CHAT, user, text)

    @Test fun literalKeywordAndScopedUserShieldsApplyWithoutRegexExecution() {
        val policy = DesktopDanmakuPreferences(keywords = listOf("A[B]"), users = listOf(DesktopBlockedUser("douyu", "blocked")))
        assertFalse(policy.allows(message(text = "prefix a[b] suffix"), "huya"))
        assertFalse(policy.allows(message(user = "blocked"), "douyu"))
        assertTrue(policy.allows(message(user = "blocked"), "huya"))
        assertTrue(policy.copy(shieldEnabled = false).allows(message(text = "A[B]"), "douyu"))
        assertFalse(policy.copy(users = listOf(DesktopBlockedUser("__all__", "blocked"))).allows(message(user = "blocked"), "huya"))
    }

    @Test fun dedupeWindowExpiresAndCanBeScopedToTheSameUser() {
        val filter = DesktopMessageDeduplicator()
        val settings = DesktopDanmakuPreferences(deduplicate = true, dedupeSeconds = 10)
        assertTrue(filter.accepts(message("one"), settings, 0))
        assertFalse(filter.accepts(message("two"), settings, 9_999))
        assertTrue(filter.accepts(message("two"), settings, 10_000))
        val perUser = DesktopMessageDeduplicator()
        assertTrue(perUser.accepts(message("one"), settings.copy(dedupeByUser = true), 0))
        assertTrue(perUser.accepts(message("two"), settings.copy(dedupeByUser = true), 1))
        assertFalse(perUser.accepts(message("one"), settings.copy(dedupeByUser = true), 2))
    }

    @Test fun clockRollbackAndDisablingDedupeResetTheCache() {
        val filter = DesktopMessageDeduplicator()
        val settings = DesktopDanmakuPreferences(deduplicate = true)
        assertTrue(filter.accepts(message(), settings, 100))
        assertTrue(filter.accepts(message(), settings, 50))
        assertTrue(filter.accepts(message(), settings.copy(deduplicate = false), 51))
        assertTrue(filter.accepts(message(), settings, 52))
    }

    @Test fun preferredQualityUsesPlatformMemoryAndFallsBackToTheConfiguredLevel() {
        val high = LivePlayQuality("2K120", PlayQualityData.Douyin(emptyList()))
        val mid = high.copy(quality = "2K60"); val low = high.copy(quality = "720P")
        val choices = listOf(high, mid, low)
        val settings = DesktopPlayerPreferences(qualityLevel = 2, qualityBySite = mapOf("douyu" to "2K60"))
        assertEquals(mid, preferredDesktopQuality(choices, "douyu", settings))
        assertEquals(low, preferredDesktopQuality(choices, "huya", settings))
        assertEquals(low, preferredDesktopQuality(choices, "douyu", settings.copy(rememberQuality = false)))
        assertEquals(high, preferredDesktopQuality(listOf(high), "douyu", settings.copy(qualityLevel = 1)))
    }

    @Test fun danmakuPreferencesAndShieldsSurviveRestartAndRejectInvalidNativeInputs() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val file = folder.root.toPath().resolve("library.json")
        val library = DesktopLibrary(file, dispatcher)
        val expected = DesktopDanmakuPreferences(fontSize = 30f, opacity = .4f, area = .5f, lines = 4,
            keywords = listOf("keyword"), users = listOf(DesktopBlockedUser("huya", "user")), deduplicate = true)
        assertTrue(library.updateDanmaku { expected })
        assertFalse(library.updateDanmaku { it.copy(fontSize = Float.NaN) })
        assertFalse(library.updateDanmaku { it.copy(lines = 0) })
        assertFalse(library.updateDanmaku { it.copy(travelSeconds = -1f) })
        val restarted = DesktopLibrary(file, dispatcher); restarted.load()
        assertEquals(expected, restarted.state.value.danmaku)
    }

    @Test fun fixedAndScrollingPositionVisibilityAreIndependent() {
        val settings = DesktopDanmakuPreferences(scroll = false, top = true, bottom = false)
        assertFalse(settings.positionAllowed(message()))
        assertTrue(settings.positionAllowed(message().copy(danmakuPosition = LiveMessageDanmakuPosition.TOP)))
        assertFalse(settings.positionAllowed(message().copy(danmakuPosition = LiveMessageDanmakuPosition.BOTTOM)))
    }
}
