---
Created:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
Updated: 2026-02-09
---
# WorkflowQueryEntityActionConfig

---

## Purpose

Queries entities by type with attribute and relationship filtering, supporting compound logic and nested relationship traversal.

---

## Responsibilities

- Configure query parameters (entity type, filters, pagination, projection)
- Validate recursive filter structure with compound AND/OR and nested relationships
- Validate template syntax in filter values
- Execute entity queries via [[riven/docs/system-design/domains/Entities/Querying/EntityQueryService]] with template-resolved filter trees

---

## Dependencies

### Internal Dependencies

| Component | Purpose | Coupling |
|---|---|---|
| [[riven/docs/system-design/domains/Workflows/Node Execution/WorkflowNodeConfigValidationService]] | Validates template syntax and config fields | Medium |
| [[riven/apps/client/lib/types/docs/WorkflowNodeConfig]] | Sealed parent class for all node configurations | High |

### Cross-Domain Dependencies

| Component | Purpose | Coupling |
|---|---|---|
| [[riven/apps/client/lib/types/docs/EntityQuery]] (Entities domain) | Query model with filter definitions | High |
| [[riven/apps/client/lib/types/docs/QueryFilter]] (Entities domain) | Sealed class for attribute/relationship filters | High |
| [[riven/apps/client/lib/types/docs/RelationshipFilter]] (Entities domain) | Relationship condition types | High |

### Runtime Execution Dependencies

| Component | Purpose | Coupling |
|---|---|---|
| [[riven/docs/system-design/domains/Entities/Querying/EntityQueryService]] (Entities domain) | Executes resolved entity queries | High |
| [[riven/docs/system-design/domains/Workflows/State Management/WorkflowNodeInputResolverService]] | Resolves template values in inputs | Medium |
| WorkflowFilterTemplateUtils | Resolves template values within filter trees | Medium |

---

## Consumed By

| Component | How It Uses This | Notes |
|---|---|---|
| [[riven/docs/system-design/domains/Workflows/Node Execution/WorkflowNodeConfigRegistry]] | Discovers at startup via classpath scan | Auto-registration |
| [[riven/apps/client/lib/types/docs/WorkflowNode]] | Executes via `execute()` method | Executes query via EntityQueryService at runtime |

---

## Config Fields

| Field | Type | Required | Description |
|---|---|---|---|
| `query` | ENTITY_QUERY | Yes | Query definition with entity type and optional filters |
| `pagination` | JSON | No | Pagination (limit, offset) and ordering configuration |
| `projection` | JSON | No | Field selection for query results |
| `timeoutSeconds` | DURATION | No | Optional timeout override in seconds |

---

## Output Metadata

Declared on companion object for frontend preview and downstream node reference:

| Field | Type | Description |
|---|---|---|
| `entities` | ENTITY_LIST | List of entities matching the query filters (entity type resolved at runtime) |
| `totalCount` | NUMBER | Total number of matching entities before pagination limit |
| `hasMore` | BOOLEAN | Whether more results exist beyond the system limit |

---

## JSON Examples

### Simple Attribute Filter (Status == "Active")

```json
{
  "version": 1,
  "type": "ACTION",
  "subType": "QUERY_ENTITY",
  "query": {
    "entityTypeId": "client-type-uuid",
    "filter": {
      "type": "ATTRIBUTE",
      "attributeId": "status-uuid",
      "operator": "EQUALS",
      "value": { "kind": "LITERAL", "value": "Active" }
    }
  }
}
```

### Compound Filter (Status == "Active" AND ARR > 100000)

```json
{
  "query": {
    "entityTypeId": "client-type-uuid",
    "filter": {
      "type": "AND",
      "conditions": [
        {
          "type": "ATTRIBUTE",
          "attributeId": "status-uuid",
          "operator": "EQUALS",
          "value": { "kind": "LITERAL", "value": "Active" }
        },
        {
          "type": "ATTRIBUTE",
          "attributeId": "arr-uuid",
          "operator": "GREATER_THAN",
          "value": { "kind": "LITERAL", "value": 100000 }
        }
      ]
    }
  }
}
```

### Relationship Filter (Projects Related to Specific Client)

```json
{
  "query": {
    "entityTypeId": "project-type-uuid",
    "filter": {
      "type": "RELATIONSHIP",
      "relationshipId": "client-relationship-uuid",
      "condition": {
        "type": "TARGET_EQUALS",
        "entityIds": ["{{ trigger.input.clientId }}"]
      }
    }
  }
}
```

### Nested Relationship Filter (Projects with Premium Clients)

```json
{
  "query": {
    "entityTypeId": "project-type-uuid",
    "filter": {
      "type": "RELATIONSHIP",
      "relationshipId": "client-relationship-uuid",
      "condition": {
        "type": "TARGET_MATCHES",
        "filter": {
          "type": "ATTRIBUTE",
          "attributeId": "client-tier-uuid",
          "operator": "EQUALS",
          "value": { "kind": "LITERAL", "value": "Premium" }
        }
      }
    }
  }
}
```

---

## Key Logic

### Execution Flow

The `execute()` method:

