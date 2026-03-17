---
tags:
  - architecture/subdomain
  - domain/workflow
Created: 2026-02-08
Domains:
  - "[[Workflows]]"
---
# Subdomain: Queue Management

## Overview

Queue Management provides asynchronous decoupling between API execution requests and Temporal workflow dispatch. Execution requests are enqueued as PENDING items, then a scheduled dispatcher (running every 5 seconds with ShedLock for distributed coordination) claims batches and processes them. Each item is processed in an isolated transaction to ensure partial failures don't rollback the entire batch. Capacity checks are performed before dispatch to enforce workspace tier limits.

The queue now supports multiple job types via a `job_type` discriminator on ExecutionQueueEntity — currently WORKFLOW_EXECUTION (original) and IDENTITY_MATCH (identity resolution pipeline). Each job type has its own dispatcher and processor but shares the same queue table and state machine.

## Components

| Component | Purpose | Type |
| --------- | ------- | ------------------------------- |
| [[WorkflowExecutionQueueService]] | Queue state CRUD — enqueue, claim, dispatch, fail, release, recover stale items | Service |
| [[WorkflowExecutionDispatcherService]] | Scheduled job (every 5s with ShedLock) — polls queue and delegates to processor. Handles WORKFLOW_EXECUTION jobs (identity match jobs have their own dispatcher in Identity Resolution domain) | Service |
| [[WorkflowExecutionQueueProcessorService]] | Processes individual WORKFLOW_EXECUTION queue items — capacity check, execution record creation, Temporal dispatch. Uses `REQUIRES_NEW` transactions. | Service |

> [!warning] Fixed batch size
> The dispatcher uses a fixed batch size (10 items) that is not adaptive to current load or pod capacity.

> [!warning] No rate limiting per workspace
> Fair-use enforcement relies on coarse capacity checks. There's no per-workspace rate limiting to prevent abuse.

## Technical Debt

|Issue|Impact|Effort|
|---|---|---|
| Fixed batch size not adaptive to load | Low | Low |
| No per-workspace rate limiting | Medium | Medium |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-03-17 | ExecutionQueueEntity genericized with job_type discriminator for multi-job-type support | Identity Resolution |
