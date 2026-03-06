# Requirements: Entity Relationship Migration

**Defined:** 2026-02-27
**Core Value:** The entity type configuration flow must cleanly separate attribute definitions from relationship definitions while keeping the UX simple and unified.

## v1 Requirements

Requirements for this migration. Each maps to roadmap phases.

### Type Foundation

- [ ] **TYPE-01**: Entity domain barrel exports new generated types (RelationshipDefinition, RelationshipTargetRule, SaveRelationshipDefinitionRequest, SaveTargetRuleRequest, EntityRelationshipCardinality)
- [ ] **TYPE-02**: Old EntityRelationshipDefinition type is removed or deprecated from domain barrel with clear migration path
- [ ] **TYPE-03**: All component imports use domain barrel (`@/lib/types/entity`), not direct model imports
- [ ] **SEM-TYPE-01**: Entity domain barrel exports semantic types (EntityTypeSemanticMetadata, SaveSemanticMetadataRequest, BulkSaveSemanticMetadataRequest, SemanticAttributeClassification, SemanticMetadataTargetType, SemanticMetadataBundle, EntityTypeWithSemanticsResponse)

### Service Layer

- [ ] **SERV-01**: Existing entity type service methods updated to handle new RelationshipDefinition API contract
- [ ] **SERV-02**: Existing mutation hooks updated to use new request/response shapes for relationship definitions
- [ ] **SERV-03**: Entity type key-to-UUID resolution implemented in service layer for target entity type references
- [ ] **SERV-04**: Cache invalidation updated to match new response shapes from relationship endpoints
- [ ] **SERV-05**: Impact confirmation flow (409 handling) preserved for relationship deletion

### Form Schema

- [ ] **FORM-01**: New Zod validation schema built from scratch against SaveRelationshipDefinitionRequest shape
- [ ] **FORM-02**: Target rules array managed via react-hook-form useFieldArray
- [ ] **FORM-03**: Form handleSubmit maps entity type keys to UUIDs before API call
- [ ] **FORM-04**: Form state reset handles modal close/reopen without race conditions

### Relationship Form UI

- [ ] **RLUI-01**: Relationship form shows name field when "Relationship" selected from type dropdown
- [ ] **RLUI-02**: Icon picker added to relationship form (reuse existing IconSelector pattern)
- [ ] **RLUI-03**: Target entity type selector allows picking which entity type(s) the relationship points to
- [ ] **RLUI-04**: Cardinality presented as simple One/Many toggle (not database terminology)
- [ ] **RLUI-05**: Polymorphic flag toggle available for multi-target relationships
- [ ] **RLUI-06**: Target rules configurable at relationship creation time
- [ ] **RLUI-07**: Frontend-design skill used for all new relationship form UI

### Entity Type Creation Form

- [ ] **CREA-01**: Type dropdown removed from entity type creation form (hard-code default value)
- [ ] **CREA-02**: Identifier field hidden from user (auto-generated internally)
- [ ] **CREA-03**: Creation form retains: plural/singular name with icon picker, description
- [ ] **CREA-04**: Frontend-design skill used for simplified creation form

### Semantic Service Layer

- [ ] **SEM-SERV-01**: KnowledgeApi factory function created in `lib/api/` following existing `create{Domain}Api(session)` pattern
- [ ] **SEM-SERV-02**: Knowledge service created in entity feature module for semantic metadata CRUD (wraps KnowledgeApi)
- [ ] **SEM-SERV-03**: Query hook to fetch semantic metadata bundle for an entity type (getAllMetadata endpoint)
- [ ] **SEM-SERV-04**: Mutation hook for saving entity type semantic definition (setEntityTypeMetadata endpoint)
- [ ] **SEM-SERV-05**: Mutation hook for saving attribute semantic metadata — single and bulk (setAttributeMetadata / bulkSetAttributeMetadata)
- [ ] **SEM-SERV-06**: Mutation hook for saving relationship semantic metadata (setRelationshipMetadata endpoint)

### Semantic UI

- [ ] **SEM-UI-01**: Entity type creation/edit includes a semantic definition textarea
- [ ] **SEM-UI-02**: Attribute schema configuration includes semantic classification dropdown (6 options: identifier, categorical, quantitative, temporal, freetext, relational_reference) and definition field per attribute
- [ ] **SEM-UI-03**: Relationship form includes a semantic definition/context field
- [ ] **SEM-UI-04**: Frontend-design skill used for all semantic metadata UI

## v2 Requirements

Deferred to future work. Tracked but not in current roadmap.

### Overlap Detection

- **OVLP-01**: Relationship overlap detection identifies when two relationship definitions point to the same target
- **OVLP-02**: Bidirectional relationship suggestion panel offers to create inverse relationships

### Advanced Relationship Features

- **ADVR-01**: Relationship definition editing after creation (modify target rules, cardinality)
- **ADVR-02**: Visual relationship graph showing connections between entity types

## Out of Scope

| Feature | Reason |
|---------|--------|
| Data table view changes | Separate concern, not part of this migration |
| New attribute types | Only restructuring existing definition flow |
| Backend API changes | Backend is already complete |
| Entity data entry UI | Only the type configuration flow is in scope |
| Mobile responsiveness | Desktop-first for configuration UI |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| TYPE-01 | Phase 1 | Pending |
| TYPE-02 | Phase 1 | Pending |
| TYPE-03 | Phase 1 | Pending |
| SERV-01 | Phase 2 | Pending |
| SERV-02 | Phase 2 | Pending |
| SERV-03 | Phase 2 | Pending |
| SERV-04 | Phase 2 | Pending |
| SERV-05 | Phase 2 | Pending |
| FORM-01 | Phase 3 | Pending |
| FORM-02 | Phase 3 | Pending |
| FORM-03 | Phase 3 | Pending |
| FORM-04 | Phase 3 | Pending |
| RLUI-01 | Phase 3 | Pending |
| RLUI-02 | Phase 3 | Pending |
| RLUI-03 | Phase 3 | Pending |
| RLUI-04 | Phase 3 | Pending |
| RLUI-05 | Phase 3 | Pending |
| RLUI-06 | Phase 3 | Pending |
| RLUI-07 | Phase 3 | Pending |
| CREA-01 | Phase 4 | Pending |
| CREA-02 | Phase 4 | Pending |
| CREA-03 | Phase 4 | Pending |
| CREA-04 | Phase 4 | Pending |
| SEM-TYPE-01 | Phase 1 | Pending |
| SEM-SERV-01 | Phase 2 | Pending |
| SEM-SERV-02 | Phase 2 | Pending |
| SEM-SERV-03 | Phase 2 | Pending |
| SEM-SERV-04 | Phase 2 | Pending |
| SEM-SERV-05 | Phase 2 | Pending |
| SEM-SERV-06 | Phase 2 | Pending |
| SEM-UI-01 | Phase 4 | Pending |
| SEM-UI-02 | Phase 5 | In Progress (form fields done, data table column in 05-02) |
| SEM-UI-03 | Phase 3 | Pending |
| SEM-UI-04 | Phase 5 | In Progress (form UI done, data table in 05-02) |

**Coverage:**
- v1 requirements: 34 total
- Mapped to phases: 34
- Unmapped: 0

---
*Requirements defined: 2026-02-27*
*Last updated: 2026-02-28 after semantic metadata integration*
