# Aimo

> ⚠️ **Active Development Notice**
> 
> This project is functional and in a working state, but is currently undergoing **heavy development and serious refactoring** toward a 1.0 release. APIs, module structure, and core architecture are subject to significant change.
> 
> See the [Roadmap](./ROADMAP.md) for planned changes and current direction.

## 📊 Project Status

**Current Phase**: ✅ **Phase 2 (ChatScopes)** is **COMPLETE**

For detailed status, phase progress, and next steps, see [ROADMAP.md](./ROADMAP.md).

| Completed | Current | Next |
|-----------|---------|------|
| ✅ Phase 1: Configuration | ✅ Phase 2: ChatScopes | 📋 Phase 3: Spring Security |
| ✅ Phase 1.5: Rename @ChatController | ✅ Phase 2: Tests (15 tests) | |

Aimo is the Artificial Intelligence Model Orchestrator: a modular Kotlin/Spring project for building AI chat applications with conversation memory, tool-calling controllers, an Ollama-backed model adapter, and a React UI.

## What this repository contains

`aimo` is a multi-module Gradle workspace with reusable libraries plus a runnable example app:

| Module | Purpose |
| --- | --- |
| `aimo-core` | Core abstractions and runtime (conversations, chat clients, builders, model-facing prompt flow, tool/system-message annotations). |
| `aimo-model-ollama` | Ollama-backed Aimo model integration. |
| `aimo-model-bedrock` | AWS Bedrock model integration. |
| `aimo-server` | REST API layer for conversations, chat streaming, and history. |
| `aimo-plugin-ui` | UI-specific server plugin (title endpoints + title tool controller). |
| `aimo-ui` | React + Vite frontend packaged into resources for server distribution. |
| `examples/simple-ollama` | Runnable Spring Boot app that composes server + UI plugin + Ollama model module. |
| `examples/simple-bedrock` | Runnable Spring Boot app that composes server + UI plugin + Bedrock model module. |

## Tech stack

- Kotlin `2.2.21`
- Spring Boot `4.0.3`
- Java toolchain `21`
- React `19` + Vite `7`

## Architecture at a glance

1. `examples/simple-ollama` starts Spring Boot and pulls in the other modules as dependencies.
2. `aimo-server` exposes API routes under `/aimo-api/*`.
3. `aimo-core` manages conversations, history, tool callbacks, and model prompt orchestration.
4. `aimo-model-ollama` provides the `ChatModel` implementation used by `aimo-core`.
5. `aimo-ui` consumes the API and `aimo-plugin-ui` adds title-specific behavior.

### Builder Pattern (Phase 1 Architecture)

Aimo uses a **builder pattern** for flexible runtime composition:

```kotlin
// 1. Inject the factories
@Service
class MyChatService(
    private val conversationFactory: ConversationFactory,
    private val chatClientBuilderFactory: ChatClientBuilderFactory
) {
    fun handleRequest(chatId: UUID, message: String, userId: String): AimoChatResponse {
        // 2. Get or create conversation
        val conversation = conversationFactory.getConversation(chatId, userId)
            ?: throw NotFoundException("Conversation not found")
        
        // 3. Build chat client with optional customization
        val chatClient = chatClientBuilderFactory
            .builder(conversation)
            .withModel("gpt-oss")  // optional: override model
            .build()
        
        // 4. Execute chat
        return chatClient.chat(AimoChatRequest(prompt = message, context = emptyMap()))
    }
}
```

**Key components:**

- **`ConversationFactory`**: Creates/loads conversation instances from storage
- **`ChatClientBuilderFactory`**: Entry point for building chat clients
- **`ChatClientBuilder`**: Fluent API for composing clients with models and interceptors
- **`AimoChatClient`**: The orchestrator that executes chats with tool handling
- **Interceptors**: Cross-cutting concerns (logging, tracing, error handling, security)

