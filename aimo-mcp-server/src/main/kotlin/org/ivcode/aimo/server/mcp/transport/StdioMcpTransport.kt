package org.ivcode.aimo.server.mcp.transport

import com.fasterxml.jackson.databind.ObjectMapper
import org.ivcode.aimo.server.mcp.protocol.JsonRpcRequest
import org.ivcode.aimo.server.mcp.protocol.JsonRpcResponse
import org.slf4j.LoggerFactory
import org.springframework.context.Lifecycle
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import kotlin.concurrent.thread

/**
 * Stdio transport for MCP server.
 *
 * Communicates via stdin/stdout for local/subprocess connections.
 * Implemented as a Spring Lifecycle bean for graceful startup/shutdown.
 */
class StdioMcpTransport(
    private val requestHandler: org.ivcode.aimo.server.mcp.handler.McpRequestHandler,
    private val objectMapper: ObjectMapper
) : McpTransport, Lifecycle {
    private val logger = LoggerFactory.getLogger(javaClass)

    override val name: String = "stdio"
    private var isRunning = false
    private var readerThread: Thread? = null
    private val reader = BufferedReader(InputStreamReader(System.`in`))
    private val writer = PrintWriter(System.out, true)

    override fun initialize() {
        logger.info("Stdio MCP transport initializing")
        start()
    }

    override fun shutdown() {
        logger.info("Stdio MCP transport shutting down")
        stop()
    }

    override fun isActive(): Boolean = isRunning

    override fun handleRequest(request: JsonRpcRequest): JsonRpcResponse {
        return requestHandler.handleRequest(request)
    }

    /**
     * Start listening on stdin (implements Lifecycle.start).
     */
    override fun start() {
        if (isRunning) {
            logger.warn("Stdio transport already running")
            return
        }

        logger.info("Starting stdio MCP transport listener")
        isRunning = true

        readerThread = thread(isDaemon = false, name = "mcp-stdio-reader") {
            try {
                while (isRunning) {
                    val line = reader.readLine()
                    if (line == null) {
                        logger.info("EOF on stdin, shutting down")
                        stop()
                        break
                    }

                    if (line.isNotBlank()) {
                        handleStdioLine(line)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    logger.error("Error reading from stdin", e)
                }
            } finally {
                logger.debug("Stdio reader thread exiting")
            }
        }
    }

    /**
     * Stop listening on stdin (implements Lifecycle.stop).
     */
    override fun stop() {
        if (!isRunning) {
            return
        }

        logger.info("Stopping stdio MCP transport listener")
        isRunning = false

        try {
            reader.close()
            writer.close()
        } catch (e: Exception) {
            logger.debug("Error closing stdio streams", e)
        }

        readerThread?.join(5000)  // Wait up to 5 seconds
    }

    /**
     * Check if the transport is running (implements Lifecycle.isRunning).
     */
    override fun isRunning(): Boolean = isRunning

    /**
     * Handle a line of input from stdin.
     */
    private fun handleStdioLine(line: String) {
        try {
            logger.debug("Processing stdio line: {}", line)

            // Parse JSON-RPC request
            val request = objectMapper.readValue(line, JsonRpcRequest::class.java)

            // Handle request
            val response = handleRequest(request)

            // Write response as JSON
            val responseJson = objectMapper.writeValueAsString(response)
            writer.println(responseJson)
            writer.flush()

            logger.debug("Sent stdio response: id={}", response.id)
        } catch (e: Exception) {
            logger.error("Error processing stdio line: {}", line, e)
            try {
                val error = mapOf(
                    "jsonrpc" to "2.0",
                    "error" to mapOf(
                        "code" to -32700,
                        "message" to "Parse error"
                    ),
                    "id" to null
                )
                val errorJson = objectMapper.writeValueAsString(error)
                writer.println(errorJson)
                writer.flush()
            } catch (writeError: Exception) {
                logger.error("Error sending error response", writeError)
            }
        }
    }
}


