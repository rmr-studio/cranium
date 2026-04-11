---
tags:
  - architecture/subdomain
  - domain/workflow
Created: 2026-02-08
Domains:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
---
# Subdomain: State Management

## Overview

State Management handles data flow between nodes during workflow execution. WorkflowNodeTemplateParserService parses `{{ }}` template expressions and extracts path segments. WorkflowNodeInputResolverService resolves those templates against the execution data registry using recursive map/list traversal. WorkflowNodeExpressionParserService and WorkflowNodeExpressionEvaluatorService handle conditional expressions for control flow nodes. EntityContextService loads entity data into the execution context so nodes can access entity information.

## Components

| Component | Purpose | Type |
| --------- | ------- | ------------------------------- |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/State Management/WorkflowNodeInputResolverService]] | Resolves template expressions against execution data registry — recursive map/list traversal | Service |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/State Management/WorkflowNodeTemplateParserService]] | Parses `{{ }}` template syntax, extracts path segments | Service |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/State Management/WorkflowNodeExpressionParserService]] | Parses conditional expressions for control flow nodes | Service |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/State Management/WorkflowNodeExpressionEvaluatorService]] | Evaluates parsed expressions against runtime data | Service |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/State Management/EntityContextService]] | Loads entity data into workflow execution context for node access | Service |

> [!warning] Template-only expressions
> The template system supports simple path-based value extraction (`{{ steps.node.output.field }}`) but does not support complex transformations, array indexing, or arithmetic operations.

> [!warning] Missing payload mapping in WorkflowCreateEntityActionConfig
> There is a TODO comment in WorkflowCreateEntityActionConfig.kt:79 indicating that payload mapping for entity creation is incomplete.

## Technical Debt

|Issue|Impact|Effort|
|---|---|---|
| No complex transformations in templates | Medium | Medium |
| No array indexing in templates | Medium | Medium |
| Missing payload mapping | High | Medium |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
|      |        | [[]]        |
