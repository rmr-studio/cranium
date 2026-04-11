---
Created:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
Updated: 2026-02-09
---
# WorkflowUpdateEntityActionConfig

## Purpose

Updates existing entity instances within workflows with template-resolved attribute values.

---

## Responsibilities

- Configure update parameters (entity ID, payload, timeout)
- Validate entity ID and payload template syntax
- Fetch existing entity to determine type
- Execute entity update via [[riven/docs/system-design/domains/Entities/Entity Management/EntityService]]
- Return updated entity metadata

## Dependencies

- [[riven/docs/system-design/domains/Entities/Entity Management/EntityService]] — fetches existing entity and saves updates
- [[riven/docs/system-design/domains/Workflows/Node Execution/WorkflowNodeConfigValidationService]] — validates template syntax and UUIDs
- [[riven/apps/client/lib/types/docs/WorkflowNodeConfig]] — sealed parent class for all node configurations

## Used By

- [[riven/docs/system-design/domains/Workflows/Node Execution/WorkflowNodeConfigRegistry]] — discovers at application startup via classpath scan
- [[riven/apps/client/lib/types/docs/WorkflowNode]] — executes via `execute()` method during workflow runtime

---

## Key Logic

The `execute()` method:

1. Extracts resolved `entityId` (UUID) and `payload` (Map) from inputs
2. **Fetches existing entity** to determine `typeId` (required for save operation)
3. Transforms payload keys from strings to UUIDs (attribute IDs)
4. Wraps each value in `EntityAttributeRequest` with `SchemaType.TEXT` (default)
5. Builds `SaveEntityRequest` with `id = entityId` (indicates update)
6. Calls `EntityService.saveEntity()` with workspace ID and entity type
7. Returns `UpdateEntityOutput` with entity ID, success flag, and updated payload

---

## Config Fields

| Field | Type | Required | Description |
|---|---|---|---|
| `entityId` | UUID | Yes | UUID of entity to update (supports templates like `{{ steps.find.output.entityId }}`) |
| `payload` | KEY_VALUE | Yes | Map of attribute ID (UUID) to new value (supports template values) |
| `timeoutSeconds` | DURATION | No | Optional timeout override in seconds (default: workflow-level timeout) |

---

## JSON Examples

### Configuration

```json
{
  "version": 1,
  "type": "ACTION",
  "subType": "UPDATE_ENTITY",
  "entityId": "{{ steps.find_client.output.entityId }}",
  "payload": {
    "status": "active",
    "lastContacted": "{{ steps.get_timestamp.output.now }}"
  }
}
```

### Output

```json
{
  "entityId": "550e8400-e29b-41d4-a716-446655440000",
  "updated": true,
  "payload": {
    "status-attr-uuid": { "value": "active", "schemaType": "TEXT" },
    "lastContacted-attr-uuid": { "value": "2024-01-15T10:30:00Z", "schemaType": "TEXT" }
  }
}
```

---

## Validation Rules

1. **entityId**: Must be valid UUID or template syntax (checked by `validateTemplateOrUuid`)
2. **payload**: All values must have valid template syntax if templated (checked by `validateTemplateMap`)
3. **timeoutSeconds**: Must be non-negative if provided (checked by `validateOptionalDuration`)

**Not validated:**
- Entity existence (fetched at execution, will throw if not found)
- Payload keys matching entity type attribute schema
- Payload values compatible with schema types

---

## Gotchas

### Schema Type Inference Not Implemented

All payload values default to `SchemaType.TEXT` regardless of actual attribute type. This is the same limitation as [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Actions/WorkflowCreateEntityActionConfig]]:

```kotlin
// TODO: Infer schema type from entity type schema for proper typing
schemaType = SchemaType.TEXT  // Default to TEXT, infer from schema later
```

**Impact**: Entity service receives TEXT types for all values, requiring downstream type coercion. Numeric, boolean, and date attributes may not validate correctly.

### Fetch Before Update

Unlike create operations, update **requires fetching the existing entity first** to determine the `typeId`. This adds latency and a potential failure point (entity not found).

**Failure mode**: If entity doesn't exist, `getEntity()` will throw exception, failing the workflow node.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Actions/Action Nodes]] — category-level overview of all action node types
- [[riven/apps/client/lib/types/docs/WorkflowNodeConfig]] — sealed parent class defining node configuration contract
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Actions/WorkflowCreateEntityActionConfig]] — related create operation with same schema type limitation
