# Codebase Concerns

**Analysis Date:** 2026-04-12

## Tech Debt

### Circuit Breaker Missing on Sync Failures

**Issue:** `NangoWebhookService.handleSyncEvent()` dispatches Temporal workflows for every inbound sync webhook without checking connection health. If Nango keeps sending webhooks while the connection is in DEGRADED or FAILED state (due to consecutive failures), the system spawns unbounded failed workflows.

**Files:** 
- `core/src/main/kotlin/riven/core/service/integration/NangoWebhookService.kt` (lines 152-198)
- `core/src/main/kotlin/riven/core/service/integration/sync/IntegrationSyncActivitiesImpl.kt`

**Impact:** 
- Consumes Temporal worker capacity with failed workflows
- Noisy logs and false signals of ongoing sync problems
- No backstop when an integration connection is permanently broken

**Fix approach:** Add a guard in `handleSyncEvent()` that checks `IntegrationHealthService.DEGRADED_THRESHOLD` before `workflowClient.start()`. Skip dispatch if connection status is DEGRADED or FAILED. Implement a manual "Force Retry" action endpoint to reset the state when an admin fixes the underlying integration issue.

**Priority:** P1 (implement before production multi-instance deployment)

---

### STALE Connection Status Never Transitions

**Issue:** `ConnectionStatus` enum includes STALE state with valid transitions defined, but nothing in the codebase transitions connections to STALE. Connections that stop syncing (e.g., Nango sync disabled, provider API revoked) remain in HEALTHY/DEGRADED indefinitely.

**Files:**
- `core/src/main/kotlin/riven/core/enums/integration/ConnectionStatus.kt` (enum definition)
- `core/src/main/kotlin/riven/core/service/integration/IntegrationHealthService.kt` (health evaluation logic)

**Impact:** 
- Users see inaccurate connection health
- Dead connections are not surfaced for investigation
- No signal that a data source has gone silent

**Fix approach:** Implement one of two approaches:
1. Scheduled job that scans `IntegrationConnectionEntity.lastSyncedAt` timestamps and transitions to STALE if older than configurable threshold (e.g., 7 days)
2. Check within `IntegrationHealthService.evaluateConnectionHealth()` if `syncState.updatedAt` is older than threshold and transition to STALE

Add a configurable threshold property in `application.yml`.

**Priority:** P2 (nice-to-have for operational clarity)

---

### No Verification on Consecutive Sync Failures

**Issue:** `IntegrationSyncActivitiesImpl` increments `consecutiveFailureCount` but there is no circuit breaker preventing rapid re-execution of failed workflows. If sync consistently fails, the same workflow could fail 100 times in rapid succession, consuming Temporal capacity.

**Files:**
- `core/src/main/kotlin/riven/core/service/integration/sync/IntegrationSyncActivitiesImpl.kt` (lines 85-145)
- `core/src/main/kotlin/riven/core/service/integration/NangoWebhookService.kt` (lines 152-198)

**Impact:** 
- Runaway workflow execution during outages
- False signal of system busyness (actually just retry spam)
- Temporal task queue congestion

**Fix approach:** Same as circuit breaker above — guard `handleSyncEvent()` dispatch with connection status check.

---

## Incomplete/Unimplemented Features

### Workflow Deserialization Lacks PARSE Config Support

**Issue:** `WorkflowNodeDeserializer.deserializeParseConfig()` is stubbed to throw an error. PARSE category nodes (data transformation, parsing) cannot be deserialized from workflow definitions.

**Files:**
- `core/src/main/kotlin/riven/core/deserializer/WorkflowNodeDeserializer.kt` (lines 62-76, line 122)

**Impact:** 
- Workflows with PARSE nodes fail at deserialization time
- Cannot execute data transformation steps in workflows
- Blocks Phase 5+ workflow capabilities

**Fix approach:** Create concrete `WorkflowParseConfig` subclasses (e.g., `JsonParseConfig`, `CsvParseConfig`) with field-level validation. Update deserializer to route to appropriate subclass. Add tests for each parse type.

**Priority:** P2 (needed for Phase 5 workflow expansion)

---

### Workflow Control Flow Nodes Incomplete

**Issue:** `WorkflowNodeDeserializer.deserializeControlConfig()` only routes CONDITION nodes. SWITCH, LOOP, and PARALLEL are mentioned as Phase 5+ work in comments (line 143) but have no infrastructure.

