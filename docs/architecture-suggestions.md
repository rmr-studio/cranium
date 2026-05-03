# Architecture Suggestions

## 2026-04-28 — Semantic Analysis Layer reframe (entity connotation pipeline)

**Trigger:** CEO review of `2026-04-18-entity-connotation-pipeline.md` reframed `connotation_metadata` from a flat sentiment record into a polymorphic snapshot of three orthogonal metadata categories (SENTIMENT, RELATIONAL, STRUCTURAL) populated by a renamed Activity 1 (`analyzeSemantics`). The change rebrands what was conceptually "connotation analysis" into a broader "Semantic Analysis Layer" that crystallizes the full per-entity semantic snapshot used to produce embeddings.

**Affected vault notes:**

- `feature-design/1. Planning/Entity Connotation Analysis.md` — Original feature design framed connotation as sentiment-only and explicitly distinguished "structural metadata" (already in embeddings) from "connotation" (sentiment, what data actually says). The reframe unifies these under one snapshot; Section 1 (Problem/Solution), Section 2 (Core Concepts → Connotation Metadata Schema), Section 3 (Data Flow → Connotation Analysis Pipeline diagram), Section 5 (Schema Changes), Section 6 (New Components) all need revision under the multi-category snapshot shape.
- `system-design/domains/Knowledge/` — Knowledge domain documentation may reference connotation as a sentiment-only concept. The Semantic Analysis framing should be folded in if/when the Knowledge domain prose is updated.
- Any vault note describing the enrichment workflow's first activity as `fetchEntityContext` — under the reframe the activity is renamed `analyzeSemantics` and its output is persisted, not ephemeral.
- Any flow diagrams showing connotation analysis as a separate Activity 0 prepended to the existing workflow — under the reframe, connotation analysis IS Activity 1 (rescoped, not prepended), and produces a multi-category snapshot.

**Suggested update:** Review and revise the Entity Connotation Analysis design doc under the Semantic Analysis Layer framing. Key conceptual shifts to capture: (1) connotation snapshot is now the per-entity semantic snapshot at embed time, not just a sentiment record; (2) RELATIONAL and STRUCTURAL metadata crystallize what was previously ephemeral computation in `EnrichmentService.fetchContext()`; (3) the persistence rationale (re-analysis without re-embedding, dual consumption, auditability) extends to all categories, not only sentiment; (4) per-category `stalenessModel` field documents the trigger class for future invalidation tooling without enforcing it; (5) Layer 4 / DTC Signal layer remain downstream consumers, not part of this layer. The CEO Review Addendum at the bottom of `2026-04-18-entity-connotation-pipeline.md` is the source of truth for the reframe pending vault authoring.

## 2026-02-21 — Entity Relationships Overhaul: Vault Updates Needed

**Trigger:** Complete rewrite of relationship architecture — ORIGIN/REFERENCE sync replaced with table-based definitions

**Affected vault notes:**

- `System Design/System Patterns/` — Any documentation of the ORIGIN/REFERENCE bidirectional sync pattern is now outdated
- `domains/Entities/Relationships/Relationships.md` — Updated, but the architectural narrative may benefit from human-authored context on why the change was made
- `domains/Entities/Entities.md` — Updated with new tables and decisions, but the domain overview prose may need human review for completeness
- Any flow diagrams referencing ORIGIN/REFERENCE relationship creation should be updated or removed

**Suggested update:** Review all vault content that references "ORIGIN", "REFERENCE", "bidirectional sync", or "inverse relationship creation" and update or remove those references. The new architecture stores relationship configuration in dedicated tables and resolves inverse visibility at query time — no inverse rows are ever stored.

## 2026-02-21 — Deepened Cross-Domain Coupling: Workflows → Entities

**Trigger:** `EntityContextService` (Workflows domain) now directly injects `RelationshipDefinitionRepository` and `RelationshipTargetRuleRepository` from the Entities domain

**Affected vault notes:**

- `integrations/Domain Integration.md` — If this documents cross-domain dependencies, the Workflows → Entities coupling has deepened
- `domains/Workflows/State Management/` — May need updated dependency documentation at the subdomain level

**Suggested update:** Consider whether `EntityContextService` should continue to directly access Entities domain repositories, or whether a service-level abstraction (e.g., a method on `EntityTypeRelationshipService`) would be a cleaner cross-domain boundary. The current approach works but creates tight coupling to the Entities domain's internal storage model.

## 2026-05-01 — Entity domain split: JWT path vs system bus

**Trigger:** Extracted `EntityIngestionService` from `EntityService` to separate JWT-fronted CRUD (still on `EntityService`, `@PreAuthorize`-guarded) from system-driven persistence (`EntityIngestionService`, no JWT). Background callers (knowledge ingestion services, Temporal backfill activities, projectors) now inject `EntityIngestionService`.

**Affected vault notes:**

- `domains/Entity/Services/` — should reflect the two-bean split. Document which controllers are allowed to call which service.
- `domains/Entity/Architecture/` (or equivalent) — the system-bus boundary is now an explicit architectural construct worth naming.
- `System Patterns/` — if there is a "service splitting" or "auth boundary" pattern note, this is a representative example to reference.

**Suggested update:** Add a short note describing the split and its rationale (controllers must not bypass `@PreAuthorize` by reaching for an `*Internal` method; the boundary makes that misuse harder by removing the methods from the JWT-path bean entirely). Cross-reference the catalog `TemplateInstallationService` which now uses the new internal `getOrCreateSystemDefinitionInternal` for idempotent reused-type seeding.
