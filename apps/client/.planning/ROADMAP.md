# Roadmap: Entity Relationship Migration

**Project:** Entity Relationship UI Migration
**Created:** 2026-02-27
**Depth:** Quick (3-5 phases)
**Total v1 requirements:** 34
**Coverage:** 34/34

---

## Phases

- [x] **Phase 1: Type Foundation** - Export new generated types and remove old ones from the entity domain barrel
- [x] **Phase 2: Service + Hooks** - Update service methods and mutation hooks to match the new RelationshipDefinition API contract
- [ ] **Phase 3: Relationship Form** - Rewrite form schema and UI component for relationship definitions
- [ ] **Phase 4: Creation Form Simplification** - Remove Type dropdown and Identifier field from entity type creation form
- [ ] **Phase 5: Attribute Semantic Configuration** - Add semantic classification and definition fields to attribute schema configuration

---

## Phase Details

### Phase 1: Type Foundation

**Goal**: The entity domain barrel cleanly exports the new RelationshipDefinition types and the old type is gone, so every downstream layer builds against the correct contract from the start.

**Depends on**: Nothing (first phase)

**Requirements**: TYPE-01, TYPE-02, TYPE-03, SEM-TYPE-01

**Success Criteria** (what must be TRUE):
  1. A developer can import RelationshipDefinition, RelationshipTargetRule, SaveRelationshipDefinitionRequest, SaveTargetRuleRequest, and EntityRelationshipCardinality from `@/lib/types/entity` without error
  2. The old EntityRelationshipDefinition type is no longer the primary exported type — it is either removed or deprecated with a migration comment
  3. No component or hook in the entity feature module imports directly from `@/lib/types/models/` for relationship types — all imports go through the domain barrel
  4. Running `npm run build` produces zero type errors originating from `lib/types/entity/` barrel files (`models.ts`, `requests.ts`, `custom.ts`, `guards.ts`). Downstream compile errors in the 10 files that referenced `EntityRelationshipDefinition` are expected and will be resolved in Phase 2/3
  5. A developer can import EntityTypeSemanticMetadata, SaveSemanticMetadataRequest, BulkSaveSemanticMetadataRequest, SemanticAttributeClassification, SemanticMetadataTargetType, SemanticMetadataBundle, and EntityTypeWithSemanticsResponse from `@/lib/types/entity` without error

**Plans:** 1 plan

Plans:
- [x] 01-01-PLAN.md — Add new type exports to barrel, hard-remove old type, update custom.ts/guards.ts, extend barrel verification test

---

### Phase 2: Service + Hooks

**Goal**: The service layer and mutation hooks are wired to the new RelationshipDefinition endpoints so that create, update, and delete operations reach the correct backend API with correct request shapes.

**Depends on**: Phase 1

**Requirements**: SERV-01, SERV-02, SERV-03, SERV-04, SERV-05, SEM-SERV-01, SEM-SERV-02, SEM-SERV-03, SEM-SERV-04, SEM-SERV-05, SEM-SERV-06

**Success Criteria** (what must be TRUE):
  1. Calling save with a relationship definition sends a request to the correct endpoint with SaveRelationshipDefinitionRequest shape (including sourceEntityTypeId as UUID, not key)
  2. Deleting a relationship definition uses DeleteRelationshipDefinitionRequest and the 409 impact confirmation flow still triggers correctly
  3. After a successful save or delete, the entity type query cache reflects the updated state without requiring a full page reload
  4. Entity type key-to-UUID resolution happens in the service layer — form components never construct raw UUIDs themselves
  5. A KnowledgeApi factory exists and can be instantiated with a session
  6. Calling the semantic metadata query hook returns a SemanticMetadataBundle containing entity-type-level, attribute-level, and relationship-level metadata
  7. Mutation hooks for entity type, attribute, and relationship semantic metadata trigger correct PUT calls and invalidate the semantic metadata cache on success

**Plans:** 1 plan

Plans:
- [x] 02-01-PLAN.md — Update service methods, mutation hooks (impact confirmation), query hooks (include param), type table/candidates hooks (RelationshipDefinition migration), configuration store (UpdateEntityTypeConfigurationRequest)

---

### Phase 3: Relationship Form

**Goal**: The relationship definition form inside the existing AttributeFormModal delivers a complete, usable relationship creation experience — name, icon, target entity type, cardinality, polymorphic toggle, and target rules — all in one modal pass.

**Depends on**: Phase 2

**Requirements**: FORM-01, FORM-02, FORM-03, FORM-04, RLUI-01, RLUI-02, RLUI-03, RLUI-04, RLUI-05, RLUI-06, RLUI-07, SEM-UI-03

