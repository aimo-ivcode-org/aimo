package org.ivcode.aimo.session.cache.ehcache.utils

import org.junit.jupiter.api.DisplayName
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("KeyedReentrantLock")
class KeyedReentrantLockTest {

    // -------------------------------------------------------------------------
    // Basic acquire / release
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("acquire returns a lock and release does not throw")
    fun acquireAndRelease() {
        val keyedLock = KeyedReentrantLock<String>()
        val lock = keyedLock.acquire("key-a")
        keyedLock.release("key-a", lock)
    }

    @Test
    @DisplayName("lock entry is removed after the sole holder releases")
    fun entryRemovedAfterRelease() {
        val keyedLock = KeyedReentrantLock<String>()
        val lock = keyedLock.acquire("key-a")
        assertEquals(1, keyedLock.activeKeyCount())
        keyedLock.release("key-a", lock)
        assertEquals(0, keyedLock.activeKeyCount())
    }

    @Test
    @DisplayName("different keys produce independent locks")
    fun differentKeysAreIndependent() {
        val keyedLock = KeyedReentrantLock<String>()
        val lockA = keyedLock.acquire("key-a")
        val lockB = keyedLock.acquire("key-b")

        assertFalse(lockA === lockB, "Different keys must not share the same lock instance")
        assertEquals(2, keyedLock.activeKeyCount())

        keyedLock.release("key-a", lockA)
        keyedLock.release("key-b", lockB)
        assertEquals(0, keyedLock.activeKeyCount())
    }

    @Test
    @DisplayName("entry is removed on release and can be recreated for the same key")
    fun entryRemovedAndCanBeRecreatedForSameKey() {
        val keyedLock = KeyedReentrantLock<String>()

        val lock1 = keyedLock.acquire("key-a")
        keyedLock.release("key-a", lock1)

        val lock2 = keyedLock.acquire("key-a")
        keyedLock.release("key-a", lock2)

        // Both acquisitions should have succeeded – no assertion on identity here
        // because after the first release the entry is removed and a new one is created.
        assertEquals(0, keyedLock.activeKeyCount())
    }

    // -------------------------------------------------------------------------
    // Concurrency: mutual exclusion
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("concurrent writes to the same key are serialized (no lost updates)")
    fun concurrentWritesSameKeyAreSerialised() {
        val keyedLock = KeyedReentrantLock<String>()
        val counter = AtomicInteger(0)
        val threads = 100
        val executor = Executors.newFixedThreadPool(threads)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threads)

