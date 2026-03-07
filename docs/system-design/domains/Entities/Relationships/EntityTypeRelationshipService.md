---
tags:
  - component/active
  - layer/service
  - architecture/component
Created: 2026-02-08
Updated: 2026-02-21
Domains:
  - "[[Entities]]"
---
# EntityTypeRelationshipService

Part of [[Relationships]]

---

## Purpose

Manages the full lifecycle of relationship definitions between entity types. Handles CRUD for type-level relationship configuration stored in the `relationship_definitions`, `relationship_target_rules`, and `relationship_definition_exclusions` tables, including semantic metadata lifecycle hooks, two-pass impact confirmation for destructive operations, and target-side exclusions for opt-out from bidirectional relationships.

---

## Responsibilities

**What this component owns:**
- Creating `RelationshipDefinitionEntity` records with their associated `RelationshipTargetRuleEntity` records
- Updating existing relationship definitions and diffing their target rules (add / remove / update in a single pass)
- Deleting relationship definitions using the two-pass impact pattern: returning a `DeleteDefinitionImpact` when confirmed data exists, then executing soft-delete of the definition and associated relationship links on confirmation
- Hard-deleting target rules and exclusion records on definition deletion (configuration data, not user data)
- Soft-deleting entity relationship links (`EntityRelationshipEntity`) when their definition is deleted
- Resolving which relationship definitions an entity type participates in, including both forward (type is source) and inverse definitions (type is a target), filtering out excluded definitions
- Managing target-side exclusions — allowing entity types to opt out of relationship definitions they are implicitly included in (via semantic group or polymorphic matching)
- Triggering `EntityTypeSemanticMetadataService` lifecycle hooks on create and delete
- Logging activity for all create, update, and delete mutations

**Explicitly NOT responsible for:**
- Managing actual entity relationship instance data (entity-to-entity links); that is owned by [[EntityRelationshipService]]
- Enforcing cardinality at link-creation time; that is owned by [[EntityRelationshipService]]
- Orchestrating the save flow for an entire entity type definition; that is coordinated by [[EntityTypeService]]
- Building or validating JSON schemas for attributes; handled by [[EntityTypeAttributeService]]

---

## Dependencies

### Internal Dependencies

| Component | Purpose | Coupling |
|---|---|---|
| [[RelationshipDefinitionRepository]] | CRUD for `relationship_definitions` | High |
| [[RelationshipTargetRuleRepository]] | CRUD for `relationship_target_rules` | High |
| RelationshipDefinitionExclusionRepository | CRUD for `relationship_definition_exclusions` | High |
| EntityRelationshipRepository | Count and soft-delete link records on definition deletion and exclusion | Medium |
| [[ActivityService]] | Logs relationship CRUD operations | Medium |
| [[AuthTokenService]] | Retrieves current user ID for activity logging | Low |
| [[EntityTypeSemanticMetadataService]] | Initialize and clean up semantic metadata for relationship definitions | Medium |

### External Dependencies

| Service/Library | Purpose | Failure Impact |
|---|---|---|
| Spring Transaction Manager | Ensures atomic multi-step mutations (save definition + rules + soft-delete links + semantic cleanup) | Data corruption if transactions fail |
| Spring Security (`@PreAuthorize`) | Workspace access control on every public method | Unauthorized access if annotation is absent |

### Injected Dependencies

```kotlin
@Service
class EntityTypeRelationshipService(
    private val definitionRepository: RelationshipDefinitionRepository,
    private val targetRuleRepository: RelationshipTargetRuleRepository,
    private val exclusionRepository: RelationshipDefinitionExclusionRepository,
    private val entityRelationshipRepository: EntityRelationshipRepository,
    private val activityService: ActivityService,
    private val authTokenService: AuthTokenService,
    private val semanticMetadataService: EntityTypeSemanticMetadataService,
    private val logger: KLogger,
)
```

---

## Consumed By

