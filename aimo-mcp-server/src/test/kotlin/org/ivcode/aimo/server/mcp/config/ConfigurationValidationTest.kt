package org.ivcode.aimo.server.mcp.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

class ConfigurationValidationTest {

    @Test
    fun `default McpServerProperties values are sane`() {
        val props = McpServerProperties()

        // Basic metadata defaults
        assertEquals("aimo-mcp-server", props.name)
        assertEquals("1.0.0", props.version)

        // Transport defaults
        assertTrue(props.transports.http.enabled, "HTTP transport should be enabled by default")
        assertFalse(props.transports.sse.enabled, "SSE transport should be disabled by default")
        assertFalse(props.transports.stdio.enabled, "Stdio transport should be disabled by default")

        // Discovery defaults
        assertTrue(props.discovery.enabled, "Discovery should be enabled by default")
        assertFalse(props.discovery.failIfEmpty, "failIfEmpty should be false by default")

        // Error handling defaults
        assertFalse(props.errorHandling.includeStackTrace)
        assertTrue(props.errorHandling.includeErrorData)
        assertTrue(props.errorHandling.failOnValidationError)
    }
}

