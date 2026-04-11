---
Created:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
Updated: 2026-02-09
---
# WorkflowEntityEventTriggerConfig

## Purpose

Triggers workflow execution when entity operations (CREATE, UPDATE, DELETE) occur on specified entity types. This is an entry point for workflows — trigger nodes do NOT execute during workflow runtime (execute() throws UnsupportedOperationException by design).

---

## Responsibilities

- Configure which entity type and operation (CREATE/UPDATE/DELETE) triggers workflow
- Define optional field-level filters to watch specific fields
- Specify filter expressions to conditionally trigger based on entity data
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
| `key` | STRING | Yes | Entity type key to watch for events |
| `operation` | ENUM | Yes | Entity operation to trigger on (CREATE/UPDATE/DELETE) |
| `field` | JSON | No | Specific fields to watch for changes (list of strings) |
| `expressions` | JSON | Yes | Filter expressions for conditional triggering |

**Operation enum values:**
- `CREATE` — Triggers when entity is created
- `UPDATE` — Triggers when entity is updated
- `DELETE` — Triggers when entity is deleted

---

## Validation Rules

| Field | Rule | Error |
|-------|------|-------|
| `key` | Non-blank string | "key cannot be blank" |
| `operation` | Valid OperationType enum | Enforced at deserialization |
| `expressions` | Not blank/empty if String or Collection | "Expressions cannot be blank" / "Expressions list cannot be empty" |

**Additional checks:**
- `expressions` is typed as `Any` — loosely typed, validated at runtime with `when` to check if String or Collection

---

## Gotchas

> [!warning] Type Safety
>
> **expressions field typing:** The `expressions` property is typed as `Any` to allow flexible configuration (could be String or Collection). This loose typing means validation must handle multiple types at runtime. The validate() method uses a `when` expression to type-check and validate based on actual runtime type.

> [!warning] Entry Point Only
>
> **execute() not called during workflow:** Triggers define workflow entry points and are evaluated externally (e.g., entity event listeners). Calling execute() throws UnsupportedOperationException — this is intentional design, not a bug.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Triggers/Trigger Nodes]] — category-level overview of all trigger types
- [[riven/apps/client/lib/types/docs/WorkflowNodeConfig]] — sealed parent class
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Triggers/WorkflowTriggerConfig]] — parent sealed interface