See the [Integration Guide](#programmatic-usage) below for detailed examples.

## Configuration

Aimo is configured via `application.yml` under the `aimo.*` prefix.

### Basic Configuration

```yaml
aimo:
  # Conversation storage directory
  data-dir: ./data/conversations
  
  # Single-user mode: default userId is "global" (GlobalUserProvider). To customize it, provide your own AimoUserProvider bean.
  global-user-id: global
  
  # Model configuration (provider-specific)
  model:
    ollama:
      gpt-oss:
        base-url: http://localhost:11434
        primary: true  # This model will be used by default
        context:
          size: 1000
          excludeThinking: true
        options:
          model: gpt-oss:20b
          temperature: 0.7
```

### Interceptor Configuration

Interceptors provide cross-cutting concerns. All are **disabled by default**.

```yaml
aimo:
  interceptors:
    # Request/response logging
    logging:
      enabled: true
      level: DEBUG  # DEBUG, INFO, WARN, ERROR
    
    # Distributed tracing
    tracing:
      enabled: false
      service-name: aimo-chat
    
    # Automatic retry on transient errors
    error-handling:
      enabled: false
      max-retries: 3
      retry-backoff-ms: 100
```

### Multiple Models

You can configure multiple models and select at runtime:

```yaml
aimo:
  model:
    ollama:
      gpt-oss:
        base-url: http://localhost:11434
        primary: true  # Default model
        options:
          model: gpt-oss:20b
      
      llama-small:
        base-url: http://localhost:11434
        options:
          model: llama3:8b
          temperature: 0.5
```

**Exactly one model must be marked `primary: true`** or you must have only one model configured.

## Programmatic Usage

### Basic Chat Client

```kotlin
@Service
class ChatService(
    private val conversationFactory: ConversationFactory,
    private val chatClientBuilderFactory: ChatClientBuilderFactory
) {
    fun chat(chatId: UUID, userMessage: String, userId: String): AimoChatResponse {
        val conversation = conversationFactory.getConversation(chatId, userId)
            ?: throw NotFoundException("Conversation not found")
        val chatClient = chatClientBuilderFactory.builder(conversation).build()
        
        return chatClient.chat(AimoChatRequest(prompt = userMessage, context = emptyMap()))
    }
}
```

### Model Selection

```kotlin
// Use a specific model by name
val chatClient = chatClientBuilderFactory
    .builder(conversation)
    .withModel("llama-small")  // Override the primary model
    .build()
```

### Adding Custom Interceptors

```kotlin
// Create a custom interceptor
class RateLimitInterceptor : ChatClientInterceptor {
    override fun intercept(chain: ChatClientInterceptor.Chain, context: MutableMap<String, Any>): AimoChatResponse {
        // Check rate limit...
        return chain.proceed(context)
    }
}

// Register as a Spring @Bean to apply globally
@Configuration
class MyConfig {
    @Bean
    fun rateLimitInterceptor() = RateLimitInterceptor()
}

// Or apply to a specific client
val chatClient = chatClientBuilderFactory
    .builder(conversation)
    .withInterceptor(RateLimitInterceptor())
    .build()
```

### Streaming Responses

```kotlin
val request = AimoChatRequest(prompt = "Tell me a story", context = emptyMap())

chatClient.chatStream(request) { response ->
    // Called for each chunk
    response.messages.forEach { message ->
        when (message.type) {
            AimoChatMessageType.ASSISTANT -> print(message.content)
            AimoChatMessageType.TOOL -> println("Tool: ${message.toolCallId}")
            else -> Unit
        }
}
```

### Creating Tools

Define tools using the `@ChatService` annotation:

```kotlin
@ChatService
class CalculatorService {
    
    @Tool(description = "Add two numbers")
    fun add(
        @ToolParam("First number") a: Int,
        @ToolParam("Second number") b: Int
    ): Int = a + b
    
    @Tool(description = "Multiply two numbers")
    fun multiply(a: Int, b: Int): Int = a * b
}
```

Tools are automatically discovered and registered at startup. The LLM can call these during conversations.

### System Messages

Inject context into every conversation:

```kotlin
@ChatService
class SecurityService {
    
    @SystemMessage
    fun securityPolicy(): String {
        return """
            You are a helpful assistant for Acme Corp.
            Never share confidential information.
            Always be professional and respectful.
        """.trimIndent()
    }
}
```

System messages are automatically prepended to every chat request.

## Chat Scopes (Phase 2)

**Chat Scopes** define which tools and system messages are available in a conversation—the autonomous decision-making capabilities. Each scope is a named configuration that restricts tool access and defines custom system messages.

### What are Chat Scopes?

- **Limit tool visibility**: Different roles/users see different tools
- **Custom instructions per scope**: Define specialized system messages for each scope
- **Scope inheritance**: Tools and system messages can be restricted to specific scopes
- **Inline + pre-defined messages**: Combine YAML-defined prompts with code-defined system messages

### Example Scope Configuration

```yaml
aimo:
  scope:
    # Research assistant with research-specific tools
    research:
      display-name: "Research Assistant"
      description: "Research and analysis capabilities"
      tool-refs: ["search", "summarize", "web_fetch"]  # Only these tools available
      system-messages:
        research_guide: |
          You are a research expert specializing in data analysis and academic research.
          Focus on accuracy, citations, and evidence-based reasoning.
      system-message-refs: ["research_prompt"]  # References @SystemMessage(name="research_prompt")
    
    # Public assistant with limited tools
    public:
      display-name: "Public Assistant"
      description: "General purpose for public users"
      tool-refs: ["help", "explain"]  # Limited toolset
      system-messages:
        public_guide: |
          You are a helpful assistant available to the public.
          Keep responses clear and avoid technical jargon.
```

**Built-in global scope**: Always available with all tools and system messages.

### Defining Scoped Tools and System Messages

Use `@ChatService` to declare scope restrictions:

```kotlin
@ChatService(scope = ["research", "admin"])  // Available only in these scopes
class ResearchService {
    
    @Tool(description = "Search research papers")
    fun searchPapers(@ToolParam("Query") query: String): String {
        // implementation
    }
    
    @SystemMessage(name = "research_prompt")
    fun researchPrompt(): String = "Focus on academic sources..."
}

@ChatService  // Empty scope = available to all scopes
class GeneralService {
    
    @Tool(description = "Get help")
    fun getHelp(): String = "How can I help?"
}
```

### Using Scopes at Runtime

Select a scope when building a chat client:

```kotlin
val scope = chatScopeProvider.getScope("research") ?: chatScopeProvider.getGlobalScope()
val chatClient = chatClientBuilderFactory
    .builder(conversation)
    .withChatScope(scope)
    .build()
```

Or persist scope in conversation metadata:

```kotlin
conversation.setSelectedChatScope("research")

// Later, resolve saved scope id and apply it explicitly
val scopeId = conversation.getSelectedChatScope() ?: "global"
val scope = chatScopeProvider.getScope(scopeId) ?: chatScopeProvider.getGlobalScope()
val chatClient = chatClientBuilderFactory
    .builder(conversation)
    .withChatScope(scope)
    .build()
```

### Scope Rules

1. **Empty scope** on `@Tool` or `@SystemMessage` = available to **all scopes**
2. **Non-empty scope** must be a subset of parent `@ChatService` scope (fail-fast validation at startup)
3. **Tool/System message names** must be unique across the application
4. **YAML references** (`tool-refs`, `system-message-refs`) must match actual tool/message names

### How Scopes Work

1. **At startup**: All tools/system messages are discovered and categorized by scope
2. **On build**: ChatClientBuilder filters tools/messages based on selected scope
3. **Scope resolution order**:
   - Explicit scope via `withChatScope()`
   - Conversation metadata (`aimo.chatScopeId`)
   - Default: `"global"` scope (all tools/messages)


## Prerequisites

- JDK 21
- Node.js + npm (for frontend builds/dev)
- Ollama running locally
- The default model configured in `aimo-model-ollama`:
  - `gpt-oss:20b`

Example model pull:

```powershell
ollama pull gpt-oss:20b
```

## Quick start (recommended)

Run the composed demo app:

```powershell
.\gradlew.bat :examples:simple-ollama:bootRun
```

Default API base URL used by the frontend clients:

- `http://localhost:8080`

## API surface (current)

All routes are rooted at `/aimo-api`.

- `POST /aimo-api/conversation/` - create conversation
- `GET /aimo-api/conversation/` - list conversations
- `DELETE /aimo-api/conversation/{chatId}` - delete conversation
- `POST /aimo-api/chat/{chatId}` - stream chat response
- `GET /aimo-api/history/{chatId}` - fetch conversation history
- `GET /aimo-api/title/` - list titles
- `GET /aimo-api/title/{chatId}` - read title
- `PUT /aimo-api/title/{chatId}/{title}` - set title

## Frontend development (Vite)

If you want faster UI iteration, run the backend and Vite separately.

Terminal 1 (backend):

```powershell
.\gradlew.bat :examples:simple-ollama:bootRun
```

Terminal 2 (frontend):

```powershell
Set-Location .\aimo-ui
npm install
npm run dev
```

Note: the generated clients in `aimo-ui/src/api/*` are currently initialized with `http://localhost:8080`.

## Build and test

From repository root:

```powershell
.\gradlew.bat build
.\gradlew.bat test
```

Frontend-only checks:

```powershell
Set-Location .\aimo-ui
npm run type-check
npm run test
npm run build
```

## Project layout

```text
aimo/
  aimo-core/
  aimo-model-ollama/
  aimo-server/
  aimo-plugin-ui/
  aimo-ui/
  examples/simple-ollama/
```

## Notes

- If dependency resolution fails, check your network/repository access for the repositories configured in `settings.gradle.kts` and `build.gradle.kts`.
