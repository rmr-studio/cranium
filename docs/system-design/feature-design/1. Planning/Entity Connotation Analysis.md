---
tags:
  - "#status/draft"
  - priority/high
  - architecture/feature
  - domain/knowledge
  - tool/temporal
  - tool/ollama
Created: 2026-04-11
Updated: 2026-04-11
Domains:
  - "[[riven/docs/system-design/domains/Knowledge/Knowledge]]"
  - "[[riven/docs/system-design/domains/Integrations/Integrations]]"
blocked-by:
  - "[[riven/docs/system-design/feature-design/1. Planning/Data Chunking and Enrichment Pipeline]]"
---
# Feature: Entity Connotation Analysis Pipeline

---

## 1. Overview

### Problem Statement

The enrichment pipeline (Phases 2-3) embeds entities based on structural semantic metadata: what entity types are defined to represent, what attributes are supposed to mean, what relationship definitions describe. But the pipeline is blind to what the actual data says.

A support ticket embedding knows "this has a subject field and a status field" but not whether the ticket is angry, escalatory, or routine. A customer's relationship summaries show "5 support tickets" but not that 4 were negative interactions about billing disputes. When the RAG query layer ships, questions like "which customers are at churn risk based on negative support interactions since the v3 release" require connotation-level understanding that the current embeddings don't carry.

The gap: structural metadata tells you what data is *supposed* to represent. Connotation analysis tells you what the data *actually says*. Without both, the embedding space is semantically shallow and cross-domain correlation queries return noise.

### Proposed Solution

**Tiered Connotation Analysis** producing structured JSONB metadata stored on entities and relationships, consumed by the enrichment pipeline as input to text construction and available independently for structured querying.

Three tiers matched to data source capabilities:

1. **Tier 1: Source Signal Mapping** — Integration tools (Zendesk, Intercom, HubSpot) already compute sentiment scores, CSAT ratings, and conversation ratings. These sync into Riven as entity attributes. Tier 1 maps these existing signals to a unified connotation metadata schema via manifest declarations. Zero inference cost.

2. **Tier 2: Local Classifier** — For entities without source-tool signals but with FREETEXT attributes, a lightweight local model via Ollama (e.g. `mistral:7b`) performs sentiment classification. Cheap, fast, handles volume. Produces binary/trinary sentiment plus 1-3 topic keywords.

3. **Tier 3: Full LLM Inference** (deferred to v2) — For deeply unstructured data (email threads, Slack conversations, meeting transcripts), a full LLM API call extracts rich connotation: sentiment, themes, urgency, action items. Expensive but scoped to entity types that explicitly need it. Not in initial scope because no email/Slack sync manifests exist yet.

### Architectural Rationale — Why Structured Metadata, Not Baked Into Embeddings

The initial design proposed running connotation analysis and injecting its output directly into the enriched text before embedding. An independent review challenged this: connotation inference is model-dependent and prompt-dependent. Unlike database indexes (deterministic, stable), connotation scores change when the model or prompt changes. Baking connotation directly into embeddings means model changes require full re-embedding of the entire corpus.

Storing connotation as structured JSONB metadata on entities/relationships decouples the two concerns:
- **Re-analysis without re-embedding.** Change the connotation model? Re-run connotation analysis and update the metadata. Only re-embed if the text builder's output changes meaningfully.
- **Dual consumption.** The enrichment pipeline reads connotation metadata when building enriched text. A future structured query engine reads it directly for filtering and aggregation.
- **Auditability.** You can inspect what the system "thinks" about an entity before it gets embedded. Debug connotation quality independently of embedding quality.

### Prerequisites

- [[riven/docs/system-design/feature-design/1. Planning/Data Chunking and Enrichment Pipeline|Data Enrichment Pipeline]] — Phases 2-3 (embedding infrastructure) must be complete
- Integration sync pipeline — Tier 1 depends on manifest field mapping
- Ollama infrastructure — Tier 2 depends on local model serving

---

## 2. Core Concepts

### Connotation Metadata Schema

Every entity and entity relationship can carry a `connotation_metadata` JSONB column. This is system-computed metadata, not a user-facing attribute. It sits alongside the normalized `entity_attributes` storage as a denormalized metadata column.

```json
{
  "sentiment": -0.7,
  "sentimentLabel": "NEGATIVE",
  "themes": ["billing_dispute", "feature_complaint"],
  "analysisVersion": "v1",
  "analysisTier": "TIER_1",
  "analyzedAt": "2026-04-11T13:00:00Z"
}
```

**Unified sentiment scale:** -1.0 (strongly negative) to +1.0 (strongly positive), with discrete labels: `VERY_NEGATIVE`, `NEGATIVE`, `NEUTRAL`, `POSITIVE`, `VERY_POSITIVE`.

