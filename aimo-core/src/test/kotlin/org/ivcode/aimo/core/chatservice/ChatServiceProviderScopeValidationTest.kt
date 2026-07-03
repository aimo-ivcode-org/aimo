package org.ivcode.aimo.core.chatservice

import org.ivcode.aimo.core.model.ToolCallback
import org.ivcode.aimo.core.model.ToolDefinition
import tools.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for ChatServiceProvider scope validation.
 *
 * Verifies that callback scopes must be subsets of provider scopes:
 * - If provider.scopes is empty: all callbacks allowed (global/unrestricted)
 * - If provider.scopes is ["a", "b"]:
 *   - Callback with scopes [] is valid (inherits)
 *   - Callback with scopes ["a"] is valid
 *   - Callback with scopes ["b"] is valid
 *   - Callback with scopes ["a", "b"] is valid
 *   - Callback with scopes ["c"] is invalid (not in parent)
 *   - Callback with scopes ["a", "c"] is invalid (c not in parent)
 */
class ChatServiceProviderScopeValidationTest {
    private val objectMapper = ObjectMapper()

    private class TestTool(
        val name: String,
        override val scopes: Set<String>,
        private val objectMapper: ObjectMapper
    ) : ToolCallback {
        override val toolDefinition: ToolDefinition =
            ToolDefinition(
                name = name,
                description = "Test tool $name",
                inputSchema = objectMapper.readTree("""{"type":"object","properties":{},"required":[]}""")
            )

        override fun call(argumentsJson: String, context: Map<String, Any>): String = "result"
    }

    private class TestSystemMessage(
        val msgName: String,
        override val scopes: Set<String>
    ) : SystemMessageCallback {
        override val name: String = msgName
        override fun call(context: SystemMessageContext): String? = "message"
    }

    private class TestProvider(
        override val id: String,
        override val scopes: Set<String>,
        private val tools: List<ToolCallback>,
        private val messages: List<SystemMessageCallback>
    ) : ChatServiceProvider {
        override fun getTools(): List<ToolCallback> = tools
        override fun getSystemMessages(): List<SystemMessageCallback> = messages
    }

    @Test
    fun `global provider (empty scopes) allows any callback scopes`() {
        val provider = TestProvider(
            id = "global",
            scopes = emptySet(),
            tools = listOf(
                TestTool("tool1", emptySet(), objectMapper),
                TestTool("tool2", setOf("admin"), objectMapper),
                TestTool("tool3", setOf("admin", "research", "custom"), objectMapper)
            ),
            messages = listOf(
                TestSystemMessage("msg1", emptySet()),
                TestSystemMessage("msg2", setOf("any", "scope", "allowed"))
            )
        )

        // Should not throw
        provider.validateCallbackScopes()
    }

    @Test
    fun `provider with scopes allows subset callback scopes`() {
        val provider = TestProvider(
            id = "restricted",
            scopes = setOf("admin", "research"),
            tools = listOf(
                TestTool("tool1", emptySet(), objectMapper),  // Empty inherits
                TestTool("tool2", setOf("admin"), objectMapper),  // Subset
                TestTool("tool3", setOf("research"), objectMapper),  // Subset
                TestTool("tool4", setOf("admin", "research"), objectMapper)  // Exact match
            ),
            messages = listOf(
                TestSystemMessage("msg1", emptySet()),
                TestSystemMessage("msg2", setOf("admin")),
                TestSystemMessage("msg3", setOf("research"))
            )
        )

        // Should not throw
        provider.validateCallbackScopes()
    }

    @Test
    fun `provider rejects callback scopes outside provider scopes`() {
        val provider = TestProvider(
            id = "restricted",
            scopes = setOf("admin", "research"),
            tools = listOf(
                TestTool("invalid_tool", setOf("forbidden"), objectMapper)  // Not in provider scopes
            ),
            messages = emptyList()
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            provider.validateCallbackScopes()
        }

        assertTrue(
            exception.message?.contains("has invalid scopes") ?: false,
            "Should reject callback with scope not in provider: ${exception.message}"
        )
        assertTrue(
            exception.message?.contains("forbidden") ?: false,
            "Should mention the invalid scope name"
        )
    }

    @Test
    fun `provider rejects partial overlap in callback scopes`() {
        val provider = TestProvider(
            id = "restricted",
            scopes = setOf("admin", "research"),
            tools = listOf(
                TestTool("partial_tool", setOf("admin", "forbidden"), objectMapper)  // One valid, one invalid
            ),
            messages = emptyList()
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            provider.validateCallbackScopes()
        }

        assertTrue(
            exception.message?.contains("has invalid scopes") ?: false,
            "Should reject when ANY scope is outside provider scopes"
        )
    }

    @Test
    fun `provider rejects callback with completely different scopes`() {
        val provider = TestProvider(
            id = "admin_only",
            scopes = setOf("admin"),
            tools = listOf(
                TestTool("research_tool", setOf("research"), objectMapper)  // Completely different scope
            ),
            messages = emptyList()
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            provider.validateCallbackScopes()
        }

        assertTrue(
            exception.message?.contains("has invalid scopes") ?: false,
            "Should reject callback with non-overlapping scopes"
        )
    }

    @Test
    fun `provider allows empty callback scopes (inheritance)`() {
        val provider = TestProvider(
            id = "restricted",
            scopes = setOf("admin", "research"),
            tools = listOf(
                TestTool("unrestricted_tool", emptySet(), objectMapper)  // Empty = inherits parent
            ),
            messages = listOf(
                TestSystemMessage("unrestricted_msg", emptySet())
            )
        )

        // Empty scopes should always be valid (callback inherits parent)
        provider.validateCallbackScopes()
    }

    @Test
    fun `validation includes tool names in error message`() {
        val provider = TestProvider(
            id = "test",
            scopes = setOf("allowed"),
            tools = listOf(
                TestTool("my_tool", setOf("forbidden"), objectMapper)
            ),
            messages = emptyList()
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            provider.validateCallbackScopes()
        }

        assertTrue(
            exception.message?.contains("my_tool") ?: false,
            "Error should mention the tool name"
        )
    }

    @Test
    fun `validation includes system message names in error message`() {
        val provider = TestProvider(
            id = "test",
            scopes = setOf("allowed"),
            tools = emptyList(),
            messages = listOf(
                TestSystemMessage("my_message", setOf("forbidden"))
            )
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            provider.validateCallbackScopes()
        }

        assertTrue(
            exception.message?.contains("my_message") ?: false,
            "Error should mention the system message name"
        )
    }
}
