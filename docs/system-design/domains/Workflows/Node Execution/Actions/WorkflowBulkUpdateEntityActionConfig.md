---
Created:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
Updated: 2026-02-13
---
# WorkflowBulkUpdateEntityActionConfig

## Purpose

Queries entities matching filter criteria and applies identical field updates to all matching entities in batches, with configurable error handling modes (FAIL_FAST or BEST_EFFORT).

---

## Responsibilities

- Configure bulk update parameters (query filters, update payload, error handling mode, pagination, timeout)
- Validate embedded EntityQuery filter structure and template syntax in payload values
- Execute paginated entity queries to collect matching entity IDs (up to 10,000 entities)
- Apply batch updates in groups of 50 entities via [[riven/docs/system-design/domains/Entities/Entity Management/EntityService]]
- Track success/failure counts with detailed error information per entity
- Support two error handling modes: stop on first failure or process all entities

**Explicitly NOT responsible for:**
- Query execution logic (delegated to [[riven/docs/system-design/domains/Entities/Querying/EntityQueryService]])
- Entity validation (delegated to [[riven/docs/system-design/domains/Entities/Entity Management/EntityService]])
- Transaction management across batches (each entity update is independent)

## Dependencies

- [[riven/docs/system-design/domains/Entities/Entity Management/EntityService]] — gets and updates individual entities in batch loop
- [[riven/docs/system-design/domains/Entities/Querying/EntityQueryService]] — executes entity queries to find matching entities
- [[riven/docs/system-design/domains/Workflows/Node Execution/WorkflowNodeConfigValidationService]] — validates template syntax and config fields
- [[riven/apps/client/lib/types/docs/WorkflowNodeConfig]] — sealed parent class for all node configurations
- WorkflowFilterTemplateUtils — resolves template values within filter trees

## Used By

- [[riven/docs/system-design/domains/Workflows/Node Execution/WorkflowNodeConfigRegistry]] — discovers at application startup via explicit registration
- [[riven/apps/client/lib/types/docs/WorkflowNode]] — executes via `execute()` method during workflow runtime
- [[riven/docs/system-design/domains/Workflows/Execution Engine/WorkflowCoordinationService]] — resolves input templates before execution

---

## Config Fields

| Field | Type | Required | Description |
|---|---|---|---|
| `query` | ENTITY_QUERY | Yes | Embedded EntityQuery with entity type and optional filters (supports templates in filter values) |
| `payload` | KEY_VALUE | Yes | Map of attribute ID (UUID) to value — updates to apply to all matching entities (supports template values) |
| `errorHandling` | ENUM | No | `FAIL_FAST` (default) or `BEST_EFFORT` — controls behavior when individual entity updates fail |
| `pagination` | JSON | No | Pagination configuration for the entity query |
| `timeoutSeconds` | DURATION | No | Optional timeout override in seconds |

---

## Output Metadata

Declared on companion object for frontend preview and downstream node reference:

| Field | Type | Description |
|---|---|---|
| `entitiesUpdated` | NUMBER | Count of entities successfully updated |
| `entitiesFailed` | NUMBER | Count of entities that failed to update |
| `failedEntityDetails` | LIST | Details of failed entity updates — each entry contains entityId and error message |
| `totalProcessed` | NUMBER | Total entities attempted (updated + failed) |

---

## JSON Examples

### Configuration

```json
{
  "version": 1,
  "type": "ACTION",
  "subType": "BULK_UPDATE_ENTITY",
  "query": {
    "entityTypeId": "client-type-uuid",
    "filter": {
      "type": "ATTRIBUTE",
      "attributeId": "status-uuid",
      "operator": "EQUALS",
      "value": { "kind": "LITERAL", "value": "Active" }
    }
  },
  "payload": {
    "status-uuid": "Archived",
    "updated-by-uuid": "{{ trigger.input.userId }}"
  },
  "errorHandling": "BEST_EFFORT"
}
```

### Output

```json
{
  "entitiesUpdated": 23,
  "entitiesFailed": 2,
  "failedEntityDetails": [
    { "entityId": "uuid-1", "error": "Validation failed: required field missing" },
    { "entityId": "uuid-2", "error": "Entity not found" }
  ],
  "totalProcessed": 25
}
```

---

## Key Logic

### Execution Flow

1. **Collect matching entity IDs** — paginated queries via `EntityQueryService` with hard cap of 10,000 entities
2. **Build entity payload** — resolves template values in payload map, wraps each value in `EntityAttributeRequest` with `SchemaType.TEXT`
3. **Process in batches of 50** — for each entity:
   a. Fetch current entity via `EntityService.getEntity()`
   b. Merge update payload into existing entity attributes
   c. Save via `EntityService.saveEntity()`
4. **Error handling per mode:**
   - **FAIL_FAST**: Throws on first entity update failure; entities already updated remain updated (no rollback)
   - **BEST_EFFORT**: Catches individual failures, records entityId + error message, continues processing remaining entities

### Entity Collection (collectMatchingEntityIds)

Paginates through query results in pages of 100, collecting entity IDs until either:
- No more results (`hasMore = false`)
- Hard cap of 10,000 entities reached
- Query returns empty page

### Batch Processing (processBatches)

Entities are processed in batches of 50. Each entity update is independent — there is no transaction spanning multiple entities. This means partial completion is the expected behavior on failure.

---

## Validation Rules

1. **query**: Recursive filter validation (same as [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Actions/WorkflowQueryEntityActionConfig]])
2. **payload**: All values must have valid template syntax if templated (checked by `validateTemplateMap`)
3. **errorHandling**: Must be valid enum value (FAIL_FAST or BEST_EFFORT)
4. **timeoutSeconds**: Must be non-negative if provided

**Not validated:**
- Entity type existence in workspace
- Payload keys matching entity type attribute schema
- Whether update payload would violate entity constraints

---

## Gotchas

### No Rollback on Partial Failure

In both FAIL_FAST and BEST_EFFORT modes, entities updated before a failure remain updated. There is no transaction spanning the entire bulk operation. This is by design — rolling back thousands of entity updates would be impractical.

### Hard Cap of 10,000 Entities

The `collectMatchingEntityIds()` method enforces a maximum of 10,000 entities regardless of how many match the query. This prevents unbounded memory consumption and execution time. Workflows needing to process more entities should use external pagination logic.

### Schema Type Inference Not Implemented

Same limitation as [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Actions/WorkflowCreateEntityActionConfig]] — all payload values default to `SchemaType.TEXT`. Numeric, boolean, and date attributes may not validate correctly.

### Cross-Domain Runtime Dependencies

This node has the heaviest cross-domain footprint of any action node:
- `EntityQueryService.execute()` — for query execution
- `EntityService.getEntity()` — for fetching current entity state
- `EntityService.saveEntity()` — for applying updates

All accessed via `NodeServiceProvider` lazy injection during execution.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Actions/Action Nodes]] — category-level overview of all action node types
- [[riven/apps/client/lib/types/docs/WorkflowNodeConfig]] — sealed parent class defining node configuration contract
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Actions/WorkflowQueryEntityActionConfig]] — uses same query model and filter validation
- [[riven/docs/system-design/domains/Entities/Querying/EntityQueryService]] — query execution in Entities domain
