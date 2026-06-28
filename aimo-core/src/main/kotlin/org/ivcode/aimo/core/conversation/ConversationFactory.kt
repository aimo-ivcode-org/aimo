package org.ivcode.aimo.core.conversation

import java.util.UUID

/**
 * Factory for creating Conversation instances from the DAO/store with interceptors.
 *
 * This factory creates Conversation instances from the underlying store and optionally
 * wraps them with interceptors for cross-cutting concerns:
 * - Security and access control (enforce user ownership)
 * - Auditing (log all DAO operations)
 * - Caching (memoize getMessages calls)
 * - Data transformation (encryption, schema migration)
 *
 * Typical usage:
 * ```kotlin
 * val conversation = factory
 *     .withInterceptor(SecurityConversationInterceptor(userId))
 *     .withInterceptor(AuditingInterceptor())
 *     .getConversation(chatId, userId)
 * ```
 *
 * **Only accepts ConversationInterceptor**, not ChatClientInterceptor.
 */
interface ConversationFactory {
    /**
     * Register a conversation-level interceptor.
     *
     * Interceptors are applied in registration order. First registered interceptor is the
     * outermost link (executes first).
     *
     * @param interceptor The interceptor to register
     * @return this factory for chaining
     */
    fun withInterceptor(interceptor: ConversationInterceptor): ConversationFactory

    /**
     * Create a conversation from the DAO/store and apply all registered interceptors.
     *
     * @param chatId The conversation identifier
     * @param userId The user identifier (for access control and scoping)
     * @return The conversation instance ready for use (wrapped with interceptors if any were registered),
     *         or null if the conversation is not found or the user does not have access
     */
    fun getConversation(chatId: UUID, userId: String): Conversation?
}