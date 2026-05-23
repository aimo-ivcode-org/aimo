package org.ivcode.aimo.session.cache.ehcache.utils

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * A keyed locking utility that provides per-key mutual exclusion with automatic
 * lock lifecycle management.
 *
 * Each key gets its own [ReentrantLock] that is created on first use and removed
 * when the last holder releases it. This prevents unbounded memory growth without
 * sacrificing correctness.
 *
 * ### Ordering guarantee (no deadlock)
 * The internal meta-lock ([lockMapLock]) is **never held** while a per-key lock is
 * being acquired or held. Specifically:
 * 1. [acquire] increments the reference count under [lockMapLock], then releases
 *    [lockMapLock] **before** calling [ReentrantLock.lock].
 * 2. [release] calls [ReentrantLock.unlock] **before** re-acquiring [lockMapLock]
 *    to decrement the reference count.
 *
 * This ordering means a thread can never hold both locks simultaneously, eliminating
 * the classic two-lock deadlock.
 *
 * ### Usage
 * ```kotlin
 * val keyedLock = KeyedReentrantLock<String>()
 *
 * val lock = keyedLock.acquire("my-key")
 * try {
 *     // critical section
 * } finally {
 *     keyedLock.release("my-key", lock)
 * }
 * ```
 *
 * @param K The type of the key.
 */
internal class KeyedReentrantLock<K> {

    private data class LockEntry(val lock: ReentrantLock, var refCount: Int)

    private val locks = ConcurrentHashMap<K, LockEntry>()
    private val lockMapLock = ReentrantLock()

    /**
     * Acquires exclusive access for [key].
     *
     * Registers interest (increments refCount) while holding the meta-lock, then
     * acquires the per-key lock outside the meta-lock. Blocks until the lock is
     * available.
     *
     * @return The [ReentrantLock] that was acquired; pass it to [release].
     */
    fun acquire(key: K): ReentrantLock {
        val lock: ReentrantLock
        lockMapLock.lock()
        try {
            lock = locks.compute(key) { _, entry ->
                if (entry != null) { entry.refCount++; entry }
                else LockEntry(ReentrantLock(), 1)
            }!!.lock
        } finally {
            lockMapLock.unlock()
        }
        lock.lock()
        return lock
    }

    /**
     * Releases exclusive access for [key].
     *
     * Unlocks the per-key lock, then deregisters interest (decrements refCount)
     * under the meta-lock. If this was the last holder the entry is removed,
     * reclaiming memory.
     *
     * @param key The key whose lock is being released.
     * @param lock The [ReentrantLock] returned by the corresponding [acquire] call.
     */
    fun release(key: K, lock: ReentrantLock) {
        lock.unlock()
        lockMapLock.lock()
        try {
            locks.compute(key) { _, entry ->
                if (entry != null) {
                    entry.refCount--
                    if (entry.refCount == 0) null else entry
                } else null
            }
        } finally {
            lockMapLock.unlock()
        }
    }

    /** Returns the number of keys that currently have an active lock entry. Intended for testing. */
    internal fun activeKeyCount(): Int = locks.size
}

