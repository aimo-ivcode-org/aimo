## ADDED Requirements

### Requirement: Effective prompt history selection
The system MUST build prompt input from the latest persisted summary for a conversation, followed by only the messages from requests created after that summary's replaced range. If no summary exists, the system MUST use the full conversation history.

#### Scenario: Prompt uses summary plus newer messages
- **WHEN** a conversation has a stored summary and newer requests after the summarized range
- **THEN** the system provides the summary request and only the messages after the summary's end request ID to the model

#### Scenario: Prompt uses full history when no summary exists
- **WHEN** a conversation has no stored summary
- **THEN** the system provides the full message history unchanged

### Requirement: Asynchronous summarization workflow
The system MUST trigger summarization asynchronously so chat response generation is not blocked. The summarizer MUST select request ranges, generate the summary content, and hand off persistence as one workflow.

#### Scenario: Summarization runs after the response is persisted
- **WHEN** a conversation exceeds the configured summarization threshold
- **THEN** the system enqueues summarization without delaying the assistant response

#### Scenario: Summarizer prepares a summary for persistence
- **WHEN** the summarizer creates a new summary
- **THEN** the system produces summary content together with the request-boundary metadata needed for persistence
- **THEN** the system hands that result to the conversation store layer for atomic persistence

### Requirement: Configurable summarization threshold
The system MUST expose configuration for enabling summarization and for controlling when summarization is triggered.

#### Scenario: Summarization remains disabled by default
- **WHEN** the application starts with default configuration
- **THEN** the system does not enqueue summarization jobs automatically

#### Scenario: Threshold controls job creation
- **WHEN** a conversation exceeds the configured trigger threshold
- **THEN** the system schedules summarization for that conversation

### Requirement: No-op budgeter selection uses summarization
The system MUST replace the current `NO_OP` budgeter behavior with summarization-aware history handling while keeping the `CONTEXT_WINDOW` budgeter behavior unchanged.

#### Scenario: No-op selection uses summary-aware history
- **WHEN** a model is configured with the `NO_OP` budgeter
- **THEN** the system builds prompt input from the latest summary request plus later requests and enqueues summarization after persistence

#### Scenario: Context-window selection remains unchanged
- **WHEN** a model is configured with the `CONTEXT_WINDOW` budgeter
- **THEN** the system continues using the existing context-window truncation behavior

### Requirement: Bound configuration for summarization
The system MUST expose bound configuration for controlling summarizer behavior and for routing `NO_OP` budgeter selection to summarization.

#### Scenario: Model context selects no-op budgeter
- **WHEN** model configuration selects the `NO_OP` budgeter in bound application properties
- **THEN** the runtime builds the chat client with summarization-aware history behavior for that model

#### Scenario: Summarizer settings are loaded from configuration
- **WHEN** summarizer settings are defined in application properties
- **THEN** the runtime binds the enable flag, thresholds, and concurrency settings and uses them when deciding whether to enqueue summarization