**Themes:** Topic keywords extracted from the entity's content. For Tier 1, raw values from manifest-declared `themeAttributes`. For Tier 2, the local classifier extracts 1-3 keywords. For Tier 3 (future), the LLM prompt specifies structured theme extraction.

**Analysis status:** Each entity also carries a `connotation_status` column (VARCHAR): `ANALYZED`, `PENDING_RETRY`, `FAILED`, `NOT_APPLICABLE`. Default `NOT_APPLICABLE` for entities without connotation signals configured.

### Manifest Connotation Signals

Integration manifests declare which attributes carry connotation signals per entity type. This follows the existing manifest-as-source-of-truth pattern.

**Tier 1 (source signal mapping):**
```json
"connotationSignals": {
  "tier": "TIER_1",
  "sentimentAttribute": "satisfaction_rating",
  "sentimentScale": {
    "sourceMin": 1,
    "sourceMax": 5,
    "targetMin": -1.0,
    "targetMax": 1.0,
    "mappingType": "LINEAR"
  },
  "themeAttributes": ["category", "subcategory"]
}
```

**Tier 2 (local classifier):**
```json
"connotationSignals": {
  "tier": "TIER_2",
  "analyzeAttributes": ["body", "description"],
  "contextAttributes": ["subject", "category"]
}
```

**Scale mapping types:**
- `LINEAR` — Linear interpolation between source and target ranges. Default.
- `THRESHOLD` — Non-linear mapping with explicit breakpoints (e.g. CSAT 1-2 = negative, 3 = neutral, 4-5 = positive). Requires additional `thresholds` field.

### Tier Routing Decision Tree

```
Integration entity with connotationSignals.tier = "TIER_1" → Tier 1 (field mapping)
Integration entity with connotationSignals.tier = "TIER_2" → Tier 2 (local classifier)
Integration entity with connotationSignals.tier = "TIER_3" → Tier 3 (deferred to v2)
User-created entity with CONNOTATION_SOURCE attributes    → Tier 2 on those attributes
User-created entity with FREETEXT but no classification   → No analysis (opt-in only)
All other entities                                        → No analysis
```

For user-created entity types, the existing `SemanticAttributeClassification` enum is extended with `CONNOTATION_SOURCE` to let users designate which attributes carry sentiment/connotation.

---

## 3. Data Flow

### Connotation Analysis Pipeline

```
Entity Mutation (create/update)
  |
  v
EnrichmentWorkflowImpl (Temporal)
  |
  v
Activity 1: analyzeConnotation(queueItemId)     ← NEW
  |
  +-- Resolve entity type + manifest/semantic metadata
  +-- Route to tier based on decision tree
  |     |
  |     +-- Tier 1: Read source attribute value → normalize → store metadata
  |     +-- Tier 2: Collect FREETEXT values → Ollama classify → store metadata
  |     +-- No analysis: skip, connotation_metadata remains null
  |
  v
Activity 2: fetchEntityContext(queueItemId)       ← EXISTING (reads connotation_metadata)
  |
  v
Activity 3: constructEnrichedText(context)        ← EXISTING (adds Connotation section)
  |
  v
Activity 4: generateEmbedding(text)               ← EXISTING
  |
  v
Activity 5: storeEmbedding(...)                   ← EXISTING
```

Connotation analysis is a new first activity within the existing `EnrichmentWorkflowImpl`. The workflow sequence becomes: `analyzeConnotation` -> `fetchEntityContext` -> `constructEnrichedText` -> `generateEmbedding` -> `storeEmbedding`. This avoids creating a separate orchestrating workflow and reuses the existing retry/timeout configuration.

### Relationship Connotation

When a relationship is created between entities, the relationship's connotation is derived from the target entity's connotation metadata:
- Customer -> Support Ticket relationship inherits the ticket's sentiment
- If the target entity's `connotation_metadata` is null (not yet analyzed or analysis failed), the relationship's connotation is left null and populated lazily on the next enrichment cycle
- Aggregation (e.g. "3 of 5 tickets are negative") is computed at enrichment text-building time by `SemanticTextBuilderService`, not pre-stored. This avoids N+1 staleness when new relationships are added.

### Enrichment Text Integration

`SemanticTextBuilderService` adds a new **Section 3: Connotation Context** after Identity (Section 2), before Attributes (Section 3, renumbered to 4). This section is never truncated (same priority as Sections 1-2) because it is the primary semantic signal this feature adds. The existing progressive truncation cascade remains unchanged.

Example output:
```
## Connotation
Sentiment: NEGATIVE (-0.7)
Themes: billing_dispute, feature_complaint
```

### Backfill Strategy

When the feature is first enabled or a new integration is connected with historical data:
- All entities with null `connotation_metadata` belonging to entity types with connotation signals configured are queued as `BATCH` priority items (following the ENRICH-17 pattern)
- Normal entity mutation triggers take `NORMAL` priority and are processed first
- Backfill uses the existing enrichment queue deduplication