| Component | How It Uses This | Notes |
|---|---|---|
| [[EntityTypeService]] | Delegates create/update/delete of relationship definitions during `saveEntityTypeDefinition` and `deleteEntityType` | Primary orchestrator; calls this service within its own transaction |
| [[EntityService]] | Calls `getDefinitionsForEntityType` to validate and resolve definition IDs during entity payload saves | Read-only usage; does not trigger mutations |
| [[EntityRelationshipService]] | Calls `getOrCreateFallbackDefinition`, `getFallbackDefinitionId` | Resolves CONNECTED_ENTITIES definition for connection CRUD |
| [[EntityTypeService]] | Calls `createFallbackDefinition` | Creates fallback definition at entity type publish time |

---

## Public Interface

### Key Methods

---

#### `createRelationshipDefinition(workspaceId, sourceEntityTypeId, request): RelationshipDefinition`

```kotlin
@Transactional
@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
fun createRelationshipDefinition(
    workspaceId: UUID,
    sourceEntityTypeId: UUID,
    request: SaveRelationshipDefinitionRequest,
): RelationshipDefinition
```

- **Purpose:** Creates a new relationship definition and its target rules, then initializes semantic metadata for the definition.
- **When to use:** When a new relationship is being added to an entity type schema. Called by `EntityTypeService.handleSaveRelationshipDefinition` when the definition ID does not yet exist in the database.
- **Side effects:**
  - Saves a `RelationshipDefinitionEntity` to `relationship_definitions`
  - Saves one or more `RelationshipTargetRuleEntity` records to `relationship_target_rules`
  - Calls `semanticMetadataService.initializeForTarget(...)` with `targetType = RELATIONSHIP`
  - Logs `Activity.ENTITY_RELATIONSHIP / CREATE` to the activity audit trail
- **Throws:** Nothing directly; `requireNotNull` on the saved definition ID will throw `IllegalStateException` if the repository returns null (should not occur in practice).
- **Returns:** Hydrated `RelationshipDefinition` model including the saved target rules.

---

#### `updateRelationshipDefinition(workspaceId, definitionId, request): RelationshipDefinition`

```kotlin
@Transactional
@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
fun updateRelationshipDefinition(
    workspaceId: UUID,
    definitionId: UUID,
    request: SaveRelationshipDefinitionRequest,
): RelationshipDefinition
```

- **Purpose:** Updates the definition's mutable fields (`name`, `iconType`, `iconColour`, `allowPolymorphic`, `cardinalityDefault`) and diffs its target rules to reflect the current request state.
- **When to use:** When an existing relationship definition is being modified. Called by `EntityTypeService.handleSaveRelationshipDefinition` when the definition ID already exists.
- **Side effects:**
  - Saves the updated `RelationshipDefinitionEntity`
  - Performs target rule diff: deletes removed rules, saves updated and new rules
  - Logs `Activity.ENTITY_RELATIONSHIP / UPDATE` to the activity audit trail
- **Throws:** `NotFoundException` (via `ServiceUtil.findOrThrow`) if the definition does not exist in the workspace.
- **Returns:** Hydrated `RelationshipDefinition` model with the post-diff target rules.

---

#### `deleteRelationshipDefinition(workspaceId, definitionId, impactConfirmed): DeleteDefinitionImpact?`

```kotlin
@Transactional
@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
fun deleteRelationshipDefinition(
    workspaceId: UUID,
    definitionId: UUID,
    impactConfirmed: Boolean,
): DeleteDefinitionImpact?
```

- **Purpose:** Deletes a relationship definition using the two-pass impact pattern. On the first pass (when existing links are found and `impactConfirmed = false`), returns a `DeleteDefinitionImpact` describing how many entity relationship links will be affected. On the second pass (`impactConfirmed = true`) or when no links exist, executes the full deletion.
- **When to use:** When removing a relationship from an entity type schema (via `EntityTypeService`). Also called directly during `EntityTypeService.deleteEntityType` with `impactConfirmed = true` to cascade-delete all definitions.
- **Side effects (when deletion executes):**
  - Soft-deletes all `EntityRelationshipEntity` records with the given `definitionId`
  - Soft-deletes the `RelationshipDefinitionEntity`
  - Hard-deletes all `RelationshipTargetRuleEntity` records for the definition
  - Hard-deletes all `RelationshipDefinitionExclusionEntity` records for the definition
  - Calls `semanticMetadataService.deleteForTarget(...)` with `targetType = RELATIONSHIP`
  - Logs `Activity.ENTITY_RELATIONSHIP / DELETE` to the activity audit trail
