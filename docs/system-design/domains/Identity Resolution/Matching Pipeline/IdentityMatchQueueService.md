---
tags:
  - component/active
  - layer/service
  - architecture/component
  - domain/identity-resolution
Created: 2026-03-19
Domains:
  - "[[Identity Resolution]]"
Sub-Domains:
  - "[[Matching Pipeline]]"
---

# IdentityMatchQueueService

## Purpose

Enqueues IDENTITY_MATCH jobs into the execution queue with deduplication via a partial unique index.

---

## Responsibilities

- Creating IDENTITY_MATCH execution queue entries for entities that need matching
- Deduplication — silently skipping enqueue when a PENDING job already exists for the same entity

## Dependencies

- [[ExecutionQueueRepository]] — queue item persistence
- KLogger — structured logging

## Used By

- [[IdentityMatchTriggerListener]] — enqueues after entity save events

---

## Key Logic

### enqueueIfNotPending

```kotlin
@Transactional
fun enqueueIfNotPending(entityId: UUID, workspaceId: UUID)
```

Inserts a new IDENTITY_MATCH queue item. Deduplication is enforced by the partial unique index `uq_execution_queue_pending_identity_match` on `(workspace_id, entity_id, job_type) WHERE status = 'PENDING'`.

When a duplicate insert violates this constraint, the resulting `DataIntegrityViolationException` is caught and inspected — only violations matching the dedup constraint name are swallowed. All other `DataIntegrityViolationException` causes are rethrown.

---

## Gotchas

- **DataIntegrityViolationException is expected** — this is the deduplication mechanism, not an error condition. The exception is caught and swallowed only when the constraint name matches the dedup index.
- **Constraint name matching** — the catch block checks for the specific constraint name `uq_execution_queue_pending_identity_match`. Other integrity violations propagate normally.

---

## Flows Involved

- [[Flow - Identity Match Pipeline]]

---

## Related

- [[Matching Pipeline]]
- [[IdentityMatchTriggerListener]]
- [[IdentityMatchDispatcherService]]
