---
tags:
  - priority/medium
  - status/draft
  - architecture/design
  - architecture/feature
Created: 2026-04-13
Updated: 2026-04-13
Domains:
  - "[[Lifecycle Analytics]]"
  - "[[Action Primitives]]"
  - "[[AI & Agents]]"
---
# Quick Design: Natural Language Dashboards and Visualisations

## What & Why

Design partners currently leave Riven to open Metabase whenever they want to see whether an operational change — a segment push, a campaign alteration, a monitoring rule, a policy tweak — actually moved the business. Riven holds the action; the warehouse holds the evidence; the operator holds the bridge between them in their head. This feature reframes that bridge as a first-class surface: the operator asks the question in natural language, the system composes an answer from Riven's own entity ecosystem, and the cause/effect of every action becomes visible **inside the tool where the action was taken**.

The ambition is not "build Metabase." It is: make the action loop legible. A workspace should be able to see what changed, why, and whether that matches intent — without ever exporting a CSV.

**Core thesis:** Riven's entity ecosystem is already a semantic layer. Identity-resolved entities, [[Smart Projection Architecture|projected domains]], [[SQL Aggregation Column Engine|aggregation columns]], [[Outcome Tracking|outcome events]], [[Living Segments|living segments]], and [[Lifecycle Analytics Views|lifecycle views]] together describe *what is true about a workspace*. A visualisation surface that composes over those primitives is a very different engineering problem — and a much more trustworthy product — than a freeform natural-language-to-SQL engine.

---

## Scope Boundaries

This feature design deliberately excludes:

- **Freeform NL → arbitrary SQL generation.** Rejected as a direction. Hallucinated SQL against a live warehouse is how AI-BI products lose user trust on the second wrong answer. The bounded alternative is described below.
- **User-authored aggregation columns / calculated fields.** Aggregation columns remain manifest-authored per [[SQL Aggregation Column Engine]]. User-level custom metric authoring is a separate feature that may be considered after this one stabilises.
- **A dashboard builder in the Looker/Metabase sense.** Saved layouts of composed cards are in scope conceptually; a full drag-and-drop layout engine with sharing, embedding, and permalinks is out of scope for the first pass.
- **Exports, public share links, embedded dashboards in external tools.** These are accepted follow-on concerns. Not in the fundamental primitives.

What **is** in scope is a small set of architectural primitives — described below — that together define "how Riven lets an operator ask a question about their data and trust the answer."

---

## Architectural Prerequisites (Current State Gap)

Before this feature is feasible, several pieces of existing architecture need to mature. This is a feature-design document, not an implementation plan — the intent of listing these is to surface the real dependency chain so that the feature is not prematurely scheduled.

1. **[[Data Extraction and Retrieval from Queries]]** is currently a stub. The classifier, parameter extractor, and retrieval layer described below cannot land until that document reaches a skeletal design.
2. **[[Prompt Construction for Knowledge Model Queries]]** is a stub. Any LLM-in-loop classification depends on a thought-through prompt-templating layer with schema injection, few-shot examples, and token-budget handling.
3. **[[Outcome Tracking]]** is draft status. Half of this feature's value (action-outcome visibility) depends on `action_events`, `outcome_definitions`, and the nightly evaluation job actually existing.
4. **[[Dashboard Metrics Pre-computation Layer]]** is deferred. Certain composed-answer surfaces — pinned cards, scheduled briefs — need a pre-compute path or they will breach latency targets at non-trivial workspace scale.
5. **[[Monitoring Rules]]** must be stable enough that a composed answer can emit a rule draft as one of its outputs.
6. **[[Extrapolate Agentic Perspectives from Entity Schema]]** overlaps directly with the "schema map" primitive described below. These may need to merge or explicitly align before either ships.

Shipping the feature without these in place means rebuilding parts of them inside the feature and then carrying that debt.

---

## Core Primitives

The feature is not one thing. It is six primitives that compose.

### 1. Question-Type Taxonomy

A small, bounded, opinionated set of **archetypes** of questions an operator asks about a workspace. Not free text patterns — a closed enum of question *shapes*. Each archetype has:

- A canonical question form ("how is X trending over Y?", "compare X before/after Z?", "why did X change?", "which Y contributes most to X?")
- A parameter surface (entity type, metric, filter, segment, time range, comparison axis)
- A render contract (chart type, table shape, delta treatment)

