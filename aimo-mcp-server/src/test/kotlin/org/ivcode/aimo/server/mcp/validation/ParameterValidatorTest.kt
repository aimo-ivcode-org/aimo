package org.ivcode.aimo.server.mcp.validation

import org.ivcode.aimo.server.mcp.annotation.McpParam
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ParameterValidatorTest {

    private val validator = ParameterValidator()

    @Test
    fun `should validate correct parameters`() {
        val method = TestClass::class.java.getMethod("add", Double::class.java, Double::class.java)

        val result = validator.validateParameters(
            methodName = "add",
            methodParameters = method.parameters,
            providedArguments = mapOf("a" to 5.0, "b" to 3.0)
        )

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `should detect missing required parameters`() {
        val method = TestClass::class.java.getMethod("add", Double::class.java, Double::class.java)

        val result = validator.validateParameters(
            methodName = "add",
            methodParameters = method.parameters,
            providedArguments = mapOf("a" to 5.0)  // Missing 'b'
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("missing") })
    }

    @Test
    fun `should convert string numbers to numeric types`() {
        val method = TestClass::class.java.getMethod("add", Double::class.java, Double::class.java)

        val result = validator.validateParameters(
            methodName = "add",
            methodParameters = method.parameters,
            providedArguments = mapOf("a" to "5.0", "b" to "3.0")
        )

        assertTrue(result.isValid)
    }

    @Test
    fun `should detect type mismatches`() {
        val method = TestClass::class.java.getMethod("add", Double::class.java, Double::class.java)

        val result = validator.validateParameters(
            methodName = "add",
            methodParameters = method.parameters,
            providedArguments = mapOf("a" to "not-a-number", "b" to 3.0)
        )

        assertFalse(result.isValid)
    }

    companion object {
        class TestClass {
            fun add(
                @McpParam(description = "First", required = true) a: Double,
                @McpParam(description = "Second", required = true) b: Double
            ): Double = a + b
        }
    }
}


