---
tags:
  - architecture/subdomain
  - domain/workflow
Created: 2026-02-08
Domains:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
---
# Subdomain: Definition Management

## Overview

Definition Management provides CRUD operations for workflow definitions, graph structure (nodes and edges), and execution records. These are thin service wrappers over repositories with workspace scoping and authorization checks. WorkflowDefinitionService handles workflow metadata. WorkflowGraphService manages node and edge operations. WorkflowExecutionService handles execution lifecycle (starting, querying, status updates).

## Components

| Component | Purpose | Type |
| --------- | ------- | ------------------------------- |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Definition Management/WorkflowDefinitionService]] | Workflow definition CRUD — create, update, delete, query definitions with workspace scoping | Service |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Definition Management/WorkflowGraphService]] | Graph node and edge management — add/remove/update nodes and edges within a workflow definition | Service |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Definition Management/WorkflowExecutionService]] | Execution lifecycle — start execution (enqueues), query execution status and history | Service |

## Technical Debt

|Issue|Impact|Effort|
|---|---|---|
| None identified | N/A | N/A |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
|      |        | [[]]        |