- **Throws:**
  - `NotFoundException` (via `ServiceUtil.findOrThrow`) if the definition does not exist in the workspace
  - `IllegalStateException` if the definition is `protected = true`
- **Returns:** `DeleteDefinitionImpact` if the caller must confirm, `null` if deletion was executed.

---

#### `getDefinitionsForEntityType(workspaceId, entityTypeId): List<RelationshipDefinition>`

```kotlin
@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
fun getDefinitionsForEntityType(
    workspaceId: UUID,
    entityTypeId: UUID,
): List<RelationshipDefinition>
```

- **Purpose:** Returns all relationship definitions an entity type participates in. This includes both forward definitions (the type is the `sourceEntityTypeId`) and inverse definitions (the type appears as a `targetEntityTypeId` in a `RelationshipTargetRuleEntity`). Definitions where this entity type has an active exclusion record are filtered out from the inverse set.
- **When to use:** When rendering the full relationship view for an entity type in the schema editor, or when validating relationship payloads during entity saves (called by `EntityService`).
- **Side effects:** None. Read-only.
- **Throws:** Nothing. Returns an empty list if no definitions exist.
- **Returns:** Concatenation of forward models and inverse models, each hydrated with their target rules and exclusion lists. The returned list is not deduplicated — a definition can appear in both forward and inverse sets if the type is both the source and a target of the same definition.

---

#### `getDefinitionById(workspaceId, definitionId): RelationshipDefinition`

```kotlin
@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
fun getDefinitionById(
    workspaceId: UUID,
    definitionId: UUID,
): RelationshipDefinition
```

- **Purpose:** Retrieves a single relationship definition by ID, hydrated with its target rules.
- **When to use:** When a specific definition must be fetched by ID for display or validation.
- **Side effects:** None. Read-only.
- **Throws:** `NotFoundException` (via `ServiceUtil.findOrThrow`) if the definition does not exist in the workspace.
- **Returns:** Hydrated `RelationshipDefinition` model.

---

### System Definitions

System definitions are managed automatically by the platform and are not user-editable. The `SystemRelationshipType` enum (`CONNECTED_ENTITIES`) identifies these definitions via the `system_type` column on `relationship_definitions`.

---

#### `createFallbackDefinition(workspaceId, entityTypeId): RelationshipDefinitionEntity`

```kotlin
fun createFallbackDefinition(
    workspaceId: UUID,
    entityTypeId: UUID,
): RelationshipDefinitionEntity
```

- **Purpose:** Creates a CONNECTED_ENTITIES fallback definition for an entity type. Called at publish time to ensure every entity type has a system-managed connection definition.
- **When to use:** Called by [[EntityTypeService]] during `publishEntityType`.
- **Side effects:** Saves a `RelationshipDefinitionEntity` with `name = "Connected Entities"`, `allowPolymorphic = true`, `cardinalityDefault = MANY_TO_MANY`, `protected = true`, `systemType = CONNECTED_ENTITIES`.
- **Returns:** The saved entity.

---

#### `getOrCreateFallbackDefinition(workspaceId, entityTypeId): RelationshipDefinitionEntity`

```kotlin
fun getOrCreateFallbackDefinition(
    workspaceId: UUID,
    entityTypeId: UUID,
): RelationshipDefinitionEntity
```

- **Purpose:** Returns the existing fallback definition or creates one if absent. Handles concurrent creation via unique constraint (`uq_relationship_definition_system_type`) by catching `DataIntegrityViolationException` and retrying with a read.
- **When to use:** Called by [[EntityRelationshipService]] when creating a connection. Supports lazy creation for entity types published before the fallback feature existed.
- **Side effects:** May create a new `RelationshipDefinitionEntity` if none exists.
- **Throws:** `NotFoundException` (via `ServiceUtil.findOrThrow`) if the concurrent-creation retry also fails (should not occur in practice).
- **Returns:** The existing or newly created definition entity.

