package org.ivcode.aimo.core.controller

import org.ivcode.aimo.core.model.AimoToolDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import tools.jackson.databind.ObjectMapper

class MethodAimoToolCallbackTest {

    private val objectMapper = ObjectMapper()
    private val inputSchema = objectMapper.createObjectNode().put("type", "object")

    @Test
    fun `call returns string result unchanged`() {
        val controller = TestToolController()
        val callback = MethodAimoToolCallback(
            target = controller,
            method = TestToolController::class.java.getDeclaredMethod("echo", String::class.java),
            toolDefinition = toolDefinition("echo"),
        )

        val result = callback.call("""{"value":"hello"}""", emptyMap())

        assertEquals("hello", result)
    }

    @Test
    fun `call injects context and serializes object response`() {
        val controller = TestToolController()
        val callback = MethodAimoToolCallback(
            target = controller,
            method = TestToolController::class.java.getDeclaredMethod("describe", String::class.java, Map::class.java),
            toolDefinition = toolDefinition("describe"),
        )

        val result = callback.call(
            argumentsJson = """{"name":"Ada"}""",
            context = mapOf("role" to "admin"),
        )

        assertEquals("""{"message":"Ada:admin"}""", result)
    }

    @Test
    fun `call fails when required argument is missing`() {
        val controller = TestToolController()
        val callback = MethodAimoToolCallback(
            target = controller,
            method = TestToolController::class.java.getDeclaredMethod("echo", String::class.java),
            toolDefinition = toolDefinition("echo"),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            callback.call("{}", emptyMap())
        }

        assertEquals("Missing required tool argument 'value' for tool 'echo'", error.message)
    }

    @Test
    fun `call binds deeply nested complex argument object`() {
        val controller = TestToolController()
        val callback = MethodAimoToolCallback(
            target = controller,
            method = TestToolController::class.java.getDeclaredMethod("summarizeProfile", ProfileRequest::class.java),
            toolDefinition = toolDefinition("summarizeProfile"),
        )

        val result = callback.call(
            argumentsJson =
                """
                {
                  "request": {
                    "user": {
                      "name": "Ada",
                      "address": {
                        "city": "London",
                        "coordinates": {
                          "lat": 51.5074,
                          "lng": -0.1278
                        }
                      }
                    },
                    "settings": {
                      "notifications": {
                        "email": true,
                        "channels": ["news", "alerts"]
                      },
                      "metadata": {
                        "priority": "high"
                      }
                    }
                  }
                }
                """.trimIndent(),
            context = emptyMap(),
        )

        assertEquals("Ada|London|51.5074|true|news,alerts|high", result)
    }

    @Test
    fun `constructor fails when method is not annotated with Tool`() {
        val controller = TestToolController()

        val error = assertFailsWith<IllegalArgumentException> {
            MethodAimoToolCallback(
                target = controller,
                method = TestToolController::class.java.getDeclaredMethod("notATool", String::class.java),
                toolDefinition = toolDefinition("not_a_tool"),
            )
        }

        assertEquals(
            "Method notATool on org.ivcode.aimo.core.controller.MethodAimoToolCallbackTest\$TestToolController must be annotated with @Tool",
            error.message,
        )
    }

    private fun toolDefinition(name: String): AimoToolDefinition = AimoToolDefinition(
        name = name,
        description = "test tool",
        inputSchema = inputSchema,
        schemaDialect = "https://json-schema.org/draft/2020-12/schema",
    )

    private class TestToolController {
        @Tool(name = "echo", description = "Echoes a value")
        fun echo(value: String): String = value

        @Tool(name = "describe", description = "Describes a user")
        fun describe(name: String, context: Map<String, Any>): ToolResponse =
            ToolResponse(message = "$name:${context["role"]}")

        @Tool(name = "summarizeProfile", description = "Summarizes a nested profile payload")
        fun summarizeProfile(request: ProfileRequest): String {
            return listOf(
                request.user.name,
                request.user.address.city,
                request.user.address.coordinates.lat,
                request.settings.notifications.email,
                request.settings.notifications.channels.joinToString(","),
                request.settings.metadata["priority"],
            ).joinToString("|")
        }

        fun notATool(value: String): String = value
    }

    private class ProfileRequest {
        var user: User = User()
        var settings: Settings = Settings()
    }

    private class User {
        var name: String = ""
        var address: Address = Address()
    }

    private class Address {
        var city: String = ""
        var coordinates: Coordinates = Coordinates()
    }

    private class Coordinates {
        var lat: Double = 0.0
        var lng: Double = 0.0
    }

    private class Settings {
        var notifications: Notifications = Notifications()
        var metadata: Map<String, String> = emptyMap()
    }

    private class Notifications {
        var email: Boolean = false
        var channels: List<String> = emptyList()
    }

    private data class ToolResponse(
        val message: String,
    )
}