**Success Criteria** (what must be TRUE):
  1. Selecting "Relationship" from the attribute type dropdown reveals the relationship form fields (name, icon, target entity type selector, cardinality toggle, polymorphic flag, target rules) without any attribute-specific fields visible
  2. A user can complete the full relationship definition in one modal session: choose a name and icon, select target entity type(s), set One or Many cardinality, and save — with the result appearing in the schema table immediately
  3. Opening the modal a second time after closing shows a clean, empty form with no stale values from the previous session
  4. Target rules can be added, configured, and removed within the modal before the relationship is saved
  5. The form UI matches the visual quality and interaction patterns of the rest of the entity configuration UI (no generic AI aesthetics)
  6. The relationship definition form includes a semantic definition field where users can describe the nature of the relationship in natural language

**Plans:** 3 plans

Plans:
- [x] 03-01-PLAN.md — Zod schema, form hook with useFieldArray, SemanticGroup barrel export, relationship util cleanup
- [x] 03-02-PLAN.md — Target rule components (item + list) and complete relationship form UI composition
- [ ] 03-03-PLAN.md — Modal integration (wire new RelationshipForm), dead overlap code removal, human verification

---

### Phase 4: Creation Form Simplification

**Goal**: The entity type creation form asks only for what the user actually needs to provide — name, plural name, icon, and description — with no internal technical fields exposed.

**Depends on**: Phase 1 (for correct types; otherwise independent of Phases 2 and 3)

**Requirements**: CREA-01, CREA-02, CREA-03, CREA-04, SEM-UI-01

**Success Criteria** (what must be TRUE):
  1. The entity type creation form shows only: plural name, singular name, icon picker, and description — no Type dropdown, no Identifier field
  2. Creating an entity type through the simplified form succeeds end-to-end — the entity type is created on the backend with a valid auto-generated identifier
  3. The creation form visual design is consistent with the rest of the configuration UI
  4. The entity type creation/edit form includes a semantic definition textarea where users can describe what this entity type represents in the business domain

**Plans**: TBD

---

### Phase 5: Attribute Semantic Configuration

**Goal**: Each attribute in the entity type schema configuration can have its semantic classification and definition set, giving the system rich context about what each field means.

**Depends on**: Phase 2 (for service/hooks), Phase 1 (for types)

**Requirements**: SEM-UI-02, SEM-UI-04

**Success Criteria** (what must be TRUE):
  1. Each attribute row in the schema configuration view has an accessible way to set/edit its semantic classification (dropdown with 6 options) and definition (text field)
  2. Saving semantic metadata for an attribute persists via the Knowledge API and reflects immediately in the UI without full page reload
  3. The semantic metadata UI matches the visual quality of the rest of the entity configuration UI
  4. Frontend-design skill is used for the attribute semantic configuration UI

**Plans:** 2 plans

Plans:
- [ ] 05-01-PLAN.md — Knowledge API infrastructure (factory, service, query hook) + form schema extension + semantic section UI in SchemaForm
- [ ] 05-02-PLAN.md — Classification column in schema data table + human verification checkpoint

---

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Type Foundation | 1/1 | Complete | 2026-02-28 |
| 2. Service + Hooks | 1/1 | Complete | 2026-02-28 |
| 3. Relationship Form | 2/3 | In progress | - |
| 4. Creation Form Simplification | 0/? | Not started | - |
| 5. Attribute Semantic Configuration | 0/2 | Not started | - |

---

## Coverage Map

| Requirement | Phase |
|-------------|-------|
| TYPE-01 | Phase 1 |
| TYPE-02 | Phase 1 |
| TYPE-03 | Phase 1 |
| SERV-01 | Phase 2 |
| SERV-02 | Phase 2 |
| SERV-03 | Phase 2 |
| SERV-04 | Phase 2 |
| SERV-05 | Phase 2 |
| FORM-01 | Phase 3 |
| FORM-02 | Phase 3 |
| FORM-03 | Phase 3 |
| FORM-04 | Phase 3 |
| RLUI-01 | Phase 3 |
| RLUI-02 | Phase 3 |
| RLUI-03 | Phase 3 |
| RLUI-04 | Phase 3 |
| RLUI-05 | Phase 3 |
| RLUI-06 | Phase 3 |
| RLUI-07 | Phase 3 |
| CREA-01 | Phase 4 |
| CREA-02 | Phase 4 |
| CREA-03 | Phase 4 |
| CREA-04 | Phase 4 |
| SEM-TYPE-01 | Phase 1 |
| SEM-SERV-01 | Phase 2 |
| SEM-SERV-02 | Phase 2 |
| SEM-SERV-03 | Phase 2 |
| SEM-SERV-04 | Phase 2 |
| SEM-SERV-05 | Phase 2 |
| SEM-SERV-06 | Phase 2 |
| SEM-UI-01 | Phase 4 |
| SEM-UI-02 | Phase 5 |
| SEM-UI-03 | Phase 3 |
| SEM-UI-04 | Phase 5 |

**Coverage: 34/34 -- complete**

### Phase 6: 4.1 Fix entity attribute form

**Goal:** [To be planned]
**Requirements**: TBD
**Depends on:** Phase 5
**Plans:** 0 plans

Plans:
- [ ] TBD (run /gsd:plan-phase 6 to break down)

---

*Roadmap created: 2026-02-27*
*Last updated: 2026-03-05 after Phase 5 planning*
