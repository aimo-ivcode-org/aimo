package org.ivcode.aimo.server.model

import jakarta.servlet.http.HttpServletRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.Enumeration

class RequestMetadataTest {

    @Test
    fun `from lowercases header names`() {
        val request = mockRequest(
            requestUri = "/aimo-api/chat/123",
            headers = listOf(
                "X-Timezone-Offset" to listOf("300"),
                "Content-Type" to listOf("application/json"),
            ),
        )

        val metadata = RequestMetadata.from(request)

        assertEquals("/aimo-api/chat/123", metadata.path)
        assertEquals(listOf("300"), metadata.headers["x-timezone-offset"])
        assertEquals(listOf("application/json"), metadata.headers["content-type"])
        assertTrue(metadata.headers.keys.all { it == it.lowercase() })
    }

    @Test
    fun `from merges values for headers that differ only by case`() {
        val request = mockRequest(
            requestUri = "/aimo-api/chat/123",
            headers = listOf(
                "X-Test" to listOf("one"),
                "x-test" to listOf("two", "three"),
            ),
        )

        val metadata = RequestMetadata.from(request)

        assertEquals(listOf("one", "two", "three"), metadata.headers["x-test"])
        assertEquals(1, metadata.headers.size)
    }

    private fun mockRequest(
        requestUri: String,
        headers: List<Pair<String, List<String>>>,
    ): HttpServletRequest {
        return Proxy.newProxyInstance(
            HttpServletRequest::class.java.classLoader,
            arrayOf(HttpServletRequest::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getRequestURI" -> requestUri
                "getHeaderNames" -> enumeration(headers.map { it.first })
                "getHeaders" -> {
                    val headerName = args?.firstOrNull() as String
                    val values = headers
                        .filter { it.first == headerName }
                        .flatMap { it.second }
                    enumeration(values)
                }
                "toString" -> "MockHttpServletRequest($requestUri)"
                else -> throw UnsupportedOperationException(
                    "Method ${method.name} is not implemented by this test mock"
                )
            }
        } as HttpServletRequest
    }

    private fun enumeration(values: List<String>): Enumeration<String> = Collections.enumeration(values)
}
