---
Created:
  - "[[Workflows]]"
  - architecture/subdomain
Updated: 2026-02-09
---# Action Nodes

## Overview

Action nodes perform business operations during workflow execution. They modify system state through entity CRUD operations, make external HTTP requests, query data, and manipulate workflow state. Each action node implements `WorkflowActionConfig` with a specific `WorkflowActionType` subtype.

Action nodes are the primary mechanism for workflows to interact with the entity system and external integrations.

## Implemented Action Nodes

| Node | Description | Status |
|------|-------------|--------|
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Actions/WorkflowCreateEntityActionConfig]] | Creates a new entity instance | Implemented |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Actions/WorkflowDeleteEntityActionConfig]] | Deletes an entity instance | Implemented |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Actions/WorkflowUpdateEntityActionConfig]] | Updates an existing entity instance | Implemented |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Actions/WorkflowHttpRequestActionConfig]] | Makes an HTTP request to an external URL | Implemented |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Actions/WorkflowQueryEntityActionConfig]] | Searches and retrieves entity instances | Implemented |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Actions/WorkflowBulkUpdateEntityActionConfig]] | Queries entities and applies batch updates with configurable error handling (FAIL_FAST/BEST_EFFORT) | Implemented |

## Unimplemented Action Types

The following action types are defined in `WorkflowActionType` enum but have no concrete implementations:

- **LINK_ENTITY** — Would establish relationships between entities
- **INTEGRATION_REQUEST** — Would interact with configured integrations
- **SET_ENVIRONMENT_VARIABLE** — Would modify workflow environment state
- **MAP_DATA** — Would transform data structures between formats