---

#### `getFallbackDefinitionId(entityTypeId): UUID?`

```kotlin
fun getFallbackDefinitionId(entityTypeId: UUID): UUID?
```

- **Purpose:** Read-only lookup returning the fallback definition ID, or null if none exists.
- **When to use:** Called by [[EntityRelationshipService]] when listing connections — avoids creating a definition just to check if connections exist.
- **Side effects:** None. Read-only.
- **Returns:** The definition UUID, or `null` if no fallback definition exists for this entity type.

### Exclusions

---

#### `excludeEntityTypeFromDefinition(workspaceId, definitionId, entityTypeId, impactConfirmed): DeleteDefinitionImpact?`

```kotlin
@Transactional
@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
fun excludeEntityTypeFromDefinition(
    workspaceId: UUID,
    definitionId: UUID,
    entityTypeId: UUID,
    impactConfirmed: Boolean,
): DeleteDefinitionImpact?
```

- **Purpose:** Excludes an entity type from a relationship definition (target-side opt-out). If the entity type has an explicit target rule for this definition, deletes the rule instead of creating an exclusion. For implicit matches (semantic group or polymorphic), creates a `RelationshipDefinitionExclusionEntity` record. Uses the two-pass impact pattern when existing instance links would be affected.
- **When to use:** When a target entity type wants to opt out of a relationship it is implicitly included in. Called by `EntityTypeService.removeEntityTypeDefinition` when `sourceEntityTypeKey` is set on the `DeleteRelationshipDefinitionRequest`.
- **Side effects (when exclusion executes):**
  - Soft-deletes instance links between entities of this type and the definition (via `softDeleteByDefinitionIdAndTargetEntityTypeId`)
  - Either deletes the explicit target rule OR creates an exclusion record
  - Logs `Activity.ENTITY_RELATIONSHIP / UPDATE` with `action = "exclude"`
- **Throws:** `IllegalArgumentException` if `entityTypeId` equals the definition's `sourceEntityTypeId` (cannot exclude the source type from its own definition)
- **Returns:** `DeleteDefinitionImpact` if the caller must confirm, `null` if exclusion was executed.

---

#### `removeExclusion(workspaceId, definitionId, entityTypeId)`

```kotlin
@Transactional
@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
fun removeExclusion(workspaceId: UUID, definitionId: UUID, entityTypeId: UUID)
```

- **Purpose:** Removes an exclusion, re-enabling the relationship for the entity type. The relationship will reappear in definition resolution and inverse link queries.
- **When to use:** When an entity type wants to reverse a previous opt-out.
- **Side effects:** Hard-deletes the `RelationshipDefinitionExclusionEntity` record. Logs `Activity.ENTITY_RELATIONSHIP / UPDATE` with `action = "remove_exclusion"`.
- **Returns:** Nothing.

---

## Key Logic

### Target Rule Diffing

The `diffTargetRules` private method reconciles the persisted target rules for a definition against the rules supplied in an update request. The algorithm is:

1. Load all existing `RelationshipTargetRuleEntity` records for the definition, indexed by ID.
2. Collect the set of IDs present in the request.
3. Any existing rules whose IDs are **not** in the request are deleted (hard-delete — configuration data).
4. For each requested rule:
   - If it carries an ID that matches an existing rule: update the existing entity (copy with new field values).
   - If it carries no ID or an unrecognized ID: create a new `RelationshipTargetRuleEntity`.
5. Save all updated/new rules with `saveAll`.

Rules without an ID in the request are always treated as new. This means the client must echo back the ID of an existing rule to preserve it on update.

---

### Two-Pass Impact Pattern

`deleteRelationshipDefinition` follows the established impact-confirmation pattern used elsewhere in the service layer for destructive operations:

**Pass 1 — impact check (`impactConfirmed = false`):**
```
count = entityRelationshipRepository.countByDefinitionId(definitionId)
if (count > 0) return DeleteDefinitionImpact(definitionId, name, count)
```