**Files:**
- `core/src/main/kotlin/riven/core/deserializer/WorkflowNodeDeserializer.kt` (line 143)
- `core/src/main/kotlin/riven/core/service/workflow/engine/coordinator/WorkflowCoordinationService.kt` (line 192)

**Impact:** 
- Workflow branching, looping, and parallelism not available
- Limits automation to linear, single-path workflows
- Reduces product versatility

**Fix approach:** Post-Phase 3 work. Design and implement `WorkflowSwitchConfig`, `WorkflowLoopConfig`, `WorkflowParallelConfig`. Update graph coordinator to handle multiple control flow exit paths.

**Priority:** P2 (Phase 5 feature)

---

### Workflow FUNCTION Nodes Not Implemented

**Issue:** `WorkflowFunctionConfig.execute()` returns a TODO placeholder (line 70). FUNCTION nodes cannot invoke external functions or custom business logic.

**Files:**
- `core/src/main/kotlin/riven/core/models/workflow/node/config/WorkflowFunctionConfig.kt` (line 70)

**Impact:** 
- Cannot execute user-defined functions in workflows
- Limits extensibility

**Fix approach:** Design FUNCTION execution model in Phase 5 — decide whether functions are user-uploaded code (Wasm, JDWP), SDK-registered handlers, or external service calls. Implement execute() with appropriate error handling.

**Priority:** P3 (Phase 5+ feature)

---

### Email Notifications Not Implemented

**Issue:** `WorkspaceInviteService.acceptInvitation()` and `rejectInvitation()` have TODO comments for sending acceptance/rejection emails (lines 151, 161). Users receive no confirmation when they accept/reject invites.

**Files:**
- `core/src/main/kotlin/riven/core/service/workspace/WorkspaceInviteService.kt` (lines 151, 161)

**Impact:** 
- Poor user experience — no notification of invite status change
- No audit trail visible to users
- Inviters unaware of accept/reject outcomes without logging in

**Fix approach:** Integrate with email service (Resend, SendGrid, or internal mail relay). Create email templates for accept/reject with personalized links back to workspace. Send via async event after invite status update. Depends on notification system being implemented.

**Priority:** P2 (improves onboarding UX)

---

## Fragile Areas

### Long Methods with Inline Phase Comments

**Issue:** Three service methods exceed 100 lines and use inline `// PHASE 1:`, `// 1.` comments instead of extracting into named private methods:

- `BlockEnvironmentService.executeOperations()` (~190 lines)
- `EntityRelationshipService.saveRelationships()` (~130 lines) 
- `EntityTypeService.saveEntityTypeDefinition()` (~130 lines)

**Files:**
- `core/src/main/kotlin/riven/core/service/block/BlockEnvironmentService.kt`
- `core/src/main/kotlin/riven/core/service/entity/EntityRelationshipService.kt`
- `core/src/main/kotlin/riven/core/service/entity/type/EntityTypeService.kt`

**Impact:** 
- Hard to follow execution flow
- Difficult to unit test individual steps
- High cognitive load for modifications
- Increases bug surface area

**Fix approach:** Extract each phase into a named private method. Example:
```kotlin
private fun executeOperations(...): SaveEnvironmentResponse {
    val result = validateOperations()
    val trees = applyStructuralChanges()
    val saved = persistBlocks()
    val layout = reconcileLayout()
    recordActivity()
    return buildResponse()
}
```

**Safe modification path:** Use IDE's "Extract Method" refactoring. Each extracted method should have a single, clear responsibility. Start with the longest method first.

**Priority:** P3 (refactoring, no functional change)

---

### Schema Validation TODOs in Entity Service

**Issue:** `EntityValidationService.validateEntityAgainstSchema()` has two TODO comments (lines 119, 136) noting that attribute validation should eventually query the current workspace schema, but currently uses hardcoded schema logic.

**Files:**
- `core/src/main/kotlin/riven/core/service/entity/EntityValidationService.kt` (lines 119, 136)

**Impact:** 
- Validation may not reflect latest schema changes if schema is modified between attribute creation and validation
- Inconsistency between what's allowed at creation time vs. validation time

**Fix approach:** Refactor to accept `EntityTypeEntity` or schema snapshot as parameter, then validate against that schema rather than querying in the validator. This is a design shift, not just a TODO fill-in. Requires careful coordination with callers.

