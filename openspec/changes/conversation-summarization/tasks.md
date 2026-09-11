## Implementation Tasks — Conversation Summarization

- [ ] Storage schema updates
  - [ ] Extend `ChatMessageEntity` and related model/transformer code with:
    - `isSummary`
    - `summarized`
    - `replacesStartRequestId`
    - `replacesEndRequestId`
    - `summaryModel`
    - `summaryTokenCount`
  - [ ] Update the in-memory DAO to preserve the new summary metadata on write/read.
  - [ ] Update the file DAO serialization to preserve the new summary metadata on write/read.
  - [ ] Document equivalent migration expectations for external SQL-backed DAO implementations.

- [ ] DAO changes
  - [ ] Add DAO methods:
    - [ ] `insertSummaryMessage(conversationId, summaryMessage, replacesStartRequestId, replacesEndRequestId, metadata)`
    - [ ] `markRequestsSummarized(conversationId, startRequestId, endRequestId)`
    - [ ] `getLatestSummary(conversationId): returns latest summary row or null`
    - [ ] `getMessagesAfterRequestId(conversationId, requestId): returns messages from requests created after the summary cursor`
  - [ ] Define atomic write expectations for each store implementation when writing summaries and summarized flags.

- [ ] Summarizer worker
  - [ ] Implement Summarizer service (background executor):
    - [ ] queue job API: `enqueueSummarize(chatId)`
    - [ ] worker picks job, performs request-level selection, calls model provider, and writes summary + replaced-request updates atomically for the active store
  - [ ] Add configuration and a small in-memory queue with clear failure handling.

- [ ] Runtime integration seam
  - [ ] Add an explicit seam between `AimoChatClientImpl`, `Conversation`, and summarization-aware history loading.
  - [ ] Decide and implement whether summarization extends `PromptBudgeter` or is coordinated by a separate summarization collaborator.
  - [ ] Preserve existing `CONTEXT_WINDOW` behavior and replace the current `NO_OP` path with summarization-aware behavior.

- [ ] `NO_OP` replacement behavior
  - [ ] Route `AimoPromptBudgeterType.NO_OP` to summarization-aware history handling.
  - [ ] Implement effective-history selection for the `NO_OP` summarization path.
  - [ ] Keep rolling-window implementation available behind config if temporarily needed.

- [ ] AimoChatClient changes
  - [ ] Resolve effective history through the new runtime seam before each model call.
  - [ ] After assistant response persisted, call `enqueueSummarize(chatId)` non-blocking through the chosen summarization collaborator.

- [ ] Tests
  - [ ] Unit tests for DAO methods and prompt selection logic
  - [ ] Unit tests for Summarizer (mock model provider) ensuring atomic writes and marked messages
  - [ ] DAO regression tests for file and memory implementations with summary metadata
  - [ ] Integration test: run `examples/simple-ollama` with summarizer enabled and assert prompt size reduces and only summary + recent messages are sent to model

- [ ] Monitoring & Metrics
  - [ ] Emit metrics: `summariesCreated`, `messagesSummarized`, `summarizationFailures`, `averageSummaryTokenCount`
  - [ ] Add logging for summarizer job lifecycle

- [ ] Documentation and config
  - [ ] Add docs in `docs/` about prompt summarization behavior, configuration keys, and how to audit summaries
  - [ ] Add bound properties under `aimo.prompt-budgets.summarizer` for enablement, thresholds, target size, and concurrency
  - [ ] Extend provider-specific model context properties/mappers so existing `NO_OP` selection activates summarization-aware behavior

- [ ] Rollout Plan
  - [ ] Merge storage-schema changes behind feature-flaged code that does not enable summarizer by default
  - [ ] Deploy with summarizer disabled; run any store-specific migrations if needed
  - [ ] Enable summarizer in staging and monitor metrics
  - [ ] Gradually enable in production per environment

Estimated effort: 3–6 days depending on store-update testing and integration test time.

Notes
- Keep original messages to allow full conversation replay and debugging.
- Use request boundaries, not request-local message IDs, to ensure precise summary ranges.
- The current source only binds `context.size` and `context.excludeThinking`; summarizer settings and `NO_OP` replacement wiring need explicit property binding work.