**Pass 2 — execution (`impactConfirmed = true` or `count == 0`):**
```
executeDeletion(entity, definitionId, workspaceId, userId)
return null
```

The caller (typically `EntityTypeService`) is responsible for surfacing the `DeleteDefinitionImpact` to the client and re-invoking with `impactConfirmed = true` after user acknowledgement.

---

### Inverse Definition Resolution

`getDefinitionsForEntityType` resolves two sources of definitions:

**Forward definitions:** `definitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, entityTypeId)` — definitions where the entity type is the authoritative source.

**Inverse definitions:** Target rules are first fetched with `targetRuleRepository.findByTargetEntityTypeId(entityTypeId)` (rules pointing to this type). The definition IDs from those rules are then used to load the parent `RelationshipDefinitionEntity` records via `findAllById`.

**Exclusion filtering:** After loading inverse definitions, exclusions are queried with `exclusionRepository.findByEntityTypeId(entityTypeId)`. Any inverse definitions whose IDs appear in the exclusion set are filtered out. Forward definitions (where the type is the source) are never filtered — the source owns the definition.

Both sets are hydrated with their full target rules and exclusion lists and returned as a combined list. All relationships are always bidirectional.

---

### Semantic Metadata Lifecycle

Relationship definitions have associated semantic metadata managed by [[EntityTypeSemanticMetadataService]]:

| Event | Call | Effect |
|---|---|---|
| Definition created | `semanticMetadataService.initializeForTarget(sourceEntityTypeId, workspaceId, RELATIONSHIP, definitionId)` | Creates an empty semantic metadata record for the relationship |
| Definition deleted | `semanticMetadataService.deleteForTarget(sourceEntityTypeId, RELATIONSHIP, definitionId)` | Hard-deletes the semantic metadata record |

Both hooks execute within the same `@Transactional` boundary as the triggering mutation.

---

### Fallback Definition Management

Every entity type gets a system-managed `CONNECTED_ENTITIES` relationship definition created at publish time. This definition:
- Has `protected = true` (cannot be deleted by users)
- Has `allowPolymorphic = true` (can link to any entity type)
- Has `cardinalityDefault = MANY_TO_MANY`
- Has `systemType = SystemRelationshipType.CONNECTED_ENTITIES`
- Has no target rules (polymorphic with no type restrictions)

**Concurrent creation safety:** `getOrCreateFallbackDefinition` handles race conditions via a unique constraint on `(source_entity_type_id, system_type)`. If two concurrent requests both try to create the fallback definition, the second one catches `DataIntegrityViolationException` and retries with a read. This avoids distributed locking.

---

### Deletion Order Within `executeDeletion`

The deletion steps are sequenced to preserve data integrity:

1. Soft-delete entity relationship links (`entityRelationshipRepository.softDeleteByDefinitionId`)
2. Soft-delete the definition entity (`definitionRepository.save` with `deleted = true`)
3. Hard-delete target rules (`targetRuleRepository.deleteByRelationshipDefinitionId`)
4. Hard-delete exclusion records (`exclusionRepository.deleteByRelationshipDefinitionId`)
5. Clean up semantic metadata (`semanticMetadataService.deleteForTarget`)
6. Log activity

Target rules and exclusion records are hard-deleted because they are schema configuration data, not user-generated data. Entity relationship links are soft-deleted to preserve auditability.

---

## Data Access

### Entities Owned

| Entity | Table | Operations | Notes |
|---|---|---|---|
| `RelationshipDefinitionEntity` | `relationship_definitions` | Create, Read, Update, Soft-delete | Extends `AuditableSoftDeletableEntity`; has `protected` flag |
| `RelationshipTargetRuleEntity` | `relationship_target_rules` | Create, Read, Update, Hard-delete | Extends `AuditableEntity` (not soft-deletable — config data) |
| `RelationshipDefinitionExclusionEntity` | `relationship_definition_exclusions` | Create, Read, Hard-delete | Extends `AuditableEntity` (not soft-deletable — config data); unique on (definition_id, entity_type_id) |

### Queries Used