**Priority:** P2 (correctness concern)

---

### Block Reference and Default Environment Unimplemented

**Issue:** `DefaultBlockEnvironmentService.createDefaultEnvironment()` has two TODO comments (lines 71-72) noting that reference block creation and entity-specific layout templates are not yet implemented.

**Files:**
- `core/src/main/kotlin/riven/core/service/block/DefaultBlockEnvironmentService.kt` (lines 71-72)

**Impact:** 
- Default environments for new entity types are minimal/empty
- No starter blocks to guide users on entity-specific layouts
- Users must manually create blocks from scratch

**Fix approach:** Design default block templates per entity type (contact, deal, ticket, etc.). Implement factory methods in `BlockReferenceService` to create reference blocks. Wire into `createDefaultEnvironment()` when a new entity type is created.

**Priority:** P2 (improves initial UX)

---

### Block Content Validation Incomplete

**Issue:** `BlockEnvironmentService.modifyBlocks()` has a TODO to validate block content (line 612). Blocks are persisted without schema validation against their block type definitions.

**Files:**
- `core/src/main/kotlin/riven/core/service/block/BlockEnvironmentService.kt` (line 612)

**Impact:** 
- Invalid block content could be saved (violates schema)
- Hydration or rendering could fail unexpectedly downstream
- Data integrity issue

**Fix approach:** Before saving blocks, validate their `content` field against the block type's schema via `SchemaService.validate()`. Throw `SchemaValidationException` if validation fails.

**Priority:** P2 (data integrity concern)

---

### Workflow Node Config Mapping Incomplete

**Issue:** `WorkflowCoordinationService.executeNode()` has a TODO at line 192 noting that node config type mapping is incomplete. The `when` expression for mapping configs to input maps is hard-coded and incomplete.

**Files:**
- `core/src/main/kotlin/riven/core/service/workflow/engine/coordinator/WorkflowCoordinationService.kt` (line 192)

**Impact:** 
- New node types require manual code changes to add config mapping
- Risk of missing a case and silently passing empty inputs
- Not scalable as node types expand

**Fix approach:** Refactor to use polymorphic dispatch. Each `WorkflowNodeConfig` subclass should implement a `toInputMap(): Map<String, Any?>` method. Remove the hard-coded `when` expression.

**Priority:** P2 (maintainability concern)

---

### Workflow Execution Record Lacks Type Safety

**Issue:** `ExecutionRecord.kt` (line 19) has a TODO noting that proper payload types should replace the current loose `Any?` storage.

**Files:**
- `core/src/main/kotlin/riven/core/models/workflow/engine/execution/ExecutionRecord.kt` (line 19)

**Impact:** 
- Payload fields lose type information at runtime
- IDE/compiler cannot catch type mismatches in payloads
- Debugging execution failures is harder

**Fix approach:** Create sealed class hierarchy for workflow payloads (e.g., `WorkflowPayload` with subtypes for each node type). Update `ExecutionRecord` to use typed payload fields instead of `Any?`.

**Priority:** P3 (developer experience improvement)

---

### Workflow Metadata Version Hardcoded

**Issue:** `WorkflowCoordinationService.executeWorkflow()` hardcodes workflow version to 1 (line 101) with a TODO to fetch from definition. The version in `WorkflowMetadata` will always be 1 regardless of actual definition version.

**Files:**
- `core/src/main/kotlin/riven/core/service/workflow/engine/coordinator/WorkflowCoordinationService.kt` (line 101)

**Impact:** 
- Cannot track which workflow definition version executed
- Audit trail loses version context
- Debugging execution failures requires manual lookup

**Fix approach:** Fetch `version` from `WorkflowDefinitionEntity` when loading the definition. Pass to `WorkflowMetadata` constructor.

**Priority:** P2 (audit trail completeness)

---

### Workflow Trigger Context Not Set

**Issue:** `WorkflowCoordinationService.executeWorkflow()` has a TODO at line 110 noting that trigger context is not set in the data store. Trigger-related data is not available to nodes that need it.

**Files:**
- `core/src/main/kotlin/riven/core/service/workflow/engine/coordinator/WorkflowCoordinationService.kt` (line 110)

**Impact:** 
- Nodes cannot access trigger metadata (e.g., which entity triggered the workflow)
- Workflow execution is context-blind
- Limits trigger-aware logic

