package org.ivcode.aimo.core.security

/**
 * Provides the current user based on the execution context.
 *
 * Different implementations determine how users are identified:
 * - GlobalUserProvider: Always returns the same "global" user
 * - BasicAuthUserProvider: Extracts user from HTTP Basic Auth
 * - OAuth2UserProvider: Extracts user from OAuth2 token
 * - JWTUserProvider: Parses user from JWT token
 * - CustomHeaderUserProvider: Reads user from custom HTTP header
 *
 * The provider is the single source of truth for determining the current user.
 */
interface AimoUserProvider {

    /**
     * Get the current user from the execution context.
     *
     * @return the current user. Should never return null; throw exception if no context.
     * @throws IllegalStateException if no user context is available
     */
    fun getCurrentUser(): AimoUser

    /**
     * Get a user by ID (optional, for admin/lookup).
     *
     * @param userId the user identifier to look up
     * @return the user if found, or null if not supported or not found
     */
    fun getUserById(userId: String): AimoUser? = null
}