The taxonomy is additive — new archetypes can be introduced without destabilising prior ones. It is the single most important design decision in this feature, because it defines what the system can and cannot answer. A sensible starting set covers: **trend**, **comparison** (before/after, segment-vs-segment, period-over-period), **cohort** (retention, behaviour by acquisition window), **ranking** (top-N entities by metric), **decomposition** ("why did this change" — contribution by dimension), **outcome-of-action** (pre/post state around a recorded `action_event`), and **distribution** (spread of a metric across a population).

The taxonomy must be derived from observed operator questions, not invented. Harvesting questions from design partners before locking the taxonomy is a soft prerequisite.

### 2. Classifier & Parameter Extractor

The piece that turns natural language into a structured `{archetype, params}` tuple. Design intent:

- **LLM-assisted, not LLM-authoritative.** The LLM proposes a classification; a deterministic layer validates it against the taxonomy, the template registry, and the resolved parameter schema. Invalid proposals are rejected, not patched.
- **A deterministic fallback exists** for when the LLM is unavailable or low-confidence. Regex / keyword template matching is the minimum bar; it covers canonical phrasings of the most common archetypes.
- **Confidence is first-class.** Below a threshold, the system does not guess — it asks a clarifying question with two or three candidate reinterpretations.
- **PII redaction on input.** Operator questions will contain entity names and identifiers; these must be redacted before the classifier call and rehydrated after, so provider-side logging never holds identifiable workspace data.

### 3. Template Registry

A manifest-defined library of **parameterised query templates**. Each template:

- Belongs to an archetype
- Declares required and optional parameters, with schema
- Binds to a query composition — a combination of [[SQL Aggregation Column Engine|aggregation columns]], [[Lifecycle Analytics Views|lifecycle views]], [[Living Segments|segments]], and [[Smart Projection Architecture|projected entity domains]]
- Ships with per-[[Workspace Type Tiers|business-type]] variants (DTC, B2B SaaS, agency, etc.)

The registry is the moat. Users cannot generate SQL directly. The composer can only select and parameterise templates the manifest has vetted. This is what makes "wrong SQL" structurally impossible — the only failure modes are "wrong template selected" (user-visible and recoverable) and "template has a bug" (fixable once, in the registry).

### 4. Card Envelope

A single unified result shape that every archetype produces. Fields include:

- `archetype` — dispatch key for the renderer
- `widget` — chart / table / stat / composite
- `data` — the actual rows / series / aggregates
- `delta` — comparison against a reference period, if archetype applies
- `sources` — the aggregation columns, views, segments, templates that contributed
- `freshness` — timestamp of the underlying data
- `confidence` — classifier confidence and any degradation state (sampled result, timed-out partial, missing domain)
- `provenance` — the show-your-work record (archetype picked, template id, params resolved, row count sampled)

The envelope is the contract between composer and renderer. Renderer polymorphism is owned by the `widget` field; feature growth is envelope growth, not renderer sprawl.

### 5. Schema Map (Semantic Introspection)

A read-only surface that exposes *what the composer knows about this workspace*:

- The entity types, their domains, their aggregation columns
- The segments, monitoring rules, outcome definitions
- Which templates are available per entity type
- Sample values where useful

Its purpose is threefold: (a) answer the silent user question "what can I ask?", which is the cold-start problem in every chat-with-your-data product; (b) make the boundary of the system honest — if Riven does not know something, the user can see that it does not, rather than asking and getting a shrug; (c) serve as a dogfooding and debugging primitive for the team.

This primitive overlaps substantially with [[Extrapolate Agentic Perspectives from Entity Schema]] and the two should be reconciled rather than built twice.

### 6. Surfaces

Where composed answers appear. The primitives above are surface-agnostic. Candidate surfaces include:

