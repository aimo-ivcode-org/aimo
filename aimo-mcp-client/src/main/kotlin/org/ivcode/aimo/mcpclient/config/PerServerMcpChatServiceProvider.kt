package org.ivcode.aimo.mcpclient.config

import org.ivcode.aimo.core.model.ToolCallback
import org.ivcode.aimo.core.chatservice.ChatServiceProvider
import org.ivcode.aimo.core.chatservice.SystemMessageCallback
import org.ivcode.aimo.mcpclient.client.McpClientManager
import org.slf4j.LoggerFactory

/**
 * Represents a single MCP server as a ChatServiceProvider.
 *
 * Each server is represented as its own provider with:
 * - Unique id based on the server id
 * - Scopes from the server configuration
 * - Tools and system messages (prompts) from that server only
 *
 * This allows proper scope-aware filtering at the provider level, ensuring
 * tools and prompts from a scoped server (e.g., "admin-tools") only appear in allowed scopes.
 */
class PerServerMcpChatServiceProvider(
    private val serverId: String,
    private val mcpClientManager: McpClientManager,
) : ChatServiceProvider {
    private val log = LoggerFactory.getLogger(javaClass)

    override val id: String = serverId

    override val scopes: Set<String> = mcpClientManager.getServerScopes(serverId)

    override fun getTools(): List<ToolCallback> {
        return mcpClientManager.getCallbacksForServer(serverId)
    }

    override fun getSystemMessages(): List<SystemMessageCallback> {
        return mcpClientManager.getSystemMessagesForServer(serverId)
    }
}