---

## 4. Failure Handling

Analysis never blocks the enrichment pipeline. Enrichment proceeds with or without connotation metadata.

| Tier | Failure Scenario | Action | Status |
|------|-----------------|--------|--------|
| Tier 1 | Missing source attribute | Log warning, skip analysis | `FAILED` |
| Tier 1 | Parse/normalization error | Log warning, skip analysis | `FAILED` |
| Tier 2 | Ollama timeout | Log warning, skip analysis | `PENDING_RETRY` |
| Tier 2 | Malformed model output | Log warning, skip analysis | `PENDING_RETRY` |
| Tier 2 | JSON validation failure | Log warning, skip analysis | `FAILED` |
| Any | Entity deleted during analysis | Log warning, skip enrichment | `FAILED` |

`PENDING_RETRY` items are retried on the next entity mutation that triggers enrichment for the same entity, via the queue deduplication mechanism.

Tier 2 model output is validated against the connotation metadata JSON schema before persisting, following the existing `SchemaService` validation pattern.

---

## 5. Schema Changes

### SQL

```sql
-- entities table
ALTER TABLE entities ADD COLUMN connotation_metadata JSONB;
ALTER TABLE entities ADD COLUMN connotation_status VARCHAR(20) NOT NULL DEFAULT 'NOT_APPLICABLE';

-- entity_relationships table
ALTER TABLE entity_relationships ADD COLUMN connotation_metadata JSONB;
```

### Manifest Schema

`integration.schema.json` must be extended to include the `connotationSignals` block as an optional field on entity type definitions within the manifest.

### Enum Extension

`SemanticAttributeClassification` gains `CONNOTATION_SOURCE` for user-created entity types where the user designates which attributes carry sentiment/connotation.

---

## 6. New Components

| Component | Layer | Purpose |
|-----------|-------|---------|
| `ConnotationAnalysisService` | Service | Routes to tier, orchestrates analysis, stores metadata |
| `ConnotationTier1Mapper` | Service (internal) | Field mapping + scale normalization for source signals |
| `ConnotationTier2Classifier` | Service (internal) | Ollama local model call + JSON validation |
| `ConnotationMetadata` | Model | Data class for the JSONB structure |
| `ConnotationStatus` | Enum | `ANALYZED`, `PENDING_RETRY`, `FAILED`, `NOT_APPLICABLE` |
| `SentimentLabel` | Enum | `VERY_NEGATIVE`, `NEGATIVE`, `NEUTRAL`, `POSITIVE`, `VERY_POSITIVE` |
| `AnalysisTier` | Enum | `TIER_1`, `TIER_2`, `TIER_3` |

---

## 7. Open Questions

1. Should Tier 2 (local classifier) use the same Ollama infrastructure as the embedding provider, or a separate model endpoint? Sentiment classification uses different models than embedding generation (e.g. `mistral:7b` for classification vs `nomic-embed-text` for embedding). Resource implications: two models loaded in Ollama simultaneously.

2. How should connotation metadata versioning work? When the analysis prompt or model changes, should existing metadata be invalidated immediately or lazily re-analyzed on next entity mutation?

3. Should connotation metadata be exposed in the entity API response, or kept internal to the enrichment pipeline? Exposing it lets the frontend display sentiment badges. Keeping it internal simplifies the API contract.

---

## 8. Validation Strategy

Before building any connotation automation: add the `connotation_metadata` JSONB column to entities and entity_relationships. Hand-populate connotation metadata on a few test entities, then run the enrichment pipeline and inspect whether the enriched text is meaningfully richer.

If the embedding quality improves noticeably with hand-crafted connotation metadata, the automation investment is justified. If the embeddings look the same, the connotation layer may not be the highest-leverage next step.

---

## 9. Cross-Domain Query Use Case

The motivating query: "Why is our churn rate higher since the v3 release?"

This requires correlating:
- Product usage spikes within a specific domain (product analytics)
- Increase in negative support tickets or interactions (support)
- Subscription cancellations or usage drops (billing)

Without connotation analysis, the enrichment pipeline can embed "5 support tickets exist" but not "4 of 5 were negative, 3 about billing." With connotation metadata, the enriched text carries "Support Tickets: 5 total, sentiment: predominantly NEGATIVE, themes: billing_dispute (3), feature_complaint (1)." This makes the embedding space rich enough for similarity search to surface churn-risk customers.

Note: an independent review argued this specific query is better served by a structured cross-domain query engine (SQL joins with temporal filtering) than by vector similarity. Both are valid. The connotation metadata serves both consumers: structured queries can filter on `sentiment < -0.5`, and embeddings carry the signal for freeform semantic search. The metadata-first architecture enables both paths.
