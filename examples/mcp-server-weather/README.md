# Weather MCP Server Example

A standalone MCP server providing weather information tools and prompts.

## Overview

This is an example of building a complete MCP server using the `aimo-mcp-server` framework. It exposes several weather-related tools and prompts that can be called by MCP clients.

## Features

### Tools

1. **get-weather** - Get current weather conditions for a city
   - Parameters: `city` (required), `includeForecast` (optional boolean)
   - Returns: Current temperature, conditions, and optionally forecast

2. **get-weather-alert** - Check for weather alerts in a city
   - Parameters: `city` (required)
   - Returns: Active weather alerts or safety status

3. **compare-weather** - Compare weather between two cities
   - Parameters: `city1`, `city2` (both required)
   - Returns: Comparison of conditions and warmest city

4. **get-weather-batch** - Get weather for multiple cities at once
   - Parameters: `cities` (comma-separated list)
   - Returns: Weather for all requested cities

### Prompts

1. **weather-help** - Get help on available weather tools
2. **forecast-explanation** - Explain weather forecast terminology
3. **weather-analysis** - Template for weather analysis

## Building

```bash
cd aimo/examples/mcp-server-weather
../../gradlew.bat build
```

## Running

```bash
../../gradlew.bat bootRun
```

The server will start on **http://localhost:8080**

## Testing

### List Available Tools

```bash
curl -X POST http://localhost:8080/mcp/ \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/list"
  }'
```

### Get Weather for a City

```bash
curl -X POST http://localhost:8080/mcp/ \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/call",
    "params": {
      "name": "get-weather",
      "arguments": {
        "city": "Seattle"
      }
    }
  }'
```

### Get Weather with Forecast

```bash
curl -X POST http://localhost:8080/mcp/ \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "get-weather",
      "arguments": {
        "city": "New York",
        "includeForecast": true
      }
    }
  }'
```

### Get Weather for Multiple Cities

```bash
curl -X POST http://localhost:8080/mcp/ \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 4,
    "method": "tools/call",
    "params": {
      "name": "get-weather-batch",
      "arguments": {
        "cities": "Seattle, San Francisco, New York"
      }
    }
  }'
```

### List Available Prompts

```bash
curl -X POST http://localhost:8080/mcp/ \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 5,
    "method": "prompts/list"
  }'
```

### Get a Prompt

```bash
curl -X POST http://localhost:8080/mcp/ \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 6,
    "method": "prompts/get",
    "params": {
      "name": "weather-help"
    }
  }'
```

## Supported Cities

- Seattle
- San Francisco
- New York
- Los Angeles
- Chicago

## Architecture

```
WeatherMcpServerApplication
  └── WeatherService (@McpService)
      ├── get-weather (@McpTool)
      ├── get-weather-alert (@McpTool)
      ├── compare-weather (@McpTool)
      ├── get-weather-batch (@McpTool)
      ├── weather-help (@McpPrompt)
      ├── forecast-explanation (@McpPrompt)
      └── weather-analysis (@McpPrompt)
```

## Integration with aimo-mcp-client

See the `mcp-client-weather` example for how to connect this server to an AIMO client with Ollama.

Configuration:
```yaml
aimo:
  mcp:
    servers:
      - id: "weather"
        transport: "http"
        url: "http://localhost:8080/mcp"
```

## API Details

### Endpoints

- `POST /mcp/` - Generic JSON-RPC endpoint
- `POST /mcp/tools/list` - List available tools
- `POST /mcp/tools/call` - Call a tool
- `POST /mcp/prompts/list` - List available prompts
- `POST /mcp/prompts/get` - Get a prompt

### Request Format

All requests use JSON-RPC 2.0 format:
```json
{
  "jsonrpc": "2.0",
  "id": <request-id>,
  "method": "<method-name>",
  "params": {
    ...
  }
}
```

### Response Format

Successful response:
```json
{
  "jsonrpc": "2.0",
  "id": <request-id>,
  "result": { ... }
}
```

Error response:
```json
{
  "jsonrpc": "2.0",
  "id": <request-id>,
  "error": {
    "code": <error-code>,
    "message": "<error-message>"
  }
}
```

## Logs

Enable debug logging in `application.yml`:
```yaml
logging:
  level:
    org.ivcode.aimo.server.mcp: DEBUG
```

## Next Steps

1. **Enhance Data**: Replace simulated weather data with real API calls (e.g., OpenWeatherMap)
2. **Add Features**: Add more tools (historical weather, radar, air quality)
3. **Expand Transports**: Enable SSE or stdio transport
4. **Error Handling**: Add validation and better error messages

## See Also

- [aimo-mcp-server Framework Documentation](../../aimo-mcp-server/README.md)
- [Weather MCP Client Example](../mcp-client-weather/README.md)

