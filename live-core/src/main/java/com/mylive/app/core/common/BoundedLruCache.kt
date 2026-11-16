package com.mylive.app.core.common

/**
 * A small, thread-safe, insertion-bounded LRU map.
 *
 * Site adapters are `@Singleton`, so anything they cache per room lives for the whole process.
 * A plain `ConcurrentHashMap` therefore grows with every room the app ever resolves, and that
 * growth is not bounded by user navigation: the follow-refresh worker resolves every followed
 * room on a timer, so a long-running session keeps accumulating entries. Those entries also hold
 * account cookies and signed handshake tokens, so keeping copies of them indefinitely is both a
 * memory and a credential-lifetime concern.
 *
 * Deliberately not `android.util.LruCache`: this type is used from `core/` and must stay usable
 * in plain JVM unit tests, without Robolectric.
 */
class BoundedLruCache<K : Any, V : Any>(private val maxEntries: Int) {

    init {
        require(maxEntries > 0) { "maxEntries must be positive, was $maxEntries" }
    }

    // accessOrder = true makes reads count as use, so the entry evicted is the least recently
    // *used* room rather than the least recently resolved one.
    private val entries = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean =
            size > maxEntries
    }

    @Synchronized
    operator fun get(key: K): V? = entries[key]

    @Synchronized
    operator fun set(key: K, value: V) {
        entries[key] = value
    }

    @Synchronized
    fun remove(key: K): V? = entries.remove(key)

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    fun size(): Int = entries.size
}
