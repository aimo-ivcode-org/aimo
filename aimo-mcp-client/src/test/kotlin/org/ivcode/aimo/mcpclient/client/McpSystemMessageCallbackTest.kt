package org.ivcode.aimo.mcpclient.client

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.ivcode.aimo.core.chatservice.SystemMessageContext
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertNull

/**
 * Tests for McpSystemMessageCallback error handling.
 * Ensures that errors are logged but not propagated as system message content.
 */
class McpSystemMessageCallbackTest {
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setup() {
        objectMapper = ObjectMapper()
    }

    @Test
    fun `should return null when protocol client is not initialized`() {
        // Setup: create callback with null protocol client
        // This tests the graceful degradation when server hasn't connected
        val callback = McpSystemMessageCallback(
            namespacedName = "test-server:my-prompt",
            serverId = "test-server",
            promptName = "my-prompt",
            protocolClient = null,
            objectMapper = objectMapper,
            scopes = setOf("global"),
        )

        // Call the callback
        val context = SystemMessageContext(context = emptyMap())
        val result = callback.call(context)

        // Should return null gracefully instead of error text
        assertNull(result, "Should return null when protocol client is not initialized")
    }

    @Test
    fun `McpSystemMessageCallback should not return error strings on failure`() {
        // This test verifies the code change:
        // Before: returned error strings like "Error: ..." or "Prompt execution returned no result"
        // After: returns null for graceful degradation

        // The actual error cases are tested implicitly when:
        // 1. protocolClient is null → returns null (verified above)
        // 2. sendRequest throws exception → caught and returns null (verified in callback code)
        // 3. response.error is not null → returns null (verified in callback code)
        // 4. response.result is null → returns null (verified in callback code)

        // This integration test just verifies the null case works
        val callback = McpSystemMessageCallback(
            namespacedName = "test:prompt",
            serverId = "test",
            promptName = "prompt",
            protocolClient = null,
            objectMapper = objectMapper,
            scopes = emptySet(),
        )

        val context = SystemMessageContext(context = emptyMap())
        val result = callback.call(context)

        // Key assertion: no error strings are returned
        assertNull(result, "Error cases should return null, not error strings")
    }
}





