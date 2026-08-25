package org.ivcode.aimo.server.mcp.transport

import com.fasterxml.jackson.databind.ObjectMapper
import org.ivcode.aimo.server.mcp.handler.McpRequestHandler
import org.ivcode.aimo.server.mcp.handler.ToolCallHandler
import org.ivcode.aimo.server.mcp.handler.PromptGetHandler
import org.ivcode.aimo.server.mcp.handler.ParameterBinder
import org.ivcode.aimo.server.mcp.registry.McpServiceRegistry
import org.ivcode.aimo.server.mcp.schema.McpSchemaGenerator
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.support.StaticApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class StdioMcpTransportTest {

    @Test
    fun `stop should not close system streams and reader should exit on EOF`() {
        val originalIn = System.`in`
        val originalOut = System.out

        try {
            // Prepare a single-line JSON-RPC initialize request followed by EOF
            val req = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}\n"
            val inBytes = req.toByteArray(Charsets.UTF_8)
            val bais = ByteArrayInputStream(inBytes)

            // Capture System.out
            val baos = ByteArrayOutputStream()
            val ps = PrintStream(baos)

            System.setIn(bais)
            System.setOut(ps)

            val objectMapper = ObjectMapper()

            // Minimal infrastructure to construct McpRequestHandler
            val appCtx = StaticApplicationContext()
            val schemaGen = McpSchemaGenerator()
            val serviceRegistry = McpServiceRegistry(appCtx, schemaGen)

            val parameterBinder = ParameterBinder(objectMapper)
            val toolCallHandler = ToolCallHandler(serviceRegistry, parameterBinder)
            val promptGetHandler = PromptGetHandler(serviceRegistry, parameterBinder)

            val requestHandler = McpRequestHandler(serviceRegistry, toolCallHandler, promptGetHandler)

            val transport = StdioMcpTransport(requestHandler, objectMapper)

            // Start transport - reader will consume the single line and then encounter EOF
            transport.start()

            // Wait for the reader loop to exit (EOF sets isRunning = false)
            var waited = 0L
            while (transport.isActive() && waited < 5000L) {
                Thread.sleep(50)
                waited += 50
            }

            // After EOF the transport should have stopped running
            assertFalse(transport.isActive(), "Transport should not be active after consuming input EOF")

            // Calling stop() should not throw or block indefinitely and should not close System.out
            transport.stop()

            // System.out should still be usable
            println("POST_STOP_CHECK")
            System.out.flush()

            val outStr = baos.toString(Charsets.UTF_8.name())
            assertTrue(
                outStr.contains("POST_STOP_CHECK"),
                "System.out should remain open and usable after transport.stop()"
            )
        } finally {
            // Restore original streams
            System.setIn(originalIn)
            System.setOut(originalOut)
        }
    }
}