- **An always-visible ask bar** in the [[Lifecycle Operations Dashboard|operations cockpit]]
- **The [[Daily Usage - Operations Cockpit Vision|Morning Brief]]** as a stream of scheduled and auto-generated cards
- **Inline miniaturised viz** on [[Inline Actions|queue items]] — the metric trajectory that justifies the queue item, shown right next to it
- **Saved collections** of pinned cards, per user or per workspace, as a user-facing "dashboard" concept without a full layout builder
- **Push channels** — a daily or weekly brief delivered to Slack or email, rendering the envelope to markdown/HTML
- **[[Monitoring Rules|Monitoring Rule drafts]]** — the same classifier path, different sink: instead of rendering a card, propose a rule for user review
- **Rule-fire annotations** — when a rule fires, nearby time-series cards gain a vertical beacon showing when and why

Each surface consumes the same envelope. Adding a surface should not require touching primitives 1–5.

---

## How This Closes the Action Loop

The feature exists because the current operator experience around altering operations is mute. To close the loop:

1. Every recorded `action_event` (per [[Outcome Tracking]]) gains a natural place to be *asked about* — "what happened after this?" becomes the **outcome-of-action** archetype.
2. Once an observation window closes, the system auto-generates a card answering that question and places it in the Morning Brief. The operator does not need to ask.
3. Any metric on any card supports a one-click **decomposition** — "why did this change?" — which runs the contribution archetype against the same query surface.
4. Anomalies (via monitoring rules) annotate time-series cards so that a trajectory and the rule that fired about it live on the same visual.
5. The "see and alert me about it" loop collapses into one interface — the same NL input path that answers a question can emit a rule draft.

The shift is not dashboards. It is that every action a workspace takes becomes something the workspace can see the consequence of, inside the tool, without a pivot to an external warehouse.

---

## Trust Model

The distinguishing design concern of this feature is trust. AI-BI products fail at the second wrong answer. The design is deliberately shaped by that failure mode:

- **Bounded output space.** No freeform SQL. The composer can only emit structured template invocations. "Wrong answer" means "wrong template picked," which is diagnosable and recoverable.
- **Show-your-work is not optional.** Every card exposes its provenance — which archetype was picked, which template, which parameters, how fresh the data is, what was sampled. Hidden behind a click is acceptable. Hidden behind a mental model of "just trust the AI" is not.
- **Graceful degradation is explicit.** Row caps and timeouts produce badged results, not silent truncation. A sampled result labels itself as sampled. A query over a partially-populated domain says so.
- **The system refuses rather than guesses.** Low classifier confidence surfaces a clarifier with two or three candidate reinterpretations. Unmapped questions route the user to the Schema Map.

---

## Failure Modes

- **Classifier mismatch with real operator phrasing.** If the taxonomy is invented rather than observed, answer rate drops, users abandon. Mitigation: harvest real questions from design partners before locking the taxonomy; treat the eval corpus as a load-bearing artifact, not a test convenience.
- **Semantic gap.** A question that cannot be answered from Riven's entity data. The Schema Map surfaces this honestly; the composer should also route the user to an alternative (pre-written query, analytics view, or explicit "not answerable here").
- **LLM provider outage or policy change.** Deterministic fallback covers common questions. A provider policy change (data handling, pricing) is a vendor risk that should inform the classifier abstraction — provider should be swappable.
- **Latency at scale.** Live composition over large entity sets will breach interactive targets. The pre-compute layer is the mitigation on hot paths (pinned cards, scheduled briefs); the ad-hoc path accepts a looser target with explicit in-progress UX.
- **Prompt injection via NL input.** Mitigated structurally — NL goes to classifier, classifier output is a parameter map, parameters flow through parameterised queries, never raw concatenation into SQL.
- **Empty Morning Brief during observation-window warm-up.** First one to two weeks after a workspace enables the feature, no outcomes have yet been evaluated. Seed with canned "try asking" queries and a single demo card to bridge the cold start.

---

## Relationship to Existing Planning Documents

This feature sits at an intersection and deliberately reuses rather than reinvents.

