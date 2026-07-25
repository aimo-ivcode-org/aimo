package org.ivcode.aimo.examples.client.weather

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Weather MCP Client Application with Ollama.
 *
 * An AIMO application that uses Ollama as the LLM and connects to the weather MCP server.
 * Includes web UI for chatting with the model.
 *
 * Requirements:
 * - Ollama running on localhost:11434
 * - Weather MCP server running on localhost:9090/mcp
 *
 * Access the UI at: http://localhost:9090
 */
@SpringBootApplication
class WeatherMcpClientApplication

fun main(args: Array<String>) {
    runApplication<WeatherMcpClientApplication>(*args)
}

