 package org.ivcode.aimo.server.mcp.handler

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ParameterBinderTest {

    class DummyService {
        fun booleanParam(flag: java.lang.Boolean): String {
            return flag.toString()
        }
        fun floatParam(value: java.lang.Float): String {
            return value.toString()
        }

        fun doubleParam(value: java.lang.Double): String {
            return value.toString()
        }
    }

    private val objectMapper = ObjectMapper()
    private val binder = ParameterBinder(objectMapper)
    private val method = DummyService::class.java.getMethod("booleanParam", java.lang.Boolean::class.java)

    @Test
    fun `accept boolean true`() {
        val result = binder.bindParameters(method, mapOf("flag" to true), emptyMap())
        assertEquals(true, result.values["flag"])
    }

    @Test
    fun `accept string true variants`() {
        val trues = listOf("true", "TRUE", "yes", "1", "on")
        for (v in trues) {
            val result = binder.bindParameters(method, mapOf("flag" to v), emptyMap())
            assertEquals(true, result.values["flag"], "value $v should be true")
        }
    }

    @Test
    fun `accept string false variants`() {
        val falses = listOf("false", "FALSE", "no", "0", "off")
        for (v in falses) {
            val result = binder.bindParameters(method, mapOf("flag" to v), emptyMap())
            assertEquals(false, result.values["flag"], "value $v should be false")
        }
    }

    @Test
    fun `reject invalid boolean string`() {
        assertThrows(ParameterBindingException::class.java) {
            binder.bindParameters(method, mapOf("flag" to "maybe"), emptyMap())
        }
    }

    @Test
    fun `bind float from number and string`() {
        val floatMethod = DummyService::class.java.getMethod("floatParam", java.lang.Float::class.java)

        // numeric input
        var result = binder.bindParameters(floatMethod, mapOf("value" to 1.23f), emptyMap())
        assertTrue(result.values["value"] is Float)
        assertEquals(1.23f.toDouble(), (result.values["value"] as Number).toDouble(), 1e-6)

        // string input
        result = binder.bindParameters(floatMethod, mapOf("value" to "1.23"), emptyMap())
        assertTrue(result.values["value"] is Float)
        assertEquals(1.23f.toDouble(), (result.values["value"] as Number).toDouble(), 1e-6)
    }

    @Test
    fun `bind double from number and string`() {
        val doubleMethod = DummyService::class.java.getMethod("doubleParam", java.lang.Double::class.java)

        // numeric input
        var result = binder.bindParameters(doubleMethod, mapOf("value" to 1.23456789), emptyMap())
        assertTrue(result.values["value"] is Double)
        assertEquals(1.23456789, (result.values["value"] as Number).toDouble(), 1e-12)

        // string input
        result = binder.bindParameters(doubleMethod, mapOf("value" to "1.23456789"), emptyMap())
        assertTrue(result.values["value"] is Double)
        assertEquals(1.23456789, (result.values["value"] as Number).toDouble(), 1e-12)
    }
}

