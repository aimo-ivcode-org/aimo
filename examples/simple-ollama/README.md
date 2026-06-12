# Simple Ollama Example

This is a runnable Spring Boot application that demonstrates Aimo's builder pattern with the Ollama model provider.

## What This Example Shows

- ✅ Configuration-driven model setup via `application.yml`
- ✅ REST API integration through `aimo-server`
- ✅ UI integration through `aimo-plugin-ui`
- ✅ Tool definition using `@ChatService` annotations
- ✅ Interceptor configuration (logging enabled)
- ✅ File-based conversation persistence

## Architecture

This example composes several Aimo modules:

```
simple-ollama (Spring Boot App)
    ├─ aimo-server (REST API)
    ├─ aimo-plugin-ui (UI + title tool)
    ├─ aimo-model-ollama (Ollama adapter)
    ├─ aimo-ui (React frontend)
    └─ aimo-core (orchestration)
```

## Prerequisites

1. **JDK 21** or higher
2. **Ollama** running locally on `http://localhost:11434`
3. **Model pulled**: `ollama pull gpt-oss:20b`

## Quick Start

From the repository root:

```powershell
.\gradlew.bat :examples:simple-ollama:bootRun
```

Then open your browser to:
- **http://localhost:8080** - React UI
- **http://localhost:8080/aimo-api/** - REST API

## Configuration

See `src/main/resources/application.yml`:

```yaml
aimo:
  # Storage location for conversations
  data-dir: ./data/conversations
  
  # Single-user mode
  global-user-id: ollama-user2
  
  # Model configuration
  model:
    ollama:
      gpt-oss:
        base-url: http://localhost:11434
        primary: true
        context:
          size: 1000
          excludeThinking: true
        options:
          model: gpt-oss:20b
          temperature: 0.7
  
  # Enable logging interceptor
  interceptors:
    logging:
      enabled: true
      level: DEBUG
```

## How It Works

### 1. API Layer (`aimo-server`)

The server module provides REST endpoints:

```kotlin
@RestController
@RequestMapping("/aimo-api")
class ChatController(
    private val chatService: ChatService  // Injected from aimo-server
) {
    @PostMapping("/chat/{chatId}")
    fun chat(@PathVariable chatId: UUID, @RequestBody request: ChatRequest) =
        chatService.chat(chatId, request)
}
```

### 2. Service Layer (Builder Pattern)

Under the hood, the service uses the builder pattern:

```kotlin
@Service
class ChatService(
    private val conversationFactory: ConversationFactory,
    private val chatClientBuilderFactory: ChatClientBuilderFactory
) {
    fun chat(chatId: UUID, request: ChatRequest): StreamingResponseBody {
        // 1. Load or create conversation
        val conversation = conversationFactory.getConversation(chatId)
        
        // 2. Build chat client (with interceptors applied)
        val chatClient = chatClientBuilderFactory.builder(conversation).build()
        
        // 3. Execute chat with streaming
        return StreamingResponseBody { outputStream ->
            chatClient.chatStream(request.toAimoChatRequest()) { response ->
                // Stream NDJSON to client...
            }
        }
    }
}
```

### 3. Model Provider (`aimo-model-ollama`)

The Ollama adapter:
- Registers itself as an `AimoChatModelProviderFactory` Spring bean
- Reads configuration from `aimo.model.ollama.*`
- Creates `OllamaChatModel` instances on demand
- Handles streaming and tool call responses

### 4. Tools (`aimo-plugin-ui`)

The UI plugin provides title management:

```kotlin
@ChatService
class TitleChatController {
    @Tool(description = "Set conversation title")
    fun setTitle(
        @ToolParam("The chat ID") chatId: String,
        @ToolParam("The new title") title: String,
        context: Map<String, Any>  // Auto-injected, not in tool schema
    ): String {
        // Validate and save title...
        return "Title set to: $title"
    }
}
```

The LLM can call this tool during conversations to set titles.

## Directory Structure

```
simple-ollama/
├── build.gradle.kts          # Dependencies
├── settings.gradle.kts        # Module configuration
├── README.md                  # This file
└── src/
    └── main/
        ├── kotlin/
        │   └── org/ivcode/aimo/examples/ollama/
        │       └── SimpleOllamaApplication.kt
        └── resources/
            └── application.yml  # Configuration
```

## Running Tests

Test the example:

```powershell
.\gradlew.bat :examples:simple-ollama:test
```

Test all modules:

```powershell
.\gradlew.bat test
```

## Extending This Example

### Add a Custom Tool

```kotlin
@ChatService
class WeatherService {
    @Tool(description = "Get current weather for a city")
    fun getWeather(
        @ToolParam("City name") city: String
    ): String {
        // Call weather API...
        return "The weather in $city is sunny, 72°F"
    }
}
```

Tools are automatically discovered at startup.

### Add a Custom Interceptor

```kotlin
@Component
class CustomLoggingInterceptor : ChatClientInterceptor {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    override fun intercept(chain: Chain, context: MutableMap<String, Any>): AimoChatResponse {
        val chatId = context["chatId"]
        logger.info("Chat starting for: $chatId")
        
        val response = chain.proceed(context)
        
        logger.info("Chat completed: ${response.messages.size} messages")
        return response
    }
}
```

All `ChatClientInterceptor` beans are automatically registered.

### Configure a Different Model

```yaml
aimo:
  model:
    ollama:
      llama-fast:
        base-url: http://localhost:11434
        primary: false
        options:
          model: llama3:8b
          temperature: 0.3
```

Then programmatically select it:

```kotlin
val chatClient = chatClientBuilderFactory
    .builder(conversation)
    .withModel("llama-fast")
    .build()
```

## Troubleshooting

### "Connection refused" Error

**Cause**: Ollama is not running

**Fix**: Start Ollama:
```powershell
ollama serve
```

### "Model not found" Error

**Cause**: The configured model is not pulled

**Fix**: Pull the model:
```powershell
ollama pull gpt-oss:20b
```

### "No Aimo chat models configured"

**Cause**: Configuration is missing or incorrect

**Fix**: Check `application.yml` has a valid `aimo.model.ollama.*` section

## Next Steps

- Review the main [README](../../README.md) for full builder pattern documentation
- Check [MIGRATION-PHASE1.md](../../MIGRATION-PHASE1.md) for architecture details
- See [AGENTS.md](../../AGENTS.md) for development conventions
- Try the Bedrock example: `examples/simple-bedrock`

## Notes

- Conversations are persisted in `./data/conversations/` (configurable)
- The UI is bundled into the server JAR (no separate frontend server needed)
- Logging is enabled by default in this example for demonstration
- All tools and system messages are auto-discovered via `@ChatService` scanning

