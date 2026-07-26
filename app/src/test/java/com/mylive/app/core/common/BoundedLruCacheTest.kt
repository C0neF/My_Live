package com.mylive.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BoundedLruCacheTest {

    @Test
    fun evictsTheLeastRecentlyUsedEntryOnceFull() {
        val cache = BoundedLruCache<String, Int>(maxEntries = 3)
        cache["a"] = 1
        cache["b"] = 2
        cache["c"] = 3

        // Touch "a" so "b" becomes the least recently used.
        assertEquals(1, cache["a"])
        cache["d"] = 4

        assertEquals(3, cache.size())
        assertNull(cache["b"])
        assertEquals(1, cache["a"])
        assertEquals(3, cache["c"])
        assertEquals(4, cache["d"])
    }

    @Test
    fun neverGrowsBeyondItsBound() {
        // The case that motivated this type: a singleton site adapter resolving room after room
        // for the lifetime of the process.
        val cache = BoundedLruCache<String, String>(maxEntries = 32)
        repeat(5_000) { index -> cache["room-$index"] = "handshake-$index" }

        assertEquals(32, cache.size())
        assertNull(cache["room-0"])
        assertEquals("handshake-4999", cache["room-4999"])
    }

    @Test
    fun overwritingAKeyDoesNotConsumeAnExtraSlot() {
        val cache = BoundedLruCache<String, Int>(maxEntries = 2)
        cache["a"] = 1
        cache["a"] = 2
        cache["b"] = 3

        assertEquals(2, cache.size())
        assertEquals(2, cache["a"])
        assertEquals(3, cache["b"])
    }

    @Test
    fun removeAndClearDropEntries() {
        val cache = BoundedLruCache<String, Int>(maxEntries = 4)
        cache["a"] = 1
        cache["b"] = 2

        assertEquals(1, cache.remove("a"))
        assertNull(cache["a"])
        assertNull(cache.remove("missing"))

        cache.clear()
        assertEquals(0, cache.size())
        assertNull(cache["b"])
    }

    @Test
    fun rejectsANonPositiveBound() {
        assertThrows(IllegalArgumentException::class.java) { BoundedLruCache<String, Int>(0) }
        assertThrows(IllegalArgumentException::class.java) { BoundedLruCache<String, Int>(-1) }
    }

    @Test
    fun staysConsistentUnderConcurrentWriters() {
        // Site caches are written from the follow-refresh worker and read from the room at the
        // same time; a bare LinkedHashMap would corrupt or throw here.
        val cache = BoundedLruCache<String, Int>(maxEntries = 16)
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)

        repeat(threads) { worker ->
            pool.execute {
                start.await()
                repeat(500) { index ->
                    cache["k-$worker-$index"] = index
                    cache["k-$worker-$index"]
                }
                done.countDown()
            }
        }
        start.countDown()

        assertEquals(true, done.await(30, TimeUnit.SECONDS))
        pool.shutdownNow()
        assertEquals(16, cache.size())
        assertNotNull(cache.size())
    }
}
