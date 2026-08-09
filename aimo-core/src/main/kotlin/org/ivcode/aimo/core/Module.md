# Module aimo-core

## Purpose

`aimo-core` is the foundational framework for building multi-turn AI conversations with tool calling, 
system message management, and durable conversation history. It provides the central orchestration 
engine that coordinates LLM requests, tool execution, conversation storage, and role-based access control.

This is the **core seam** of the AIMO architecture: the central hub that all composable applications 
depend on, regardless of which model provider (Ollama, Bedrock, etc.), transport layer (HTTP, WebSocket), 
or UI they use.

## What It Does

### 1. **Chat Client Orchestration** (`chatclient/`)
   - Implements the multi-turn conversation loop (`AimoChatClientImpl`)
   - Handles the request flow: fetch history → merge context → call LLM → execute tools → persist messages
   - Supports tool calling with automatic parameter binding and JSON schema generation
   - Manages prompt budgeting and message caching
   - Provides interceptor-based extensibility for cross-cutting concerns (logging, tracing, auditing)

### 2. **Conversation Storage** (`conversation/` + `dao/`)
   - Abstracts conversation persistence via pluggable `AimoChatClientDao` interface
   - Supports multiple backends: in-memory, file-based, or custom (SQL, NoSQL)
   - Manages durable metadata (e.g., chat titles) and message history
   - Enforces scope-based access control at the DAO layer
   - Provides `ConversationInterceptor` for advanced use cases like encryption or compression

### 3. **Tool & System Message Discovery** (`chatservice/`)
   - Reflection-based discovery of `@ChatService` beans at startup
   - Annotation-driven registration: `@Tool` for callable functions, `@SystemMessage` for prompt text
   - Auto-generates JSON schemas for tool parameters
   - Supports method signatures `() -> String?` or `(SystemMessageContext) -> String?` for system messages
   - Special handling: `context: Map` parameters are auto-injected and excluded from JSON schemas

### 4. **Role-Based Access Control via Chat Scopes** (`chatscope/`)
   - Defines which tools and system messages are available in a conversation
   - Scopes configured in YAML; every runtime has a built-in `"global"` scope (unrestricted)
   - Filtering happens at conversation build time—tools/messages are pre-filtered by selected scope
   - Scope metadata validated at startup; fail-fast on invalid definitions
   - Supports both named system messages (stable references) and inline system messages (YAML-defined)

### 5. **Model Provider Integration** (`model/`)
   - Pluggable model interface: `AimoChatModel` and `AimoChatEngine`
   - Exactly one primary model must exist globally; provider-specific factories enforce local rules
   - Allows different LLM backends (Ollama, Bedrock, custom) to coexist in Gradle workspace

### 6. **Configuration & Wiring** (`conf/`, `properties/`)
   - Spring-based auto-configuration that discovers models, tools, scopes, and system messages
   - Loads scope definitions and MCP server configurations from `application.yml`
   - Registers all `ChatServiceProvider` instances (local + MCP-discovered)
   - Validates configuration at startup; fail-fast on conflicts


## Key Abstractions

- **`AimoChatClient`**: The conversation interface; callers invoke `chat(AimoChatRequest)` and get `AimoChatResponse`
- **`Conversation`**: Represents a single chat's persisted history and metadata
- **`AimoChatClientDao`**: Storage abstraction for conversations
- **`AimoChatModel`**: LLM interface (token counting, model info, etc.)
- **`AimoChatEngine`**: LLM execution (streaming completions, tool calling)
- **`ChatScope`**: Named set of available tools and system messages for a conversation
- **`ChatServiceProvider`**: Discovers tools and system messages (local or remote via MCP)
- **`ConversationInterceptor`**: Extensibility point for conversation operations


