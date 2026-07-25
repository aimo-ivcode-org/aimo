package org.ivcode.aimo.server.mcp.registry

import org.ivcode.aimo.server.mcp.annotation.McpParam
import org.ivcode.aimo.server.mcp.annotation.McpPrompt
import org.ivcode.aimo.server.mcp.annotation.McpService
import org.ivcode.aimo.server.mcp.annotation.McpTool
import org.ivcode.aimo.server.mcp.schema.McpSchemaGenerator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.context.support.GenericApplicationContext

class McpServiceRegistryTest {

    private val schemaGenerator = McpSchemaGenerator()
    private val applicationContext = GenericApplicationContext()

    @BeforeEach
    fun setUp() {
        applicationContext.refresh()
    }

    @Test
    fun `should discover single McpService bean`() {
        val service = TestCalculatorService()
        applicationContext.beanFactory.registerSingleton("calculatorService", service)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val services = registry.getServices()
        assertEquals(1, services.size)
        assertTrue(services.containsKey("calculatorService"))
    }

    @Test
    fun `should discover tools from McpService bean`() {
        val service = TestCalculatorService()
        applicationContext.beanFactory.registerSingleton("calculatorService", service)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val toolDefinitions = registry.getToolDefinitions()
        assertEquals(2, toolDefinitions.size)
        assertTrue(toolDefinitions.any { it.name == "add" })
        assertTrue(toolDefinitions.any { it.name == "multiply" })
    }

    @Test
    fun `should discover prompts from McpService bean`() {
        val service = TestCalculatorService()
        applicationContext.beanFactory.registerSingleton("calculatorService", service)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val promptDefinitions = registry.getPromptDefinitions()
        assertEquals(1, promptDefinitions.size)
        assertTrue(promptDefinitions.any { it.name == "calculationHelp" })
    }

    @Test
    fun `should register tools with correct bean name prefix`() {
        val service = TestCalculatorService()
        applicationContext.beanFactory.registerSingleton("calculatorService", service)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val toolIds = registry.getToolIds()
        assertTrue(toolIds.any { it.contains("calculatorService:add") })
        assertTrue(toolIds.any { it.contains("calculatorService:multiply") })
    }

    @Test
    fun `should look up tool by full ID`() {
        val service = TestCalculatorService()
        applicationContext.beanFactory.registerSingleton("calculatorService", service)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val tool = registry.getTool("calculatorService:add")
        assertNotNull(tool)
        assertEquals("add", tool!!.schema.name)
    }

    @Test
    fun `should look up tool by simple name`() {
        val service = TestCalculatorService()
        applicationContext.beanFactory.registerSingleton("calculatorService", service)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val tool = registry.getTool("add")
        assertNotNull(tool)
        assertEquals("add", tool!!.schema.name)
    }

    @Test
    fun `should return null for non-existent tool`() {
        val service = TestCalculatorService()
        applicationContext.beanFactory.registerSingleton("calculatorService", service)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val tool = registry.getTool("nonexistent")
        assertNull(tool)
    }

    @Test
    fun `should look up prompt by full ID`() {
        val service = TestCalculatorService()
        applicationContext.beanFactory.registerSingleton("calculatorService", service)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val prompt = registry.getPrompt("calculatorService:calculationHelp")
        assertNotNull(prompt)
        assertEquals("calculationHelp", prompt!!.schema.name)
    }

    @Test
    fun `should look up prompt by simple name`() {
        val service = TestCalculatorService()
        applicationContext.beanFactory.registerSingleton("calculatorService", service)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val prompt = registry.getPrompt("calculationHelp")
        assertNotNull(prompt)
        assertEquals("calculationHelp", prompt!!.schema.name)
    }

    @Test
    fun `should discover multiple services`() {
        val calcService = TestCalculatorService()
        val stringService = TestStringService()
        applicationContext.beanFactory.registerSingleton("calculatorService", calcService)
        applicationContext.beanFactory.registerSingleton("stringService", stringService)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val services = registry.getServices()
        assertEquals(2, services.size)
        assertTrue(services.containsKey("calculatorService"))
        assertTrue(services.containsKey("stringService"))
    }

    @Test
    fun `should get service by bean name`() {
        val service = TestCalculatorService()
        applicationContext.beanFactory.registerSingleton("calculatorService", service)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val managedService = registry.getService("calculatorService")
        assertNotNull(managedService)
        assertEquals("calculatorService", managedService!!.beanName)
        assertEquals(service, managedService.bean)
    }

    @Test
    fun `should handle service with no tools or prompts`() {
        val service = EmptyService()
        applicationContext.beanFactory.registerSingleton("emptyService", service)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val services = registry.getServices()
        assertEquals(1, services.size)
        val managedService = services["emptyService"]!!
        assertEquals(0, managedService.tools.size)
        assertEquals(0, managedService.prompts.size)
    }

    // Test service implementations
    @McpService
    class TestCalculatorService {
        @McpTool
        fun add(
            @McpParam(description = "First number") a: Double,
            @McpParam(description = "Second number") b: Double
        ): Double = a + b

        @McpTool
        fun multiply(
            @McpParam(description = "First number") a: Double,
            @McpParam(description = "Second number") b: Double
        ): Double = a * b

        @McpPrompt
        fun calculationHelp(): String = "Use 'add' or 'multiply' to perform calculations"
    }

    @McpService
    class TestStringService {
        @McpTool
        fun reverse(
            @McpParam(description = "String to reverse") text: String
        ): String = text.reversed()
    }

    @McpService
    class EmptyService
}





