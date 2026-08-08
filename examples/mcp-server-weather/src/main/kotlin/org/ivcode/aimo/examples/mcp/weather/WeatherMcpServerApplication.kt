package org.ivcode.aimo.examples.mcp.weather

import org.ivcode.aimo.server.mcp.config.EnableMcpServer
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Weather MCP Server Application.
 *
 * A standalone MCP server providing weather information via tools and prompts.
 * Runs on http://localhost:9090
 *
 * Example requests:
 * - Get weather:
 *   curl -X POST http://localhost:9090/mcp/ \
 *     -H "Content-Type: application/json" \
 *     -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get-weather","arguments":{"city":"Seattle"}}}'
 *
 * - List tools:
 *   curl -X POST http://localhost:9090/mcp/ \
 *     -H "Content-Type: application/json" \
 *     -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
 */
@SpringBootApplication
@EnableMcpServer
class WeatherMcpServerApplication

fun main(args: Array<String>) {
    runApplication<WeatherMcpServerApplication>(*args)
}

