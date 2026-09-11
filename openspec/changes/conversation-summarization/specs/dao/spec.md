## ADDED Requirements

### Requirement: Persisted conversation summaries
The system MUST persist summarization output as a synthetic conversation request that references the contiguous range of original requests it replaces. The system MUST retain the original requests and messages and mark them as summarized. The system MUST NOT rely on request-local message IDs as replay boundaries.

#### Scenario: Summary message is stored with replacement metadata
- **WHEN** the summarizer processes a range of existing conversation requests
- **THEN** the system stores one summary request with references to the replaced start and end request IDs
- **THEN** the system marks each replaced original request as summarized

#### Scenario: Original messages remain available for replay
- **WHEN** a user requests the full conversation transcript
- **THEN** the system returns both the summary request and the original requests that were summarized

### Requirement: Supported conversation stores preserve summary metadata
The system MUST preserve conversation summary metadata through the configured conversation store abstraction. Supported in-repo DAO implementations MUST be able to persist and reload summary metadata without losing replay boundaries or summarized state.

#### Scenario: File-backed conversation store reloads summary metadata
- **WHEN** a conversation summary is persisted through the file-backed DAO and the conversation is loaded again
- **THEN** the system reloads the summary metadata and summarized state unchanged

#### Scenario: In-memory conversation store retains summary metadata
- **WHEN** a conversation summary is persisted through the in-memory DAO during a running session
- **THEN** the system returns the same summary metadata and summarized state on subsequent reads

### Requirement: Asynchronous summarization and transactional writes
The system MUST trigger summarization asynchronously so chat response generation is not blocked. When a summary is created, the summary request/message and summarized flags for the replaced requests MUST be written atomically for the active conversation store implementation.

#### Scenario: Summarization runs after the response is persisted
- **WHEN** a conversation exceeds the configured summarization threshold
- **THEN** the system enqueues summarization without delaying the assistant response

#### Scenario: Summary writes are atomic
- **WHEN** the summarizer creates a new summary
- **THEN** the system commits the summary request/message and the summarized flags together
- **THEN** the system does not persist a partial summary state if the transaction fails
