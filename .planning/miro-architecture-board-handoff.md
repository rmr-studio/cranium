# Handoff: Miro System Architecture Board

**Created:** 2026-04-22
**Status:** Context critical at 92% — handing off fresh. No diagrams created yet.

---

## Goal

Populate a new Miro board with system architecture diagrams, flow diagrams, and ER diagrams based on `/home/jared/dev/cranium/docs/system-design/flows/` (14 flow docs) and current backend codebase state. Purpose: visualize architecture to support design and expansion planning.

## User decisions captured

- **Create a new Miro board** (user confirmed — no existing board URL).
- **Caveman mode active** in session (fragments OK, drop filler). Normal prose for code/commits.
- **Auto mode active** — execute, minimize interruptions.
- CLAUDE.md skill routing applies: architecture review → `plan-eng-review` skill is noted but user requested direct diagram generation.

## Prerequisites for fresh session

1. Miro MCP connected (user already auth'd: `/mcp auth` successful to miro earlier).
2. Deferred tools to load via ToolSearch at session start:
   - `mcp__miro__diagram_create`
   - `mcp__miro__diagram_get_dsl`
   - `mcp__miro__doc_create`
   - `mcp__miro__context_explore`
3. Working dir: `/home/jared/dev/cranium/`
4. Flow docs: `/home/jared/dev/cranium/docs/system-design/flows/`

## Flow doc inventory (all read this session)

| #   | Doc                                          | Status                                                    | Use for                                                                                             |
| --- | -------------------------------------------- | --------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| 1   | `Auth & Authorization.md`                    | FULL                                                      | Flowchart + sequence                                                                                |
| 2   | `Entity CRUD.md`                             | FULL (create/retrieve/delete sub-flows)                   | 3 sequence diagrams or 1 combined flowchart                                                         |
| 3   | `Entity Type Definition.md`                  | FULL (incl. decision tree for breaking changes)           | Flowchart                                                                                           |
| 4   | `Flow - Node Input Resolution.md`            | STUB only                                                 | Skip                                                                                                |
| 5   | `Flow - File Upload.md`                      | FULL                                                      | Sequence diagram                                                                                    |
| 6   | `Flow - Semantic Metadata Lifecycle Sync.md` | FULL (4 sub-flows)                                        | Flowchart showing lifecycle events                                                                  |
| 7   | `Flow - Signed URL Download.md`              | FULL                                                      | Sequence diagram                                                                                    |
| 8   | `Flow - Stale Item Recovery.md`              | STUB only                                                 | Skip (content exists in Queue Processing §Stale Item Recovery Path)                                 |
| 9   | `Flow - Workflow Execution Queueing.md`      | STUB only                                                 | Skip (covered by Queue Processing + Workflow Execution)                                             |
| 10  | `Integration Connection Lifecycle.md`        | FULL (10-state machine)                                   | State diagram (use flowchart DSL — no state diagram type in Miro) + sequence for connect/disconnect |
| 11  | `Integration Data Sync Pipeline.md`          | FULL (3 paths: auth webhook, sync webhook, sync workflow) | 3 sequence diagrams                                                                                 |
| 12  | `Invitation Acceptance.md`                   | FULL                                                      | Sequence diagram                                                                                    |
| 13  | `Queue Processing.md`                        | FULL                                                      | Sequence diagram + stale recovery flowchart                                                         |
| 14  | `Workflow Execution.md`                      | FULL (incl. DAG execution detail)                         | 2 sequence diagrams (main + DAG)                                                                    |

## Diagrams to create

Plan: 1 board, ~12-15 diagrams total. Use x/y coords to lay out on board grid (~2000px spacing). Suggested layout:

### Layout (coords)

```
Row 1 (y=-3000): Overview
  [System Architecture Overview — flowchart]   x=0

Row 2 (y=-1500): Domain Model
  [ER Diagram — Core Domain]                   x=0

Row 3 (y=0): Auth & Workspaces cluster
  [Auth & Authorization sequence]      x=-2500
  [Invitation Acceptance sequence]     x=0
  [Workspace Security decision tree]   x=2500

Row 4 (y=1500): Entities cluster
  [Entity CRUD — Create sequence]      x=-2500
  [Entity Type Definition flowchart]   x=0
  [Semantic Metadata Lifecycle]        x=2500

Row 5 (y=3000): Workflows cluster
  [Workflow Execution — end-to-end]    x=-2500
  [Queue Processing sequence]          x=0
  [DAG Execution detail sequence]      x=2500

Row 6 (y=4500): Integrations cluster
  [Connection Lifecycle state flow]    x=-2500
  [Sync Pipeline — main workflow]      x=0
  [Auth webhook + template materialize] x=2500

Row 7 (y=6000): Storage cluster
  [File Upload sequence]               x=-1250
  [Signed URL Download sequence]       x=1250
```

### Suggested diagrams (priority order)

**Priority 1 — foundational:**

1. **System Architecture Overview** (flowchart) — Client → REST controllers → Services → {PostgreSQL, Temporal, Nango, Local Storage, Supabase JWKS}. Group services by domain (Workspaces & Users, Entities, Workflows, Integrations, Storage, Knowledge).
2. **ER Diagram — Core Domain** (entity_relationship) — `workspaces`, `workspace_members`, `workspace_invites`, `entity_types`, `entities`, `entity_unique_values`, `entity_relationships`, `entity_type_semantic_metadata`, `workflow_definitions`, `workflow_execution_queue`, `workflow_execution`, `workflow_execution_nodes`, `integration_definitions`, `integration_connections`, `integration_sync_state`, `file_metadata`, `shedlock`, `activity_log`.

**Priority 2 — critical flows:** 3. **Auth & Authorization** (uml_sequence) — full JWT → @PreAuthorize → RLS chain. Source: flow doc §Happy Path. 4. **Workflow Execution end-to-end** (uml_sequence) — Client → Controller → Queue → Dispatcher → Temporal → Orchestration → Coordination → Completion. Condensed from flow doc high-level diagram. 5. **DAG Execution detail** (uml_sequence) — GraphCoordination → QueueManagement → Store → nodeExecutor loop. 6. **Queue Processing** (uml_sequence) — Scheduled → ShedLock → claimBatch → processItem (REQUIRES_NEW) → capacity check → dispatch/release. 7. **Integration Sync Pipeline — main workflow** (uml_sequence) — IntegrationSyncWorkflow 3-pass (fetch+process / relationships / sync state+health).

**Priority 3 — remaining flows:** 8. **Connection Lifecycle** (flowchart) — 10-state machine as flowchart since Miro has no state diagram type. Nodes = states, edges = valid transitions. 9. **Sync Pipeline — Auth webhook path** (uml_sequence) — Nango → Controller (HMAC) → Service → ConnectionService → TemplateMaterialization. 10. **Entity CRUD — Create** (uml_sequence) — Controller → Security → EntityService → Validation → Persist → Relationships → Activity. 11. **Entity Type Definition** (flowchart) — decision tree for breaking change impact analysis. 12. **Semantic Metadata Lifecycle** (flowchart) — 6 events (publish, attribute add/remove, relationship add/remove, entity type soft-delete) each triggering metadata create/hard-delete/soft-delete. 13. **File Upload** (uml_sequence) — Controller → Security → StorageService → ContentValidation (Tika) → LocalStorage → Metadata save → SignedUrl gen. 14. **Signed URL Download** (uml_sequence) — unauthenticated; HMAC validation → file read → metadata lookup → streaming response. 15. **Invitation Acceptance** (uml_sequence) — two phases (create invite, accept invite).

## Implementation approach for fresh session

1. **Load tools** via ToolSearch:
   ```
   select:mcp__miro__diagram_create,mcp__miro__diagram_get_dsl,mcp__miro__doc_create,mcp__miro__context_explore
   ```
2. **Create board implicitly** via first `diagram_create` call without `miro_url` (user already confirmed new board). Consider creating the System Architecture Overview flowchart first — this establishes the board.
3. **Get DSL spec once per type** — three types needed: `flowchart`, `uml_sequence`, `entity_relationship`. Call `diagram_get_dsl` once per type, reuse spec across all diagrams of that type.
4. **Capture board URL** from first diagram's response. Pass as `miro_url` for all subsequent diagrams.
5. **Create in priority order** — stop between priorities to let user review. Priority 1 alone establishes board and is valuable. Priorities 2+3 can be batched in later sessions.
6. **Titles** — use format "Cranium — <Area>: <Flow Name>" so they sort nicely on the board.
7. **Create a doc_create** as a board-level README at x=0, y=-4500 with links to each cluster and short description. Markdown only (no code blocks or tables).

## Key content references per diagram

Condensed from flow docs — each mermaid block in the source doc maps roughly 1:1 to a Miro DSL diagram:

- **Auth sequence:** flow doc `Auth & Authorization.md` §Happy Path mermaid block.
- **Entity CRUD Create:** `Entity CRUD.md` §Happy Path: Create Entity.
- **Entity Type Definition:** `Entity Type Definition.md` §Decision Path (the flowchart one, not the sequence ones).
- **Semantic Metadata:** `Flow - Semantic Metadata Lifecycle Sync.md` — 4 mermaid blocks; consolidate into 1 flowchart showing trigger → action fan-out.
- **File Upload:** `Flow - File Upload.md` §Happy Path mermaid.
- **Signed URL Download:** `Flow - Signed URL Download.md` §Happy Path mermaid.
- **Connection Lifecycle:** `Integration Connection Lifecycle.md` has stateDiagram-v2 (use State Transition Table directly as flowchart nodes+edges).
- **Sync Pipeline 3 paths:** `Integration Data Sync Pipeline.md` has 3 mermaid sequences — port each.
- **Invitation:** `Invitation Acceptance.md` §Steps mermaid.
- **Queue Processing:** `Queue Processing.md` §Happy Path mermaid.
- **Workflow Execution:** `Workflow Execution.md` §High-Level Flow + §DAG Execution Detail mermaid blocks.

## Backend architecture context (for Architecture Overview + ER)

Domain modules referenced in flows (paths into `core/`):

- **Workspaces & Users** — SecurityConfig, TokenDecoder, WorkspaceSecurity, AuthTokenService, WorkspaceService, WorkspaceInviteService, UserService, ActivityService
- **Entities** — EntityController, EntityService, EntityValidationService, EntityRelationshipService, EntityTypeController, EntityTypeService, EntityTypeAttributeService, EntityTypeRelationshipService, EntityTypeRelationshipDiffService, EntityTypeRelationshipImpactAnalysisService, EntityTypeSemanticMetadataService
- **Workflows** — WorkflowExecutionController, WorkflowExecutionService, WorkflowExecutionQueueService, WorkflowExecutionDispatcherService (Spring @Scheduled + ShedLock), WorkflowExecutionQueueProcessorService, WorkflowOrchestrationService (Temporal workflow), WorkflowCoordinationService (Temporal activity), WorkflowGraphCoordinationService, WorkflowGraphQueueManagementService, WorkflowNodeInputResolverService, WorkflowCompletionActivityImpl
- **Integrations** — IntegrationConnectionService, IntegrationDefinitionService, NangoClientWrapper, NangoWebhookController, NangoWebhookService, TemplateMaterializationService, IntegrationSyncWorkflow (Temporal), IntegrationSyncActivities, SchemaMappingService, IntegrationConnectionHealthService
- **Storage** — StorageController, StorageService, ContentValidationService (Apache Tika), LocalStorageProvider, FileMetadataRepository, SignedUrlService (HMAC-SHA256)

External dependencies:

- **PostgreSQL** (with RLS policies + ShedLock table)
- **Temporal** (workflow orchestration for both workflow execution + integration sync)
- **Nango Cloud** (OAuth + sync webhooks)
- **Supabase** (JWT signing / JWKS — optional)
- **Local filesystem** (StorageProvider — strategy pattern allows swap)

## Memory notes relevant

From `MEMORY.md`:

- `docs/` is separate git repo — no commits needed to core for this work since we're only writing to Miro.
- Two-layer data model (Source + Projection) per `project_two_layer_data_model.md` — worth including in ER if diagraming deeply.
- Integration entities are readonly per `project_integration_entity_architecture.md` — note this in Sync Pipeline diagram annotations.

## Estimated context cost per diagram

Each diagram_create call ≈ 3-5% context (DSL text + response). 15 diagrams ≈ 50-75% context from scratch. A fresh session with no flow doc re-reading (use this handoff as source) should fit comfortably:

- Handoff read: ~5%
- DSL specs (3 calls): ~6%
- 15 diagrams: ~60%
- Doc README: ~2%
- Buffer: ~27%

If tight, split into 2 sessions: Priority 1+2 first (~8 diagrams), Priority 3 second (~7 diagrams).

## Fresh session kickoff prompt

Paste this into fresh session:

> Continue populating the Miro system architecture board per `/home/jared/dev/cranium/.planning/miro-architecture-board-handoff.md`. Read the handoff, then execute Priority 1 diagrams (System Architecture Overview flowchart + Core Domain ER). Don't re-read the flow docs — the handoff captures what's needed. Create the new Miro board via the first diagram_create call. Report board URL back. Caveman mode still active.
