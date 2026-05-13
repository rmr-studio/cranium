---
tags:
  - architecture/decision
  - adr/accepted
Created: 2026-05-13
Updated: 2026-05-13
---
# ADR-011: Reuse the Storage Spine + Infra, Gut & Rebuild the Type / Projection / Routing / Catalog Machinery

---

## Context

`~/dev/cranium` was built as a generic typed-entity engine for a customer-analytics product: user-creatable `entity_types` (3-plane `EntityTypeRole`), per-type JSON schemas + breaking-change detection, a per-type-pair `RelationshipDefinition` system, a deterministic `(LifecycleDomain, SemanticGroup)` projection-rule engine, a multi-source manifest/catalog engine, and a Nango integration sync. The 2026-05 pivot rescopes the product to an LLM-synthesized wiki over an engineering codebase (Karpathy's LLM-wiki pattern). That product wants a flat node-and-edge store, fixed page kinds in code, and an LLM synthesis pipeline — not a user-configurable type system. The question: greenfield, strangler-fig, or in-place rescope?

---

## Decision

**In-place rescope.** Keep the storage spine (the unified entity table, normalized attributes, the relationship table — renamed to `pages` / `pages.frontmatter` jsonb / `page_links`), the embeddings table + HNSW index, the auth/workspace/RLS/JWT layer, the Temporal infra + isolated task queues, the activity log + websocket. **Gut and rebuild** the type/projection/routing/catalog machinery: delete `RelationshipDefinitionEntity` + its cardinality/semantic-constraint/polymorphic-`target_kind`/inverse-row machinery; delete `EntityValidationService` schema-per-type + `detectSchemaBreakingChanges()`; delete entity-type CRUD endpoints + `EntityTypeRole`; replace `EntityProjectionService` + `ProjectionRuleEntity` + `TemplateMaterializationService` with the D4 LLM synthesis workflow; delete the manifest/catalog engine; delete the Nango integration sync (v1.1).

---

## Rationale

- **Not greenfield** — that re-pays for working auth/RLS, Temporal wiring, the embeddings pipeline, the websocket layer, the activity log. None of that needs to change.
- **Not strangler-fig** — there is no production deployment to protect; the only data is the dev DB. A strangler approach buys nothing and costs a parallel-implementation period.
- **The storage spine is already right-shaped.** `knowledge-entity-graduation` already proved "everything is a page" works (Notes/Glossary collapsed into the unified table). The pivot finishes that move.

---

## Alternatives Considered

### Option 1: Greenfield rewrite

- **Pros:** Clean slate; no legacy assumptions; no rename archaeology.
- **Cons:** Re-implements working auth/RLS, Temporal, embeddings, websocket, activity log — weeks of re-paid cost for zero new capability.
- **Why rejected:** The infra is sound; only the type/projection layer is wrong-shaped.

### Option 2: Strangler-fig (run old + new side by side)

- **Pros:** Standard for migrating live systems; incremental cutover.
- **Cons:** No production to protect ⇒ no benefit; doubles the surface area during the migration.
- **Why rejected:** Pre-product team, dev DB only — the safety the pattern provides isn't needed.

---

## Consequences

### Positive

- Working infra is preserved; the pivot's effort concentrates on the type/projection/synthesis machinery, which is where it belongs.
- The phasing rule (ADR-016) makes the structural collapse mechanical and gated.

### Negative

- The rename touches the hottest modules (entity reads, attribute reads, relationship reads); native SQL queries need manual fix-up (the `target_entity_id`→`target_id` scar).
- The delete list is large; "nothing kept just in case" is a hard discipline gate.

### Neutral

- Existing ADRs for the file pipeline (ADR-005/006/007) and dedup (ADR-009) stay in force unchanged — those layers aren't touched.

---

## Implementation Notes

- Phase 1a renames; 1b collapses relationships (backlink-parity gate); 1c reworks the taxonomy + deletes the type/manifest/projection/sync machinery + the forced re-embed; 1d collapses the `apps/client` screens whose backend dies in 1c. See [[architecture-pivot]] §Migration sequence.
- Bail criterion: if Phase 1 (1a+1b+1c) isn't done-and-green by ~week 3 of Claude Code time, stop and reassess.

---

## Related

- [[architecture-pivot]]
- [[ADR-012 Single page_links Edge Table with In-Code Label Enum]]
- [[ADR-013 Thin Per-Kind Frontmatter in Code, Loose Extras]]
- [[ADR-016 Structural Collapse Before Behavioral Change]]
- [[ADR-018 One pages Table Family for Synthesis Storage]]
- [[ADR-019 Full Pivot Before a Design Partner]]
