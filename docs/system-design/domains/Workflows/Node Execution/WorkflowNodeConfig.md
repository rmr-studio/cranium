---
tags:
  - layer/model
  - component/active
  - architecture/component
Domains:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
Created: 2026-02-08
Updated: 2026-02-08
---
# WorkflowNodeConfig

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Node Execution]]

## Purpose

Sealed interface defining the polymorphic execution contract for all workflow node types — each subtype implements `execute()` to define node-specific behavior with access to workflow data store, resolved inputs, and Spring services.

---

## Responsibilities

- Define execution contract via `execute(dataStore, inputs, services): NodeOutput`
- Define validation contract via `validate(injector): ConfigValidationResult`
- Expose raw configuration as `config: JsonObject`
- Provide schema definition via `configSchema: List<WorkflowNodeConfigField>` for dynamic UI generation
- Optionally declare output field shape via `outputMetadata: WorkflowNodeOutputMetadata` on companion object for frontend preview and downstream reference
- Support polymorphic JSON deserialization via type/subtype discrimination

---

## Dependencies

- `WorkflowDataStore` — Access to step outputs, trigger context, metadata during execution
- `NodeServiceProvider` — On-demand Spring service resolution for node execution
- `JsonObject` — Raw configuration representation
- `NodeOutput` — Typed execution result wrapper
- `ConfigValidationResult` — Validation result with success/failure and messages
- `WorkflowNodeConfigDeserializer` — Custom Jackson deserializer for polymorphic type resolution

## Used By

- [[riven/docs/system-design/domains/Workflows/Execution Engine/WorkflowGraphCoordinationService]] — Calls `execute()` via `WorkflowNode` wrapper during DAG execution
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/WorkflowNodeConfigRegistry]] — Discovers implementations via reflection to build schema registry
- [[riven/docs/system-design/domains/Workflows/Graph Coordination/WorkflowGraphValidationService]] — Calls `validate()` to check node configuration correctness before execution

---

## Key Logic

**Sealed interface hierarchy:**

Subtypes organized by `WorkflowNodeType`:
- **Triggers:** WEBHOOK, SCHEDULE, ENTITY_EVENT, etc.
- **Actions:** CREATE_ENTITY, UPDATE_ENTITY, HTTP_REQUEST, SEND_EMAIL, etc.
- **Controls:** CONDITION, SWITCH, FOR_EACH, PARALLEL, etc.
- **Utilities:** SET_VARIABLE, TRANSFORM, DELAY, etc.
- **Functions:** CUSTOM_FUNCTION, EXPRESSION_EVAL, etc.
- **Human Interaction:** APPROVAL, FORM_SUBMISSION, etc.

Each subtype implements:
1. `execute()` — Node-specific execution logic with access to:
   - `dataStore` — Read step outputs, trigger context, metadata
   - `inputs` — Resolved inputs (templates already evaluated to values)
   - `services` — Lazy Spring service resolution via `NodeServiceProvider`
2. `validate()` — Configuration validation with access to Spring services for application-based validation
3. `config` — Raw configuration as `JsonObject`
4. `configSchema` — Field definitions for OpenAPI docs and dynamic UI generation

**Companion object contract:**

Each subtype's companion object provides:
1. `configSchema: List<WorkflowNodeConfigField>` — Required. Field definitions for configuration UI
2. `metadata: WorkflowNodeTypeMetadata` — Required. Display metadata (name, description, icon)
3. `outputMetadata: WorkflowNodeOutputMetadata?` — Optional. Declares the shape of node execution output for frontend preview and downstream template reference

**Polymorphic deserialization:**

`WorkflowNodeConfigDeserializer` discriminates by `type` and `subType` fields in JSON:

```json
{
  "type": "ACTION",
  "subType": "CREATE_ENTITY",
  "entityType": "Contact",
  "fields": {...}
}
```

Deserializer maps `type + subType` to concrete config class (e.g., `WorkflowCreateEntityActionConfig`).

**Execution contract:**

- Actions return typed outputs: `CreateEntityOutput`, `HttpResponseOutput`, etc.
- Controls return typed outputs: `ConditionOutput`, `SwitchOutput`, etc.
- Exceptions thrown during `execute()` are caught by Temporal activity implementation and surfaced as workflow errors

---

## Public Methods

### `execute(dataStore: WorkflowDataStore, inputs: JsonObject, services: NodeServiceProvider): NodeOutput`

Execute this node with given datastore and resolved inputs. Returns typed `NodeOutput` representing execution result. Throws exceptions on execution failure (caught by activity layer).

### `validate(injector: NodeServiceProvider): ConfigValidationResult`

Validate configuration using provided services. Returns validation result with success/failure and messages. Service injector allows application-based validation (e.g., checking if entity type exists).

---

## Gotchas

- **Sealed interface, not abstract class:** Enables exhaustive when expressions in Kotlin for type-safe pattern matching.
- **Custom deserialization required:** Standard Jackson polymorphism doesn't support two-field discrimination (`type` + `subType`). Custom deserializer maps both fields to config class.
- **Service injection is lazy:** `NodeServiceProvider.get()` resolves services on-demand. Avoids loading all services for every node execution. Throws `NoSuchBeanDefinitionException` if service not found.
- **Config schema is static:** Defined in companion objects, not instance methods. Enables schema discovery without instantiation via `WorkflowNodeConfigRegistry`.
- **Version field for evolution:** `version: Int` enables backward-compatible schema changes. Not currently enforced, reserved for future migration handling.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/WorkflowNode]] — Runtime DTO wrapper with entity metadata (id, workspaceId, key)
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/WorkflowNodeConfigRegistry]] — Discovers config schemas via reflection
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Node Execution]] — Parent subdomain
