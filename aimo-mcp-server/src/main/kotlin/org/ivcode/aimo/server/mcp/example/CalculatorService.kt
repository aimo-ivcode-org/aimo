package org.ivcode.aimo.server.mcp.example

import org.ivcode.aimo.server.mcp.annotation.McpParam
import org.ivcode.aimo.server.mcp.annotation.McpService
import org.ivcode.aimo.server.mcp.annotation.McpTool
import org.ivcode.aimo.server.mcp.annotation.McpPrompt
import org.ivcode.aimo.server.mcp.annotation.McpContext

/**
 * Example MCP service demonstrating calculator operations.
 *
 * Shows how to use @McpService, @McpTool, and @McpPrompt annotations.
 */
@McpService
class CalculatorService {

    /**
     * Add two numbers.
     */
    @McpTool(
        name = "add",
        description = "Add two numbers together"
    )
    fun add(
        @McpParam(description = "First number")
        a: Double,

        @McpParam(description = "Second number")
        b: Double
    ): Double {
        return a + b
    }

    /**
     * Subtract two numbers.
     */
    @McpTool(
        name = "subtract",
        description = "Subtract the second number from the first"
    )
    fun subtract(
        @McpParam(description = "First number")
        a: Double,

        @McpParam(description = "Second number")
        b: Double
    ): Double {
        return a - b
    }

    /**
     * Multiply two numbers.
     */
    @McpTool(
        name = "multiply",
        description = "Multiply two numbers"
    )
    fun multiply(
        @McpParam(description = "First number")
        a: Double,

        @McpParam(description = "Second number")
        b: Double
    ): Double {
        return a * b
    }

    /**
     * Divide two numbers.
     */
    @McpTool(
        name = "divide",
        description = "Divide the first number by the second"
    )
    fun divide(
        @McpParam(description = "Dividend")
        a: Double,

        @McpParam(description = "Divisor")
        b: Double
    ): String {
        if (b == 0.0) {
            return "Error: Cannot divide by zero"
        }
        return (a / b).toString()
    }

    /**
     * Get calculator instructions.
     */
    @McpPrompt(
        name = "calculator-help",
        description = "Get help on calculator operations"
    )
    fun getHelp(): String {
        return """
            # Calculator Tool

            Available operations:
            - add(a, b) - Add two numbers
            - subtract(a, b) - Subtract b from a
            - multiply(a, b) - Multiply two numbers
            - divide(a, b) - Divide a by b

            Example usage:
            - add(5, 3) = 8
            - divide(10, 2) = 5
        """.trimIndent()
    }

    /**
     * Get arithmetic tutorial.
     */
    @McpPrompt(
        name = "arithmetic-tutorial",
        description = "Learn about basic arithmetic"
    )
    fun getTutorial(
        @McpParam(description = "Topic: addition, subtraction, multiplication, division")
        topic: String = "all"
    ): String {
        return when (topic.lowercase()) {
            "addition" -> "Addition combines two numbers to get their sum."
            "subtraction" -> "Subtraction finds the difference between two numbers."
            "multiplication" -> "Multiplication combines groups of equal numbers."
            "division" -> "Division splits a number into equal parts."
            else -> "Available topics: addition, subtraction, multiplication, division"
        }
    }

    /**
     * Example of context injection.
     */
    @McpTool(
        name = "calculate-with-context",
        description = "Perform calculation with request context"
    )
    fun calculateWithContext(
        @McpParam(description = "Calculation expression")
        expression: String,

        @McpContext
        context: Map<String, Any?>
    ): String {
        val requestId = context["requestId"] ?: "unknown"
        return "Calculation requested via $requestId: $expression"
    }
}

