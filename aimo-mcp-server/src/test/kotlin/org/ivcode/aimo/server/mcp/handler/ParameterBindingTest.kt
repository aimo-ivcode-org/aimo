package org.ivcode.aimo.server.mcp.handler

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
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

        val binding = parameterBinder.bindParameters(method, arguments, context)

        assertEquals(123, binding.values["a"])
        assertEquals(456, binding.values["b"])
        assertTrue(binding.provided.containsAll(listOf("a", "b")))
    }

    @Test
    fun `should successfully bind valid double parameters`() {
        val method = TestService::class.java.getMethod("calculatePercentage", Double::class.java)
        val arguments = mapOf("value" to "50.5")
        val context = emptyMap<String, Any?>()

        val binding = parameterBinder.bindParameters(method, arguments, context)

        assertEquals(50.5, binding.values["value"] as Double, 0.01)
        assertTrue(binding.provided.contains("value"))
    }

    @Test
    fun `should successfully bind mixed numeric types`() {
        val method = TestService::class.java.getMethod(
            "mixedTypes",
            Int::class.java,
            Long::class.java,
            Double::class.java
        )
        val arguments = mapOf("intVal" to "100", "longVal" to 500L, "doubleVal" to "3.14")
        val context = emptyMap<String, Any?>()

        val binding = parameterBinder.bindParameters(method, arguments, context)

        assertEquals(100, binding.values["intVal"])
        assertEquals(500L, binding.values["longVal"])
        assertEquals(3.14, binding.values["doubleVal"] as Double, 0.01)
        assertTrue(binding.provided.containsAll(listOf("intVal", "longVal", "doubleVal")))
    }

    @Test
    fun `should successfully bind float parameter from number`() {
        val method = TestService::class.java.getMethod("processFloat", Float::class.java)
        val arguments = mapOf("value" to 1.5f)
        val context = emptyMap<String, Any?>()

        val binding = parameterBinder.bindParameters(method, arguments, context)

        assertTrue(binding.values["value"] is Float)
        assertEquals(1.5f, binding.values["value"] as Float, 0.0001f)
        assertTrue(binding.provided.contains("value"))
    }

    @Test
    fun `should successfully bind float parameter from string`() {
        val method = TestService::class.java.getMethod("processFloat", Float::class.java)
        val arguments = mapOf("value" to "2.75")
        val context = emptyMap<String, Any?>()

        val binding = parameterBinder.bindParameters(method, arguments, context)

        assertTrue(binding.values["value"] is Float)
        assertEquals(2.75f, binding.values["value"] as Float, 0.0001f)
        assertTrue(binding.provided.contains("value"))
    }

    @Test
    fun `should throw exception when invalid float string is provided`() {
        val method = TestService::class.java.getMethod("processFloat", Float::class.java)
        val arguments = mapOf("value" to "not_a_float")
        val context = emptyMap<String, Any?>()

        val exception = assertThrows(ParameterBindingException::class.java) {
            parameterBinder.bindParameters(method, arguments, context)
        }

        assertTrue(exception.message?.contains("value") ?: false)
        assertTrue((exception.message?.contains("float") == true) || (exception.message?.contains("decimal") == true))
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

        fun processFloat(value: Float): Float = value

        fun processCustom(data: CustomData): String = data.toString()

        fun processArray(items: Array<String>): Int = items.size
    }

    data class CustomData(
        val id: String,
        val name: String
    )
}




