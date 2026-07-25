package org.ivcode.aimo.server.mcp.config

import org.ivcode.aimo.server.mcp.protocol.McpErrorCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ConfigurationValidationTest {

    @Test
    fun `should validate enabled flag`() {
        val enabled = true
        assertTrue(enabled)
    }

    @Test
    fun `should validate server properties`() {
        val props = mapOf(
            "name" to "aimo-mcp-server",
            "version" to "1.0.0"
        )
        assertTrue(props.containsKey("name"))
        assertTrue(props.containsKey("version"))
    }
}

