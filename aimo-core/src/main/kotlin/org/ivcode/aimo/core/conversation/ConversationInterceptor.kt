package org.ivcode.aimo.core.conversation

import java.util.UUID

/**
 * Interceptor for conversation operations.
 *
 * Interceptors can add cross-cutting concerns such as:
 * - **Auditing**: Log all DAO operations
 * - **Caching**: Memoize results
 * - **Data transformation**: Encryption, compression, schema migration
 * - **Metadata enrichment**: Add or modify metadata before DAO calls
 *
 * ## Usage Pattern
 *
 * Interceptors receive the `chatId` and a mutable `metadata` map. They can:
 * 1. Enrich the metadata with additional entries
 * 2. Call the chain to proceed to the next interceptor or final DAO operation
 * 3. Post-process the result
 *
 * ```kotlin
 * factory
 *     .withInterceptor(MyCustomInterceptor())
 *     .getConversation(chatId, mapOf("tenant" to "acme"))
 * ```
 */
interface ConversationInterceptor {
    /**
     * Intercept a conversation operation.
     *
     * @param chain The interceptor chain to proceed with
     * @param chatId The chat identifier for this operation
     * @param metadata Mutable metadata map for DAO scoping; interceptors may add entries to enrich the metadata
     * @return The result of the operation
     */
    fun intercept(chain: Chain, chatId: UUID, metadata: MutableMap<String, Any>): Any?

    interface Chain {
        /**
         * Proceed to the next interceptor or final operation.
         *
         * @param chatId The chat identifier
         * @param metadata The metadata for this operation (may have been enriched by interceptors)
         * @return The operation result
         */
        fun proceed(chatId: UUID, metadata: MutableMap<String, Any>): Any?
    }
}
