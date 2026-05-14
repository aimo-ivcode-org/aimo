package org.ivcode.aimo.ollama.client

import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

private const val DEFAULT_URL = "http://localhost:11434"
private const val CHAT_PATH = "/api/chat"
private const val ERROR_BODY_SNIPPET_LIMIT = 512

internal class OllamaChatClient (
    url: String = DEFAULT_URL,
    val mapper: ObjectMapper = jacksonObjectMapper(),
) {
    private val log = LoggerFactory.getLogger(OllamaChatClient::class.java)
    val url = if (url.endsWith("/")) "$url${CHAT_PATH.substring(1)}" else "$url$CHAT_PATH"
    val client: HttpClient = HttpClient.newHttpClient()

    fun chat(request: ChatRequest, callback: ChatCallback?=null): ChatResponse {
        val parts = mutableListOf<ChatResponse>()
        val requestBody = mapper.writeValueAsString(request)

        log.debug(
            "Ollama chat request model={}, messages={}, stream={}",
            request.model,
            request.messages.size,
            request.stream,
        )
        if (log.isTraceEnabled) {
            log.trace("Ollama chat request url={} payload={}", url, asLogValue(request))
        }

        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Accept", "application/ndjson")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        val response = client.send(
            httpRequest,
            HttpResponse.BodyHandlers.ofInputStream()
        )

        log.debug(
            "Ollama chat response status={} model={}",
            response.statusCode(),
            request.model,
        )

        if (response.statusCode() !in 200..299) {
            val errorBodySnippet = response.body().readBodySnippet()
            log.error(
                "Ollama chat request failed status={} model={} errorBodySnippet={}",
                response.statusCode(),
                request.model,
                errorBodySnippet,
            )
            throw IllegalStateException(
                "Ollama chat request failed with HTTP ${response.statusCode()}: $errorBodySnippet"
            )
        }

        response.body().bufferedReader(StandardCharsets.UTF_8).use { reader ->
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val responseLine = line!!.trim()
                if (responseLine.isEmpty()) continue
                if (log.isTraceEnabled) {
                    log.trace("Ollama chat response ndjson line model={} payload={}", request.model, responseLine)
                }

                val chatResponse = mapper.readValue(responseLine, ChatResponse::class.java)
                parts.add(chatResponse)
                if (log.isTraceEnabled) {
                    log.trace("Ollama chat response event model={} payload={}", request.model, asLogValue(chatResponse))
                }
                callback?.invoke(chatResponse)
            }
        }

        val merged = concatResponses(parts)
        if (log.isTraceEnabled) {
            log.trace("Ollama chat response merged model={} payload={}", request.model, asLogValue(merged))
        }
        return merged
    }

    private fun concatResponses(responses: List<ChatResponse>): ChatResponse {
        if (responses.isEmpty()) {
            throw IllegalStateException("Ollama chat request returned HTTP 2xx but contained no NDJSON response lines")
        }

        if(responses.size == 1) {
            return responses[0]
        }

        val lastIndex = responses.size - 1
        val last = responses[lastIndex]

        val message = Message(
            role = last.message.role,
            content = responses.joinToString(separator = "") { it.message.content },
            thinking = responses.joinToString(separator = "") { it.message.thinking.orEmpty() },
            toolCalls = responses.flatMap { it.message.toolCalls.orEmpty() },
            toolName = last.message.toolName,
        )

        return last.copy(message = message)
    }

    private fun asLogValue(value: Any?): String {
        return try {
            mapper.writeValueAsString(value)
        } catch (_: Exception) {
            value?.toString() ?: "null"
        }
    }
}

private fun InputStream.readBodySnippet(): String {
    use { input ->
        val bytes = input.readNBytes(ERROR_BODY_SNIPPET_LIMIT)
        return bytes.toString(StandardCharsets.UTF_8)
    }
}

internal typealias ChatCallback = (ChatResponse) -> Unit