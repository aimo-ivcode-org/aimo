package org.ivcode.aimo.ollama.client

import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import java.time.Instant
import kotlin.time.Duration

class DurationSerializer : ValueSerializer<Duration>() {
    override fun serialize(value: Duration, gen: JsonGenerator, ctxt: SerializationContext) {
        gen.writeString(value.toString()) // outputs "32s", "1m", etc.
    }
}

class DurationDeserializer : ValueDeserializer<Duration>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Duration {
        val text = p.valueAsString
        return Duration.parse(text) // parses "32s", "1m", etc.
    }
}

class InstantSerializer : ValueSerializer<Instant>() {
    override fun serialize(value: Instant, gen: JsonGenerator, ctxt: SerializationContext) {
        // Write Instant in ISO-8601 format (e.g. 2026-02-10T05:49:12.5858417Z)
        gen.writeString(value.toString())
    }

}

class InstantDeserializer : ValueDeserializer<Instant>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Instant {
        val text = p.valueAsString
        // java.time.Instant.parse supports ISO-8601 strings with fractional seconds
        return Instant.parse(text)
    }
}