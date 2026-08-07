package org.ivcode.aimo.server.mcp.transport

import org.ivcode.aimo.server.mcp.config.McpServerProperties
import kotlin.test.Test
import kotlin.test.assertTrue
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
        override fun handleRequest(request: org.ivcode.aimo.server.mcp.protocol.JsonRpcRequest): org.ivcode.aimo.server.mcp.protocol.JsonRpcResponse {
            throw UnsupportedOperationException()
        }
    }

    private class FixedProvider(private val value: McpTransport?) : ObjectProvider<McpTransport> {
        override fun getIfAvailable(): McpTransport? = value
        override fun getIfUnique(): McpTransport? = value
        override fun getObject(vararg args: Any?): McpTransport = value ?: throw NoSuchElementException()
        override fun getObject(): McpTransport = value ?: throw NoSuchElementException()
    }

    @Test
    fun `stdio transport is initialized when enabled`() {
        val properties = McpServerProperties()
        properties.transports.stdio.enabled = true

        val stdio = TestTransport("stdio")

        val coordinator = TransportCoordinator(
            properties,
            FixedProvider(null) as ObjectProvider<HttpMcpTransport>, // no http
            FixedProvider(null) as ObjectProvider<SseMcpTransport>, // no sse
            FixedProvider(stdio)
        )

        coordinator.initializeTransports()

        val active = coordinator.getActiveTransports()
        assertTrue(active.contains(stdio), "Stdio transport should be active after initialization")

        // shutdown
        coordinator.shutdownTransports()
        assertTrue(stdio.shutdownCalled, "Stdio transport should have been shutdown")
    }
}


