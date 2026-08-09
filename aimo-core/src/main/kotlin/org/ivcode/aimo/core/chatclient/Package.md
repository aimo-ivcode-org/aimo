# Package org.ivcode.aimo.core.chatclient

Chat client API and implementations for the AIMO core.

This package contains the types, builders, implementations, and helpers used to manage
chat-related behavior inside AIMO's core orchestration. Typical responsibilities include:

- Managing multi-turn conversations (message lifecycle, sequencing, and persistence)
- Implementing the `AimoChatClient` interface and concrete clients such as
  `AimoChatClientImpl`
- Building and configuring clients via builders and factories
- Interceptors for logging, tracing and error handling
- Prompt budgeters and utilities for controlling context windows and prompt construction

What “chat” means in AIMO
----

In the context of this package, “chat” refers to a structured, multi-turn request/response
interaction between a client (typically a human user or an automated caller) and an
assistant (an LLM-backed responder). This is not intended to describe an autonomous
agent — the assistant responds to prompts and may produce instructions or tool-invocation
requests, but it does not act independently. Control, orchestration, and decision-making
remain with the runtime and application code that invoke and interpret the model’s outputs.

A chat is composed of an ordered sequence of messages (system, user, assistant, and tool
outputs), associated metadata (timestamps, message ids, authorship), and durable
conversation-level properties (titles, scopes, and other annotations).

