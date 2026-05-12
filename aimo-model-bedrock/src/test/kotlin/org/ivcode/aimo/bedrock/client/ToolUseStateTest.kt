package org.ivcode.aimo.bedrock.client

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import software.amazon.awssdk.core.document.Document
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DisplayName("ToolUseState")
class ToolUseStateTest {

    private val mapper = jacksonObjectMapper()

    @Test
    @DisplayName("mergeFrom updates toolUseId")
    fun testMergeFromToolUseId() {
        val state = ToolUseState()
        val partial = ToolUsePartial(toolUseId = "tool-123")

        state.mergeFrom(partial)
        assertEquals("tool-123", state.toolUseId)
    }

    @Test
    @DisplayName("mergeFrom updates name")
    fun testMergeFromName() {
        val state = ToolUseState()
        val partial = ToolUsePartial(name = "calculator")

        state.mergeFrom(partial)
        assertEquals("calculator", state.name)
    }

    @Test
    @DisplayName("mergeFrom appends inputChunk")
    fun testMergeFromInputChunk() {
        val state = ToolUseState()
        state.mergeFrom(ToolUsePartial(inputChunk = "{\"a"))
        state.mergeFrom(ToolUsePartial(inputChunk = "\":"))
        state.mergeFrom(ToolUsePartial(inputChunk = "1}"))

        assertEquals("{\"a\":1}", state.inputChunks.toString())
    }

    @Test
    @DisplayName("mergeFrom accumulates input across multiple deltas")
    fun testMergeFromInputAccumulation() {
        val state = ToolUseState()
        state.mergeFrom(ToolUsePartial(inputChunk = "part1"))
        state.mergeFrom(ToolUsePartial(inputChunk = "part2"))
        state.mergeFrom(ToolUsePartial(inputChunk = "part3"))

        assertEquals("part1part2part3", state.inputChunks.toString())
    }

    @Test
    @DisplayName("mergeFrom handles invalid names by not updating")
    fun testMergeFromBlankName() {
        val state = ToolUseState()
        state.mergeFrom(ToolUsePartial(name = "first"))
        state.mergeFrom(ToolUsePartial(name = "  "))  // blank, should not update

        assertEquals("first", state.name)
    }

    @Test
    @DisplayName("toToolUse returns null when toolUseId is missing")
    fun testToToolUseNullWithoutId() {
        val state = ToolUseState()
        state.name = "calculator"
        state.inputChunks.append("{}")

        assertNull(state.toToolUse(mapper))
    }

    @Test
    @DisplayName("toToolUse returns null when name is missing")
    fun testToToolUseNullWithoutName() {
        val state = ToolUseState()
        state.toolUseId = "tool-123"
        state.inputChunks.append("{}")

        assertNull(state.toToolUse(mapper))
    }

    @Test
    @DisplayName("toToolUse parses JSON input chunks")
    fun testToToolUseJsonInput() {
        val state = ToolUseState()
        state.toolUseId = "tool-123"
        state.name = "calculator"
        state.inputChunks.append("{\"operation\":\"add\",\"a\":1,\"b\":2}")

        val result = state.toToolUse(mapper)
        assertNotNull(result)
        assertEquals("tool-123", result.toolUseId)
        assertEquals("calculator", result.name)
        assertEquals("add", result.input["operation"])
        // Jackson returns Int for integer literals, not Double
        assertEquals(1, result.input["a"])
        assertEquals(2, result.input["b"])
    }

    @Test
    @DisplayName("toToolUse handles malformed JSON by using raw fallback")
    fun testToToolUseMalformedJson() {
        val state = ToolUseState()
        state.toolUseId = "tool-123"
        state.name = "calculator"
        state.inputChunks.append("{invalid json")

        val result = state.toToolUse(mapper)
        assertNotNull(result)
        assertEquals("tool-123", result.toolUseId)
        assertEquals("calculator", result.name)
        assertEquals("{invalid json", result.input["raw"])
    }

    @Test
    @DisplayName("toToolUse returns empty map when no input provided")
    fun testToToolUseEmptyInput() {
        val state = ToolUseState()
        state.toolUseId = "tool-123"
        state.name = "calculator"

        val result = state.toToolUse(mapper)
        assertNotNull(result)
        assertEquals(emptyMap(), result.input)
    }

    @Test
    @DisplayName("toToolUse resolves Document input")
    fun testToToolUseDocumentInput() {
        val state = ToolUseState()
        state.toolUseId = "tool-123"
        state.name = "calculator"
        state.inputDocument = Document.fromMap(mapOf(
            "operation" to Document.fromString("add"),
            "value" to Document.fromNumber(42)
        ))

        val result = state.toToolUse(mapper)
        assertNotNull(result)
        assertEquals("tool-123", result.toolUseId)
        assertEquals("add", result.input["operation"])
        // Document unwrap returns Double for numbers
        assertEquals(42.0, result.input["value"])
    }

    @Test
    @DisplayName("toToolUse prefers Document input over JSON chunks")
    fun testToToolUseDocumentPrecedence() {
        val state = ToolUseState()
        state.toolUseId = "tool-123"
        state.name = "calculator"
        state.inputChunks.append("{\"from\":\"json\"}")
        state.inputDocument = Document.fromMap(mapOf(
            "from" to Document.fromString("document")
        ))

        val result = state.toToolUse(mapper)
        assertNotNull(result)
        assertEquals("document", result.input["from"])
    }

    @Test
    @DisplayName("toToolUse handles complex nested JSON")
    fun testToToolUseComplexJson() {
        val state = ToolUseState()
        state.toolUseId = "tool-456"
        state.name = "api_call"
        state.inputChunks.append("""{
            "endpoint": "/api/users",
            "method": "POST",
            "body": {
                "name": "John",
                "age": 30,
                "active": true
            }
        }""")

        val result = state.toToolUse(mapper)
        assertNotNull(result)
        assertEquals("tool-456", result.toolUseId)
        assertEquals("/api/users", result.input["endpoint"])
        assertEquals("POST", result.input["method"])
        val body = result.input["body"] as? Map<*, *>
        assertNotNull(body)
        assertEquals("John", body["name"])
        assertEquals(30, body["age"])  // Jackson returns Int for integer literals
        assertEquals(true, body["active"])
    }
}

