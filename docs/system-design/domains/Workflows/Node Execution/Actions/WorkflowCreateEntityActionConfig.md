---
Created:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
Updated: 2026-02-09
---
# WorkflowCreateEntityActionConfig

## Purpose

Creates new entity instances within workflows with template-resolved entity type and payload values.

---

## Responsibilities

- Configure creation parameters (entity type, attribute payload, timeout)
- Validate entity type ID and payload template syntax
- Execute entity creation via [[riven/docs/system-design/domains/Entities/Entity Management/EntityService]]
- Return created entity metadata (ID, type, payload)

## Dependencies

- [[riven/docs/system-design/domains/Entities/Entity Management/EntityService]] — creates entity instances with validated payload
- [[riven/docs/system-design/domains/Workflows/Node Execution/WorkflowNodeConfigValidationService]] — validates template syntax and UUIDs
- [[riven/apps/client/lib/types/docs/WorkflowNodeConfig]] — sealed parent class for all node configurations

## Used By

- [[riven/docs/system-design/domains/Workflows/Node Execution/WorkflowNodeConfigRegistry]] — discovers at application startup via classpath scan
- [[riven/apps/client/lib/types/docs/WorkflowNode]] — executes via `execute()` method during workflow runtime

---

## Key Logic

The `execute()` method:

1. Extracts resolved `entityTypeId` (UUID) and `payload` (Map) from inputs
2. Transforms payload keys from strings to UUIDs (attribute IDs)
3. Wraps each value in `EntityAttributeRequest` with `SchemaType.TEXT` (default)
4. Builds `SaveEntityRequest` with `id = null` (indicates new entity)
5. Calls `EntityService.saveEntity()` with workspace ID and entity type
6. Returns `CreateEntityOutput` with entity ID, type ID, and final payload

---

## Config Fields

| Field | Type | Required | Description |
|---|---|---|---|
| `entityTypeId` | ENTITY_TYPE | Yes | UUID of entity type to create (supports templates like `{{ steps.x.output.typeId }}`) |
| `payload` | KEY_VALUE | Yes | Map of attribute ID (UUID) to value (supports template values) |
| `timeoutSeconds` | DURATION | No | Optional timeout override in seconds (default: workflow-level timeout) |

---

## Output Metadata

Declared on companion object for frontend preview and downstream node reference:

| Field | Type | Description |
|---|---|---|
| `entityId` | UUID | ID of the newly created entity |
| `entityTypeId` | UUID | ID of the entity type |
| `payload` | MAP | Entity attributes as UUID-keyed map |

Downstream nodes can reference these via templates: `{{ steps.create_node.output.entityId }}`

---

## JSON Examples

### Configuration

```json
{
  "version": 1,
  "type": "ACTION",
  "subType": "CREATE_ENTITY",
  "entityTypeId": "{{ steps.get_type.output.typeId }}",
  "payload": {
    "name": "{{ steps.fetch_data.output.clientName }}",
    "email": "client@example.com"
  }
}
```

### Output

```json
{
  "entityId": "550e8400-e29b-41d4-a716-446655440000",
  "entityTypeId": "123e4567-e89b-12d3-a456-426614174000",
  "payload": {
    "name-attr-uuid": { "value": "Acme Corp", "schemaType": "TEXT" },
    "email-attr-uuid": { "value": "client@example.com", "schemaType": "TEXT" }
  }
}
```

---

## Validation Rules

1. **entityTypeId**: Must be valid UUID or template syntax (checked by `validateTemplateOrUuid`)
2. **payload**: All values must have valid template syntax if templated (checked by `validateTemplateMap`)
3. **timeoutSeconds**: Must be non-negative if provided (checked by `validateOptionalDuration`)

**Not validated (TODO):**
- Entity type existence in workspace
- Payload keys matching entity type attribute schema
- Payload values compatible with schema types
- Required fields per entity type schema

---

## Gotchas

### Schema Type Inference Not Implemented

All payload values default to `SchemaType.TEXT` regardless of actual attribute type. This is explicitly marked as TODO in source code:

```kotlin
// TODO: Infer schema type from entity type schema for proper typing
schemaType = SchemaType.TEXT  // Default to TEXT, infer from schema later
```

**Impact**: Entity service receives TEXT types for all values, requiring downstream type coercion. Numeric, boolean, and date attributes may not validate correctly.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Actions/Action Nodes]] — category-level overview of all action node types
- [[riven/apps/client/lib/types/docs/WorkflowNodeConfig]] — sealed parent class defining node configuration contract
- [[riven/docs/system-design/feature-design/4. Completed/Entity Querying]] — related entity operations (Entities domain)
