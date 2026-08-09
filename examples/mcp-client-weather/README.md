# Weather MCP Client Example (with Ollama)

An AIMO application that demonstrates integration of:
- **Ollama** as the LLM (Language Model)
- **Weather MCP Server** as a tool provider
- **AIMO Chat UI** for interactive conversations

## Overview

This example shows how to build an AIMO application that:

1. Uses Ollama locally for natural language understanding and generation
2. Connects to an external MCP server (weather) for real-world data access
3. Provides a web UI for conversational interaction
4. Automatically discovers and uses weather tools in conversations

## Architecture

```
┌────────────────────────────────────────┐
│    AIMO Chat Application               │
│  (aimo-server + aimo-plugin-ui)        │
└────────────────┬───────────────────────┘
                 │
    ┌────────────┼────────────┐
    │            │            │
    ▼            ▼            ▼
┌────────┐  ┌─────────────┐  ┌──────────────┐
│ Ollama │  │ aimo-mcp-   │  │ Memory DAO   │
│        │  │ client      │  │              │
└────────┘  └────────┬────┘  └──────────────┘
                     │
                     ▼
            ┌────────────────────┐
            │  Weather MCP       │
            │  Server            │
            │  (HTTP)            │
            └────────────────────┘
```

## Prerequisites

### 1. Ollama

Install and run Ollama locally:

```bash
# Install from https://ollama.ai

# Run Ollama
ollama serve

# In another terminal, pull a model
ollama pull llama2
```

Verify Ollama is running:
```bash
curl http://localhost:11434/api/tags
```

### 2. Weather MCP Server

Start the weather server (see `mcp-server-weather` example):

```bash
cd ../mcp-server-weather
../../gradlew.bat bootRun
```

Verify it's running:
```bash
curl -X POST http://localhost:9090/mcp/ \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

## Building

```bash
cd aimo/examples/mcp-client-weather
../../gradlew.bat build
```

## Running

```bash
../../gradlew.bat bootRun
```

The application will start on **http://localhost:8080**

## Usage

### Access the Web UI

1. Open browser to **http://localhost:8080**
2. You should see the AIMO chat interface
3. Start a new conversation

### Example Conversations

#### Example 1: Simple Weather Query

**User:** "What's the weather like in Seattle?"

**Bot:** Uses the `weather:get-weather` tool to fetch conditions, then responds:
"According to the weather service, Seattle is currently Rainy, 62°F with 85% humidity."

#### Example 2: Multi-City Comparison

**User:** "Compare weather in New York, Los Angeles, and Seattle"

**Bot:** Uses `weather:compare-weather` tool multiple times or `weather:get-weather-batch`:
"New York is Sunny at 75°F (50% humidity), Los Angeles is Sunny at 85°F (30% humidity), and Seattle is Rainy at 62°F (85% humidity). Los Angeles is the warmest."

#### Example 3: Alert Checking

**User:** "Are there any weather alerts for Los Angeles?"

**Bot:** Uses `weather:get-weather-alert` tool:
"Yes, there's a heat advisory with a high of 105°F expected."

#### Example 4: Natural Conversation

**User:** "I'm planning a trip. Should I go to Seattle or San Francisco this weekend?"

**Bot:** Uses both `weather:get-weather` and reasoning:
"Based on weather, San Francisco might be better - it's cloudy at 65°F, while Seattle is rainy at 62°F. San Francisco has better visibility for sightseeing. However, both are mild - bring a light jacket."

## Configuration

The app is configured in `src/main/resources/application.yml`:

### Ollama Configuration

```yaml
aimo:
  models:
    ollama:
      - id: "llama2"
        url: "http://localhost:11434"
        model: "llama2"
        primary: true
        temperature: 0.7
```

**Options:**
- `url` - Ollama server URL
- `model` - Model name (must be pulled in Ollama)
- `temperature` - Creativity level (0.0-1.0)
- `contextSize` - Conversation history length
- `streaming` - Stream responses as generated

### MCP Client Configuration

```yaml
aimo:
  mcp:
    required: false
    servers:
      - id: "weather"
        transport:
          type: "http"
          url: "http://localhost:9090/mcp"
        scope: ["global"]
```

**Options:**
- `required` - Fail startup if server unreachable (false = graceful degradation)
- `transport` - "http", "sse", or "stdio"
- `url` - MCP server endpoint
- `scope` - Which scopes have access (empty = global)

## Troubleshooting

### Ollama not available

**Error:** "Connection refused: localhost:11434"

**Solution:**
```bash
# Check if Ollama is running
curl http://localhost:11434/api/tags

