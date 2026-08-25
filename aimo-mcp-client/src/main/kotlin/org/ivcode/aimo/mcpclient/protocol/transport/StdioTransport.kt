package org.ivcode.aimo.mcpclient.protocol.transport

import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.io.InputStreamReader
import java.io.IOException

private const val PROCESS_SHUTDOWN_WAIT_SECONDS = 5L
private const val PROCESS_FORCE_SHUTDOWN_WAIT_SECONDS = 2L

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
        runCatching {
            val processBuilder = ProcessBuilder(listOf(command) + args)
            processBuilder.redirectErrorStream(true)
            process = processBuilder.start()

            writer = BufferedWriter(OutputStreamWriter(process!!.outputStream, Charsets.UTF_8))
            reader = BufferedReader(InputStreamReader(process!!.inputStream, Charsets.UTF_8))

            log.info("Stdio transport connected: command=$command args=$args")
        }.onFailure {
            log.error("Failed to start stdio transport process", it)
        }.getOrThrow()
    }

    override fun disconnect() {
        runCatching {
            writer?.close()
            reader?.close()

            process?.let { p ->
                // Avoid hanging indefinitely during shutdown.
                if (!p.waitFor(PROCESS_SHUTDOWN_WAIT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)) {
                    p.destroy()
                    if (!p.waitFor(PROCESS_FORCE_SHUTDOWN_WAIT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)) {
                        p.destroyForcibly()
                    }
                }
            }

            log.info("Stdio transport disconnected")
        }.onFailure { log.error("Error during stdio disconnect", it) }
    }

    override fun send(message: String) {
        runCatching {
            writer?.write(message)
            writer?.write("\n")
            writer?.flush()
        }.onFailure {
            log.error("Failed to send message via stdio", it)
        }.getOrThrow()
    }

    override fun receive(): String {
        while (true) {
            val line = reader?.readLine() ?: throw IOException("Stdio reader closed unexpectedly")
            if (line.isNotEmpty()) {
                return line
            }
        }
    }
}