1. Resolves template values in the filter tree via `WorkflowFilterTemplateUtils.resolveFilterTemplates()`
2. Resolves template values in relationship conditions via `resolveRelationshipConditionTemplates()`
3. Builds resolved `EntityQuery` with workspace ID from data store
4. Calls `EntityQueryService.execute()` with resolved query and pagination
5. Enforces system-wide `DEFAULT_QUERY_LIMIT = 100` entities per query
6. Transforms entity results to map representations
7. Returns `QueryEntityOutput` with entities list, total count, and hasMore flag

**Template resolution in filters:** Filter values using `{{ steps.x.output.field }}` syntax are resolved against the workflow data store before query execution. This enables dynamic filtering based on outputs from previous workflow nodes.

### Recursive Filter Validation

The validation logic dispatches through three private methods based on filter structure:

```mermaid
graph TD
    A[validateFilter] -->|QueryFilter.Attribute| B[validateFilterValue]
    A -->|QueryFilter.Relationship| C[validateRelationshipCondition]
    A -->|QueryFilter.And| D[Recurse on each condition]
    A -->|QueryFilter.Or| D

    B -->|FilterValue.Literal| E[No validation needed]
    B -->|FilterValue.Template| F[Validate template syntax]

    C -->|Exists/NotExists| G[No validation needed]
    C -->|TargetEquals| H[Validate UUID list]
    C -->|TargetMatches| I[Recurse validateFilter]
    C -->|TargetTypeMatches| J[Recurse on branch filters]
    C -->|CountMatches| K[Validate count >= 0]

    D --> A
    I --> A
    J --> A
```

#### 1. validateFilter(filter, path, validationService)

Dispatches based on `QueryFilter` sealed class type:

- **Attribute**: Validates the filter value (literal or template)
- **Relationship**: Validates the relationship condition
- **And**: Checks at least one condition exists, recursively validates each
- **Or**: Checks at least one condition exists, recursively validates each

#### 2. validateFilterValue(value, path, validationService)

Validates filter value based on `FilterValue` sealed class type:

- **Literal**: No validation (any value allowed)
- **Template**: Validates template syntax (e.g., `{{ steps.x.output.field }}`)

#### 3. validateRelationshipCondition(condition, path, validationService)

Validates relationship condition based on `RelationshipFilter` sealed class type:

- **Exists**: No validation
- **NotExists**: No validation
- **TargetEquals**: Validates list of entity IDs (each must be UUID or template)
- **TargetMatches**: Recursively validates nested filter
- **TargetTypeMatches**: Validates branches (each branch has optional filter)
- **CountMatches**: Validates count is non-negative

---

## Validation Rules

1. **entityTypeId**: Must be valid UUID (part of EntityQuery model)
2. **filter** (if provided): Recursive validation per diagram above
3. **pagination** (if provided):
   - `limit` must be non-negative
   - `offset` must be non-negative
4. **timeoutSeconds** (if provided): Must be non-negative

**Validation gaps:**
- Entity type existence in workspace not checked
- Attribute IDs not verified against entity type schema
- Relationship IDs not verified against relationship definitions
- Filter depth not limited (potential stack overflow)

---

## Error Handling

### Errors Thrown

| Error/Exception | When | Expected Handling |
|---|---|---|
| `IllegalArgumentException` | Entity type not found, invalid filter structure | Workflow node execution fails with VALIDATION_ERROR classification |
| Runtime exceptions from EntityQueryService | Query execution failure | Workflow node execution fails with EXECUTION_ERROR classification (retryable) |

---

## Gotchas & Edge Cases

> [!info] System Query Limit
> All queries enforce `DEFAULT_QUERY_LIMIT = 100`. Even if more entities match, only 100 are returned per execution. The `hasMore` output field indicates whether additional results exist. Workflows requiring all matching entities should use [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Actions/WorkflowBulkUpdateEntityActionConfig]] which handles internal pagination.

> [!info] runBlocking Usage
> The execute method uses `runBlocking` to bridge suspend functions from EntityQueryService into the synchronous workflow execution context. This is acceptable within Temporal activity threads but should not be used in coroutine contexts.

### Cross-Domain Dependency

The `EntityQuery` model is imported from the Entities domain:

```kotlin
import riven.core.models.entity.query.EntityQuery
import riven.core.models.entity.query.filter.QueryFilter
import riven.core.models.entity.query.filter.RelationshipFilter
```

**Impact:** Changes to Entities domain query models break Workflows domain. This creates tight coupling between domains.

**Trade-off:** Query model is shared because entity querying logic belongs in Entities domain, not Workflows. Workflows orchestrate, Entities execute.

### Unlimited Filter Depth

No limit on recursive filter nesting. A deeply nested filter like:

```
AND → OR → AND → RELATIONSHIP(TARGET_MATCHES) → AND → ...
```

Could cause stack overflow during validation or execution.

**Mitigation:** The Entities domain's [[riven/docs/system-design/domains/Entities/Querying/AttributeFilterVisitor]] documents separate depth limits for AND/OR nesting vs relationship traversal, but this config validator doesn't enforce them.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Actions/Action Nodes]] — category-level overview of all action node types
- [[riven/apps/client/lib/types/docs/WorkflowNodeConfig]] — sealed parent class defining node configuration contract
- [[riven/docs/system-design/feature-design/4. Completed/Entity Querying]] — Entities domain querying subsystem with execution logic
- [[riven/docs/system-design/domains/Entities/Querying/AttributeFilterVisitor]] — Entities domain visitor with depth limit documentation
