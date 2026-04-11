---
Created:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
  - architecture/subdomain
Updated: 2026-02-09
---
# Control Flow Nodes

## Overview

Control flow nodes manage workflow execution branching, looping, parallelization, and timing. They determine which nodes execute next based on runtime conditions and orchestrate complex execution patterns.

Each control flow node implements `WorkflowControlConfig` with a specific `WorkflowControlType` subtype.

> [!warning] Limited control flow implementation
> Only the CONDITION control type is implemented. Advanced control flow patterns (SWITCH, LOOP, PARALLEL, DELAY, MERGE) are defined in the type system but have no implementations.

## Implemented Control Flow Nodes

| Node | Description | Status |
|------|-------------|--------|
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Controls/WorkflowConditionControlConfig]] | Branches workflow based on a boolean condition | Implemented |

## Unimplemented Control Types

The following control flow types are defined in `WorkflowControlType` enum but have no concrete implementations:

- **SWITCH** — Would support multi-way branching based on value matching
- **LOOP** — Would enable iteration over collections or conditional repetition
- **PARALLEL** — Would execute multiple branches concurrently
- **DELAY** — Would pause execution for a specified duration
- **MERGE** — Would synchronize multiple parallel branches before continuing
