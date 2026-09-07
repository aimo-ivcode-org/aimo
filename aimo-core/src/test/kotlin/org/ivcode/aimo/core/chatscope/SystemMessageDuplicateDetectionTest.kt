package org.ivcode.aimo.core.chatscope

import org.ivcode.aimo.core.chatservice.ChatService
import org.ivcode.aimo.core.chatservice.SystemMessageCallback
import org.ivcode.aimo.core.chatservice.SystemMessage
import org.ivcode.aimo.core.chatservice.SystemMessageContext
import org.ivcode.aimo.core.chatservice.Tool
import org.ivcode.aimo.core.chatservice.ToolParam
import org.ivcode.aimo.core.chatservice.toSystemMessageCallbacks
import tools.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests detection and prevention of duplicate system message names.
 *
 * Verifies that the system message name registry detects and rejects duplicate
 * system message names across all ChatService beans, since names must be unique
 * for YAML references to work correctly.
 */
class SystemMessageDuplicateDetectionTest {

    @Test
    fun `duplicate explicit system message names are detected within same service`() {
        // Setup: Create service with two system messages having same explicit name
        val service = ServiceWithDuplicateNames()

        // Extract system messages - should fail during extraction
        val exception = assertFailsWith<IllegalArgumentException> {
            toSystemMessageCallbacks(service, emptySet())
        }

        assertTrue(
            exception.message?.contains("Duplicate system message name") == true,
            "Should detect duplicate explicit system message names: ${exception.message}"
        )
    }

    @Test
    fun `duplicate auto-generated system message names are detected within same service`() {
        // Setup: Create service with methods that auto-generate names but have collision
        val service = ServiceWithAmbiguousNames()

        // Extract system messages - should fail during extraction
        val exception = assertFailsWith<IllegalArgumentException> {
            toSystemMessageCallbacks(service, emptySet())
        }

        assertTrue(
            exception.message?.contains("Duplicate system message name") == true,
            "Should detect duplicate auto-generated system message names: ${exception.message}"
        )
    }

     @Test
     fun `unique system message names are accepted`() {
         // Setup: Single service with unique system message names
         val messages = toSystemMessageCallbacks(ServiceWithUniqueNames(), emptySet())

         assertEquals(2, messages.size)
         val names = messages.map { it.name }
         assertEquals(setOf("prompt1", "prompt2"), names.toSet())
     }

     @Test
     fun `single system message with unique name is accepted`() {
         // Setup: Single service with one system message
         val messages = toSystemMessageCallbacks(ServiceWithSingleMessage(), emptySet())

         assertEquals(1, messages.size)
         assertEquals("system_prompt", messages[0].name)
     }

    @Test
    fun `system messages with different scopes but same name are still duplicates within service`() {
        // Setup: One service with two messages that would have same name
        val service = ServiceWithScopedDuplicates()

        // Extract system messages - should fail during extraction
        val exception = assertFailsWith<IllegalArgumentException> {
            toSystemMessageCallbacks(service, setOf("admin", "research"))
        }

        assertTrue(
            exception.message?.contains("Duplicate system message name") == true,
            "Should detect duplicates regardless of scope: ${exception.message}"
        )
    }

    @Test
    fun `empty system message extraction is valid`() {
        // Setup: Service with no system messages
        val service = ServiceWithoutMessages()

        val messages = toSystemMessageCallbacks(service, emptySet())

        assertEquals(0, messages.size)
    }

    @Test
    fun `error message includes duplicate name for debugging`() {
        // Setup: Create duplicate for diagnosis
        val service = ServiceWithDuplicateNames()

        val exception = assertFailsWith<IllegalArgumentException> {
            toSystemMessageCallbacks(service, emptySet())
        }

        assertTrue(exception.message?.contains("Duplicate") == true, "Error message should identify the issue")
    }

     // Helper to build registry (extracted from AimoConfig logic)
     private fun buildSystemMessageNameRegistry(
         scopedSystemMessages: List<SystemMessageCallback>
     ): Map<String, SystemMessageCallback> {
         val registry = mutableMapOf<String, SystemMessageCallback>()

         for (message in scopedSystemMessages) {
             require(!registry.containsKey(message.name)) {
                 "Duplicate system message name '${message.name}' detected. System message names must be unique."
             }
             registry[message.name] = message
         }

         return registry
     }

    // Test classes

    @ChatService
    private class ServiceWithDuplicateNames {
        @SystemMessage(name = "admin_prompt")
        @Suppress("FunctionOnlyReturningConstant")
        fun firstAdminPrompt(): String = "Admin rules 1"

        @SystemMessage(name = "admin_prompt")
        @Suppress("FunctionOnlyReturningConstant")
        fun secondAdminPrompt(): String = "Admin rules 2"
    }

    @ChatService
    private class ServiceWithAmbiguousNames {
        @SystemMessage(name = "prompt1")
        @Suppress("FunctionOnlyReturningConstant")
        fun firstPrompt(): String = "First"

        @SystemMessage
        @Suppress("FunctionOnlyReturningConstant")
        fun prompt1(): String = "Second"
    }

    @ChatService
    private class ServiceWithUniqueNames {
        @SystemMessage
        @Suppress("FunctionOnlyReturningConstant")
        fun prompt1(): String = "Unique 1"

        @SystemMessage
        @Suppress("FunctionOnlyReturningConstant")
        fun prompt2(): String = "Unique 2"
    }

    @ChatService
    private class ServiceWithSingleMessage {
        @SystemMessage
        @Suppress("FunctionOnlyReturningConstant")
        fun system_prompt(): String = "Single message"
    }

    @ChatService(scope = ["admin", "research"])
    private class ServiceWithScopedDuplicates {
        @SystemMessage(name = "important_rules", scope = ["admin"])
        @Suppress("FunctionOnlyReturningConstant")
        fun rulesForAdmin(): String = "Admin rules"

        @SystemMessage(name = "important_rules", scope = ["research"])
        @Suppress("FunctionOnlyReturningConstant")
        fun rulesForResearch(): String = "Research rules"
    }

    @ChatService
    private class ServiceWithoutMessages {
        @Tool(name = "justATool", description = "No messages")
        @Suppress("FunctionOnlyReturningConstant")
        fun justATool(): String = "Tool"
    }
}










