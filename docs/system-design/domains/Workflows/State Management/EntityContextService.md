---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-02-08
Updated: 2026-02-21
Domains:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
---
# EntityContextService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/State Management/State Management]]

## Purpose

Converts entity data to expression-compatible context maps by transforming UUID-keyed entity payloads into string-keyed maps using entity type schema labels, enabling human-readable expressions.

---

## Responsibilities

- Load entity and entity type from repositories
- Transform UUID-keyed payload to string-keyed context map
- Recursively resolve entity relationships up to configurable depth
- Handle one-to-one, many-to-one, one-to-many, many-to-many relationship cardinalities
- Prevent infinite cycles via depth limiting
- Extract primitive values from entity attributes

---

## Dependencies

- `EntityRepository` — Fetch entity by ID
- `EntityTypeRepository` — Fetch entity type schema
- `EntityRelationshipService` — Load related entities
- `RelationshipDefinitionRepository` — Load relationship definitions for context building (forward and inverse-visible)
- `RelationshipTargetRuleRepository` — Hydrate relationship definitions with target rules
- **Cross-domain dependency:** Entity domain — deeper coupling than before; now reads directly from relationship definition repositories in addition to entity/type repositories

## Used By

- CONDITION nodes — Build context for expression evaluation
- Workflow nodes that need entity data in context

---

## Key Logic

**Context building flow:**

1. Fetch entity by ID
2. Fetch entity type schema
3. Load relationship definitions via `loadDefinitions()`:
   - Fetches forward definitions (entity type is source)
   - Fetches inverse-visible definitions (entity type is target with `inverseVisible=true`)
   - Hydrates each definition with its target rules
   - Returns `Map<UUID, RelationshipDefinition>`
4. Iterate entity payload (Map<UUID, EntityAttribute>)
5. For each UUID key:
   - Lookup schema field to get human-readable label
   - Extract value (primitive or relationship)
   - Add to context map with label as key

**Relationship traversal:**

- If `maxDepth > 0`, recursively build contexts for related entities
- Cardinality is determined from `definition.cardinalityDefault` on the `RelationshipDefinition` object (previously read from the entity type JSONB schema field)
- Relationship cardinality determines structure:
  - ONE_TO_ONE / MANY_TO_ONE → Single nested map
  - ONE_TO_MANY / MANY_TO_MANY → List of nested maps
- Depth limiting prevents infinite cycles (e.g., Client → Address → Client)

**Example transformation:**

```kotlin
// Entity payload (UUID keys)
{
  UUID("...") -> EntityAttributePrimitivePayload(value = "active"),
  UUID("...") -> EntityAttributeRelationPayload(relations = [entityLink])
}

// Context map (string keys from schema labels)
{
  "status" -> "active",
  "address" -> { "city" -> "London", "country" -> "UK" }
}
```

---

## Public Methods

### `buildContext(entityId, workspaceId): Map<String, Any?>`

Builds context without relationship traversal (maxDepth=0). Returns map with primitive values only. Relationships return null.

### `buildContextWithRelationships(entityId, workspaceId, maxDepth=3): Map<String, Any?>`

Builds context with recursive relationship traversal. Returns map with nested objects/lists for relationships. Default depth of 3 prevents infinite cycles while supporting reasonable traversal.

---

## Gotchas

- **Cross-domain dependency:** This service in Workflows domain depends on Entity domain repositories — `EntityRepository`, `EntityTypeRepository`, `RelationshipDefinitionRepository`, and `RelationshipTargetRuleRepository`. The addition of the two relationship repositories deepens the coupling; changes to how relationship definitions are stored or queried in the Entity domain will directly affect this service.
- **Schema label requirement:** If entity type schema is missing a label for a UUID field, throws `IllegalArgumentException`. Schema must be complete.
- **Relationship depth explosion:** With `maxDepth=3` and entities with many relationships, context map can become very large. Consider performance for deeply nested structures.
- **Stale relationships handled gracefully:** If related entity is deleted, logs warning and excludes from context (returns empty list or null). Doesn't fail entire context build.
- **Depth limit as entity ID strings:** When depth exceeded, returns list of `"entity:<uuid>"` strings for debugging. Prevents infinite recursion.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/State Management/WorkflowNodeExpressionEvaluatorService]] — Evaluates expressions against these contexts
- Entity domain — Source of entity data
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/State Management/State Management]] — Parent subdomain
