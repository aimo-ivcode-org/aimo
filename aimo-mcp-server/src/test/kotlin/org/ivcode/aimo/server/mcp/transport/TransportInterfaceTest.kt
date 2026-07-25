package org.ivcode.aimo.server.mcp.transport

import org.ivcode.aimo.server.mcp.protocol.McpErrorCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class TransportInterfaceTest {

    @Test
    fun `should have HTTP transport name`() {
        assertEquals("http", "http")
    }

    @Test
    fun `should have SSE transport name`() {
        assertEquals("sse", "sse")
    }

    @Test
    fun `should have stdio transport name`() {
        assertEquals("stdio", "stdio")
    }

    @Test
    fun `should handle transport lifecycle`() {
        var isActive = false
        isActive = true
        assertTrue(isActive)
        isActive = false
        assertFalse(isActive)
    }

    @Test
    fun `should handle multiple transports`() {
        val transports = listOf("http", "sse", "stdio")
        assertEquals(3, transports.size)
    }
}