| Repository | Method | Purpose | Notes |
|---|---|---|---|
| `RelationshipDefinitionRepository` | `findByWorkspaceIdAndSourceEntityTypeId` | Load all forward definitions for a type | Used in `getDefinitionsForEntityType` |
| `RelationshipDefinitionRepository` | `findByIdAndWorkspaceId` | Single definition lookup scoped to workspace | Used in update, delete, and `getDefinitionById` |
| `RelationshipDefinitionRepository` | `findAllById` | Batch load inverse definitions by ID list | Used in `getDefinitionsForEntityType` after rule lookup |
| `RelationshipTargetRuleRepository` | `findByRelationshipDefinitionId` | Load all rules for a single definition | Used in diff, hydration |
| `RelationshipTargetRuleRepository` | `findByRelationshipDefinitionIdIn` | Batch load rules for multiple definitions | Used in `getDefinitionsForEntityType` hydration |
| `RelationshipTargetRuleRepository` | `findByTargetEntityTypeId` | Find rules targeting a type | Used in inverse definition resolution |
| `RelationshipTargetRuleRepository` | `deleteByRelationshipDefinitionId` | Hard-delete all rules for a definition | Used in `executeDeletion` |
| `RelationshipDefinitionExclusionRepository` | `findByEntityTypeId` | Find exclusions for a type | Used in inverse definition filtering |
| `RelationshipDefinitionExclusionRepository` | `findByRelationshipDefinitionId` | Find exclusions for a definition | Used in model hydration |
| `RelationshipDefinitionExclusionRepository` | `findByRelationshipDefinitionIdIn` | Batch load exclusions for multiple definitions | Used in batch definition resolution |
| `RelationshipDefinitionExclusionRepository` | `deleteByRelationshipDefinitionId` | Hard-delete all exclusions for a definition | Used in `executeDeletion` |
| `EntityRelationshipRepository` | `countByDefinitionId` | Count active links for impact analysis | Used in definition deletion impact check |
| `EntityRelationshipRepository` | `countByDefinitionIdAndTargetEntityTypeId` | Count links for a specific target type | Used in exclusion impact check |
| `EntityRelationshipRepository` | `softDeleteByDefinitionId` | Bulk soft-delete all links for a definition | Native SQL `@Modifying` query |
| `EntityRelationshipRepository` | `softDeleteByDefinitionIdAndTargetEntityTypeId` | Soft-delete links for a specific target type | Native SQL `@Modifying` query; used during exclusion |

---

## Error Handling

### Errors Thrown

| Exception | When | HTTP Mapping |
|---|---|---|
| `NotFoundException` | Definition not found by ID + workspace in `findOrThrow` | 404 Not Found |
| `IllegalStateException` | Attempt to delete a `protected = true` definition | 400 Bad Request (via `@ControllerAdvice`) |

### Errors Handled

| Exception | Source | Recovery Strategy |
|---|---|---|
| None | N/A | All errors propagate to `@ControllerAdvice` |

---

## Observability

### Log Events

| Event | Level | When | Key Fields |
|---|---|---|---|
| `Activity.ENTITY_RELATIONSHIP / CREATE` | INFO | Definition created | `relationshipId`, `relationshipName`, `sourceEntityTypeId` |
| `Activity.ENTITY_RELATIONSHIP / UPDATE` | INFO | Definition updated | `relationshipId`, `relationshipName`, `sourceEntityTypeId` |
| `Activity.ENTITY_RELATIONSHIP / DELETE` | INFO | Definition deleted (execution pass only) | `relationshipId`, `relationshipName`, `sourceEntityTypeId` |
| Structured log: "Created relationship definition..." | INFO | After successful create | definition name, ID, source entity type ID |
| Structured log: "Updated relationship definition..." | INFO | After successful update | definition name, ID |
| Structured log: "Deleted relationship definition..." | INFO | After successful deletion | definition name, ID |

Activity log `entityType` is always `ApplicationEntityType.ENTITY_TYPE` with `entityId` set to `sourceEntityTypeId`.

---

## Gotchas & Edge Cases

