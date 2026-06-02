package org.ivcode.aimo.core.security

/**
 * Default provider that always returns the same global user.
 *
 * Used for single-user mode where all HTTP requests and operations
 * use the same userId. All conversations with that userId are effectively
 * shared among all requests.
 *
 * @param globalUserId the user ID to use for all requests (default: "global")
 */
class GlobalUserProvider(
    private val globalUserId: String = "global"
) : AimoUserProvider {
    override fun getCurrentUser(): AimoUser = AimoUser(userId = globalUserId)
}

