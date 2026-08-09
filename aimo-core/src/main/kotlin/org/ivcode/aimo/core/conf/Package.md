# Package org.ivcode.aimo.core.conf

Spring auto-configuration for AIMO core components and system-wide validation.

This package contains the central Spring configuration class (`AimoConfig`) that wires together 
all core components: models, tools, scopes, conversation storage, interceptors, and the chat 
client builder factory. It also performs comprehensive fail-fast validation at startup to catch 
configuration errors early.

Responsibilities
----------------
- Provide Spring `@Configuration` and `@Bean` definitions for core components
- Discover and register all `@ChatService` beans via `ChatServiceProviderRegistry`
- Load YAML configuration from `AimoProperties` (aimo.* prefix)
- Build and validate chat scopes from YAML scope definitions
- Ensure exactly one primary model exists globally; enforce provider-specific uniqueness
- Wire `AimoChatModelProviderFactory` to discover and expose available models
- Configure default interceptors (logging, tracing, error handling)
- Build `ChatClientBuilderFactory` with all discovered models, tools, and scopes
- Perform scope validation: ensure all scoped tools/messages are subsets of parent service scopes
- Fail fast: throw exceptions at startup if configuration is invalid

Key Concepts
------------
- **Single entry point**: `AimoConfig` is the sole `@Configuration` class in core; 
  all beans flow through it.
- **Model uniqueness**: Exactly one global primary model is required. Provider-specific factories 
  also enforce at most one `primary=true` per provider (e.g., Ollama may have one, Bedrock 
  may have one, but only one is global primary).
- **Scope registration**: Chat scopes are pre-defined in YAML under `aimo.scope.*`; 
  each scope lists tool-refs and system-message-refs.
- **Provider registry**: `ChatServiceProviderRegistry` collects local `@ChatService` beans 
  and remote providers (e.g., MCP servers) into a unified registry.
- **Interceptor composition**: Default interceptors are registered and composed into 
  the builder factory for cross-cutting concerns.
- **Validation rules**:
  - Exactly one primary model globally
  - At most one primary model per provider
  - All scoped tools/messages must belong to their parent service scope
  - No duplicate tool names across all providers
  - Scope references in YAML must exist in service declarations

Integration Points
-------------------
- Depends on `AimoProperties` for all configuration (model configs, scopes, paths)
- Depends on `AimoChatModelProviderFactory` implementations (injected by submodules like 
  `aimo-model-ollama`, `aimo-model-bedrock`)
- Registers `ChatServiceProviderRegistry` for use by the chat client builder
- Builds `ChatClientBuilderFactory` used by HTTP controllers and test code

Developer Notes
----------------
- This class is large and wires many components; if you add a new component type, 
  add a `@Bean` method here and integrate with the provider registry or model factory.
- Validation errors are printed to startup logs; review build output carefully for warnings.
- To troubleshoot scope or model issues, look at startup messages for validation failures.


