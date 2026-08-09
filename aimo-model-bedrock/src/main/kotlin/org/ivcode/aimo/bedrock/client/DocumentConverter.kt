package org.ivcode.aimo.bedrock.client

import software.amazon.awssdk.core.document.Document

/**
 * Bidirectional conversion between Aimo types and AWS Document types.
 */
internal object DocumentConverter {

    fun anyToDocument(value: Any?): Document = when (value) {
        null -> Document.fromNull()
        is Document -> value
        is String -> Document.fromString(value)
        is Boolean -> Document.fromBoolean(value)
        is Int -> Document.fromNumber(value)
        is Long -> Document.fromNumber(value)
        is Float -> Document.fromNumber(value)
        is Double -> Document.fromNumber(value)
        is Number -> Document.fromNumber(value.toDouble())
        is Map<*, *> -> Document.fromMap(value.entries.associate { (k, v) -> k.toString() to anyToDocument(v) })
        is Iterable<*> -> Document.fromList(value.map { anyToDocument(it) })
        is Array<*> -> Document.fromList(value.map { anyToDocument(it) })
        else -> Document.fromString(value.toString())
    }

    fun unwrapDocument(document: Document): Any? = when {
        document.isNull -> null
        document.isString -> document.asString()
        document.isBoolean -> document.asBoolean()
        document.isNumber -> document.asNumber().toDouble()
        document.isMap -> document.asMap().mapValues { (_, v) -> unwrapDocument(v) }
        document.isList -> document.asList().map { unwrapDocument(it) }
        else -> document.unwrap()
    }

    fun documentToMap(document: Document): Map<String, Any?> {
        val unwrapped = unwrapDocument(document)
        return when (unwrapped) {
            is Map<*, *> -> unwrapped.entries.associate { (k, v) -> k.toString() to v }
            else -> mapOf("raw" to unwrapped)
        }
    }
}

