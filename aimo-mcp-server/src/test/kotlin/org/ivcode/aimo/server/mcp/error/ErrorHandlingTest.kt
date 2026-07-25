package org.ivcode.aimo.server.mcp.error

import org.ivcode.aimo.server.mcp.protocol.McpErrorCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ErrorHandlingTest {

    private val errorHandler = McpErrorHandler()

    @Test
    fun `should handle ToolNotFoundException`() {
        val exception = ToolNotFoundException("Tool not found: calculate")
        val error = errorHandler.handleException(exception, 1)
        assertEquals(McpErrorCode.TOOL_NOT_FOUND, error.code)
    }

    @Test
    fun `should handle PromptNotFoundException`() {
        val exception = PromptNotFoundException("Prompt not found: help")
        val error = errorHandler.handleException(exception, 2)
        assertEquals(McpErrorCode.PROMPT_NOT_FOUND, error.code)
    }

    @Test
    fun `should handle ParameterValidationException`() {
        val exception = ParameterValidationException("Missing required parameter")
        val error = errorHandler.handleException(exception, 3)
        assertEquals(McpErrorCode.INVALID_PARAMS, error.code)
    }

    @Test
    fun `should handle ToolExecutionException`() {
        val exception = ToolExecutionException("Execution failed", RuntimeException("cause"))
        val error = errorHandler.handleException(exception, 4)
        assertEquals(McpErrorCode.TOOL_EXECUTION_FAILED, error.code)
    }

    @Test
    fun `should handle generic Exception`() {
        val exception = RuntimeException("Unknown error")
        val error = errorHandler.handleException(exception, 5)
        assertEquals(McpErrorCode.INTERNAL_ERROR, error.code)
    }

    @Test
    fun `should create missingRequiredParameter error`() {
        val error = errorHandler.missingRequiredParameter("username")
        assertEquals(McpErrorCode.INVALID_PARAMS, error.code)
        assertTrue(error.message.contains("username"))
    }

    @Test
    fun `should create invalidParameterType error`() {
        val error = errorHandler.invalidParameterType("age", "Integer")
        assertEquals(McpErrorCode.INVALID_PARAMS, error.code)
        assertTrue(error.message.contains("age"))
        assertTrue(error.message.contains("Integer"))
    }
}

