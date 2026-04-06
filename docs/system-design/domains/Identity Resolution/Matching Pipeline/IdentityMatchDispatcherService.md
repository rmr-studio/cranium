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

# IdentityMatchDispatcherService

## Purpose

Scheduled dispatcher for the IDENTITY_MATCH execution queue, using ShedLock for distributed locking to ensure single-instance processing across replicas.

---

## Responsibilities

- Polling the execution queue on a fixed schedule (every 5s) and delegating item processing
- Recovering stale items stuck in CLAIMED state (every 60s)
- Distributed lock coordination via ShedLock to prevent duplicate processing across instances

## Dependencies

- [[IdentityMatchQueueProcessorService]] — batch claiming and per-item processing
- [[ExecutionQueueRepository]] — stale item recovery queries
- [[WorkflowExecutionQueueService]] — releasing stale items back to PENDING
- KLogger — structured logging

## Used By

- Spring scheduler — both methods are triggered by `@Scheduled` annotations, not called by other services

---

## Key Logic

### processQueue (every 5s)

ShedLock name: `processIdentityMatchQueue` (distinct from the workflow dispatcher's lock).

Claims a batch of 10 items via `processorService.claimBatch(10)`, then iterates and calls `processorService.processItem()` for each. No `@Transactional` on the dispatcher — each item gets its own transaction via the processor's `REQUIRES_NEW`.

### recoverStaleItems (every 60s)

ShedLock name: `recoverStaleIdentityMatchItems`.

Finds items stuck in CLAIMED state beyond a staleness threshold and releases them back to PENDING. This handles cases where a processor crashed mid-batch without completing item processing.

---

## Gotchas

- **No @Transactional on dispatcher methods** — transaction management is intentionally delegated to the processor service. Adding @Transactional here would defeat the per-item isolation.
- **ShedLock names must be unique** — `processIdentityMatchQueue` and `recoverStaleIdentityMatchItems` are distinct from the workflow domain's dispatcher locks.
- **Batch size is hardcoded to 10** — not configurable via properties.

---

## Flows Involved

- [[Flow - Identity Match Pipeline]]

---

## Related

- [[Matching Pipeline]]
- [[IdentityMatchQueueProcessorService]]
