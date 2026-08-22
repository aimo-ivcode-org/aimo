package org.ivcode.aimo.server.mcp.registry

import org.ivcode.aimo.server.mcp.annotation.McpParam
import org.ivcode.aimo.server.mcp.annotation.McpPrompt
import org.ivcode.aimo.server.mcp.annotation.McpService
import org.ivcode.aimo.server.mcp.annotation.McpTool
import org.ivcode.aimo.server.mcp.schema.McpSchemaGenerator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertNotEquals
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
        // Tool names are client-visible format (tool name only for unnamed services)
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
        // Prompt names are client-visible format (prompt name only for unnamed services)
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

    @Test
    fun `should use service name when specified in @McpService annotation`() {
        val service = ServiceWithName()
        applicationContext.beanFactory.registerSingleton("weatherService", service)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val toolDefinitions = registry.getToolDefinitions()
        assertEquals(1, toolDefinitions.size)

        // With service name, client-visible tool name should be "serviceName:toolName"
        val expectedName = "weather:getTemp"
        assertTrue(toolDefinitions.any { it.name == expectedName },
            "Expected tool name '$expectedName' but got: ${toolDefinitions.map { it.name }}")
    }

    @Test
    fun `should build tool IDs without service name when not specified`() {
        val service = TestCalculatorService()
        applicationContext.beanFactory.registerSingleton("calculatorService", service)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val toolDefinitions = registry.getToolDefinitions()
        // Without service name, client-visible tool names should be just the tool name
        assertTrue(toolDefinitions.any { it.name == "add" })
        assertTrue(toolDefinitions.any { it.name == "multiply" })
    }

    @Test
    fun `should handle multiple services with same tool names using explicit service names`() {
        val forecastService = ServiceWithNameForecast()
        val historyService = ServiceWithNameHistory()

        applicationContext.beanFactory.registerSingleton("weatherService1", forecastService)
        applicationContext.beanFactory.registerSingleton("weatherService2", historyService)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val toolDefinitions = registry.getToolDefinitions()
        // Both have "getTemp" tool but with different service names in client-visible format
        assertTrue(toolDefinitions.any { it.name == "forecast:getTemp" })
        assertTrue(toolDefinitions.any { it.name == "history:getTemp" })
    }

    @Test
    fun `should use service name in prompt definitions when specified`() {
        val service = ServiceWithNameAndPrompt()
        applicationContext.beanFactory.registerSingleton("weatherService", service)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val promptDefinitions = registry.getPromptDefinitions()
        assertEquals(1, promptDefinitions.size)

        // With service name, client-visible prompt name should be "serviceName:promptName"
        assertTrue(promptDefinitions.any { it.name == "weather:weatherHelp" })
    }

    @Test
    fun `should throw exception when private method has @McpTool annotation`() {
        val service = ServiceWithPrivateTool()
        applicationContext.beanFactory.registerSingleton("serviceWithPrivateTool", service)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            registry.discoverServices()
        }

        assertTrue(exception.message?.contains("private/protected method") ?: false)
        assertTrue(exception.message?.contains("@McpTool") ?: false)
        assertTrue(exception.message?.contains("Only public methods") ?: false)
    }

    @Test
    fun `should throw exception when protected method has @McpTool annotation`() {
        val service = ServiceWithProtectedTool()
        applicationContext.beanFactory.registerSingleton("serviceWithProtectedTool", service)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            registry.discoverServices()
        }

        assertTrue(exception.message?.contains("private/protected method") ?: false)
        assertTrue(exception.message?.contains("@McpTool") ?: false)
    }

    @Test
    fun `should throw exception when private method has @McpPrompt annotation`() {
        val service = ServiceWithPrivatePrompt()
        applicationContext.beanFactory.registerSingleton("serviceWithPrivatePrompt", service)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            registry.discoverServices()
        }

        assertTrue(exception.message?.contains("private/protected method") ?: false)
        assertTrue(exception.message?.contains("@McpPrompt") ?: false)
        assertTrue(exception.message?.contains("Only public methods") ?: false)
    }

    @Test
    fun `should throw exception when unnamed services have conflicting tool names`() {
        // Create two unnamed services with the same tool name
        val service1 = ServiceUnnamedWithGetWeather()
        val service2 = ServiceUnnamedConflictingGetWeather()
        applicationContext.beanFactory.registerSingleton("weatherService1", service1)
        applicationContext.beanFactory.registerSingleton("weatherService2", service2)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            registry.discoverServices()
        }

        assertTrue(exception.message?.contains("Tool name conflict") ?: false)
        assertTrue(exception.message?.contains("getWeather") ?: false)
    }

    @Test
    fun `should throw exception when unnamed services have conflicting prompt names`() {
        // Create two unnamed services with the same prompt name
        val service1 = ServiceUnnamedWithPrompt()
        val service2 = ServiceUnnamedConflictingPrompt()
        applicationContext.beanFactory.registerSingleton("promptService1", service1)
        applicationContext.beanFactory.registerSingleton("promptService2", service2)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            registry.discoverServices()
        }

        assertTrue(exception.message?.contains("Prompt name conflict") ?: false)
        assertTrue(exception.message?.contains("help") ?: false)
    }

    @Test
    fun `should allow multiple unnamed services with different tool names`() {
        val service1 = ServiceUnnamedWithGetWeather()
        val service2 = TestStringService()
        applicationContext.beanFactory.registerSingleton("weatherService", service1)
        applicationContext.beanFactory.registerSingleton("stringService", service2)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val toolDefinitions = registry.getToolDefinitions()
        assertTrue(toolDefinitions.any { it.name == "getWeather" })
        assertTrue(toolDefinitions.any { it.name == "reverse" })
    }

    @Test
    fun `should allow services with explicit names even if tool names conflict`() {
        // Named services should not conflict even with same tool names
        val service1 = ServiceWithNameForecast()
        val service2 = ServiceWithNameHistory()
        applicationContext.beanFactory.registerSingleton("weatherService", service1)
        applicationContext.beanFactory.registerSingleton("historyService", service2)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val toolDefinitions = registry.getToolDefinitions()
        // Should have both tools with client-visible format "serviceName:toolName"
        assertTrue(toolDefinitions.any { it.name == "forecast:getTemp" })
        assertTrue(toolDefinitions.any { it.name == "history:getTemp" })
    }

    @Test
    fun `should throw exception when named services have same tool name with same service name`() {
        // Two services with same tool name AND same service name should conflict
        val service1 = ServiceWithNameForecast()  // name="forecast", tool="getTemp"
        val service2 = ServiceWithNameForecastConflict()  // also name="forecast", tool="getTemp"
        applicationContext.beanFactory.registerSingleton("weatherService1", service1)
        applicationContext.beanFactory.registerSingleton("weatherService2", service2)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            registry.discoverServices()
        }

        assertTrue(exception.message?.contains("Tool name conflict") ?: false)
        assertTrue(exception.message?.contains("forecast:getTemp") ?: false)
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

    @McpService
    class ServiceWithPrivateTool {
        @McpTool
        private fun privateTool(): String = "This is private"
    }

    @McpService
    class ServiceWithProtectedTool {
        @McpTool
        protected fun protectedTool(): String = "This is protected"
    }

    @McpService
    class ServiceWithPrivatePrompt {
        @McpPrompt
        private fun privatePrompt(): String = "This is a private prompt"
    }

    @McpService(name = "weather")
    class ServiceWithName {
        @McpTool
        fun getTemp(): Double = 72.5
    }


    @McpService(name = "forecast")
    class ServiceWithNameForecast {
        @McpTool
        fun getTemp(): Double = 75.0
    }

    @McpService(name = "history")
    class ServiceWithNameHistory {
        @McpTool
        fun getTemp(): Double = 65.0
    }

    @McpService(name = "weather")
    class ServiceWithNameAndPrompt {
        @McpTool
        fun getTemp(): Double = 72.5

        @McpPrompt
        fun weatherHelp(): String = "Get current weather temperature"
    }

    @McpService(name = "forecast")
    class ServiceWithNameForecastConflict {
        @McpTool
        fun getTemp(): Double = 75.0
    }

    // Services for conflict detection tests
    @McpService
    class ServiceUnnamedWithGetWeather {
        @McpTool
        fun getWeather(): String = "Sunny, 72F"
    }

    @McpService
    class ServiceUnnamedConflictingGetWeather {
        @McpTool
        fun getWeather(): String = "Rainy, 65F"
    }

    @McpService
    class ServiceUnnamedWithPrompt {
        @McpPrompt
        fun help(): String = "Get help"
    }

    @McpService
    class ServiceUnnamedConflictingPrompt {
        @McpPrompt
        fun help(): String = "More help"
    }
}
