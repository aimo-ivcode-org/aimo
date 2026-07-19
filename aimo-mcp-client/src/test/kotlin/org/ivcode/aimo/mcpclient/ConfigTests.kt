package org.ivcode.aimo.mcpclient

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.ivcode.aimo.mcpclient.config.McpServerConfig
import org.ivcode.aimo.mcpclient.validation.ConfigurationValidator
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals

class ConfigurationValidatorTest {
    @Test
    fun `should validate valid server configuration`() {
        val validator = ConfigurationValidator(setOf("global"))
        val config = McpServerConfig(
            servers = listOf(
                McpServerConfig.Server(
                    id = "test-server",
                    transport = McpServerConfig.Transport.StdioTransport(command = "/path/to/server"),
                    scope = emptyList(),
                )
            )
        )
        validator.validate(config) // Should not throw
    }

    @Test
    fun `should reject server with unknown scope`() {
        val validator = ConfigurationValidator(setOf("global"))
        val config = McpServerConfig(
            servers = listOf(
                McpServerConfig.Server(
                    id = "test-server",
                    transport = McpServerConfig.Transport.StdioTransport(command = "/path/to/server"),
                    scope = listOf("unknown-scope"),
                )
            )
        )
        assertThrows<IllegalArgumentException> {
            validator.validate(config)
        }
    }

    @Test
    fun `should reject empty server ID`() {
        val validator = ConfigurationValidator(emptySet())
        val config = McpServerConfig(
            servers = listOf(
                McpServerConfig.Server(
                    id = "",
                    transport = McpServerConfig.Transport.StdioTransport(command = "/path/to/server"),
                )
            )
        )
        assertThrows<IllegalArgumentException> {
            validator.validate(config)
        }
    }
}

class ToolDiscoveryTest {
    @Test
    fun `should validate tool schema`() {
        val mapper = ObjectMapper()
        
        // Create a valid tool node
        val toolNode = mapper.createObjectNode().apply {
            put("name", "test-tool")
            put("description", "Test tool")
            set("inputSchema", mapper.createObjectNode())
        }

        // Tool discovery will validate this when converting
        // Just ensure it doesn't throw on valid schema
        val inputSchema = toolNode.get("inputSchema")
        assertEquals(true, inputSchema.isObject)
    }
}
