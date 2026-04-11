---
tags:
  - layer/utility
  - component/active
  - architecture/component
Created: 2026-02-08
Updated: 2026-02-08
Domains:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
---
# WorkflowErrorClassifier

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Execution Engine/Execution Engine]]

## Purpose

Utility object for classifying workflow execution errors into retryable vs. non-retryable types, determining Temporal retry behavior based on exception type and node context.

---

## Responsibilities

- Classify exceptions into `WorkflowErrorType` categories
- Distinguish retryable errors (5xx, network, transient) from non-retryable (4xx, validation, security)
- Handle CONTROL_FLOW node special case (deterministic evaluation, always non-retryable)
- Format error messages with context
- Support Temporal retry policy matching via error type names

---

## Dependencies

None (stateless utility object, no Spring injection needed)

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Execution Engine/WorkflowCoordinationService]] — Classifies exceptions during node execution

---

## Key Logic

**Classification rules (order matters):**

| Exception Type | Node Type | Error Type | Retryable | Reason |
|---|---|---|---|---|
| `WebClientResponseException` (4xx) | Any | `HTTP_CLIENT_ERROR` | No | Client error, data won't change |
| `WebClientResponseException` (5xx) | Any | `HTTP_SERVER_ERROR` | Yes | Server issue, likely transient |
| Any | `CONTROL_FLOW` | `CONTROL_FLOW_ERROR` | No | Deterministic evaluation |
| `IllegalArgumentException`, `SchemaValidationException` | Any | `VALIDATION_ERROR` | No | Invalid input/config |
| `SecurityException` | Any | `SECURITY_ERROR` | No | Permission denied |
| All others | Any | `EXECUTION_ERROR` | Yes | Assume transient |

**Node type checked before exception type:** CONTROL_FLOW context takes precedence. A 500 error in a CONTROL_FLOW node is still `CONTROL_FLOW_ERROR` (non-retryable) because condition evaluation must be deterministic.

---

## Public Methods

### `classifyError(e: Exception, nodeType: WorkflowNodeType): WorkflowErrorType`

Classifies exception into error type based on exception class and node type.

### `classifyErrorWithMessage(e: Exception, nodeType: WorkflowNodeType): Pair<WorkflowErrorType, String>`

Classifies exception and returns both error type and formatted message with context (e.g., "HTTP 404: Resource not found").

---

## Gotchas

- **Singleton object, not a service:** Kotlin `object` (not `class`) for direct function calls without Spring injection. Easier to test and use from non-Spring contexts.
- **Error type names match Temporal doNotRetry list:** `WorkflowOrchestrationService` configures Temporal retry options with `doNotRetry = listOf("HTTP_CLIENT_ERROR", "VALIDATION_ERROR", ...)`. Must use exact error type names.
- **CONTROL_FLOW determinism:** Control flow nodes (conditions, switches) must produce the same result on replay. If an HTTP call is used in condition logic and returns 500, it's classified as `CONTROL_FLOW_ERROR` (non-retryable) to prevent non-deterministic workflow history.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Execution Engine/WorkflowCoordinationService]] — Primary consumer
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Execution Engine/WorkflowOrchestrationService]] — Configures Temporal retry policy using these error types
