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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.springframework.context.support.GenericApplicationContext

class MultiServiceScenariosTest {

    private val schemaGenerator = McpSchemaGenerator()
    private val applicationContext = GenericApplicationContext()

    @BeforeEach
    fun setUp() {
        applicationContext.refresh()
    }

    @Test
    fun `should register multiple services without conflicts`() {
        val mathService = MathService()
        val stringService = StringService()
        val weatherService = WeatherService()

        applicationContext.beanFactory.registerSingleton("mathService", mathService)
        applicationContext.beanFactory.registerSingleton("stringService", stringService)
        applicationContext.beanFactory.registerSingleton("weatherService", weatherService)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val services = registry.getServices()
        assertEquals(3, services.size)
        assertTrue(services.containsKey("mathService"))
        assertTrue(services.containsKey("stringService"))
        assertTrue(services.containsKey("weatherService"))
    }

    @Test
    fun `should discover tools from multiple services`() {
        val mathService = MathService()
        val stringService = StringService()

        applicationContext.beanFactory.registerSingleton("mathService", mathService)
        applicationContext.beanFactory.registerSingleton("stringService", stringService)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val tools = registry.getToolDefinitions()
        assertEquals(4, tools.size)  // add, multiply, reverse, uppercase
        // Tool names are client-visible format (tool name only for unnamed services)
        assertTrue(tools.any { it.name == "add" })
        assertTrue(tools.any { it.name == "multiply" })
        assertTrue(tools.any { it.name == "reverse" })
        assertTrue(tools.any { it.name == "uppercase" })
    }

    @Test
    fun `should discover prompts from multiple services`() {
        val mathService = MathService()
        val stringService = StringService()

        applicationContext.beanFactory.registerSingleton("mathService", mathService)
        applicationContext.beanFactory.registerSingleton("stringService", stringService)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val prompts = registry.getPromptDefinitions()
        assertEquals(2, prompts.size)  // mathHelp, stringHelp
        // Prompt names are client-visible format (prompt name only for unnamed services)
        assertTrue(prompts.any { it.name == "mathHelp" })
        assertTrue(prompts.any { it.name == "stringHelp" })
    }

    @Test
    fun `should prefix tool IDs with service name`() {
        val mathService = MathService()
        val stringService = StringService()

        applicationContext.beanFactory.registerSingleton("mathService", mathService)
        applicationContext.beanFactory.registerSingleton("stringService", stringService)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val toolIds = registry.getToolIds()
        assertTrue(toolIds.any { it == "mathService:add" })
        assertTrue(toolIds.any { it == "mathService:multiply" })
        assertTrue(toolIds.any { it == "stringService:reverse" })
        assertTrue(toolIds.any { it == "stringService:uppercase" })
    }

    @Test
    fun `should find tools by simple name across services`() {
        val mathService = MathService()
        val stringService = StringService()

        applicationContext.beanFactory.registerSingleton("mathService", mathService)
        applicationContext.beanFactory.registerSingleton("stringService", stringService)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        // Should find tools by simple name
        val addTool = registry.getTool("add")
        assertNotNull(addTool)
        assertEquals("add", addTool!!.schema.name)

        val reverseTool = registry.getTool("reverse")
        assertNotNull(reverseTool)
        assertEquals("reverse", reverseTool!!.schema.name)
    }

    @Test
    fun `should find tools by full ID with service prefix`() {
        val mathService = MathService()
        val stringService = StringService()

        applicationContext.beanFactory.registerSingleton("mathService", mathService)
        applicationContext.beanFactory.registerSingleton("stringService", stringService)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val addTool = registry.getTool("mathService:add")
        assertNotNull(addTool)
        assertEquals("add", addTool!!.schema.name)

        val reverseTool = registry.getTool("stringService:reverse")
        assertNotNull(reverseTool)
        assertEquals("reverse", reverseTool!!.schema.name)
    }

    @Test
    fun `should maintain tool isolation between services`() {
        val mathService = MathService()
        val stringService = StringService()

        applicationContext.beanFactory.registerSingleton("mathService", mathService)
        applicationContext.beanFactory.registerSingleton("stringService", stringService)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val mathService1 = registry.getService("mathService")!!
        val stringService1 = registry.getService("stringService")!!

        assertNotEquals(mathService1.bean, stringService1.bean)
        assertEquals(2, mathService1.tools.size)
        assertEquals(2, stringService1.tools.size)
    }

    @Test
    fun `should handle services with overlapping tool names using explicit service names`() {
        val service1 = OverlapService1WithName()
        val service2 = OverlapService2WithName()

        applicationContext.beanFactory.registerSingleton("service1", service1)
        applicationContext.beanFactory.registerSingleton("service2", service2)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        // Both services have a 'process' tool but with explicit service names
        val tools = registry.getToolDefinitions()
        assertEquals(2, tools.size)

        // Client-visible format: "serviceName:toolName"
        val tool1 = registry.getTool("processor1:process")
        val tool2 = registry.getTool("processor2:process")
        assertNotNull(tool1)
        assertNotNull(tool2)
        assertNotEquals(tool1, tool2)
    }

    @Test
    fun `should provide all service instances to caller`() {
        val mathService = MathService()
        val stringService = StringService()
        val weatherService = WeatherService()

        applicationContext.beanFactory.registerSingleton("mathService", mathService)
        applicationContext.beanFactory.registerSingleton("stringService", stringService)
        applicationContext.beanFactory.registerSingleton("weatherService", weatherService)

        val registry = McpServiceRegistry(applicationContext, schemaGenerator)
        registry.discoverServices()

        val allServices = registry.getServices()
        assertEquals(3, allServices.size)

        // Verify correct service instances
        assertSame(mathService, allServices["mathService"]!!.bean)
        assertSame(stringService, allServices["stringService"]!!.bean)
        assertSame(weatherService, allServices["weatherService"]!!.bean)
    }

    // Test service implementations

    @McpService
    class MathService {
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
        fun mathHelp(): String = "Math help guide"
    }

    @McpService
    class StringService {
        @McpTool
        fun reverse(@McpParam(description = "Text to reverse") text: String): String = text.reversed()

        @McpTool
        fun uppercase(@McpParam(description = "Text to uppercase") text: String): String = text.uppercase()

        @McpPrompt
        fun stringHelp(): String = "String help guide"
    }

    @McpService
    class WeatherService {
        @McpTool
        fun getWeather(@McpParam(description = "City name") city: String): String = "Weather for $city: sunny"
    }

    @McpService
    class OverlapService1 {
        @McpTool
        fun process(@McpParam(description = "Input") input: String): String = "Service1: $input"
    }

    @McpService
    class OverlapService2 {
        @McpTool
        fun process(@McpParam(description = "Input") input: String): String = "Service2: $input"
    }

    @McpService(name = "processor1")
    class OverlapService1WithName {
        @McpTool
        fun process(@McpParam(description = "Input") input: String): String = "Service1: $input"
    }

    @McpService(name = "processor2")
    class OverlapService2WithName {
        @McpTool
        fun process(@McpParam(description = "Input") input: String): String = "Service2: $input"
    }
}


