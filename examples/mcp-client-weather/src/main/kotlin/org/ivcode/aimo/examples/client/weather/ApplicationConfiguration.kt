package org.ivcode.aimo.examples.client.weather

import org.ivcode.aimo.core.dao.AimoChatClientDao
import org.ivcode.aimo.core.dao.AimoChatClientDaoMemory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for the weather MCP client application.
 *
 * This class provides Spring beans required for the AIMO framework to operate,
 * specifically the conversation storage DAO.
 *
 * ## Architecture
 *
 * The AIMO framework requires:
 * - An LLM provider (Ollama in this case, configured in application.yml)
 * - An MCP client to discover remote tools (weather server in this case)
 * - A DAO for conversation storage (provided here)
 * - A chat server to accept user requests (aimo-server)
 * - A web UI to interact with the chat (aimo-plugin-ui)
 *
 * ## Conversation Storage
 *
 * This configuration uses in-memory storage, which is perfect for:
 * - Development and testing
 * - Stateless deployments
 * - Quick prototyping
 *
 * For production, replace AimoChatClientDaoMemory with:
 * - AimoChatClientDaoFile - file-based storage
 * - Custom database implementation (PostgreSQL, MongoDB, etc.)
 *
 * ## Example: File-based Storage
 *
 * ```kotlin
 * @Bean
 * fun aimoChatClientDao(): AimoChatClientDao {
 *     return AimoChatClientDaoFile(File("./data/conversations"))
 * }
 * ```
 *
 * @see org.ivcode.aimo.core.dao.AimoChatClientDao
 * @see org.ivcode.aimo.core.dao.AimoChatClientDaoMemory
 * @see org.ivcode.aimo.core.dao.AimoChatClientDaoFile
 */
@Configuration
class ApplicationConfiguration {

    /**
     * Provide in-memory DAO for conversation storage.
     *
     * This bean is required by the AIMO core framework to store and retrieve
     * conversation history. Using in-memory storage means conversations are
     * lost when the application restarts.
     *
     * @return An in-memory DAO implementation
     */
    @Bean
    fun aimoChatClientDao(): AimoChatClientDao {
        return AimoChatClientDaoMemory()
    }
}




