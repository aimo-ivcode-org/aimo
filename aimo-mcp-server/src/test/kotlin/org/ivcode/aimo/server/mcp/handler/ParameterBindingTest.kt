package org.ivcode.aimo.server.mcp.handler

import com.fasterxml.jackson.databind.ObjectMapper
import org.ivcode.aimo.server.mcp.annotation.McpParam
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class ParameterBindingTest {
    private lateinit var parameterBinder: ParameterBinder
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        objectMapper = ObjectMapper()
        parameterBinder = ParameterBinder(objectMapper)
    }

    @Test
    fun `should throw exception when invalid integer string is provided`() {
        val method = TestService::class.java.getMethod("addNumbers", Int::class.java, Int::class.java)
        val arguments = mapOf("a" to "not_a_number", "b" to 5)
        val context = emptyMap<String, Any?>()

        val exception = assertThrows(ParameterBindingException::class.java) {
            parameterBinder.bindParameters(method, arguments, context)
        }

        assertTrue(exception.message?.contains("a") ?: false)
        assertTrue(exception.message?.contains("integer") ?: false)
        assertTrue(exception.message?.contains("not_a_number") ?: false)
    }

    @Test
    fun `should throw exception when invalid double string is provided`() {
        val method = TestService::class.java.getMethod("calculatePercentage", Double::class.java)
        val arguments = mapOf("value" to "abc123")
        val context = emptyMap<String, Any?>()

        val exception = assertThrows(ParameterBindingException::class.java) {
            parameterBinder.bindParameters(method, arguments, context)
        }

        assertTrue(exception.message?.contains("value") ?: false)
        assertTrue(exception.message?.contains("decimal") ?: false)
    }

    @Test
    fun `should throw exception when invalid long string is provided`() {
        val method = TestService::class.java.getMethod("processLong", Long::class.java)
        val arguments = mapOf("value" to "999999999999999999999999")
        val context = emptyMap<String, Any?>()

        val exception = assertThrows(ParameterBindingException::class.java) {
            parameterBinder.bindParameters(method, arguments, context)
        }

        assertTrue(exception.message?.contains("value") ?: false)
        assertTrue(exception.message?.contains("long") ?: false)
    }

    @Test
    fun `should throw exception when wrong type is provided for integer parameter`() {
        val method = TestService::class.java.getMethod("addNumbers", Int::class.java, Int::class.java)
        val arguments = mapOf("a" to listOf(1, 2, 3), "b" to 5)
        val context = emptyMap<String, Any?>()

        val exception = assertThrows(ParameterBindingException::class.java) {
            parameterBinder.bindParameters(method, arguments, context)
        }

        assertTrue(exception.message?.contains("a") ?: false)
        assertTrue(exception.message?.contains("integer") ?: false)
    }

    @Test
    fun `should successfully bind valid integer parameters`() {
        val method = TestService::class.java.getMethod("addNumbers", Int::class.java, Int::class.java)
        val arguments = mapOf("a" to "123", "b" to 456)
        val context = emptyMap<String, Any?>()

        val boundArgs = parameterBinder.bindParameters(method, arguments, context)

        assertEquals(2, boundArgs.size)
        assertEquals(123, boundArgs[0])
        assertEquals(456, boundArgs[1])
    }

    @Test
    fun `should successfully bind valid double parameters`() {
        val method = TestService::class.java.getMethod("calculatePercentage", Double::class.java)
        val arguments = mapOf("value" to "50.5")
        val context = emptyMap<String, Any?>()

        val boundArgs = parameterBinder.bindParameters(method, arguments, context)

        assertEquals(1, boundArgs.size)
        assertEquals(50.5, boundArgs[0] as Double, 0.01)
    }

    @Test
    fun `should successfully bind mixed numeric types`() {
        val method = TestService::class.java.getMethod("mixedTypes", Int::class.java, Long::class.java, Double::class.java)
        val arguments = mapOf("intVal" to "100", "longVal" to 500L, "doubleVal" to "3.14")
        val context = emptyMap<String, Any?>()

        val boundArgs = parameterBinder.bindParameters(method, arguments, context)

        assertEquals(3, boundArgs.size)
        assertEquals(100, boundArgs[0])
        assertEquals(500L, boundArgs[1])
        assertEquals(3.14, boundArgs[2] as Double, 0.01)
    }

    @Test
    fun `should throw exception with details about parameter name`() {
        val method = TestService::class.java.getMethod("addNumbers", Int::class.java, Int::class.java)
        val arguments = mapOf("a" to "invalid", "b" to 5)
        val context = emptyMap<String, Any?>()

        val exception = assertThrows(ParameterBindingException::class.java) {
            parameterBinder.bindParameters(method, arguments, context)
        }

        assertEquals("a", exception.parameterName)
        assertTrue(exception.expectedType == Int::class.java || exception.expectedType == Integer::class.java)
        assertEquals("invalid", exception.providedValue)
    }

    @Test
    fun `should throw exception when cannot convert to custom object type`() {
        // Try to convert an incompatible value to a custom type
        val method = TestService::class.java.getMethod("processCustom", CustomData::class.java)
        val arguments = mapOf("data" to 12345)  // Integer can't be converted to CustomData
        val context = emptyMap<String, Any?>()

        val exception = assertThrows(ParameterBindingException::class.java) {
            parameterBinder.bindParameters(method, arguments, context)
        }

        assertTrue(exception.message?.contains("data") ?: false)
        assertTrue(exception.message?.contains("Could not convert") ?: false)
    }

    @Test
    fun `should throw exception when array type is mismatched`() {
        val method = TestService::class.java.getMethod("processArray", Array<String>::class.java)
        val arguments = mapOf("items" to "not_an_array")  // String instead of array
        val context = emptyMap<String, Any?>()

        val exception = assertThrows(ParameterBindingException::class.java) {
            parameterBinder.bindParameters(method, arguments, context)
        }

        assertTrue(exception.message?.contains("items") ?: false)
    }

    @Test
    fun `should throw exception with detailed message about conversion failure`() {
        val method = TestService::class.java.getMethod("addNumbers", Int::class.java, Int::class.java)
        val arguments = mapOf("a" to mapOf("nested" to "value"), "b" to 5)
        val context = emptyMap<String, Any?>()

        val exception = assertThrows(ParameterBindingException::class.java) {
            parameterBinder.bindParameters(method, arguments, context)
        }

        // Should mention the parameter name and that it's incompatible type
        assertTrue(exception.message?.contains("a") ?: false)
        assertTrue(exception.message?.contains("LinkedHashMap") ?: false || exception.message?.contains("Map") ?: false)
    }

    // Test service methods
    class TestService {
        fun addNumbers(a: Int, b: Int): Int = a + b

        fun calculatePercentage(value: Double): Double = value * 0.1

        fun processLong(value: Long): Long = value * 2

        fun mixedTypes(intVal: Int, longVal: Long, doubleVal: Double): String = "$intVal:$longVal:$doubleVal"

        fun processCustom(data: CustomData): String = data.toString()

        fun processArray(items: Array<String>): Int = items.size
    }

    data class CustomData(
        val id: String,
        val name: String
    )
}




