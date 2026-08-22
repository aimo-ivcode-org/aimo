package org.ivcode.aimo.server.mcp.schema

import org.ivcode.aimo.server.mcp.annotation.McpParam
import org.ivcode.aimo.server.mcp.annotation.McpTool
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNull

class McpSchemaGeneratorTest {

    private val generator = McpSchemaGenerator()

    @Test
    fun `should generate schema from simple tool method`() {
        val method = TestService::class.java.getMethod("add", Double::class.java, Double::class.java)

        val schema = generator.generateToolSchema(method)

        assertEquals("add", schema.name)
        assertNotNull(schema.inputSchema)
        assertEquals(2, schema.inputSchema!!.properties.size)
        assertTrue(schema.inputSchema!!.properties.containsKey("a"))
        assertTrue(schema.inputSchema!!.properties.containsKey("b"))
    }

    @Test
    fun `should validate required fields in schema`() {
        val method = TestService::class.java.getMethod("add", Double::class.java, Double::class.java)

        val schema = generator.generateToolSchema(method)
        val errors = generator.validateSchema(schema)

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `should detect missing tool name`() {
        val schema = org.ivcode.aimo.server.mcp.protocol.ToolDefinition(
            name = "",
            description = "Test"
        )

        val errors = generator.validateSchema(schema)

        assertTrue(errors.any { it.contains("name") })
    }

    @Test
    fun `should handle methods without parameters`() {
        val method = TestService::class.java.getMethod("getInfo")

        val schema = generator.generateToolSchema(method)

        assertNull(schema.inputSchema)
    }

    companion object {
        class TestService {
            @McpTool
            fun add(
                @McpParam(description = "First number") a: Double,
                @McpParam(description = "Second number") b: Double
            ): Double = a + b

            @McpTool
            fun getInfo(): String = "Info"
        }
    }
}


