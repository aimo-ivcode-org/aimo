package org.ivcode.aimo.server.mcp.handler

import com.fasterxml.jackson.databind.ObjectMapper
import org.ivcode.aimo.server.mcp.annotation.McpService
import org.ivcode.aimo.server.mcp.annotation.McpTool
import org.ivcode.aimo.server.mcp.protocol.JsonRpcRequest
import org.ivcode.aimo.server.mcp.protocol.McpErrorCode
import org.ivcode.aimo.server.mcp.registry.McpServiceRegistry
import org.ivcode.aimo.server.mcp.schema.McpSchemaGenerator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.context.support.GenericApplicationContext

class ToolCallHandlerTest {
    private lateinit var handler: ToolCallHandler
    private lateinit var registry: McpServiceRegistry
    private lateinit var parameterBinder: ParameterBinder
    private lateinit var objectMapper: ObjectMapper
    private lateinit var applicationContext: GenericApplicationContext

    @BeforeEach
    fun setUp() {
        applicationContext = GenericApplicationContext()
        applicationContext.refresh()
        objectMapper = ObjectMapper()
        parameterBinder = ParameterBinder(objectMapper)
        registry = McpServiceRegistry(applicationContext, McpSchemaGenerator())
    }

    @Test
    fun `should return INVALID_PARAMS when integer parameter conversion fails`() {
        val service = MathService()
        applicationContext.beanFactory.registerSingleton("mathService", service)
        registry.discoverServices()
        handler = ToolCallHandler(registry, parameterBinder, objectMapper)

        val request = JsonRpcRequest(
            id = "test-1",
            method = "tools/call",
            params = mapOf(
                "name" to "add",
                "arguments" to mapOf("a" to "not_a_number", "b" to 5)
            )
        )

        val response = handler.handle(request)

        assertNotNull(response.error)
        assertEquals(McpErrorCode.INVALID_PARAMS, response.error?.code)
        assertTrue(response.error?.message?.contains("invalid") ?: false)
    }

    @Test
    fun `should return INVALID_PARAMS when double parameter conversion fails`() {
        val service = MathService()
        applicationContext.beanFactory.registerSingleton("mathService", service)
        registry.discoverServices()
        handler = ToolCallHandler(registry, parameterBinder, objectMapper)

        val request = JsonRpcRequest(
            id = "test-2",
            method = "tools/call",
            params = mapOf(
                "name" to "divide",
                "arguments" to mapOf("a" to 10, "b" to "invalid_double")
            )
        )

        val response = handler.handle(request)

        assertNotNull(response.error)
        assertEquals(McpErrorCode.INVALID_PARAMS, response.error?.code)
    }

    @Test
    fun `should successfully invoke tool with valid integer parameters`() {
        val service = MathService()
        applicationContext.beanFactory.registerSingleton("mathService", service)
        registry.discoverServices()
        handler = ToolCallHandler(registry, parameterBinder, objectMapper)

        val request = JsonRpcRequest(
            id = "test-3",
            method = "tools/call",
            params = mapOf(
                "name" to "add",
                "arguments" to mapOf("a" to "5", "b" to 3)
            )
        )

        val response = handler.handle(request)

        assertNull(response.error)
        assertNotNull(response.result)
    }

    @Test
    fun `should successfully invoke tool with valid double parameters`() {
        val service = MathService()
        applicationContext.beanFactory.registerSingleton("mathService", service)
        registry.discoverServices()
        handler = ToolCallHandler(registry, parameterBinder, objectMapper)

        val request = JsonRpcRequest(
            id = "test-4",
            method = "tools/call",
            params = mapOf(
                "name" to "divide",
                "arguments" to mapOf("a" to 10.0, "b" to "2.5")
            )
        )

        val response = handler.handle(request)

        assertNull(response.error)
        assertNotNull(response.result)
    }

    @Test
    fun `should return error message with parameter name when binding fails`() {
        val service = MathService()
        applicationContext.beanFactory.registerSingleton("mathService", service)
        registry.discoverServices()
        handler = ToolCallHandler(registry, parameterBinder, objectMapper)

        val request = JsonRpcRequest(
            id = "test-5",
            method = "tools/call",
            params = mapOf(
                "name" to "add",
                "arguments" to mapOf("a" to listOf(1, 2), "b" to 5)
            )
        )

        val response = handler.handle(request)

        assertNotNull(response.error)
        assertEquals(McpErrorCode.INVALID_PARAMS, response.error?.code)
        assertTrue(response.error?.message?.contains("a") ?: false)
        assertTrue(response.error?.message?.contains("integer") ?: false)
    }

    @Test
    fun `should return error when parameter cannot be converted to custom type`() {
        val service = MathService()
        applicationContext.beanFactory.registerSingleton("mathService", service)
        registry.discoverServices()
        handler = ToolCallHandler(registry, parameterBinder, objectMapper)

        val request = JsonRpcRequest(
            id = "test-6",
            method = "tools/call",
            params = mapOf(
                "name" to "add",
                "arguments" to mapOf("a" to mapOf("nested" to "object"), "b" to 5)
            )
        )

        val response = handler.handle(request)

        assertNotNull(response.error)
        assertEquals(McpErrorCode.INVALID_PARAMS, response.error?.code)
        assertTrue(response.error?.message?.contains("a") ?: false)
    }

    @Test
    fun `should include helpful error details when conversion fails`() {
        val service = MathService()
        applicationContext.beanFactory.registerSingleton("mathService", service)
        registry.discoverServices()
        handler = ToolCallHandler(registry, parameterBinder, objectMapper)

        val request = JsonRpcRequest(
            id = "test-7",
            method = "tools/call",
            params = mapOf(
                "name" to "divide",
                "arguments" to mapOf("a" to "not_a_number", "b" to "also_not")
            )
        )

        val response = handler.handle(request)

        assertNotNull(response.error)
        assertEquals(McpErrorCode.INVALID_PARAMS, response.error?.code)
        // Error should mention the first failing parameter and what it expected
        assertTrue(response.error?.message?.contains("decimal") ?: false)
    }

    @McpService
    class MathService {
        @McpTool
        fun add(a: Int, b: Int): Int = a + b

        @McpTool
        fun divide(a: Double, b: Double): Double = a / b
    }
}


