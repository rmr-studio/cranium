---
tags:
  - architecture/subdomain
  - domain/workflow
Created: 2026-02-08
Domains:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
---
# Subdomain: Graph Coordination

## Overview

Graph Coordination implements pure graph algorithms for DAG execution. WorkflowGraphValidationService validates the DAG structure before execution (cycle detection, edge validation, connectivity checks). WorkflowGraphTopologicalSorterService computes the execution order using Kahn's algorithm. WorkflowGraphQueueManagementService tracks node dependencies via in-degree maps, maintains a ready queue of nodes with in-degree zero, and provides the pull-based scheduling interface for the execution loop.

## Components

| Component | Purpose | Type |
| --------- | ------- | ------------------------------- |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Graph Coordination/WorkflowGraphValidationService]] | Validates DAG before execution — cycle detection, edge validation, connectivity check | Service |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Graph Coordination/WorkflowGraphTopologicalSorterService]] | Computes execution order using Kahn's algorithm | Service |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Graph Coordination/WorkflowGraphQueueManagementService]] | Tracks in-degree map, ready queue, adjacency list — determines which nodes are ready to execute | Service |

> [!warning] WorkflowGraphQueueManagementService holds mutable state
> This service maintains in-memory state (in-degree map, ready queue, adjacency list) and is not thread-safe. It must be instantiated per workflow execution, not shared.

> [!warning] No loops, switch/case, parallel branches, or sub-workflow composition
> The DAG model only supports simple dependency-based execution. Control flow is limited to binary conditions via WorkflowConditionControlConfig.

## Technical Debt

|Issue|Impact|Effort|
|---|---|---|
| Mutable state not thread-safe | Medium | Medium |
| Limited control flow constructs | High | High |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
|      |        | [[]]        |