**Fix approach:** Extract trigger context from `WorkflowDataStore.state` and populate it into the store when executing workflows triggered by events. This requires trigger execution to be wired in Phase 4+.

**Priority:** P2 (Phase 4 feature)

---

### Workflow Sequence Index Not Tracked

**Issue:** `WorkflowCoordinationService.executeNode()` hardcodes `sequenceIndex = 0` (line 305) with a TODO to track actual sequence. Step output ordering is not preserved.

**Files:**
- `core/src/main/kotlin/riven/core/service/workflow/engine/coordinator/WorkflowCoordinationService.kt` (line 305)

**Impact:** 
- Cannot reconstruct execution order from execution records
- Debugging multi-step workflows is harder
- Audit trail loses temporal ordering

**Fix approach:** Track step execution sequence during graph traversal. Increment a sequence counter as nodes complete. Pass to `StepOutput` constructor.

**Priority:** P2 (audit trail completeness)

---

### Workflow Parallel Execution is Sequential

**Issue:** `WorkflowCoordinationService.executeWorkflow()` (line 115) has a comment noting that parallel execution is "just glorified sequential execution for now." Multiple ready nodes are executed serially, not truly in parallel.

**Files:**
- `core/src/main/kotlin/riven/core/service/workflow/engine/coordinator/WorkflowCoordinationService.kt` (line 115)

**Impact:** 
- Workflows with parallel branches execute sequentially, missing performance benefit
- Long-running workflows with independent steps are slower than necessary
- False signal of parallel capability

**Fix approach:** Use Temporal child workflows for true parallel execution. Spawn one child workflow per ready node, merge results after all complete.

**Priority:** P3 (performance optimization, Phase 5 feature)

---

## Pagination and Scaling Concerns

### EntityQueryService Uses LIMIT/OFFSET Pagination

**Issue:** `EntityQueryService` uses LIMIT/OFFSET pagination via `QueryPagination(limit, offset, orderBy)`. This causes duplicate/skip bugs with infinite scroll when data changes between page loads.

**Files:**
- `core/src/main/kotlin/riven/core/service/entity/query/EntityQueryService.kt`

**Impact:** 
- Infinite scroll lists may show duplicate items or skip items
- Poor UX for users scrolling through large entity lists
- Affects all entity-scoped queries (workspace notes, entity lists, etc.)

**Fix approach:** Migrate to cursor-based pagination using the shared `CursorPagination` utility (created in workspace notes PR). Replace `QueryPagination` model to use cursor instead of offset. Update `EntityQueryAssembler` to generate `WHERE (sort_col < :cursorVal OR (sort_col = :cursorVal AND id < :cursorId))` instead of `OFFSET`. Update frontend hooks to use cursor pagination.

**Priority:** P2 (correctness concern)

**Depends on:** CursorPagination utility must exist in workspace notes PR

---

### Query Filter Branch Type Validation Incomplete

**Issue:** `QueryFilterValidator.validateInCondition()` (line 184) has a TODO noting that branch entity type validation is incomplete. Cross-references are not verified between filter and entity definition.

**Files:**
- `core/src/main/kotlin/riven/core/service/entity/query/QueryFilterValidator.kt` (line 184)

**Impact:** 
- Invalid entity type references in filters may pass validation
- Queries fail at execution time with unclear errors
- Poor error messages for malformed filters

**Fix approach:** Load entity type definition and validate that branch references match known entity types. Throw `QueryFilterException` with clear message if validation fails.

**Priority:** P2 (correctness concern)

---

## Missing Edge Case Handling

### Null Display Name in Entity Context

**Issue:** Frontend needs to handle null `WorkspaceNote.entityDisplayName` in workspace notes list. Backend sub-SELECT for display name returns null if entity's identifier attribute was deleted or never set.

**Files:**
- Frontend: note list DataTable, breadcrumb in editor (location TBD in client codebase)
- Backend context: `WorkspaceNote` model, `EntityContext` enrichment

**Impact:** 
- Blank cells in notes list UI
- Missing context in breadcrumbs
- Poor UX for notes attached to entities without display names

**Fix approach:** Frontend should show fallback (e.g., "Unnamed", entity type + ID, or UUID) when `entityDisplayName` is null. Update both list and breadcrumb components.

**Priority:** P2 (UX polish)

---

## Performance Bottlenecks

### EXPLAIN ANALYZE on Entity-Scoped Queries Not Done

