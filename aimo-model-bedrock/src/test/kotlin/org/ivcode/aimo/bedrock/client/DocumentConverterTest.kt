package org.ivcode.aimo.bedrock.client

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import software.amazon.awssdk.core.document.Document
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("DocumentConverter")
class DocumentConverterTest {

    @Test
    @DisplayName("anyToDocument converts null to Document.fromNull()")
    fun testAnyToDocumentNull() {
        val result = DocumentConverter.anyToDocument(null)
        assertTrue { result.isNull }
    }

    @Test
    @DisplayName("anyToDocument converts String to Document.fromString()")
    fun testAnyToDocumentString() {
        val result = DocumentConverter.anyToDocument("hello")
        assertEquals("hello", result.asString())
    }

    @Test
    @DisplayName("anyToDocument converts Boolean to Document.fromBoolean()")
    fun testAnyToDocumentBoolean() {
        val result = DocumentConverter.anyToDocument(true)
        assertEquals(true, result.asBoolean())
    }

    @Test
    @DisplayName("anyToDocument converts Int to Document.fromNumber()")
    fun testAnyToDocumentInt() {
        val result = DocumentConverter.anyToDocument(42)
        assertEquals(42.0, result.asNumber().toDouble())
    }

    @Test
    @DisplayName("anyToDocument converts Long to Document.fromNumber()")
    fun testAnyToDocumentLong() {
        val result = DocumentConverter.anyToDocument(42L)
        assertEquals(42.0, result.asNumber().toDouble())
    }

    @Test
    @DisplayName("anyToDocument converts Float to Document.fromNumber()")
    fun testAnyToDocumentFloat() {
        val result = DocumentConverter.anyToDocument(3.14f)
        assertEquals(3.14, result.asNumber().toDouble(), 0.01)
    }

    @Test
    @DisplayName("anyToDocument converts Double to Document.fromNumber()")
    fun testAnyToDocumentDouble() {
        val result = DocumentConverter.anyToDocument(3.14)
        assertEquals(3.14, result.asNumber().toDouble(), 0.001)
    }

    @Test
    @DisplayName("anyToDocument converts Map to Document.fromMap()")
    fun testAnyToDocumentMap() {
        val map = mapOf("key1" to "value1", "key2" to 42)
        val result = DocumentConverter.anyToDocument(map)
        assertTrue { result.isMap }
        assertEquals("value1", result.asMap()["key1"]?.asString())
        assertEquals(42.0, result.asMap()["key2"]?.asNumber()?.toDouble())
    }

    @Test
    @DisplayName("anyToDocument converts List to Document.fromList()")
    fun testAnyToDocumentList() {
        val list = listOf("a", "b", 42)
        val result = DocumentConverter.anyToDocument(list)
        assertTrue { result.isList }
        assertEquals(3, result.asList().size)
        assertEquals("a", result.asList()[0].asString())
        assertEquals("b", result.asList()[1].asString())
        assertEquals(42.0, result.asList()[2].asNumber().toDouble())
    }

    @Test
    @DisplayName("anyToDocument converts nested Map to Document")
    fun testAnyToDocumentNestedMap() {
        val nested = mapOf(
            "outer" to mapOf("inner" to "value"),
            "number" to 10
        )
        val result = DocumentConverter.anyToDocument(nested)
        assertTrue { result.isMap }
        assertEquals("value", result.asMap()["outer"]?.asMap()?.get("inner")?.asString())
    }

    @Test
    @DisplayName("unwrapDocument converts Document.fromNull() back to null")
    fun testUnwrapDocumentNull() {
        val doc = Document.fromNull()
        assertNull(DocumentConverter.unwrapDocument(doc))
    }

    @Test
    @DisplayName("unwrapDocument converts Document.fromString() back to String")
    fun testUnwrapDocumentString() {
        val doc = Document.fromString("hello")
        assertEquals("hello", DocumentConverter.unwrapDocument(doc))
    }

    @Test
    @DisplayName("unwrapDocument converts Document.fromBoolean() back to Boolean")
    fun testUnwrapDocumentBoolean() {
        val doc = Document.fromBoolean(true)
        assertEquals(true, DocumentConverter.unwrapDocument(doc))
    }

    @Test
    @DisplayName("unwrapDocument converts Document.fromNumber() back to Double")
    fun testUnwrapDocumentNumber() {
        val doc = Document.fromNumber(42)
        assertEquals(42.0, DocumentConverter.unwrapDocument(doc))
    }

    @Test
    @DisplayName("unwrapDocument converts Document.fromList() with mixed types")
    fun testUnwrapDocumentList() {
        val doc = documentOf(listOf("text", 42, true))
        val result = DocumentConverter.unwrapDocument(doc)
        assertTrue { result is List<*> }
        val list = result as List<*>
        assertEquals("text", list[0])
        assertEquals(42.0, list[1])
        assertEquals(true, list[2])
    }

    @Test
    @DisplayName("documentToMap extracts Map from Document")
    fun testDocumentToMap() {
        val doc = Document.fromMap(mapOf(
            "key1" to Document.fromString("value1"),
            "key2" to Document.fromNumber(42)
        ))
        val result = DocumentConverter.documentToMap(doc)
        assertEquals("value1", result["key1"])
        assertEquals(42.0, result["key2"])
    }

    @Test
    @DisplayName("documentToMap wraps non-map Document in 'raw' key")
    fun testDocumentToMapNonMap() {
        val doc = Document.fromString("not a map")
        val result = DocumentConverter.documentToMap(doc)
        assertEquals("not a map", result["raw"])
    }

    @Test
    @DisplayName("round-trip: anyToDocument then unwrapDocument preserves data")
    fun testRoundTripConversion() {
        val original = mapOf(
            "text" to "hello",
            "number" to 42,
            "nested" to mapOf("inner" to "value")
        )
        val doc = DocumentConverter.anyToDocument(original)
        val unwrapped = DocumentConverter.unwrapDocument(doc)

        assertTrue { unwrapped is Map<*, *> }
        @Suppress("UNCHECKED_CAST")
        val map = unwrapped as Map<String, Any?>
        assertEquals("hello", map["text"])
        assertEquals(42.0, map["number"])
    }

    private fun documentOf(value: Any?): Document {
        return DocumentConverter.anyToDocument(value)
    }
}

