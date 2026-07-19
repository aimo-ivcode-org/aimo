package org.ivcode.aimo.core.chatservice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import tools.jackson.databind.ObjectMapper

/**
 * Tests for ControllerHelpers, which handles:
 * - Tool discovery and JSON schema generation from annotations
 * - System message discovery and callback wrapping
 * - Scope computation and validation for tools and system messages
 * - Auto-injected context parameter exclusion from schema
 * - @ToolParam description propagation
 */
class ControllerHelpersTest {

    private val objectMapper = ObjectMapper()

    // ===== TOOL DISCOVERY & SCHEMA GENERATION =====

    @Test
    fun discoversToolsFromToolAnnotatedMethods() {
        val controller = TestController()
        val callbacks = toToolCallbacks(controller, objectMapper)

        assertEquals(3, callbacks.size, "Should discover 3 tools")

        val toolNames = callbacks.map { it.toolDefinition.name }.sorted()
        assertEquals(listOf("add", "divide", "multiply"), toolNames)
    }

    @Test
    fun generatesCorrectJsonSchemaForToolParameters() {
        val controller = TestController()
        val callbacks = toToolCallbacks(controller, objectMapper)
        val addTool = callbacks.find { it.toolDefinition.name == "add" }
            ?: throw AssertionError("add tool not found")

        val schema = addTool.toolDefinition.inputSchema
        assertEquals("object", schema.get("type").asText())

        val properties = schema.get("properties")
        assertTrue(properties.has("a"), "Schema should have 'a' parameter")
        assertTrue(properties.has("b"), "Schema should have 'b' parameter")
        assertEquals("number", properties.get("a").get("type").asText())
        assertEquals("number", properties.get("b").get("type").asText())

        val required = schema.get("required")
        assertEquals(2, required.size(), "Both 'a' and 'b' should be required")
    }

    @Test
    fun excludesAutoInjectedContextParameterFromJsonSchema() {
        val controller = TestController()
        val callbacks = toToolCallbacks(controller, objectMapper)
        val divide = callbacks.find { it.toolDefinition.name == "divide" }
            ?: throw AssertionError("divide tool not found")

        val schema = divide.toolDefinition.inputSchema
        val properties = schema.get("properties")

        // divide has signature: divide(a: Double, b: Double, context: Map<String, Any>)
        // Schema should only include 'a' and 'b', not 'context'
        assertEquals(2, properties.size(), "Schema should only have 'a' and 'b' (context excluded)")
        assertTrue(properties.has("a"), "Schema should have 'a' parameter")
        assertTrue(properties.has("b"), "Schema should have 'b' parameter")
        assertEquals(false, properties.has("context"), "Schema should NOT include auto-injected context parameter")
    }

    @Test
    fun propagatesToolParamDescriptionsToSchema() {
        val controller = TestController()
        val callbacks = toToolCallbacks(controller, objectMapper)
        val multiply = callbacks.find { it.toolDefinition.name == "multiply" }
            ?: throw AssertionError("multiply tool not found")

        val schema = multiply.toolDefinition.inputSchema
        val properties = schema.get("properties")

        // multiply has signature: multiply(x: Double @ToolParam("First operand"), y: Double @ToolParam("Second operand"))
        assertEquals("First operand", properties.get("x").get("description").asText())
        assertEquals("Second operand", properties.get("y").get("description").asText())
    }

    @Test
    fun usesCustomToolNameFromAnnotation() {
        val controller = TestControllerWithCustomNames()
        val callbacks = toToolCallbacks(controller, objectMapper)
        val toolNames = callbacks.map { it.toolDefinition.name }

        assertTrue(toolNames.contains("custom_add"), "Should use custom name 'custom_add' from @Tool(name=...)")
        assertEquals(1, callbacks.size, "Should only have 1 tool")
    }

    // ===== SCOPE COMPUTATION & VALIDATION =====

    @Test
    fun computesEmptyDeclaredScopesInheritParentScopes() {
        val controller = TestControllerWithScopes()
        val parentServiceScopes = setOf("admin", "research")
        val callbacks = toToolCallbacks(controller, objectMapper, parentServiceScopes)

        // Both tools in TestControllerWithScopes have empty @Tool(scope=[])
        // They should inherit parent scopes
        callbacks.forEach { callback ->
            assertEquals(
                parentServiceScopes,
                callback.scopes,
                "Tool ${callback.toolDefinition.name} should inherit parent scopes when declared scope is empty"
            )
        }
    }

    @Test
    fun computesNonEmptyDeclaredScopesIntersectWithParentScopes() {
        val controller = TestControllerAdminOnly()
        val parentServiceScopes = setOf("admin", "research", "public")
        val callbacks = toToolCallbacks(controller, objectMapper, parentServiceScopes)

        // Tool has @Tool(scope=["admin"])
        // Intersection with parent = ["admin"]
        assertEquals(1, callbacks.size)
        assertEquals(setOf("admin"), callbacks[0].scopes)
    }