**Issue:** All entity-scoped note queries now JOIN through `note_entity_attachments` instead of direct `WHERE entity_id`. Index usage and query performance not verified with realistic data.

**Files:**
- Database schema: `note_entity_attachments` table with index `idx_note_attachments_entity`
- Repository: `EntityNoteRepository` queries

**Impact:** 
- Risk of slow queries with large note volumes
- Unknown index hit rate
- Could regress performance on workspace notes feature

**Fix approach:** Post-deployment to staging with realistic data volume, run `EXPLAIN ANALYZE` on:
- `findByEntityIdAndWorkspaceId()`
- `searchByEntityIdAndWorkspaceId()`
- `findEntityContext()`

Verify index hit rate and query cost. Adjust indexes or query strategy if needed.

**Priority:** P2 (post-deployment verification)

**Depends on:** Entity-spanning notes migration deployed

---

## Cross-Domain Data Consistency

### Relationship Reconciliation Not Implemented

**Issue:** Only attribute schema reconciliation is implemented in `SchemaReconciliationService`. When core models add or remove `RelationshipDefinitionEntity` rows, existing workspaces do not get the updates.

**Files:**
- `core/src/main/kotlin/riven/core/service/schema/SchemaReconciliationService.kt` (primary reconciliation logic)
- Affected: Any workspace with entity types that should have new relationships from core model updates

**Impact:** 
- Workspaces miss new cross-type relationships added in core model updates
- Incomplete relationship graph
- Users cannot leverage new relationship types without manual intervention

**Fix approach:** Extend `SchemaReconciliationService` to also reconcile `RelationshipDefinitionEntity` rows. Use same hash-and-compare approach as attributes. Apply new relationships, remove deprecated ones. Requires separate diff/apply logic from attributes.

**Priority:** P2 (completeness concern, follows attribute reconciliation)

---

## Architectural Inconsistencies

### @Valid Usage Inconsistent on Request Bodies

**Issue:** `BlockEnvironmentController` uses `@Valid` on `@RequestBody` parameters; `EntityController` does not. Neither approach is consistently applied across the codebase.

**Files:**
- `core/src/main/kotlin/riven/core/controller/block/BlockEnvironmentController.kt` (uses `@Valid`)
- `core/src/main/kotlin/riven/core/controller/entity/EntityController.kt` (no `@Valid`)
- All other controllers: varies

**Impact:** 
- Request validation is inconsistent
- Some requests may pass with invalid data
- Validation failures produce inconsistent error responses

**Fix approach:** Standardize on always using `@Valid` for request bodies that have validation annotations (e.g., `@NotNull`, `@Size`). Update all controllers to follow the pattern consistently.

**Priority:** P3 (code consistency)

---

### Error Handling in EntityController.deleteEntity Inline

**Issue:** `EntityController.deleteEntity()` checks `response.error` and returns status codes manually instead of using the exception hierarchy. This is a pattern violation — errors should throw exceptions.

**Files:**
- `core/src/main/kotlin/riven/core/controller/entity/EntityController.kt` (deleteEntity method)

**Impact:** 
- Inconsistent error handling pattern
- Harder to debug error flows
- Exception advice logic is bypassed

**Fix approach:** Refactor `deleteEntity()` service to throw domain exceptions (`NotFoundException`, `ConflictException`, etc.) instead of returning error flags. Let `@ControllerAdvice` handle HTTP status mapping.

**Priority:** P3 (code consistency)

---

### userId Retrieval Style Inconsistent

**Issue:** Services use two patterns for retrieving userId:
1. `val userId = authTokenService.getUserId()` at top of method (e.g., `WorkflowDefinitionService`)
2. Wrapping entire body in `authTokenService.getUserId().let { userId -> ... }` (e.g., `BlockEnvironmentService`, `EntityTypeService`)

**Files:**
- `core/src/main/kotlin/riven/core/service/block/BlockEnvironmentService.kt` (uses `.let`)
- `core/src/main/kotlin/riven/core/service/entity/type/EntityTypeService.kt` (uses `.let`)
- `core/src/main/kotlin/riven/core/service/workflow/WorkflowDefinitionService.kt` (uses `val` assignment)

**Impact:** 
- Inconsistent code style
- Unnecessary nesting with `.let` approach

