# Package org.ivcode.aimo.core.model

LLM model configuration, provider factories, and execution interfaces.

This package defines the model abstraction layer that allows AIMO to work with different 
LLM backends (Ollama, Bedrock, custom). It includes model configuration classes, the provider 
factory interface, and the core execution interfaces (`AimoChatEngine`, `AimoChatModel`).

Responsibilities
----------------
- Define `AimoChatModel` interface for model metadata and capabilities
- Define `AimoChatEngine` interface for LLM execution (completions, streaming, tool calling)
- Provide `AimoChatModelConfig` for model configuration (name, engine, context, options)
- Provide `AimoChatModelProviderFactory` interface for provider-specific model discovery
- Define `AimoChatOptions` for provider-agnostic request options (temperature, max tokens, etc.)
- Define `AimoChatContext` for prompt budgeting configuration per model
- Support model selection: exactly one primary model globally, zero or one primary per provider
- Expose well-known model IDs for runtime selection

Key Concepts
------------
- **Model configuration**: `AimoChatModelConfig` wraps an engine with name, context size, 
  budgeter type, and default options.
- **Primary model selection**: Exactly one model globally is marked `isPrimary = true`. 
  This model is used when no explicit model is selected.
- **Provider factory pattern**: Each provider (Ollama, Bedrock) implements 
  `AimoChatModelProviderFactory` to expose its models. Factories are discovered and 
  registered by the configuration module.
- **Engine abstraction**: `AimoChatEngine` abstracts the LLM API behind a unified interface 
  for completions and streaming responses.
- **Context and budgeting**: Each model can specify context size and budgeting strategy 
  (context window vs. no-op) for prompt management.

Integration Points
-------------------
- Depends on provider modules (`aimo-model-ollama`, `aimo-model-bedrock`) to implement 
  `AimoChatModelProviderFactory`
- Registered in `AimoConfig` for model discovery and validation
- Used by `ChatClientBuilderFactory` to select models at runtime
- Passes `AimoChatOptions` to `AimoChatEngine` for provider-specific request tuning

Provider Implementation Requirements
-------------------------------------
- Each provider module implements `AimoChatModelProviderFactory`
- Factory discovers available models (from configuration or API)
- Returns `AimoChatModelConfig` for each model with a provider-backed `AimoChatEngine`
- At most one model per provider can be marked `primary = true`
- Provider modules are responsible for model-specific options and execution details

Example Provider Implementation
--------------------------------
```kotlin
class OllamaChatModelFactory : AimoChatModelProviderFactory {
    override fun getPrimaryModel(): AimoChatModelConfig? { ... }
    override fun getModel(name: String): AimoChatModelConfig? { ... }
    override fun getAllModels(): List<AimoChatModelConfig> { ... }
}
```

Developer Notes
----------------
- Do not add provider-specific logic to this package; keep it generic
- All provider details live in adapter modules (`aimo-model-*`)
- Model selection happens at builder time; once selected, the model cannot change during a chat
- Prompt budgeting is model-specific and configured per model in `AimoChatContext`