    @Test
    fun throwsErrorWhenToolScopeNotSubsetOfParentScope() {
        val controller = TestControllerInvalidScope()
        val parentServiceScopes = setOf("admin", "research")

        val exception = assertFailsWith<IllegalArgumentException> {
            toToolCallbacks(controller, objectMapper, parentServiceScopes)
        }

        assertTrue(
            exception.message?.contains("has scopes not in parent service") ?: false,
            "Error should mention invalid scope"
        )
    }

    @Test
    fun throwsErrorWhenToolScopeHasZeroIntersectionWithParent() {
        val controller = TestControllerIntersectionFail()
        val parentServiceScopes = setOf("admin", "research")

        val exception = assertFailsWith<IllegalArgumentException> {
            toToolCallbacks(controller, objectMapper, parentServiceScopes)
        }

        // Since all declared scopes are NOT in parent, this triggers "not in parent service" error
        // The intersection error would only occur if some (but not all) scopes were in parent
        assertTrue(
            exception.message?.contains("has scopes not in parent service") ?: false,
            "Error should mention invalid scope not in parent"
        )
    }

    // ===== SYSTEM MESSAGE DISCOVERY =====

    @Test
    fun discoversSystemMessagesFromAnnotations() {
        val controller = TestControllerSystemMessages()
        val callbacks = toSystemMessageCallbacks(controller)

        assertEquals(2, callbacks.size, "Should discover 2 system messages")

        val names = callbacks.map { it.name }.sorted()
        assertEquals(listOf("custom_name", "greeting"), names)
    }

    @Test
    fun usesCustomSystemMessageNameFromAnnotation() {
        val controller = TestControllerSystemMessages()
        val callbacks = toSystemMessageCallbacks(controller)
        val customNamed = callbacks.find { it.name == "custom_name" }

        assertTrue(customNamed != null, "Should have system message with custom name")
    }

    @Test
    fun autoGeneratesSystemMessageNameFromMethodName() {
        val controller = TestControllerSystemMessages()
        val callbacks = toSystemMessageCallbacks(controller)
        val autoNamed = callbacks.find { it.name == "greeting" }

        assertTrue(autoNamed != null, "Should have system message with auto-generated name 'greeting'")
    }

    @Test
    fun detectsDuplicateSystemMessageNames() {
        val controller = TestControllerDuplicateSystemMessages()

        val exception = assertFailsWith<IllegalArgumentException> {
            toSystemMessageCallbacks(controller)
        }

        assertTrue(
            exception.message?.contains("Duplicate system message name") ?: false,
            "Should detect duplicate system message names"
        )
    }

    @Test
    fun systemMessagesWithEmptyScopeInheritParentScopes() {
        val controller = TestControllerSystemMessages()
        val parentServiceScopes = setOf("admin", "public")
        val callbacks = toSystemMessageCallbacks(controller, parentServiceScopes)

        callbacks.forEach { callback ->
            assertEquals(
                parentServiceScopes,
                callback.scopes,
                "System message ${callback.name} should inherit parent scopes when declared scope is empty"
            )
        }
    }

    // ===== TEST FIXTURES =====

    @ChatService
    class TestController {
        @Tool(description = "Add two numbers")
        fun add(a: Double, b: Double): Double = a + b

        @Tool(description = "Multiply two numbers")
        fun multiply(
            @ToolParam("First operand") x: Double,
            @ToolParam("Second operand") y: Double
        ): Double = x * y

        @Tool(description = "Divide two numbers")
        fun divide(a: Double, b: Double, context: Map<String, Any>): Double {
            // Context is auto-injected and should NOT appear in schema
            return a / b
        }
    }

    @ChatService
    class TestControllerWithCustomNames {
        @Tool(name = "custom_add", description = "Custom-named add tool")
        fun add(a: Int, b: Int): Int = a + b
    }

    @ChatService(scope = ["admin", "research"])
    class TestControllerWithScopes {
        @Tool(description = "Tool with inherited scope")
        fun protectedTool(): String = "protected"

        @Tool(scope = [], description = "Tool with empty scope (inherits parent)")
        fun inheritedTool(): String = "inherited"
    }

    @ChatService(scope = ["admin", "research", "public"])
    class TestControllerAdminOnly {
        @Tool(scope = ["admin"], description = "Admin-only tool")
        fun adminTool(): String = "admin"
    }

    @ChatService(scope = ["admin", "research"])
    class TestControllerInvalidScope {
        @Tool(scope = ["superadmin"], description = "Tool with invalid scope")
        fun invalidScopeTool(): String = "invalid"
    }

    @ChatService(scope = ["admin", "research"])
    class TestControllerIntersectionFail {
        @Tool(scope = ["superadmin", "superuser"], description = "Tool with zero intersection")
        fun intersectionFailTool(): String = "fail"
    }

    @ChatService
    class TestControllerSystemMessages {
        @SystemMessage(name = "custom_name")
        fun getCustomMessage(): String = "Custom system message"

        @SystemMessage
        fun greeting(): String = "Hello!"
    }

    @ChatService
    class TestControllerDuplicateSystemMessages {
        @SystemMessage(name = "duplicate")
        fun first(): String = "First"

        @SystemMessage(name = "duplicate")
        fun second(): String = "Second"
    }
}