- **[[SQL Aggregation Column Engine]]** is the metric substrate. Templates compose aggregation columns. No metric is invented outside the manifest.
- **[[Lifecycle Analytics Views]]** provides three pre-built cross-domain aggregations (channel performance, cohort overview, support-revenue correlation) that are effectively the first entries in the template registry.
- **[[Pre-written Lifecycle Queries]]** is the closed-form ancestor of this feature. Its manifest query templates are the seed library for the natural-language version; the ask bar generalises "click to run."
- **[[Outcome Tracking]]** provides the action-outcome substrate. Without it, the loop-closing promise has no data.
- **[[Delta-First Surfaces]]** defines how metrics are rendered with change-over-time as the default. The card envelope carries the delta field; the renderer applies delta-first treatment by default.
- **[[Lifecycle Operations Dashboard]]** is the primary host surface for the ask bar, pinned cards, and inline viz.
- **[[Daily Usage - Operations Cockpit Vision]]** defines the daily-habit flywheel this feature reinforces.
- **[[Monitoring Rules]]** is both an input (rule fires → Anomaly Beacon) and an output (NL → rule draft) of the composer.
- **[[Dashboard Metrics Pre-computation Layer]]** is a latency mitigation the hot paths depend on.
- **[[Data Extraction and Retrieval from Queries]]** and **[[Prompt Construction for Knowledge Model Queries]]** are the implementation details of the classifier layer; currently stubs.
- **[[Extrapolate Agentic Perspectives from Entity Schema]]** overlaps with the Schema Map primitive.
- **[[CLI and MCP Server for Agent Interaction]]** is a downstream consumer — the same template registry and schema map can be exposed as agent tooling once this feature is stable.

---

## Open Questions for Design Partner Iteration

These are the questions whose answers should come from more design partners rather than architectural deliberation. They are deliberately not resolved in this document.

1. **What does the current Metabase usage actually look like?** How much is "did it work?" versus freeform ad-hoc versus CSV-for-the-boss versus dashboards-for-stakeholders? Without this data, the taxonomy and priorities are guesswork. The answer directly determines scope.
2. **Which archetypes are most common in practice?** The seven-archetype starting set is a hypothesis. Real operator questions will confirm, reject, or add to it.
3. **How do operators phrase these questions?** "Show me," "why did," "compare," "how is X doing" — phrasing patterns shape the classifier's prompt and fallback regex set.
4. **What is the natural host surface?** An always-visible ask bar, a dedicated page, a keyboard-invoked command palette, a chat pane — each has different UX consequences. Design partners should weigh in.
5. **How much custom saving do users actually want?** Pin-for-self, per-workspace shared layouts, role-based presets, named dashboards — the scope of the saved-dashboard primitive is best sized by watching what partners ask to save repeatedly.
6. **How much of the answer do users want to see versus trust?** Show-your-work is non-negotiable in principle; its default visibility (collapsed, expanded-on-first-use, always-expanded) is a real design question.
7. **Where is the Metabase gap unfillable?** For questions that require data Riven does not ingest or domains Riven does not model, what is the graceful off-ramp? Link to Metabase? Suggest a new integration? Silently unanswerable?
8. **Observation window defaults per action type.** Outcome Tracking defaults to a single workspace-level window; in practice flash campaigns want shorter windows than retention plays. Whether per-action override lands here or in Outcome Tracking is a design call.
9. **Push channels or in-app only?** Slack and email briefs extend the daily-habit flywheel to where operators live, at the cost of pulling them out of the app. The value of in-app time vs distribution reach is a strategic question, not an architectural one.
10. **Business-type template packs.** The manifest can ship per-vertical query libraries; the return on investment depends on whether partners cluster around specific verticals.

---

## Deliberate Non-Goals

- This feature does not replace Metabase for users who need raw SQL or schemas Riven does not model. It narrows the gap until most operator questions are answerable in-app; it does not close the gap to zero.
- This feature does not ship with a full dashboard builder in the first pass. Pinning and small saved collections are the scope.
- This feature does not aim to be a general-purpose BI tool. It is purpose-built for operators closing action loops over the workspace's own entity ecosystem.

---

## Next Design Steps

1. Before committing to implementation, instrument design-partner workspaces to observe actual analytical usage patterns. Harvest real questions, measure archetype distribution, identify semantic gaps.
2. Resolve [[Data Extraction and Retrieval from Queries]] and [[Prompt Construction for Knowledge Model Queries]] from stubs into skeletal designs.
3. Reconcile Schema Map with [[Extrapolate Agentic Perspectives from Entity Schema]].
4. Lock the first-version archetype taxonomy from observed data, not a priori reasoning.
5. Decide the scope and shape of the saved-dashboard primitive from observed pinning behaviour in a closed beta.
6. Iterate the card envelope schema against real rendered cards before freezing.
