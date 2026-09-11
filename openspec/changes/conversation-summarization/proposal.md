# Conversation Summarization — Proposal

What: Replace the current `NO_OP` prompt-budgeter behavior with conversation summarization that persists summaries as synthetic request-level history and reduces prompt size by summarizing older requests, while keeping the rolling-window path intact.

Why: The current source stores history as request bundles, and message IDs are only unique within a request. Summaries therefore need request-level boundaries to stay replayable and deterministic while still reducing prompt size.

Goals
- Persist summaries as synthetic conversation requests/messages with request-boundary metadata.
- Mark original requests/messages as summarized (retain originals).
- When building prompts, include the latest summary plus requests after it only.
- Summarization performed asynchronously (non-blocking) and written transactionally.
- Work with the existing conversation storage abstractions, including the file and memory DAO implementations already in the repo.
- Add explicit runtime/config wiring for replacing `NO_OP` with summarization and configuring its thresholds.

Acceptance Criteria
- Conversation storage supports summary message fields, summarized flag, and request-boundary references across supported DAO implementations.
- Summaries are persisted and referenced by replacesStartRequestId/replacesEndRequestId.
- Prompt builder retrieves summary + subsequent requests only.
- Runtime wiring cleanly connects the chat client, conversation access, and summarization trigger path.
- Configuration exposes summarizer settings in bound properties and routes the `NO_OP` budgeter selection to summarization behavior.
- Tests covering summarizer, DAO writes, and prompt-size reduction.
- Rollout/migration plan included.