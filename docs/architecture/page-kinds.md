---
type: resource
Created: 2026-05-13
Updated: 2026-05-13
tags:
  - architecture/page-kinds
  - cranium/v1
---
# Page Kinds (v1)

The `pages.kind` enum is backed by a Kotlin **sealed `Page` hierarchy**. v1 = **7** kinds. Each subclass owns: frontmatter shape, `synthesisContract` (`aggregations` + `windows` + `narrative sections` + `narrativeGenerator: TEMPLATED | LLM` + `bodyTokenBudget`), and per-kind helpers (resolution key, link-emit rules, render hooks). The `riven.core.aggregation` engine + `NarrativeArcGenerator` consume the contract off the sealed object — no JSON manifest.

`feature` / `interaction` / `sop` / `insight` kinds are **explicitly out of v1**.

See [[overview]] for the layer model and [[architecture-pivot]] for the locked decisions. Frontmatter shape rationale: [[ADR-013]]. Resolution policy: [[ADR-015]].

---

## `DomainPage`

- **Represents:** a code domain — a directory subtree, the unit of ownership and the place ADRs hang off.
- **Resolution:** **deterministic key** — directory path. File → its directory → the Domain page. The common case; off the vector path.
- **Frontmatter:** `path`, `owner` (Person page ref), `smes` (Person page refs), `repos`, `aliases` (e.g. `auth` / `authentication` / `authn`), `adr_refs`, `confidence`.
- **Aggregations / windows:** recent-PR volume (7d/30d/90d), distinct contributors, review-density, decision count, drift indicators (commits since the owner last touched it), churn.
- **Narrative sections:** Overview · Who owns this · Recent activity · Key decisions · Drift / risk.
- **`narrativeGenerator`:** `LLM` (the prose is the value here).
- **`bodyTokenBudget`:** ~`3000`.
- **Key helpers:** `directoryToDomainKey()`, `aliasMatch()`, `rollUpFromFilePages()`.
- **Links it emits:** `contains` → File pages; `owns` ← Person page (the owner); `defines` → ADR/Decision pages; `depends_on` → other Domain pages (from import/co-change signal, weighted).

## `PersonPage`

- **Represents:** a human contributor; the answer to "who do I ask about X". Mirrored from workspace members where possible.
- **Resolution:** **deterministic key** — git commit-author email. Workspace-member identity → GitHub login → commit-author email is the bridge. Aliases (multiple emails) handled by the identity-cluster machinery → `page_merge_candidates`.
- **Frontmatter:** `emails`, `github_login`, `display_name`, `workspace_member_id` (nullable), `owns_domains` (Domain page refs), `aliases`, `confidence`.
- **Aggregations / windows:** PRs authored / reviewed (7d/30d/90d), domains touched, review load, recency (last commit), areas of concentration (blame-weighted).
- **Narrative sections:** Overview · What they own · Recent work · Review load · Ask-this-person hints.
- **`narrativeGenerator`:** `TEMPLATED` (mostly structured rollup; LLM-opt-in if the prose is wanted).
- **`bodyTokenBudget`:** ~`1500`.
- **Key helpers:** `emailToPersonKey()`, `mergeAliasEmails()`.
- **Links it emits:** `owns` → Domain pages; `authored` is a `references` link via `source_entity_id` (the commit/PR); `mentions` ← Decision pages that reference them.

## `FilePage`

- **Represents:** a single source file. Rolls up into its Domain page the way `SKU → Order → Customer` did.
- **Resolution:** **deterministic key** — repo-relative file path. Renames handled by the parser (git rename detection → re-key, keep history link).
- **Frontmatter:** `path`, `domain` (Domain page ref), `owners` (blame-concentration Person refs), `adr_refs`, `language`, `confidence`.
- **Aggregations / windows:** churn (commits, lines) 7d/30d/90d, distinct authors, blame concentration, ADR references count.
- **Narrative sections:** Overview · Owners · Churn · Referenced decisions.
- **`narrativeGenerator`:** `TEMPLATED` ($0; thousands of these — LLM would be a cost sink).
- **`bodyTokenBudget`:** ~`800`.
- **Key helpers:** `pathToFileKey()`, `blameConcentration()`, `rollUpToDomain()`.
- **Links it emits:** `contains` ← Domain page; `references` → ADR/Decision pages (touched-file-references-an-ADR); `owns` ← Person pages (blame).

## `DecisionPage`

