> ⚠️ **Active Development Notice**
>
> This project is functional and in a working state, but is currently undergoing **heavy development and serious refactoring** toward a 1.0 release. APIs, module structure, and core architecture are subject to significant change.
>

# Aimo
[![Aimo Build](https://github.com/aimo-ivcode-org/aimo/actions/workflows/build.yml/badge.svg)](https://github.com/aimo-ivcode-org/aimo/actions/workflows/build.yml)

**Aimo**, the Artificial Intelligence Model Orchestrator, is a lightweight JVM framework for building AI chatbots, assistants, and agentic workflows. It gives you a clean, Spring-compatible way to add conversational AI to your application with minimal friction.

Our mission is to make building LLM-powered systems simple, fast, and reliable, from your first tool call to fully autonomous agents.

We aim to provide:

- A simple, predictable, and flexible interface that works consistently across multiple LLM providers.
- Powerful tools to develop, test, debug, and interact with LLM-powered systems.
- Clear documentation and practical examples that scale from simple applications, such as LLMs with tool integration, to complex agentic systems capable of identifying problems and taking action autonomously.

### Modules

| Module | Summary | Docs | Coverage |
| --- | --- | --- | --- |
| [Aimo Core](aimo-core/) | Core runtime and abstractions for conversations and chat orchestration. | [![Javadoc](https://aimo-ivcode-org.github.io/aimo/aimo-core/javadoc/badge.svg)](https://aimo-ivcode-org.github.io/aimo/aimo-core/javadoc/) [![KDoc](https://aimo-ivcode-org.github.io/aimo/aimo-core/html/badge.svg)](https://aimo-ivcode-org.github.io/aimo/aimo-core/html/) | [![Jacoco](https://aimo-ivcode-org.github.io/aimo/aimo-core/jacoco/badge.svg)](https://aimo-ivcode-org.github.io/aimo/aimo-core/jacoco/) |
| [Aimo Model Bedrock](aimo-model-bedrock/) | Model provider adapter for AWS Bedrock. | [![Javadoc](https://aimo-ivcode-org.github.io/aimo/aimo-model-bedrock/javadoc/badge.svg)](https://aimo-ivcode-org.github.io/aimo/aimo-model-bedrock/javadoc/) [![KDoc](https://aimo-ivcode-org.github.io/aimo/aimo-model-bedrock/html/badge.svg)](https://aimo-ivcode-org.github.io/aimo/aimo-model-bedrock/html/) | [![Jacoco](https://aimo-ivcode-org.github.io/aimo/aimo-model-bedrock/jacoco/badge.svg)](https://aimo-ivcode-org.github.io/aimo/aimo-model-bedrock/jacoco/) |
| [Aimo Model Ollama](aimo-model-ollama/) | Model provider adapter for Ollama. | [![Javadoc](https://aimo-ivcode-org.github.io/aimo/aimo-model-ollama/javadoc/badge.svg)](https://aimo-ivcode-org.github.io/aimo/aimo-model-ollama/javadoc/) [![KDoc](https://aimo-ivcode-org.github.io/aimo/aimo-model-ollama/html/badge.svg)](https://aimo-ivcode-org.github.io/aimo/aimo-model-ollama/html/) | [![Jacoco](https://aimo-ivcode-org.github.io/aimo/aimo-model-ollama/jacoco/badge.svg)](https://aimo-ivcode-org.github.io/aimo/aimo-model-ollama/jacoco/) |
| [Aimo Server](aimo-server/) | REST API server exposing `/aimo-api` endpoints and streaming chat. | [![Javadoc](https://aimo-ivcode-org.github.io/aimo/aimo-server/javadoc/badge.svg)](https://aimo-ivcode-org.github.io/aimo/aimo-server/javadoc/) [![KDoc](https://aimo-ivcode-org.github.io/aimo/aimo-server/html/badge.svg)](https://aimo-ivcode-org.github.io/aimo/aimo-server/html/) | [![Jacoco](https://aimo-ivcode-org.github.io/aimo/aimo-server/jacoco/badge.svg)](https://aimo-ivcode-org.github.io/aimo/aimo-server/jacoco/) |
| [Aimo Plugin UI](aimo-plugin-ui/) | Server-side UI plugin providing title endpoints and UI integration. | [![Javadoc](https://aimo-ivcode-org.github.io/aimo/aimo-plugin-ui/javadoc/badge.svg)](https://aimo-ivcode-org.github.io/aimo/aimo-plugin-ui/javadoc/) [![KDoc](https://aimo-ivcode-org.github.io/aimo/aimo-plugin-ui/html/badge.svg)](https://aimo-ivcode-org.github.io/aimo/aimo-plugin-ui/html/) | |
| [Aimo MCP Client](aimo-mcp-client/) | MCP client that discovers tools and prompts from Model Context Protocol servers. | [![Javadoc](https://aimo-ivcode-org.github.io/aimo/aimo-mcp-client/javadoc/badge.svg)](https://aimo-ivcode-org.github.io/aimo/aimo-mcp-client/javadoc/) [![KDoc](https://aimo-ivcode-org.github.io/aimo/aimo-mcp-client/html/badge.svg)](https://aimo-ivcode-org.github.io/aimo/aimo-mcp-client/html/) | [![Jacoco](https://aimo-ivcode-org.github.io/aimo/aimo-mcp-client/jacoco/badge.svg)](https://aimo-ivcode-org.github.io/aimo/aimo-mcp-client/jacoco/) |
| [Aimo MCP Server](aimo-mcp-server/) | Library to host MCP tools and prompts. | [![Javadoc](https://aimo-ivcode-org.github.io/aimo/aimo-mcp-server/javadoc/badge.svg)](https://aimo-ivcode-org.github.io/aimo/aimo-mcp-server/javadoc/) [![KDoc](https://aimo-ivcode-org.github.io/aimo/aimo-mcp-server/html/badge.svg)](https://aimo-ivcode-org.github.io/aimo/aimo-mcp-server/html/) | [![Jacoco](https://aimo-ivcode-org.github.io/aimo/aimo-mcp-server/jacoco/badge.svg)](https://aimo-ivcode-org.github.io/aimo/aimo-mcp-server/jacoco/) |


## Examples

Runnable example apps live under `examples/`. Each composes the `aimo-server` with selected modules — use the Gradle wrapper to run them locally.

### Simple Ollama Example

  - Description: Composed demo that runs `aimo-server` + `aimo-plugin-ui` + the Ollama model adapter.
  - Requirements: Ollama must be running locally and the configured model pulled (see `Prerequisites`).
  - Run:

    ```bash
    ./gradlew.bat :examples:simple-ollama:bootRun
    ```
  - Access: Open your browser to [http://localhost:8080](http://localhost:8080) to access the UI.