**Fix approach:** Standardize on `val userId = authTokenService.getUserId()` at method top. Three separate `val` statements are easier to debug than nested `.let` chains.

**Priority:** P3 (code consistency)

---

### KLogger Injection Pattern Inconsistency

**Issue:** `WorkflowGraphService` uses module-level `KotlinLogging.logger {}` instead of constructor-injected `KLogger` bean. This is a pattern violation — all services should use injected loggers.

**Files:**
- `core/src/main/kotlin/riven/core/service/workflow/WorkflowGraphService.kt`

**Impact:** 
- Inconsistent logger setup across codebase
- Logger not wired through dependency injection
- Logging level cannot be controlled via Spring config

**Fix approach:** Inject `KLogger` via constructor using the prototype-scoped bean from `LoggerConfig`. Remove the module-level logger declaration.

**Priority:** P3 (code consistency)

---

### Duplicate Workspace Verification Logic

**Issue:** `WorkflowDefinitionService` and `WorkflowGraphService` use both `@PreAuthorize` annotations AND inline `if (entity.workspaceId != workspaceId)` checks. The inline check is only needed when repository query doesn't filter by workspace.

**Files:**
- `core/src/main/kotlin/riven/core/service/workflow/WorkflowDefinitionService.kt`
- `core/src/main/kotlin/riven/core/service/workflow/WorkflowGraphService.kt`

**Impact:** 
- Redundant security checks
- Harder to reason about authorization flow
- Risk of forgetting one layer during refactoring

**Fix approach:** Ensure repository queries filter by workspace. Remove inline checks when `@PreAuthorize` is sufficient. Inline checks only needed when fetching by non-workspace-scoped ID.

**Priority:** P3 (code consistency)

---

## Security Considerations

### Nango Tag Field Repurposing

**Issue:** `NangoWebhookService` repurposes Nango's `endUserEmail` tag field to carry `integrationDefinitionId` (a UUID) because Nango doesn't support custom metadata fields. If Nango validates field formats in the future, this breaks.

**Files:**
- `core/src/main/kotlin/riven/core/service/integration/NangoWebhookService.kt` (lines 72-86, tag mapping documentation)

**Impact:** 
- Brittle integration with Nango API
- If Nango validates email format, auth webhooks fail
- Non-obvious behavior for future maintainers

**Fix approach:** 
1. Monitor Nango API for custom metadata field support
2. When available, migrate to proper custom fields
3. Add acceptance test that verifies tag field format handling

**Current mitigation:** Well-documented mapping in KDoc (lines 72-86)

**Priority:** P2 (integration robustness)

---

### No Rate Limiting at Application Level for Webhooks

**Issue:** `NangoWebhookService` accepts all webhook requests without rate limiting. A compromised Nango account could spam webhook endpoints with high-frequency requests.

**Files:**
- `core/src/main/kotlin/riven/core/controller/integration/NangoWebhookController.kt`
- `core/src/main/kotlin/riven/core/service/integration/NangoWebhookService.kt`

**Impact:** 
- Resource exhaustion via webhook spam
- No circuit breaker for denial-of-service scenarios
- Temporal queue could be flooded with workflows

**Current mitigation:** General application rate limiting filter (Bucket4j in-memory), Cloudflare edge-level rate limiting (planned)

**Fix approach:** 
1. Configure stricter rate limit on webhook endpoints (e.g., 10 req/10s per IP)
2. Add per-connection rate limiting (e.g., 100 webhooks/hour per connection)
3. Implement Cloudflare WAF rule for `/api/v1/webhooks/*` (P2 in TODOS.md)

**Priority:** P1 (before production)

---

### Redis-Backed Rate Limiting for Multi-Instance

**Issue:** Current rate limiting uses Caffeine in-memory cache. Each instance has independent buckets, allowing users to bypass limits by distributing requests across instances.

**Files:**
- `core/src/main/kotlin/riven/core/configuration/RateLimitFilterConfiguration.kt` (Bucket4j Caffeine backend)

**Impact:** 
- Rate limits ineffective in multi-instance deployments
- Users could get 200 rpm per instance instead of 200 rpm total
- Scales down to single instance for now

**Fix approach:** When multi-instance deployment is planned, swap Caffeine backend for Redis-backed `ProxyManager<String>` via `bucket4j-redis`. No other code changes needed.

**Priority:** P2 (defer until multi-instance)

**Depends on:** Multi-instance deployment planning