# If not, start Ollama
ollama serve
```

### Weather server not available

**Error:** "MCP server 'weather' unavailable"

**Solution:**
```bash
# Check if weather server is running
curl -X POST http://localhost:9090/mcp/ \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'

# If not, start it
cd ../mcp-server-weather
../../gradlew.bat bootRun
```

### Tools not appearing in chat

**Problem:** Weather tools not available to model

**Check:**
1. MCP server is running and reachable
2. Application logs show tool discovery
3. `aimo.mcp.required: false` allows graceful degradation

**Debug:** Enable debug logging:
```yaml
logging:
  level:
    org.ivcode.aimo.mcp.client: DEBUG
```

### Model responses are slow

**Cause:** Llama2 on CPU is slower; GPU acceleration recommended

**Options:**
1. Use a smaller model: `ollama pull mistral`
2. Enable GPU in Ollama settings
3. Use faster models: TinyLlama, Orca

## API Endpoints

The application exposes REST APIs for programmatic access:

### Chat Endpoint

```
POST /aimo-api/chat/{chatId}

Request:
{
  "messages": [
    {
      "role": "user",
      "content": "What's the weather in Seattle?"
    }
  ]
}

Response: (NDJSON streaming)
{"delta":"The"}
{"delta":" weather"}
{"delta":" in"}
...
```

See `aimo-server` documentation for full API details.

## Customization

### Add More Tools

1. Create a new `@McpService` bean in the weather server
2. Add `@McpTool` and `@McpPrompt` methods
3. Restart the weather server
4. Restart the client (or enable periodic refresh in MCP config)

### Change the Model

```yaml
aimo:
  models:
    ollama:
      - id: "mistral"
        model: "mistral"       # Use Mistral instead
        temperature: 0.5
```

Then:
```bash
ollama pull mistral
```

### Add Another MCP Server

```yaml
aimo:
  mcp:
    servers:
      - id: "weather"
        transport:
          type: "http"
          url: "http://localhost:9090/mcp"
      - id: "news"
        transport:
          type: "http"
          url: "http://localhost:8081/mcp"
      - id: "research"
        transport:
          type: "http"
          url: "http://localhost:8082/mcp"
```

## Performance Tips

1. **GPU Acceleration**: Configure Ollama to use GPU
2. **Streaming**: Enable streaming responses for faster perceived performance
3. **Model Size**: Use smaller models for faster response times
4. **Context Limit**: Reduce context size to process faster
5. **Temperature**: Lower temperature for faster, more deterministic responses

## Architecture Details

### Request Flow

1. **User enters message** in UI
2. **Chat API** receives and stores in conversation
3. **AIMO Core** processes:
   - Gets system messages
   - Retrieves conversation history
   - Checks available tools (including MCP tools)
   - Calls Ollama with context and tool definitions
4. **Ollama responds** with message or tool call
5. **Tool Invocation**:
   - If tool call, route to aimo-mcp-client
   - aimo-mcp-client forwards to weather server
   - Result returned to model
   - Model generates final response
6. **Response streamed** to client as NDJSON

### Tool Integration

Weather tools are automatically discovered and made available:
- Local tools (if any in aimo-core)
- Remote tools from MCP servers (prefixed with server ID)

Example tool names:
- `weather:get-weather`
- `weather:get-weather-alert`
- `weather:compare-weather`

## Scaling

For production use:

1. **Multiple Models**: Configure multiple Ollama models for failover
2. **Load Balancing**: Run multiple AIMO instances
3. **MCP Servers**: Deploy weather and other MCP servers separately
4. **Conversation Storage**: Use persistent DAO (PostgreSQL, MongoDB, etc.)
5. **Monitoring**: Add logging and metrics

## Next Steps

1. **Extend Features**: Add more MCP servers (news, search, calendar)
2. **Customize UI**: Modify React components in aimo-ui
3. **Add Memory**: Implement long-term conversation memory
4. **Deploy**: Deploy to production with persistent storage
5. **Fine-tune**: Create custom Ollama model for your domain

## See Also

- [Weather MCP Server](../mcp-server-weather/README.md)
- [aimo-mcp-server Documentation](../../aimo-mcp-server/README.md)
- [aimo-mcp-client Documentation](../../aimo-mcp-client/README.md)
- [AIMO Core Documentation](../../aimo-core/README.md)
- [Ollama Documentation](https://ollama.ai)

