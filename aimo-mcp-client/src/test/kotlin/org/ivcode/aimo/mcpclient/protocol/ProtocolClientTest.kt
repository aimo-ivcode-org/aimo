package org.ivcode.aimo.mcpclient.protocol

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Timeout
import org.ivcode.aimo.mcpclient.protocol.transport.ProtocolTransport
import tools.jackson.databind.ObjectMapper
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Tests for ProtocolClient lifecycle and disconnection handling.
 * Ensures that reader thread failures properly clean up pending requests
 * and mark the connection as closed.
 */
class ProtocolClientTest {
    private lateinit var mockTransport: ProtocolTransport
    private lateinit var objectMapper: ObjectMapper
    private lateinit var client: ProtocolClient

    @BeforeEach
    fun setup() {
        objectMapper = ObjectMapper()
        mockTransport = MockProtocolTransport()
        client = ProtocolClient(mockTransport, objectMapper)
    }

    @Test
    @Timeout(5, unit = TimeUnit.SECONDS)
    fun `should mark disconnected and fail pending requests when reader thread exits due to transport error`() {
        // Setup: make transport throw IOException only on receive (not send)
        val mockTransportImpl = mockTransport as MockProtocolTransport
        mockTransportImpl.throwOnReceive = true
        mockTransportImpl.throwOnSend = false

        // Connect the client and wait until the reader thread is blocked in receive().
        client.connect()
        assertTrue(mockTransportImpl.receiveStarted.await(1, TimeUnit.SECONDS))

        // Release the reader so it fails and runs its cleanup path.
        mockTransportImpl.failReceive.countDown()

        // Verify: isConnected() should now be false.
        assertTrue(awaitDisconnected())
        assertFalse(
            client.isConnected(),
            "Client should be disconnected after reader thread encounters transport error"
        )
    }

    @Test
    @Timeout(5, unit = TimeUnit.SECONDS)
    fun `pending request should complete with exception when reader thread exits`() {
        // Setup: make transport fail only after the request has been sent.
        val mockTransportImpl = mockTransport as MockProtocolTransport
        mockTransportImpl.throwOnReceive = true
        mockTransportImpl.throwOnSend = false

        // Connect the client and wait until the reader thread is actively waiting on receive.
        client.connect()
        assertTrue(mockTransportImpl.receiveStarted.await(1, TimeUnit.SECONDS))

        // Start a thread to send a request and wait until it has been issued.
        var exceptionCaught: Exception? = null
        val requestThread = Thread {
            try {
                client.sendRequest("test.method", null, timeoutMs = 3000)
            } catch (e: Exception) {
                exceptionCaught = e
            }
        }
        requestThread.start()
        assertTrue(mockTransportImpl.sendStarted.await(1, TimeUnit.SECONDS))

        // Now let the reader fail so the pending request is completed exceptionally.
        mockTransportImpl.failReceive.countDown()
        requestThread.join()

        assertNotNull(exceptionCaught, "Request should fail with exception")

        // Connection should now be marked as closed
        assertTrue(awaitDisconnected())
        assertFalse(client.isConnected(), "Connection should be closed after reader error")
    }

    @Test
    @Timeout(5, unit = TimeUnit.SECONDS)
    fun `should handle normal disconnect without errors`() {
        // Setup: normal operation, no errors
        (mockTransport as MockProtocolTransport).throwOnReceive = false

        // Connect and disconnect
        client.connect()
        assertTrue(client.isConnected())

        client.disconnect()
        assertFalse(client.isConnected(), "Client should be disconnected after disconnect()")
    }

    /**
     * Mock transport that can simulate errors for testing.
     */
    private class MockProtocolTransport : ProtocolTransport {
        @Volatile
        var throwOnReceive = false

        @Volatile
        var throwOnSend = false

        @Volatile
        var lastSendSucceeded = false

        val receiveStarted = CountDownLatch(1)
        val sendStarted = CountDownLatch(1)
        val failReceive = CountDownLatch(1)

        override fun connect() {
            // No-op for mock
        }

        override fun disconnect() {
            // No-op for mock
        }

        override fun send(message: String) {
            sendStarted.countDown()
            if (throwOnSend) {
                throw IOException("Mock transport send error")
            }
            lastSendSucceeded = true
        }

        override fun receive(): String {
            receiveStarted.countDown()
            if (throwOnReceive) {
                failReceive.await()
                throw IOException("Mock transport error")
            }
            // Block to simulate waiting for message
            Thread.sleep(100)
            return "{}"
        }
    }

    private fun awaitDisconnected(timeoutMs: Long = 1_000): Boolean {
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadlineNanos) {
            if (!client.isConnected()) {
                return true
            }
            Thread.sleep(25)
        }
        return !client.isConnected()
    }
}



