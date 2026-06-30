package org.ivcode.aimo.core.dao

/**
 * Returns whether [stored] conversation metadata satisfies [scopeMetadata].
 *
 * An empty [scopeMetadata] matches every conversation (unrestricted access).
 * A non-empty [scopeMetadata] matches only if every key in scope has a matching value in stored (AND logic).
 *
 * Semantics: scope metadata represents the caller's permissions; stored metadata represents
 * the conversation's requirements. The matcher validates that all permission keys from scope
 * match the corresponding stored values.
 *
 * Examples:
 * - stored={}, scope={} → true (both empty = unrestricted)
 * - stored={userId: "user1"}, scope={} → true (empty scope = unrestricted access)
 * - stored={}, scope={userId: "user1"} → false (stored value null, cannot match)
 * - stored={userId: "user1"}, scope={userId: "user1"} → true (permission key matches stored value)
 * - stored={userId: "user1", tenant: "acme"}, scope={userId: "user1"} → true (permission subset of requirements)
 * - stored={userId: "user1"}, scope={userId: "user2"} → false (permission value doesn't match stored)
 */
internal object ConversationMetadataMatcher {

    fun matches(stored: Map<String, Any>, scopeMetadata: Map<String, Any>): Boolean {
        // Empty scope = unrestricted access
        if (scopeMetadata.isEmpty()) return true

        // Non-empty scope: all keys in scope must have matching values in stored
        return scopeMetadata.all { (key, expected) -> valuesMatch(stored[key], expected) }
    }

    private fun valuesMatch(stored: Any?, expected: Any): Boolean {
        if (stored == null) return false
        // Require strict equality; reject type mismatches
        return stored == expected
    }
}
