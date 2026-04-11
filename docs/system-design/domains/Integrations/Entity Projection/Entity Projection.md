---
tags:
  - architecture/subdomain
  - domain/integration
Created: 2026-03-29
Domains:
  - "[[riven/docs/system-design/domains/Integrations/Integrations]]"
---
# Entity Projection

This subdomain handles Pass 3 of the integration sync pipeline — projecting integration entities into core lifecycle entities. After integration data is synced and schema-mapped (Passes 1-2), projection rules route each integration entity type to its corresponding core entity type. Identity resolution determines whether the incoming data matches an existing core entity (update) or requires a new one (create). Attribute transfer copies field values from the integration entity to the core entity using a source-wins merge strategy. The pipeline runs in Temporal activity context without JWT auth — workspace isolation is by parameter.

## [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Entity Projection/FAQ]]


## Components

| Component | Purpose | Type |
|-----------|---------|------|
| [[EntityProjectionService]] | Core pipeline orchestrator — loads rules, delegates to identity resolution, creates/updates entities, links relationships | Service |
| [[IdentityResolutionService]] | Two-query batch identity resolution — external ID match then identifier key fallback | Service |
| [[ProjectionRuleEntity]] | JPA entity mapping source integration types to target core lifecycle types | Entity |
| [[ProjectionRuleRepository]] | Workspace-scoped projection rule queries | Repository |
| [[ProjectionResult]] | Pipeline outcome tracking — created/updated/skipped/error counts with per-entity details | Model |
| [[ResolutionResult]] | Sealed class for identity resolution outcomes — ExistingEntity or NewEntity | Model |

## Key Flows

- [[Flow - Entity Projection Pipeline]] — End-to-end projection from Temporal activity through identity resolution and entity creation/update

## Cross-Domain Dependencies

- **[[riven/docs/system-design/domains/Entities/Entities]]** — Target domain for projected entities. EntityRepository, EntityAttributeRepository, EntityAttributeService, EntityRelationshipRepository are all entity-domain components consumed by the projection pipeline.
- **[[riven/docs/system-design/domains/Identity Resolution/Identity Resolution]]** — [[IdentityClusterService]] and [[EntityTypeClassificationService]] are consumed for cluster assignment and IDENTIFIER attribute lookup. Architecturally distinct from the async matching pipeline in that domain — this subdomain does deterministic, synchronous matching at ingestion time.
- **[[riven/docs/system-design/domains/Catalog/Catalog]]** — [[CoreModelRegistry]] and [[CoreModelDefinition]] define the `projectionAccepts` rules that drive projection rule installation during [[riven/docs/system-design/domains/Integrations/Enablement/Enablement]].

## Recent Changes

| Date | Change | Context |
|------|--------|---------|
| 2026-03-29 | Initial implementation — projection pipeline (Pass 3), identity resolution, projection rules, attribute transfer | Entity Ingestion Pipeline |
