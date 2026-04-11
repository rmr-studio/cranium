---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-04-10
Domains:
  - "[[riven/docs/system-design/domains/Knowledge/Knowledge]]"
---

Part of [[riven/docs/system-design/domains/Knowledge/Enrichment Pipeline/Enrichment Pipeline]]

# SemanticTextBuilderService

---

## Purpose

Renders an `EnrichmentContext` snapshot into multi-section Markdown text optimised for embedding generation, with progressive truncation when over a 27,000-character budget (~6,750 tokens). It is the only piece of the pipeline that decides what the embedding model actually "sees".

---

## Responsibilities

- Build a 6-section Markdown document from an `EnrichmentContext`.
- Format attribute values according to their `SemanticAttributeClassification` (temporal, freetext, relational reference, default).
- Apply progressive, deterministic truncation when the rendered text exceeds the character budget.
- Return a rendered result with the text body, a `truncated` flag, and a coarse token estimate.

**Explicitly NOT responsible for:**

- Loading any data from PostgreSQL — the `EnrichmentContext` is passed in fully assembled by [[EnrichmentService]].
- Calling the embedding model — that is [[EmbeddingProvider]].
- Persisting the produced text or its embedding — that is [[EnrichmentService]].
- Making authorisation decisions — the service is pure and has no workspace awareness.

---

## Dependencies

### Internal Dependencies

None. The service is pure given its input.

### External Dependencies

- `KLogger` — structured logging for a single DEBUG line per invocation.
- `java.time` — ISO-8601 parsing and relative-date bucketing.

### Injected Dependencies

```kotlin
@Service
class SemanticTextBuilderService(
    private val logger: KLogger
)
```

---

## Used By

- [[EnrichmentActivitiesImpl]] — invokes `buildText` from the `constructEnrichedText` activity during [[EnrichmentWorkflow]] execution.

---

## Public Interface

### Key Methods

#### `fun buildText(context: EnrichmentContext): EnrichedTextResult`

- **Purpose:** Build a 6-section enriched-text document from the context snapshot. Each optional section is constructed independently and added only if present and within budget.
- **Side effects:** Logs DEBUG `Built enriched text with {N} sections for entity {id} (truncated={bool})`.
- **Returns:** `EnrichedTextResult(text, truncated, estimatedTokens)` where `estimatedTokens = text.length / 4`.

---

## Key Logic

### Six-section format

The rendered document has up to six sections, in fixed order:

1. **Entity type context** — entity type name, optional definition, semantic group, lifecycle domain. Always included.
2. **Identity / classification** — entity type classification. Always included.
3. **Attributes** — semantic label : value pairs.
4. **Relationship summaries** — count, top categories, last activity per relationship.
5. **Cluster context** — members grouped by source type with entity type names.
6. **Relationship semantic definitions** — name + definition pairs.

Sections 1 and 2 are always emitted. Sections 3–6 are optional: they are built independently and appended only if the context has content for them and the running total stays within budget.

### Progressive truncation algorithm

When the full-fidelity render exceeds `CHAR_BUDGET = 27_000`, truncation is applied in four deterministic steps, in strict order:

1. Try all four optional sections (3–6) at full fidelity.
2. Compact Section 5 — drop entity type names from cluster members, keeping source names only.
3. Compact Section 4 — drop top categories from relationships, keeping count + latest activity only.
4. Reduce Section 3 — drop `FREETEXT` and `RELATIONAL_REFERENCE` attributes.

The algorithm is deterministic: the same input always produces the same output and the same truncation choices. The `truncated` flag in the result is true if any step beyond step 1 was taken.

### Value formatting rules

Attribute values are formatted by their `SemanticAttributeClassification`:

- **TEMPORAL** — parses the raw ISO-8601 value and renders as `Month Day, Year (N units ago)`. The relative label is bucketed by `relativeDate()`:
  - `today`
  - `N days ago`
  - `N weeks ago`
  - `N months ago`
  - `N years ago`
  On parse failure, falls back to the raw string value.
- **FREETEXT** — truncated to 500 characters with `...` appended if longer.
- **RELATIONAL_REFERENCE** — treats the raw value as a UUID, looks it up in `context.referencedEntityIdentifiers`, and renders the resolved display string. On parse error or missing entry, renders `[reference not resolved]`.
- **All other classifications** — rendered as the raw string value.
- **Null values** — rendered as `[not set]`.

### Token estimation

`estimatedTokens = text.length / 4`. This is a coarse approximation that assumes an English-like tokenizer (roughly 4 characters per token). It is reported in the result for callers that want to log or enforce token limits downstream, but it is not used to drive truncation — truncation is keyed on `CHAR_BUDGET` directly.

---

## Configuration

Configuration lives in the companion object:

```kotlin
companion object {
    const val CHAR_BUDGET = 27_000
    val DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, yyyy")
}
```

- `CHAR_BUDGET` — hard character limit at which truncation kicks in. Sized for a ~8k-token context model assuming ~4 chars per token.
- `DATE_FORMATTER` — fixed format used by temporal value rendering.

---

## Error Handling

| Exception | Source | Meaning |
| --- | --- | --- |
| (none thrown) | — | The service catches parse errors internally and falls back to raw values or `[reference not resolved]`. A well-formed `EnrichmentContext` always produces a valid `EnrichedTextResult`. |

---

## Gotchas & Edge Cases

> [!warning] Truncation order is load-bearing
> Changing the priority of the four truncation steps changes which entities lose precision first. That changes embedding quality consistency over time and may invalidate similarity scores against existing embeddings. If you need to change the order, plan for a full re-embedding pass of affected entities.

> [!warning] Character budget is tied to model context window
> `CHAR_BUDGET = 27_000` assumes a ~8k-token model and the `length / 4` token heuristic. If you swap to a model with a different context window, recompute the budget. The `length / 4` heuristic also assumes an English-like tokenizer — multilingual or byte-level tokenizers will diverge noticeably.

> [!warning] Pure function — no hidden state
> This service has no external state or dependencies beyond the logger. Tests should construct an `EnrichmentContext` fixture and assert against the produced text directly. If you find yourself wanting to mock something in a test for this service, step back — the input is the only lever.

---

## Testing

- **File:** `core/src/test/kotlin/riven/core/service/enrichment/SemanticTextBuilderServiceTest.kt` (791 lines)
- **Key scenarios:**
  - Each of the six sections rendered independently from minimal fixtures.
  - Each truncation step triggered by constructing contexts that exceed `CHAR_BUDGET` at successive fidelity levels.
  - Each value-formatter branch — TEMPORAL parse success and fallback, FREETEXT truncation, RELATIONAL_REFERENCE resolution and fallback, default rendering, null values.
  - Relative-date bucketing for today / days / weeks / months / years.
  - Deterministic output — identical contexts produce byte-identical text.

---

## Related

- [[EnrichmentService]] — produces the context this service consumes
- [[EnrichmentActivitiesImpl]] — invokes `buildText` from the `constructEnrichedText` activity
- [[EnrichmentWorkflow]] — the workflow that orchestrates both
- [[EmbeddingProvider]] — consumes the produced text downstream
- [[Flow - Entity Enrichment Pipeline]] — end-to-end flow
