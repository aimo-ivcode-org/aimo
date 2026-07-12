package org.ivcode.aimo.core.chatservice

import org.ivcode.aimo.core.model.ToolCallback

/**
 * Provider wrapper for a single annotated @ChatService bean.
 *
 * Each discovered @ChatService bean is wrapped as an individual provider,
 * allowing each service to be independently managed and scoped.
 *
 * Reports an empty provider-level [scopes] set because per-scope restriction
 * for annotated tools and system messages is enforced entirely through each
 * callback's own [scopes], exactly as today.
 *
 * ## Scope Inheritance and Validation
 *
 * **AnnotatedChatServiceProvider always has empty provider-level scopes** (unrestricted/global).
 * This is by design because scope restriction for annotated tools and system messages is
 * enforced entirely through each callback's own [scopes], validated at annotation-processing
 * time (in ControllerHelpers.computeActualScopes).
 *
 * **For future providers (MCP servers, adapters with non-empty provider.scopes):**
 * - Provider.scopes defines which scope IDs the provider can contribute to
 * - All callbacks MUST have scopes that are subsets of provider.scopes
 * - Valid examples: provider with scopes ["admin", "research"] can have callbacks with scopes:
 *   - [] (unrestricted, inherits provider)
 *   - ["admin"]
 *   - ["research"]
 *   - ["admin", "research"]
 * - Invalid: callback with scope ["forbidden"] would be an error
 *
 * @property entity The ChatServiceEntity representing this @ChatService bean
 */
class AnnotatedChatServiceProvider(
    private val entity: ChatServiceEntity
) : ChatServiceProvider {

    override val id: String = entity.name

    override val scopes: Set<String> = emptySet()

    override fun getTools(): List<ToolCallback> {
        return entity.tools
    }

    override fun getSystemMessages(): List<SystemMessageCallback> {
        return entity.systemMessages
    }
}
