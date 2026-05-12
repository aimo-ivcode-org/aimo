package org.ivcode.aimo.bedrock.client

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

@DisplayName("TypeExtractors")
class TypeExtractorsTest {

    @Test
    @DisplayName("extractReasoningText returns null for null input")
    fun testExtractReasoningTextNull() {
        assertNull(TypeExtractors.extractReasoningText(null))
    }

    @Test
    @DisplayName("extractReasoningText returns null when no reasoning field exists")
    fun testExtractReasoningTextNoField() {
        val obj = object {
            fun someOtherMethod() = "value"
        }
        assertNull(TypeExtractors.extractReasoningText(obj))
    }

    @Test
    @DisplayName("extractReasoningText extracts text via reasoningContent().text()")
    fun testExtractReasoningTextViaReasoningContent() {
        val reasoning = ReasoningMock("thinking step 1")
        val obj = MockWithReasoningContent(reasoning)

        val result = TypeExtractors.extractReasoningText(obj)
        assertEquals("thinking step 1", result)
    }

    @Test
    @DisplayName("extractReasoningText extracts text via reasoning().text()")
    fun testExtractReasoningTextViaReasoning() {
        val reasoning = ReasoningMock("thinking step 2")
        val obj = MockWithReasoning(reasoning)

        val result = TypeExtractors.extractReasoningText(obj)
        assertEquals("thinking step 2", result)
    }

    @Test
    @DisplayName("extractToolUseStart returns null for null input")
    fun testExtractToolUseStartNull() {
        assertNull(TypeExtractors.extractToolUseStart(null))
    }

    @Test
    @DisplayName("extractToolUseStart returns null when no toolUse field exists")
    fun testExtractToolUseStartNoField() {
        val obj = object {
            fun someMethod() = "value"
        }
        assertNull(TypeExtractors.extractToolUseStart(obj))
    }

    @Test
    @DisplayName("extractToolUseStart extracts tool-use fields")
    fun testExtractToolUseStart() {
        val toolUse = ToolUseMock("tool-123", "calculator", "{\"a\": 1}")
        val obj = MockWithToolUse(toolUse)

        val result = TypeExtractors.extractToolUseStart(obj)
        assertNotNull(result)
        assertEquals("tool-123", result.toolUseId)
        assertEquals("calculator", result.name)
        assertEquals("{\"a\": 1}", result.inputChunk)
    }

    @Test
    @DisplayName("extractToolUseDelta returns null for null input")
    fun testExtractToolUseDeltaNull() {
        assertNull(TypeExtractors.extractToolUseDelta(null))
    }

    @Test
    @DisplayName("extractToolUseDelta extracts tool-use delta fields")
    fun testExtractToolUseDelta() {
        val toolUse = ToolUseMock("tool-456", "api_call", "{ \"b\"")
        val obj = MockWithToolUse(toolUse)

        val result = TypeExtractors.extractToolUseDelta(obj)
        assertNotNull(result)
        assertEquals("tool-456", result.toolUseId)
        assertEquals("api_call", result.name)
        assertEquals("{ \"b\"", result.inputChunk)
    }

    @Test
    @DisplayName("invokeNoArg returns null for non-existent method")
    fun testInvokeNoArgNonExistent() {
        val obj = object {
            fun existingMethod() = "value"
        }
        assertNull(TypeExtractors.invokeNoArg(obj, "nonExistentMethod"))
    }

    @Test
    @DisplayName("invokeNoArg invokes method and returns result")
    fun testInvokeNoArgSuccess() {
        val obj = object {
            fun getValue() = "result"
        }
        val result = TypeExtractors.invokeNoArg(obj, "getValue")
        assertEquals("result", result)
    }

    @Test
    @DisplayName("invokeNoArg returns null if method has parameters")
    fun testInvokeNoArgWithParameters() {
        val obj = object {
            fun methodWithParam(arg: String) = arg
        }
        assertNull(TypeExtractors.invokeNoArg(obj, "methodWithParam"))
    }

    // Mock classes for testing reflection-based extraction

    private class ReasoningMock(val textValue: String) {
        fun text() = textValue
    }

    private class MockWithReasoningContent(val reasoningContentValue: ReasoningMock) {
        fun reasoningContent() = reasoningContentValue
    }

    private class MockWithReasoning(val reasoningValue: ReasoningMock) {
        fun reasoning() = reasoningValue
    }

    private class ToolUseMock(val idValue: String, val nameValue: String, val inputValue: String) {
        fun toolUseId() = idValue
        fun name() = nameValue
        fun input() = inputValue
    }

    private class MockWithToolUse(val toolUseValue: ToolUseMock) {
        fun toolUse() = toolUseValue
    }
}

