package org.ivcode.aimo.server.mcp.transport

import org.ivcode.aimo.server.mcp.config.McpServerProperties
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import org.springframework.beans.factory.ObjectProvider

/**
 * Pure unit test for TransportCoordinator without Spring test wiring.
 * Uses simple test doubles to avoid creating real stdio reader threads.
 */
class TransportCoordinatorTest {

    private class TestTransport(override val name: String) : McpTransport {
        var initialized = false
        var shutdownCalled = false

        override fun initialize() { initialized = true }
        override fun shutdown() { shutdownCalled = true }
        override fun isActive(): Boolean = initialized && !shutdownCalled
        override fun handleRequest(
            request: org.ivcode.aimo.server.mcp.protocol.JsonRpcRequest
        ): org.ivcode.aimo.server.mcp.protocol.JsonRpcResponse {
            throw UnsupportedOperationException()
        }
    }

    private class FixedProvider<T : Any>(private val value: T?) : ObjectProvider<T> {
        override fun getIfAvailable(): T? = value
        override fun getIfUnique(): T? = value
        override fun getObject(vararg args: Any?): T = value ?: throw NoSuchElementException()
        override fun getObject(): T = value ?: throw NoSuchElementException()
    }

    @Test
    fun `stdio transport is initialized when enabled`() {
        val properties = McpServerProperties()
        properties.transports.stdio.enabled = true

        val stdio = TestTransport("stdio")

        val coordinator = TransportCoordinator(
            properties,
            FixedProvider<HttpMcpTransport>(null), // no http
            FixedProvider<SseMcpTransport>(null), // no sse
            FixedProvider<McpTransport>(stdio)
        )

        coordinator.initializeTransports()

        val active = coordinator.getActiveTransports()
        assertTrue(active.contains(stdio), "Stdio transport should be active after initialization")

        // shutdown
        coordinator.shutdownTransports()
        assertTrue(stdio.shutdownCalled, "Stdio transport should have been shutdown")
    }

    @Test
    fun `fail fast when no transports active`() {
        val properties = McpServerProperties()
        // disable all transports
        properties.transports.http.enabled = false
        properties.transports.sse.enabled = false
        properties.transports.stdio.enabled = false

        val coordinator = TransportCoordinator(
            properties,
            FixedProvider<HttpMcpTransport>(null),
            FixedProvider<SseMcpTransport>(null),
            FixedProvider<McpTransport>(null)
        )

        assertFailsWith<IllegalStateException> {
            coordinator.initializeTransports()
        }
    }
}


