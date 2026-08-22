package org.ivcode.aimo.server.mcp.handler

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies that ParameterBinder falls back to Kotlin parameter names when
 * Java reflection parameter names are synthetic (e.g., "arg0").
 *
 * The test is tolerant to environments where Java parameter names are present
 * (for example when compiled with -parameters). In that case the test is
 * skipped because the fallback code path cannot be exercised.
 */
class ParameterBinderFallbackTest {

    class KotlinService {
        fun greet(city: String, days: Int): String {
            return "${'$'}city:${'$'}days"
        }
    }

    private val binder = ParameterBinder(ObjectMapper())

    @Test
    fun `bind using kotlin parameter names when java names synthetic`() {
        val method = KotlinService::class.java.getMethod("greet", String::class.java, Integer.TYPE)

        // If the Java parameter names are present (not synthetic) then we cannot
        // reliably assert the fallback path; skip the test in that case.
        val javaNames = method.parameters.map { it.name }
        val anySynthetic = javaNames.any { it == null || it.matches(Regex("arg\\d+")) }
        assumeTrue(anySynthetic, "Java parameter names are present; skipping fallback-specific test")

        val args = mapOf<String, Any?>("city" to "Seattle", "days" to 3)

        val result = binder.bindParameters(method, args, emptyMap())

        assertEquals("Seattle", result.values["city"])
        // binder converts numeric primitives appropriately
        assertTrue(result.values["days"] is Int || result.values["days"] is Number)
        assertEquals(3, (result.values["days"] as Number).toInt())
    }
}

