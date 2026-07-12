package org.ivcode.aimo.mcpclient.protocol.transport

import org.slf4j.LoggerFactory
import java.io.*

/**
 * Transport abstraction for MCP communication.
 */
interface ProtocolTransport {
    fun connect()
    fun disconnect()
    fun send(message: String)
    fun receive(): String
}

/**
 * Stdio-based MCP transport using JSONL (JSON Lines) framing.
 * Spawns a subprocess and communicates via stdin/stdout.
 */
class StdioTransport(
    private val command: String,
    private val args: List<String> = emptyList(),
) : ProtocolTransport {
    private val log = LoggerFactory.getLogger(javaClass)
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null

    override fun connect() {
        try {
            val processBuilder = ProcessBuilder(listOf(command) + args)
            processBuilder.redirectErrorStream(true)
            process = processBuilder.start()
            
            writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            reader = BufferedReader(InputStreamReader(process!!.inputStream))
            
            log.info("Stdio transport connected: command=$command args=$args")
        } catch (e: Exception) {
            log.error("Failed to start stdio transport process", e)
            throw e
        }
    }

    override fun disconnect() {
        try {
            writer?.close()
            reader?.close()

            process?.let { p ->
                // Avoid hanging indefinitely during shutdown.
                if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    p.destroy()
                    if (!p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                        p.destroyForcibly()
                    }
                }
            }

            log.info("Stdio transport disconnected")
        } catch (e: Exception) {
            log.error("Error during stdio disconnect", e)
        }
    }

    override fun send(message: String) {
        try {
            writer?.write(message)
            writer?.write("\n")
            writer?.flush()
        } catch (e: Exception) {
            log.error("Failed to send message via stdio", e)
            throw e
        }
    }

    override fun receive(): String {
        val line = reader?.readLine() ?: throw IOException("Stdio reader closed unexpectedly")
        if (line.isEmpty()) throw IOException("Empty message received")
        return line
    }
}