- **Represents:** a decision the team made — may originate from an ADR file, a Slack-thread conclusion (v1.1), or a meeting outcome. Distinct from `ADRPage`: a Decision is the *thing decided*; an ADR is *one document* recording it.
- **Resolution:** **fuzzy-only**. There is **no canonical key** for a Decision. If ADR-backed, it keys on the backing ADR file path; with no backing file it is fuzzy-only — **never auto-created**, **always** via the `decision_queue` (`promote-to-Decision` suggestion). See invariant 3 / [[ADR-015]].
- **Frontmatter:** `status` (`proposed | accepted | superseded | rejected`), `rationale`, `superseded_by` (Decision page ref), `adr_ref` (nullable), `touched_files` (File page refs), `domains` (Domain page refs), `confidence`.
- **Aggregations / windows:** files touched, supersession chain depth, age vs. domain activity (the "stale ADR" signal), re-litigation count (mentions across PRs).
- **Narrative sections:** What was decided · Why · What it touches · Superseded by / supersedes · Open questions.
- **`narrativeGenerator`:** `LLM`.
- **`bodyTokenBudget`:** ~`2500`.
- **Key helpers:** `adrPathToDecisionKey()` (when ADR-backed), `fuzzyResolve()`, `supersessionChain()`.
- **Links it emits:** `references` → File / Domain pages; `defines` ← Domain page; `mentions` → Person pages; `depends_on` → other Decision pages (supersession).

## `ADRPage`

- **Represents:** a single ADR document in `/docs/decisions/*.md` — the canonical record. The deterministic backbone the Decision-page fuzzy layer leans on.
- **Resolution:** **deterministic key** — the ADR file path. The most reliable resolution in the system.
- **Frontmatter:** `path`, `number`, `title`, `status`, `decision_ref` (the Decision page it records, nullable), `last_edited_at`, `confidence`.
- **Aggregations / windows:** referenced-by count (PRs, files), staleness delta (`last_edited_at` vs. activity in the domains it covers), 7d/30d/90d touch counts.
- **Narrative sections:** Summary · Status · Referenced by · Staleness signal.
- **`narrativeGenerator`:** `TEMPLATED` (it's a structured projection of an existing document; the document *is* the prose).
- **`bodyTokenBudget`:** ~`1200`.
- **Key helpers:** `adrPathToKey()`, `parseAdrFrontmatter()` (tolerant of malformed frontmatter — log + degrade, don't throw), `stalenessDelta()`.
- **Links it emits:** `defines` → Decision page; `references` ← File pages that touch it; `contains` ← Domain page.

## `SystemPage`

- **Represents:** a deployable/runnable unit larger than a domain — a service, an app, a top-level module. Coarser than `DomainPage`.
- **Resolution:** **deterministic key** where the repo layout makes it obvious (e.g. a top-level `apps/*` or `services/*` dir, a `build.gradle`/`package.json` boundary); **fuzzy fallback** otherwise.
- **Frontmatter:** `path` / `module_id`, `domains` (Domain page refs it contains), `owner` (Person page ref), `tech` (language/framework tags), `deploy_target`, `confidence`.
- **Aggregations / windows:** aggregate PR volume across its domains, contributor count, build/config churn.
- **Narrative sections:** Overview · What it contains · Owner · Recent activity.
- **`narrativeGenerator`:** `TEMPLATED` (LLM-opt-in).
- **`bodyTokenBudget`:** ~`1500`.
- **Key helpers:** `moduleBoundaryToKey()`, `rollUpFromDomains()`.
- **Links it emits:** `contains` → Domain pages; `owns` ← Person page; `depends_on` → other System pages.

## `FlowPage`

- **Represents:** a cross-file behaviour observed from **PR-cluster co-change only** — files that consistently change together → a candidate "flow". **Not** derived from the call graph.
- **Resolution:** **fuzzy-only**. No deterministic key — a flow is a cluster, not an artifact. Always candidate-find + score; ambiguous → `decision_queue`.
- **Frontmatter:** `member_files` (File page refs), `member_domains` (Domain page refs), `cohesion` (co-change strength), `label` (LLM-named), `confidence`.
- **Aggregations / windows:** co-change frequency, cluster size, recent PR activity touching ≥2 members.
- **Narrative sections:** What this flow appears to be · Files involved · Recent changes · Confidence note.
- **`narrativeGenerator`:** `LLM` (it has to *name* the cluster).
- **`bodyTokenBudget`:** ~`1500`.
- **Key helpers:** `coChangeCluster()`, `cohesionScore()`.
- **Links it emits:** `contains` → File pages; `references` → Domain pages.
- **Caveat:** ships **disabled** if it proves untrustworthy on the cranium dogfood. The decision-queue gate keeps a low-cohesion cluster from auto-materializing.

---

## Adding a page kind

1. New `sealed class … : Page` subclass — defines the `kind` enum value, the frontmatter shape, the `synthesisContract`, and the per-kind helpers.
2. A migration **only if** the `classification` enum or the `aggregations` jsonb shape changes.
3. A re-render of existing rows of adjacent kinds **if** their `synthesisContract` changes.
4. (OSS self-hosters add a kind the same way — via a PR, like a `CoreModelDefinition`. No runtime registration API — that would re-introduce the type machinery the pivot deleted.)

This is not free. Document the cost when you do it.

---

## Related
- [[overview]] — layer model, data model, invariants
- [[architecture-pivot]] — locked decisions
- [[domains/Pages & Links Core/Pages & Links Core|Pages & Links Core]] — the sealed-class registry lives here
- [[domains/Page Resolution & Decision Queue/Page Resolution & Decision Queue|Page Resolution & Decision Queue]] — how each kind is resolved
