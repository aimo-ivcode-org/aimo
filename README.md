# Aimo

Aimo is the Artificial Intelligence Model Orchestrator: a modular Kotlin/Spring project for building AI chat applications with conversation memory, tool-calling controllers, an Ollama-backed model adapter, and a React UI.

## What this repository contains

`aimo` is a multi-module Gradle workspace with reusable libraries plus a runnable example app:

| Module | Purpose |
| --- | --- |
| `aimo-core` | Core abstractions and runtime (`Aimo`, conversations, chat clients, model-facing prompt flow, tool/system-message annotations). |
| `aimo-model-ollama` | Ollama-backed Aimo model integration. |
| `aimo-session-cache-ehcache` | Ehcache-backed session cache implementation for retaining conversation session state in memory across requests during application runtime. |
| `aimo-server` | REST API layer for conversations, chat streaming, and history. |
| `aimo-plugin-ui` | UI-specific server plugin (title endpoints + title tool controller). |
| `aimo-ui` | React + Vite frontend packaged into resources for server distribution. |
| `examples/simple-ollama` | Runnable Spring Boot app that composes server + UI plugin + Ollama model module. |

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
  aimo-session-cache-ehcache/
  aimo-server/
  aimo-plugin-ui/
  aimo-ui/
  examples/simple-ollama/
```

## Notes

- If dependency resolution fails, check your network/repository access for the repositories configured in `settings.gradle.kts` and `build.gradle.kts`.
