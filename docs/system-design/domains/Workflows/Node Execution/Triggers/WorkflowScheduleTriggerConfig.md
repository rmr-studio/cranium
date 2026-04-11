---
Created:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
Updated: 2026-02-09
---
# WorkflowScheduleTriggerConfig

## Purpose

Triggers workflow execution on a schedule defined by cron expression or fixed interval. This is an entry point for workflows — trigger nodes do NOT execute during workflow runtime (execute() throws UnsupportedOperationException by design).

---

## Responsibilities

- Configure schedule timing via either cron expression OR fixed interval
- Specify timezone for schedule interpretation
- Validate that at least one scheduling mechanism (cron or interval) is provided
- Validate configuration at workflow creation time

---

## Dependencies

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Triggers/WorkflowTriggerConfig]] — parent sealed interface
- [[riven/apps/client/lib/types/docs/WorkflowNodeConfig]] — sealed parent class for all node configurations

## Used By

- [[riven/docs/system-design/domains/Workflows/Node Execution/WorkflowNodeConfigRegistry]] — discovered and registered at startup via reflection

---

## Config Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `cronExpression` | STRING | No* | Cron expression for scheduling (e.g., '0 0 * * *' for daily at midnight) |
| `interval` | DURATION | No* | Fixed interval between executions (alternative to cron) |
| `timeZone` | STRING | Yes | Time zone for schedule interpretation (e.g., 'America/New_York') |

**\*At least one required:** Either `cronExpression` OR `interval` must be provided

---

## Validation Rules

| Field | Rule | Error |
|-------|------|-------|
| `cronExpression` / `interval` | At least one must be provided | "Either cronExpression or interval must be provided" |
| `cronExpression` | Not blank if provided | "Cron expression cannot be blank" |
| `interval` | Positive duration if provided | "Interval must be positive" |
| `timeZone` | Non-null | Enforced by constructor |

**Additional checks:**
- **Init block enforcement:** The `init` block contains `require(cronExpression != null || interval != null)` which throws IllegalArgumentException at construction time if neither is provided — validation is partly duplicated in validate() method

---

## Gotchas

> [!warning] Dual Validation
>
> **Init block + validate():** The "at least one scheduling option" requirement is enforced BOTH in the `init` block (throws IllegalArgumentException at construction) AND in the `validate()` method (returns ConfigValidationError). This means invalid configurations fail fast at construction time, before validate() is called. The validate() check is defensive but may never trigger in practice.

> [!warning] Entry Point Only
>
> **execute() not called during workflow:** Triggers define workflow entry points and are evaluated externally (e.g., by a scheduler service). Calling execute() throws UnsupportedOperationException — this is intentional design, not a bug.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Triggers/Trigger Nodes]] — category-level overview of all trigger types
- [[riven/apps/client/lib/types/docs/WorkflowNodeConfig]] — sealed parent class
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Triggers/WorkflowTriggerConfig]] — parent sealed interface
