package com.mylive.app.data

import com.mylive.app.data.local.entity.ShieldEntity
import com.mylive.app.testing.ReviewRoomFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShieldKeywordMergeTest : ReviewRoomFixture() {
    @Test
    fun repeatingMergeDoesNotDuplicateRulesAndOneDeleteRemovesTheRule() = runBlocking {
        shields.addShield(ShieldEntity(value = "user:__all__:keep-user"))
        shields.importKeywords(listOf("same-keyword", "same-keyword"), overlay = false)
        shields.importKeywords(listOf("same-keyword"), overlay = false)

        val keywordRows = shields.getAllShields().first().filter { it.value.startsWith("keyword:") }
        assertEquals("A repeated merge must leave one rule for this keyword", 1, keywordRows.size)
        shields.removeShield(keywordRows.single().id)

        val remaining = shields.getAllShields().first()
        assertTrue(remaining.none { it.value == "keyword:same-keyword" })
        assertEquals(listOf("user:__all__:keep-user"), remaining.map { it.value })
    }

    @Test
    fun concurrentMergesPreserveExistingRowsAndAddEachIncomingRuleOnce() = runBlocking {
        shields.addShield(ShieldEntity(value = "keyword:existing"))
        shields.addShield(ShieldEntity(value = "user:douyu:keep-user"))
        val original = shields.getAllShields().first().associateBy { it.value }
        val start = CompletableDeferred<Unit>()
        val imports = List(8) { index ->
            launch(Dispatchers.Default) {
                start.await()
                shields.importKeywords(listOf("existing", "shared", "unique-$index", "shared"),
                    overlay = false)
            }
        }

        start.complete(Unit)
        withTimeout(10_000) { imports.joinAll() }

        val actual = shields.getAllShields().first()
        val expectedValues = setOf("keyword:existing", "keyword:shared", "user:douyu:keep-user") +
            (0 until 8).map { "keyword:unique-$it" }
        assertEquals(expectedValues, actual.map { it.value }.toSet())
        assertEquals("Concurrent imports must not insert duplicate values", expectedValues.size, actual.size)
        original.forEach { (value, row) -> assertEquals(row, actual.single { it.value == value }) }
    }

    @Test
    fun overlayReplacesOnlyKeywordsAndDeduplicatesTheIncomingValues() = runBlocking {
        shields.addShield(ShieldEntity(value = "keyword:old"))
        shields.addShield(ShieldEntity(value = "user:__all__:keep-user"))
        val user = shields.getAllShields().first().single { it.value.startsWith("user:") }

        shields.importKeywords(listOf("new", "new"), overlay = true)

        val actual = shields.getAllShields().first()
        assertEquals(2, actual.size)
        assertEquals(user, actual.single { it.value.startsWith("user:") })
        assertEquals("keyword:new", actual.single { it.value.startsWith("keyword:") }.value)
    }
}