        repeat(threads) {
            executor.submit {
                try {
                    startLatch.await()
                    val lock = keyedLock.acquire("shared-key")
                    try {
                        // Non-atomic read-modify-write; safe only under our lock
                        val current = counter.get()
                        Thread.yield() // encourage interleaving
                        counter.set(current + 1)
                    } finally {
                        keyedLock.release("shared-key", lock)
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Timed out waiting for threads")
        executor.shutdown()

        assertEquals(threads, counter.get(), "Every increment must be visible – no lost updates")
        assertEquals(0, keyedLock.activeKeyCount(), "All entries must be cleaned up")
    }

    @Test
    @DisplayName("concurrent operations on different keys do not block each other")
    fun concurrentWritesDifferentKeysDoNotBlock() {
        val keyedLock = KeyedReentrantLock<Int>()
        val keys = 10
        val threadsPerKey = 10
        val total = keys * threadsPerKey
        val counters = Array(keys) { AtomicInteger(0) }

        val executor = Executors.newFixedThreadPool(total)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(total)

        for (key in 0 until keys) {
            repeat(threadsPerKey) {
                executor.submit {
                    try {
                        startLatch.await()
                        val lock = keyedLock.acquire(key)
                        try {
                            val current = counters[key].get()
                            Thread.yield()
                            counters[key].set(current + 1)
                        } finally {
                            keyedLock.release(key, lock)
                        }
                    } finally {
                        doneLatch.countDown()
                    }
                }
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Timed out waiting for threads")
        executor.shutdown()

        for (key in 0 until keys) {
            assertEquals(threadsPerKey, counters[key].get(), "Counter for key $key must equal $threadsPerKey")
        }
        assertEquals(0, keyedLock.activeKeyCount(), "All entries must be cleaned up")
    }

    // -------------------------------------------------------------------------
    // Reference counting
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("entry stays alive while concurrent holders have not yet released")
    fun entryStaysAliveWithMultipleConcurrentHolders() {
        val keyedLock = KeyedReentrantLock<String>()
        val holdingLatch = CountDownLatch(1)
        val acquiredLatch = CountDownLatch(1)

        // Thread B acquires the lock and parks, keeping refCount ≥ 1
        val executor = Executors.newSingleThreadExecutor()
        executor.submit {
            val lock = keyedLock.acquire("key-a")
            acquiredLatch.countDown()
            try {
                holdingLatch.await()
            } finally {
                keyedLock.release("key-a", lock)
            }
        }

        // Wait until thread B has acquired (refCount = 1)
        assertTrue(acquiredLatch.await(5, TimeUnit.SECONDS))
        assertEquals(1, keyedLock.activeKeyCount())

        // Signal thread B to release and verify cleanup
        holdingLatch.countDown()
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        assertEquals(0, keyedLock.activeKeyCount())
    }

    @Test
    @DisplayName("entry stays registered while another thread is waiting/holding and is removed after the final release")
    fun entryRemovedOnlyAfterLastInterestedThreadReleases() {
        val keyedLock = KeyedReentrantLock<String>()
        val holderAcquired = CountDownLatch(1)
        val releaseHolder = CountDownLatch(1)
        val waiterAcquired = CountDownLatch(1)
        val releaseWaiter = CountDownLatch(1)

        val holderThread = Thread {
            val lock = keyedLock.acquire("key-a")
            holderAcquired.countDown()
            try {
                releaseHolder.await()
            } finally {
                keyedLock.release("key-a", lock)
            }
        }

        val waiterThread = Thread {
            val lock = keyedLock.acquire("key-a")
            waiterAcquired.countDown()
            try {
                releaseWaiter.await()
            } finally {
                keyedLock.release("key-a", lock)
            }
        }

        holderThread.start()
        assertTrue(holderAcquired.await(5, TimeUnit.SECONDS), "Holder did not acquire key-a in time")
        assertEquals(1, keyedLock.activeKeyCount(), "Entry should exist while the first holder is active")

        waiterThread.start()
        awaitCondition("Waiter never blocked while trying to acquire key-a") {
            waiterThread.state == Thread.State.WAITING || waiterThread.state == Thread.State.BLOCKED
        }
        assertEquals(1, keyedLock.activeKeyCount(), "Entry must remain while another thread is waiting on the same key")

        releaseHolder.countDown()
        assertTrue(waiterAcquired.await(5, TimeUnit.SECONDS), "Waiter did not acquire key-a after holder release")
        assertEquals(1, keyedLock.activeKeyCount(), "Entry must remain while the waiter still holds the key")

        releaseWaiter.countDown()
        holderThread.join(5_000)
        waiterThread.join(5_000)

        assertFalse(holderThread.isAlive, "Holder thread should have terminated")
        assertFalse(waiterThread.isAlive, "Waiter thread should have terminated")
        assertEquals(0, keyedLock.activeKeyCount(), "Entry should be removed only after the last interested thread releases")
    }

    // -------------------------------------------------------------------------
    // No deadlock (implementation invariant: lockMapLock is never held while a
    // per-key lock is held)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("a thread blocked waiting for key-A does not prevent acquire for key-B")
    fun blockedThreadOnKeyADoesNotBlockKeyB() {
        // If the implementation incorrectly held lockMapLock while trying to acquire
        // the per-key lock, this test would deadlock / time out.
        val keyedLock = KeyedReentrantLock<String>()

        // Step 1 – Thread HOLDER acquires "key-A" and parks there
        val holderReady = CountDownLatch(1)
        val holderRelease = CountDownLatch(1)
        val holderExecutor = Executors.newSingleThreadExecutor()
        holderExecutor.submit {
            val lock = keyedLock.acquire("key-A")
            holderReady.countDown()          // signal: key-A is now locked
            holderRelease.await()            // park until test signals release
            keyedLock.release("key-A", lock)
        }

        assertTrue(holderReady.await(5, TimeUnit.SECONDS), "Holder did not acquire key-A in time")

        // Step 2 – Thread WAITER tries to acquire the same "key-A" → will block
        val waiterDone = CountDownLatch(1)
        val waiterExecutor = Executors.newSingleThreadExecutor()
        waiterExecutor.submit {
            val lock = keyedLock.acquire("key-A")    // blocks until HOLDER releases
            keyedLock.release("key-A", lock)
            waiterDone.countDown()
        }

        // Step 3 – While WAITER is blocked, acquire a completely different key.
        // This must NOT block – it proves lockMapLock is not held by the blocked thread.
        val unrelatedLock = keyedLock.acquire("key-B")
        keyedLock.release("key-B", unrelatedLock)   // succeeds → no deadlock on lockMapLock

        // Step 4 – Unblock HOLDER and let WAITER finish
        holderRelease.countDown()
        assertTrue(waiterDone.await(5, TimeUnit.SECONDS), "Waiter did not finish in time")

        holderExecutor.shutdown()
        waiterExecutor.shutdown()
        assertEquals(0, keyedLock.activeKeyCount())
    }

    private fun awaitCondition(message: String, timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (System.nanoTime() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(10)
        }
        assertTrue(condition(), message)
    }
}


