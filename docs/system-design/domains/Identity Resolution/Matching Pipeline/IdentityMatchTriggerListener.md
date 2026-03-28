---
tags:
  - component/active
  - layer/listener
  - architecture/component
  - domain/identity-resolution
Created: 2026-03-19
Domains:
  - "[[Identity Resolution]]"
Sub-Domains:
  - "[[Matching Pipeline]]"
---

# IdentityMatchTriggerListener

## Purpose

Listens for IdentityMatchTriggerEvent after entity saves and decides whether to enqueue identity matching based on IDENTIFIER attribute presence and changes.

---

## Responsibilities

- Consuming IdentityMatchTriggerEvent published by EntityService
- Determining whether the saved entity warrants identity matching
- Delegating enqueue to IdentityMatchQueueService when matching is needed

## Dependencies

- [[EntityTypeClassificationService]] — checks if entity type has IDENTIFIER attributes
- [[IdentityMatchQueueService]] — enqueues IDENTITY_MATCH jobs
- KLogger — structured logging

## Used By

- Spring event system — EntityService publishes IdentityMatchTriggerEvent after entity saves

---

## Key Logic

### onEntitySaved

```kotlin
@TransactionalEventListener(phase = AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
fun onEntitySaved(event: IdentityMatchTriggerEvent)
```

Decision logic:

1. **No IDENTIFIER attributes** — skip. The entity type has no attributes classified as IDENTIFIER, so identity matching is not applicable.
2. **Create operation** — always enqueue if the entity type has IDENTIFIER attributes, even if the identifier values are currently empty.
3. **Update operation** — enqueue only if at least one IDENTIFIER attribute value changed. Skips enqueue if only non-identifier fields were modified.

### Transaction Context

`@TransactionalEventListener(phase = AFTER_COMMIT)` fires after the originating transaction commits, at which point there is no active transaction. `@Transactional(propagation = Propagation.REQUIRES_NEW)` opens a new transaction for the enqueue operation.

---

## Gotchas

- **REQUIRES_NEW is mandatory** — AFTER_COMMIT listeners run outside the original transaction. Without REQUIRES_NEW, the enqueue would fail due to no active transaction.
- **Package placement** — lives in `service.entity`, not `service.identity`, because it consumes entity domain events. This follows the same pattern as WorkspaceAnalyticsListener in `service.analytics`.
- **Create always enqueues** — even with empty identifier values, a create triggers matching. This ensures that if identifier values are populated later via a separate update, the initial match attempt has already been recorded.

---

## Flows Involved

- [[Flow - Identity Match Pipeline]]

---

## Related

- [[Matching Pipeline]]
- [[EntityTypeClassificationService]]
- [[IdentityMatchQueueService]]
