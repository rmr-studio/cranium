---
Created:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
Updated: 2026-02-09
---
# WorkflowFunctionTriggerConfig

## Purpose

Triggers workflow execution when called programmatically with a specified input schema. This is an entry point for workflows — trigger nodes do NOT execute during workflow runtime (execute() throws UnsupportedOperationException by design).

---

## Responsibilities

- Define expected input structure for programmatic workflow invocation
- Validate configuration at workflow creation time
- Simplest trigger type — only requires schema definition

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
| `schema` | JSON | Yes | Input structure definition for function calls |

---

## Validation Rules

| Field | Rule | Error |
|-------|------|-------|
| `schema` | Non-null | Enforced by constructor |

**Additional checks:**
- validate() returns `ConfigValidationResult.valid()` because schema is non-null in constructor
- Could add deeper schema validation in future if needed

---

## Gotchas

> [!warning] Simplest Trigger
>
> **Single config field:** This is the simplest trigger type with only one configuration field (`schema`). It has no scheduling logic, no entity watching, no HTTP concerns — just a schema defining the expected input structure when the workflow is called as a function.

> [!warning] Schema Import Alias
>
> **Name collision handling:** Uses `io.swagger.v3.oas.annotations.media.Schema as SwaggerSchema` import alias to avoid collision with `riven.core.models.common.validation.Schema` (the actual property type).

> [!warning] Entry Point Only
>
> **execute() not called during workflow:** Triggers define workflow entry points and are evaluated externally (e.g., by programmatic workflow invocation APIs). Calling execute() throws UnsupportedOperationException — this is intentional design, not a bug.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Triggers/Trigger Nodes]] — category-level overview of all trigger types
- [[riven/apps/client/lib/types/docs/WorkflowNodeConfig]] — sealed parent class
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Triggers/WorkflowTriggerConfig]] — parent sealed interface
