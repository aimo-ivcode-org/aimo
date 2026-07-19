package org.ivcode.aimo.mcpclient

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Test SSE protocol line filtering logic used in HttpTransport
 */
class SseLineParsingTest {
    
    @Test
    fun `should extract JSON from data line with colon-space prefix`() {
        val line = "data: {\"id\": 1, \"method\": \"initialize\"}"
        val result = extractJsonFromSseLine(line)
        assertEquals("{\"id\": 1, \"method\": \"initialize\"}", result)
    }
    
    @Test
    fun `should extract JSON from data line with colon only prefix`() {
        val line = "data:{\"id\": 2, \"method\": \"ping\"}"
        val result = extractJsonFromSseLine(line)
        assertEquals("{\"id\": 2, \"method\": \"ping\"}", result)
    }
    
    @Test
    fun `should skip event metadata line`() {
        val line = "event: message"
        val result = extractJsonFromSseLine(line)
        assertNull(result)
    }
    
    @Test
    fun `should skip id metadata line`() {
        val line = "id: 123"
        val result = extractJsonFromSseLine(line)
        assertNull(result)
    }
    
    @Test
    fun `should skip retry metadata line`() {
        val line = "retry: 5000"
        val result = extractJsonFromSseLine(line)
        assertNull(result)
    }
    
    @Test
    fun `should skip comment line`() {
        val line = ": this is a comment"
        val result = extractJsonFromSseLine(line)
        assertNull(result)
    }
    
    @Test
    fun `should parse raw JSON without data prefix`() {
        val line = "{\"id\": 3, \"method\": \"tools/call\"}"
        val result = extractJsonFromSseLine(line)
        assertEquals("{\"id\": 3, \"method\": \"tools/call\"}", result)
    }
    
    @Test
    fun `should ignore empty lines`() {
        val line = ""
        val result = extractJsonFromSseLine(line)
        assertNull(result)
    }
    
    /**
     * Mirrors the logic in HttpTransport.readSseStream()
     */
    private fun extractJsonFromSseLine(line: String): String? {
        return when {
            line.isEmpty() -> null
            line.startsWith("data: ") -> line.substring(6)
            line.startsWith("data:") -> line.substring(5)
            line.startsWith("event:") || line.startsWith("retry:") || 
            line.startsWith("id:") || line.startsWith(":") -> null
            else -> line  // Raw JSON
        }
    }
}
