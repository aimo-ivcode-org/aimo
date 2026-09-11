Design: Conversation Summarization

Overview
- SummarizingPromptBudgeter compresses chat history by creating a persistent summary request that represents a contiguous range of older requests.
- Summaries are persisted chat message records with `isSummary=true` and metadata fields: replacesStartRequestId, replacesEndRequestId, model, tokenCount.
- Original requests/messages are retained and marked summarized=true; they remain in DAO for audit and full-replay.

Storage schema evolution
- Extend the conversation storage model (`ChatMessageEntity` and any persisted representation behind `AimoChatClientDao`) with:
  - `is_summary BOOLEAN DEFAULT FALSE`
  - `summarized BOOLEAN DEFAULT FALSE`
  - `replaces_start_request_id UUID NULL`
  - `replaces_end_request_id UUID NULL`
  - `summary_model VARCHAR NULL`
  - `summary_token_count INT NULL`
- Update the in-repo file and memory DAO implementations to read and write the new fields.
- For external SQL-backed DAO implementations, add equivalent migration/backfill steps in the storage technology they use.

Summarizer
- Asynchronous worker that:
  1. Identifies conversation chatIds exceeding token/window threshold.
  2. Selects a contiguous request range (oldest N requests or by time) to summarize.
  3. Calls the configured model with prompt template to produce a summary.
  4. In one atomic store update: insert summary request/message data, set summarized=true on replaced requests/messages, and update conversation properties (e.g., summary cursor or latest summary id).
- Summaries include metadata listing replaced request ids for precise replay.

Chat client integration seam
- The current `PromptBudgeter` interface only receives message lists and cannot access `Conversation` or `chatId`, while `AimoChatClientImpl` constructs the budgeter directly from model config.
- Introduce an explicit collaborator seam for summarization-aware history selection and post-persist triggering. Acceptable implementations include:
  - expanding the prompt-budgeting abstraction to support effective-history loading and post-persist hooks, or
  - keeping token budgeting separate and introducing a summarization coordinator that `AimoChatClientImpl` calls before model execution and after persistence.
- The chosen seam must preserve `CONTEXT_WINDOW` behavior unchanged and replace the current `NO_OP` path with summarization-aware behavior.

AimoChatClient Integration
- Before building model call input, resolve effective history through the new summarization-aware seam so the prompt contains system messages, the latest summary request when present, and only subsequent requests.
- After sending the assistant response, enqueue summarization asynchronously through the same seam so summarization does not block response persistence.

Transactional Guarantees
- All writes related to creating a summary and updating replaced requests/messages must be atomic for the active storage implementation.
- Summarizer should retry on transient store errors where the implementation supports it and must not lose original messages.

Edge cases & mitigations
- Hallucinated summaries: store model id + token counts; include short excerpts or message index mapping in summary metadata to allow manual inspection.
- Long-running summarization: run in background queue with backoff and monitoring.

Read path
- Effective history loading finds the latest summary for chatId (by stored request order / created_at) and returns it plus subsequent requests; if none, returns all messages unchanged.

Configuration
- Route the existing `NO_OP` budgeter selection to summarization-aware behavior while keeping the existing bound `size` and `excludeThinking` settings available.
- Bind summarizer settings (thresholds, summary target size, concurrency, and enable flag) from `application.yml` under aimo.prompt-budgets.summarizer.*.
- Extend provider-specific model property mappers (for example Ollama and Bedrock) so they can construct `AimoChatContext` with any needed summarization settings and preserve compatibility with the existing budgeter enum values.

Testing
- Unit tests for effective-history selection and atomic summary writes
- DAO tests covering file and memory implementations with summary metadata
- Integration test wiring async summarizer in examples/simple-ollama to validate prompt size reduction

Rollout
1. Land storage-schema updates and config wiring
2. Add summarization-aware runtime seam and replace the `NO_OP` path
3. Deploy with summarizer disabled by default, enable per environment
4. Gradually enable with monitoring of token reduction and summary quality
