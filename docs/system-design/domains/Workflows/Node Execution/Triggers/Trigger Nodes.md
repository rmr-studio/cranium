---
Created:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
  - architecture/subdomain
Updated: 2026-02-09
---
# Trigger Nodes

## Overview

Trigger nodes define workflow entry points — they specify when and how a workflow should be initiated. Trigger nodes do not execute during workflow runtime; instead, they are evaluated by workflow scheduling and event handling systems to determine when to start a workflow execution.

Each trigger node implements `WorkflowTriggerConfig` with a specific `WorkflowTriggerType` subtype.

## Implemented Trigger Nodes

| Node | Description | Status |
|------|-------------|--------|
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Triggers/WorkflowEntityEventTriggerConfig]] | Triggers when an entity is created, updated, or deleted | Implemented |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Triggers/WorkflowScheduleTriggerConfig]] | Triggers on a recurring schedule or cron expression | Implemented |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Triggers/WorkflowWebhookTriggerConfig]] | Triggers when an HTTP request is received | Implemented |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Triggers/WorkflowFunctionTriggerConfig]] | Triggers when called programmatically | Implemented |

All trigger types defined in `WorkflowTriggerType` enum have implementations.
