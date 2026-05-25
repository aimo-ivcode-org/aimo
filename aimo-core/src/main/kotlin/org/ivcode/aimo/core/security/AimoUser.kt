package org.ivcode.aimo.core.security

/**
 * Represents a user in the Aimo system.
 *
 * @param userId A string identifier for the user. This is provider-specific:
 *   - GlobalUserProvider returns "global"
 *   - BasicAuthUserProvider returns username
 *   - OAuth2UserProvider returns subject claim
 *   - etc. No special semantics; all userIds are treated identically.
 * @param metadata User-level metadata (org, role, tier, preferences, etc.)
 */
data class AimoUser(
    val userId: String,
    val metadata: Map<String, Any> = emptyMap()
)