---

## Testing Gaps

### Phase 3 Output Metadata Coverage Incomplete

**Issue:** `OutputMetadataValidationTest` tracks nodes missing `outputMetadata` as Phase 3 TODOs (multiple warnings and TODO list). Some workflow node types lack output metadata definitions.

**Files:**
- `core/src/test/kotlin/riven/core/models/workflow/node/config/OutputMetadataValidationTest.kt` (lines 22, 135, 261-270)

**Impact:** 
- Incomplete metadata for node execution tracking
- Execution records may lack output type hints
- Runtime type coercion could fail

**Fix approach:** Audit all Phase 3 implemented node types. Define `outputMetadata` for any missing types. Update test to assert full coverage instead of printing warnings.

**Priority:** P2 (test completeness, Phase 3 work)

---

### WorkspaceServiceTest Entirely Commented Out

**Issue:** `WorkspaceServiceTest` is fully commented out, representing broken or drifted tests that are not being run.

**Files:**
- `core/src/test/kotlin/riven/core/service/workspace/WorkspaceServiceTest.kt`

**Impact:** 
- Workspace service has zero automated test coverage
- Regressions undetected
- Critical domain untested

**Fix approach:** Restore and fix test cases. Use test factories for workspace creation. Add tests for:
- Workspace CRUD operations
- Role-based access control
- Membership management
- Soft-delete behavior

**Priority:** P1 (critical test coverage gap)

---

### Entity Display Name Null Edge Case Not Tested

**Issue:** `WorkspaceNote.entityDisplayName` can be null but there are no tests covering this scenario. Frontend fallback behavior is untested.

**Files:**
- Backend: `WorkspaceNote` model and enrichment logic
- Frontend: note list and breadcrumb components (TBD in client codebase)

**Impact:** 
- Null handling logic untested
- Risk of NullPointerException or blank UI elements in production

**Fix approach:** Add integration test that creates a note attached to an entity, deletes the entity's identifier attribute, and verifies null display name. Add frontend test verifying fallback rendering.

**Priority:** P2 (edge case coverage)

---

## Dependency/Integration Risks

### Legacy Jackson ObjectMapperConfig Duplication

**Issue:** `ObjectMapperConfig` calls `.setDateFormat()` and `.registerModules(JavaTimeModule())` twice each.

**Files:**
- `core/src/main/kotlin/riven/core/configuration/ObjectMapperConfig.kt`

**Impact:** 
- Redundant configuration
- First call is wasted
- Harder to maintain config

**Fix approach:** Remove duplicate calls. Keep single instance of each configuration.

**Priority:** P3 (housekeeping)

---

### Test Style Mixing: Mockito vs. mockito-kotlin

**Issue:** Some tests use `Mockito.when()` static calls, others use `mockito-kotlin`'s `whenever()` DSL. No consistency.

**Files:**
- Various test files across service layer

**Impact:** 
- Inconsistent test style
- Harder for new contributors to match style
- Mixed idioms make reading tests slower

**Fix approach:** Standardize on `mockito-kotlin` DSL (`whenever`, `verify`). Migrate existing tests in refactoring passes.

**Priority:** P3 (test consistency)

---

## Known Issues by Severity

### P0 (Before Production)

1. **Circuit breaker on sync failures** — Unbounded failed workflows could consume Temporal capacity
2. **Rate limiting at webhook level** — Denial-of-service vector for webhook endpoints
3. **WorkspaceServiceTest coverage** — Workspace service untested

### P1 (Near-term)

4. **Missing STALE transition logic** — Dead connections not visible to users
5. **Verify query performance post-deployment** — EXPLAIN ANALYZE on cursor pagination

### P2 (Current Sprint)

6. **Email notifications in workspace invites** — UX gap in onboarding
7. **Workflow metadata completeness** — Version, trigger context, sequence tracking
8. **Relationship reconciliation** — Incomplete schema drift handling
9. **Null display name handling** — Edge case in workspace notes
10. **Cursor-based pagination for EntityQueryService** — Correctness issue with infinite scroll

### P3 (Future/Refactoring)

11. **Extract long methods into named phases** — Code clarity concern
12. **Consistent @Valid usage** — Validation pattern standardization
13. **Workflow control flow expansion** — Phase 5 feature
14. **FUNCTION node execution** — Phase 5 feature

---

*Concerns audit: 2026-04-12*
