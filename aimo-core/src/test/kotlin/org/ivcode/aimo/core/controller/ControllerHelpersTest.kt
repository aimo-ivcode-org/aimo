package org.ivcode.aimo.core.controller

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tools.jackson.databind.ObjectMapper

class ControllerHelpersTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `toAimoToolCallbacks returns empty list when controller has no tool annotations`() {
        val callbacks = toAimoToolCallbacks(NoToolController(), objectMapper)

        assertTrue(callbacks.isEmpty())
    }

    @Test
    fun `toAimoToolCallbacks returns callbacks when controller has tool annotations`() {
        val callbacks = toAimoToolCallbacks(HasToolController(), objectMapper)

        assertEquals(1, callbacks.size)
    }

    @Test
    fun `toAimoToolCallbacks returns callbacks with derived tool definitions`() {
        val callbacks = toAimoToolCallbacks(HasToolController(), objectMapper)

        assertEquals(1, callbacks.size)
        assertEquals("ping", callbacks.first().toolDefinition.name)
        assertEquals("Returns pong", callbacks.first().toolDefinition.description)
        assertFalse(callbacks.first().toolDefinition.inputSchema.path("properties").has("context"))
    }

    @Test
    fun `toAimoToolCallback derives tool metadata and schema from annotated method`() {
        val controller = AimoToolController()
        val callback = toAimoToolCallback(
            controller = controller,
            method = AimoToolController::class.java.getDeclaredMethod("describe", String::class.java, Map::class.java),
            objectMapper = objectMapper,
        )

        assertEquals("describe", callback.toolDefinition.name)
        assertEquals("Describes a user", callback.toolDefinition.description)
        assertEquals("object", callback.toolDefinition.inputSchema.path("type").toString().trim('"'))
        assertEquals("string", callback.toolDefinition.inputSchema.path("properties").path("name").path("type").toString().trim('"'))
        assertEquals(1, callback.toolDefinition.inputSchema.path("required").size())
        assertEquals("name", callback.toolDefinition.inputSchema.path("required")[0].toString().trim('"'))
        assertFalse(callback.toolDefinition.inputSchema.path("properties").has("context"))
    }

    @Test
    fun `toAimoToolCallback falls back to method name when Tool name is blank`() {
        val controller = AimoToolController()
        val callback = toAimoToolCallback(
            controller = controller,
            method = AimoToolController::class.java.getDeclaredMethod("autoNamed", String::class.java),
            objectMapper = objectMapper,
        )

        assertEquals("autoNamed", callback.toolDefinition.name)
        assertEquals("Uses the method name", callback.toolDefinition.description)
    }

    @Test
    fun `toAimoToolCallback can invoke complex object parameter`() {
        val controller = AimoToolController()
        val callback = toAimoToolCallback(
            controller = controller,
            method = AimoToolController::class.java.getDeclaredMethod("summarizeProfile", ProfileRequest::class.java),
            objectMapper = objectMapper,
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
        assertEquals("object", callback.toolDefinition.inputSchema.path("properties").path("request").path("type").toString().trim('"'))
    }

    @Test
    fun `toAimoToolCallback uses Kotlin-aware default mapper for data classes`() {
        val controller = AimoToolController()
        val callback = toAimoToolCallback(
            controller = controller,
            method = AimoToolController::class.java.getDeclaredMethod("summarizeAccount", AccountRequest::class.java),
            objectMapper = objectMapper,
        )

        val result = callback.call(
            argumentsJson = """
                {
                  "request": {
                    "owner": {
                        "name": "Ada",
                        "plan": "pro"
                    },
                    "limits": {
                      "maxUsers": 7,
                      "retentionDays": 45
                    }
                  }
                }
            """.trimIndent(),
            context = emptyMap(),
        )

        assertEquals("Ada|pro|7|45", result)
    }

    @Test
    fun `toSystemMessageCallbacks supports plain kotlin property annotation`() {
        val callbacks = toSystemMessageCallbacks(PlainPropertySystemMessageController())

        assertEquals(1, callbacks.size)
        assertEquals("property-system-message", callbacks.first().call(SystemMessageContext(emptyMap())))
    }

    @Test
    fun `toSystemMessageCallbacks still supports field annotation`() {
        val callbacks = toSystemMessageCallbacks(FieldSystemMessageController())

        assertEquals(1, callbacks.size)
        assertEquals("field-system-message", callbacks.first().call(SystemMessageContext(emptyMap())))
    }

    @Test
    fun `toAimoToolCallback includes parameter descriptions from ToolParam annotation`() {
        val controller = AimoToolController()
        val callback = toAimoToolCallback(
            controller = controller,
            method = AimoToolController::class.java.getDeclaredMethod("search", String::class.java, Int::class.java),
            objectMapper = objectMapper,
        )

        val properties = callback.toolDefinition.inputSchema.path("properties")
        assertEquals("Search term to find", properties.path("query").path("description").toString().trim('"'))
        assertEquals("Number of results to return", properties.path("limit").path("description").toString().trim('"'))
    }

    private class NoToolController {
        fun ping(): String = "pong"
    }

    private class HasToolController {
        @Tool(name = "ping", description = "Returns pong")
        fun ping(): String = "pong"
    }

    private class AimoToolController {
        @Tool(name = "describe", description = "Describes a user")
        fun describe(name: String, context: Map<String, Any>): String = "$name:${context["role"]}"

        @Tool(description = "Uses the method name")
        fun autoNamed(value: String): String = value.uppercase()

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

        @Tool(name = "summarizeAccount", description = "Summarizes a data-class payload")
        fun summarizeAccount(request: AccountRequest): String {
            return listOf(
                request.owner.name,
                request.owner.plan,
                request.limits.maxUsers,
                request.limits.retentionDays,
            ).joinToString("|")
        }

        @Tool(name = "search", description = "Search for items")
        fun search(
            @ToolParam(description = "Search term to find") query: String,
            @ToolParam(description = "Number of results to return") limit: Int,
        ): String = "$query:$limit"
    }

    private class PlainPropertySystemMessageController {
        @SystemMessage
        val message = "property-system-message"
    }

    private class FieldSystemMessageController {
        @field:SystemMessage
        val message = "field-system-message"
    }

    private class ProfileRequest {
        var user: User = User()
        var settings: Settings = Settings()
    }

    data class AccountRequest(
        val owner: Owner,
        val limits: Limits,
    )

    data class Owner(
        val name: String,
        val plan: String,
    )

    data class Limits(
        val maxUsers: Int,
        val retentionDays: Int,
    )

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
}

