---
tags:
  - architecture/subdomain
  - domain/workflow
Created: 2026-02-08
Domains:
  - "[[Workflows]]"
---
# Subdomain: Execution Engine

## Overview

The Execution Engine subdomain integrates with Temporal.io to provide durable workflow orchestration. WorkflowOrchestrationService is the deterministic Temporal workflow that orchestrates the DAG execution lifecycle. WorkflowCoordinationService is a Spring-managed Temporal activity that performs all non-deterministic operations (database access, node execution, state persistence). WorkflowGraphCoordinationService runs the DAG execution loop using pull-based scheduling to maximize parallelism. WorkflowCompletionActivityImpl handles final status recording and queue cleanup.

## Components

| Component | Purpose | Type |
| --------- | ------- | ------------------------------- |
| [[WorkflowOrchestrationService]] | Temporal workflow — orchestrates DAG execution lifecycle, delegates all I/O to activities via `sideEffect()` for config snapshot | Temporal Workflow |
| [[WorkflowCoordinationService]] | Temporal activity — coordinates node execution, manages graph state, persists results. Entry point for all non-deterministic work. | Temporal Activity / Service |
| [[WorkflowGraphCoordinationService]] | DAG execution loop — validates graph, sorts topologically, iterates ready nodes in batches | Service |
| [[WorkflowCompletionActivityImpl]] | Records final workflow execution status, updates/deletes queue entries | Temporal Activity / Service |
| [[TemporalWorkerConfiguration]] | Registers Temporal workers for multiple task queues — default workflows, external API activities, and identity matching | Configuration |

> [!warning] Sequential batch execution
> Nodes in the same batch execute sequentially, not truly parallel. This is a WorkflowCoordinationService limitation marked as TODO in the code.

> [!warning] Single activity for all nodes
> The entire coordination runs in one activity call. This simplifies the implementation but limits granularity of retry and error handling.

## Technical Debt

|Issue|Impact|Effort|
|---|---|---|
| Sequential batch execution instead of parallel | Medium | High |
| Single monolithic activity call | Low | Medium |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-03-17 | TemporalWorkerConfiguration now registers identity match worker on dedicated identity.match task queue | Identity Resolution |