> [!warning] Impact check does NOT fire when called during entity type deletion
> `EntityTypeService.deleteEntityType` calls `deleteRelationshipDefinition` with `impactConfirmed = true` unconditionally for all definitions on the type. This bypasses the two-pass pattern. Any associated entity relationship links are soft-deleted silently as part of the cascade. This is intentional — the entity type deletion itself is guarded by its own impact-confirmation flow.

> [!warning] Target rules are hard-deleted — no soft-delete trail
> `RelationshipTargetRuleEntity` does not extend `SoftDeletable`. Rules removed during a diff or a definition deletion are permanently gone. If a rollback is needed, re-creation from the client request is the only recovery path.

> [!warning] Definition can appear in both forward and inverse lists
> If an entity type is both the `sourceEntityTypeId` of a definition AND a `targetEntityTypeId` in the same definition's rules (self-referential relationship), `getDefinitionsForEntityType` will return the definition twice — once in `forwardModels` and once in `inverseModels`. Callers should be aware of this and deduplicate if required.

> [!warning] Rule ID must be echoed to preserve it on update
> During `updateRelationshipDefinition`, target rules with no `id` in the request are always created as new. A rule that was previously saved will be deleted if its ID is not included in the update request's `targetRules` list. Clients must read and echo back existing rule IDs to preserve them.

> [!warning] Polymorphic definitions may have zero target rules
> When `allowPolymorphic = true`, target rules are optional. `buildTargetRuleEntities` will produce an empty list and `saveAll` will be a no-op. The definition is valid with no rules.

> [!warning] Fallback definitions have no target rules
> Unlike user-created definitions, CONNECTED_ENTITIES definitions have zero `RelationshipTargetRuleEntity` records. This means inverse query visibility relies on the `system_type` column match (via LEFT JOIN) rather than the `inverse_visible` flag on target rules.

> [!warning] No semantic metadata hooks for fallback definitions
> `createFallbackDefinition` does NOT call `semanticMetadataService.initializeForTarget(...)`. System definitions do not participate in the semantic metadata lifecycle.

> [!warning] Exclusions are hard-deleted — no soft-delete trail
> `RelationshipDefinitionExclusionEntity` does not extend `SoftDeletable`. Exclusions removed via `removeExclusion` or definition deletion are permanently gone. Re-excluding requires creating a new exclusion record.

> [!warning] Explicit target rules are deleted, not excluded
> When `excludeEntityTypeFromDefinition` finds an explicit target rule for the entity type, it deletes the rule rather than creating an exclusion record. This is intentional — the rule's existence is the mechanism for inclusion, so deleting it is the correct way to remove the type.

### Known Limitations

| Limitation | Impact | Severity |
|---|---|---|
| No deduplication in `getDefinitionsForEntityType` | Self-referential definitions appear twice in the result | Low — uncommon pattern but possible |
| No validation that `targetEntityTypeId` in rules refers to a real entity type | Orphaned rule references if entity type is deleted without cascade | Medium |
| Soft-deleted definition link count includes links soft-deleted in prior runs | `countByDefinitionId` may overcount if the query includes soft-deleted rows | Low — needs query audit if this causes issues |

---

## Technical Debt

| Issue | Impact | Effort |
|---|---|---|
| No validation that `targetEntityTypeId` exists in the workspace before saving rules | Can create rules pointing to deleted or foreign entity types | Medium |
| `getDefinitionsForEntityType` issues two `findByRelationshipDefinitionIdIn` calls (one for forward, one for inverse) | Extra query when both sets share definitions; minor N+1 risk | Low |
| `diffTargetRules` uses unrecognized request IDs as "new" rather than throwing | Silent data corruption if a stale client sends an old ID not in the DB | Low |

---

## Testing

### Unit Test Coverage

- **Location:** `src/test/kotlin/riven/core/service/entity/type/EntityTypeRelationshipServiceTest.kt`
- **Framework:** JUnit 5, `mockito-kotlin` (`whenever` / `verify`)
- **Auth context:** `@WithUserPersona` with `WorkspaceRoles.OWNER`

| Scenario | Covered |
|---|---|
| Create with single target rule | Yes |
| Create with multiple target rules | Yes |
| Create polymorphic (empty rules) | Yes |
| Create with semantic type constraint | Yes |
| Update: change name | Yes |
| Update: add target rule | Yes |
| Update: remove target rule (diff deletes) | Yes |
| Update: change cardinality default | Yes |
| Delete: no instance data — executes immediately | Yes |
| Delete: instance data present, not confirmed — returns impact | Yes |
| Delete: protected definition — throws `IllegalStateException` | Yes |
| Delete: soft-deletes associated links when confirmed | Yes |
| Delete: cleans up exclusion records | Yes |
| Exclude: explicit target rule — deletes rule | Yes |
| Exclude: semantic/polymorphic match — creates exclusion record | Yes |
| Exclude: returns impact when instance data exists and not confirmed | Yes |
| Exclude: soft-deletes links and creates exclusion when confirmed | Yes |
| Exclude: rejects exclusion of source entity type | Yes |
| Remove exclusion: deletes exclusion record | Yes |
| Read: returns both forward and inverse definitions | Yes |
| Read: filters out excluded inverse definitions | Yes |

### How to Test Manually

1. Create two entity types (e.g., Task and Person) via `POST /api/v1/workspaces/{id}/entity-types`.
2. Save a relationship definition on Task pointing to Person via `POST /api/v1/workspaces/{id}/entity-types/{id}` (include a `relationships` entry in the save request).
3. Verify the definition appears for Task via a read of the entity type schema.
4. Set `inverseVisible = true` on the target rule and re-save. Verify the definition appears in `getDefinitionsForEntityType` for Person (inverse-visible lookup).
5. Create entity instances of both types and link them via the relationship.
6. Attempt to delete the relationship definition without `impactConfirmed`. Verify a `DeleteDefinitionImpact` is returned with `impactedLinkCount > 0`.
7. Re-issue deletion with `impactConfirmed = true`. Verify definition is soft-deleted and links are soft-deleted.
8. Attempt to delete a `protected = true` definition and confirm a 400 is returned.

---

## Related

- [[Relationships]] — Parent subdomain
- [[EntityTypeService]] — Orchestrates entity type saves and delegates here for relationship mutations
- [[EntityRelationshipService]] — Manages entity-to-entity link instance data (downstream consumer of definitions)
- [[EntityTypeSemanticMetadataService]] — Initialized and cleaned up by this service on definition lifecycle events
- `RelationshipDefinitionEntity` — JPA entity for `relationship_definitions`
- `RelationshipTargetRuleEntity` — JPA entity for `relationship_target_rules`
- `DeleteDefinitionImpact` — Data class returned from the first pass of the delete flow

---

## Changelog

| Date | Change | Reason |
|---|---|---|
| 2026-02-08 | Initial documentation | Phase 2 Plan 2 — Entities domain Relationships subdomain component docs |
| 2026-02-19 | Added `EntityTypeSemanticMetadataService` dependency and lifecycle hooks for relationship metadata | Semantic Metadata Foundation |
| 2026-02-21 | Complete rewrite — removed ORIGIN/REFERENCE bidirectional sync architecture, replaced with `relationship_definitions` + `relationship_target_rules` table model; documented two-pass impact pattern, target rule diff algorithm, inverse visibility resolution, new dependencies, and full test coverage table | Entity relationships architecture migration |
| 2026-03-01 | Added system definition management — `createFallbackDefinition`, `getOrCreateFallbackDefinition`, `getFallbackDefinitionId` for CONNECTED_ENTITIES fallback definitions | Entity Connections |
| 2026-03-06 | Always bidirectional — removed `inverseVisible` flag. Added `excludeEntityTypeFromDefinition` and `removeExclusion` methods, `RelationshipDefinitionExclusionRepository` dependency, exclusion filtering in `getDefinitionsForEntityType/Types`, exclusion cleanup in `executeDeletion`. New `RelationshipDefinitionExclusionEntity` and `relationship_definition_exclusions` table. | Target-Side Exclusions / Always Bidirectional |
