---
name: docs:diff
description: "Analyze branch diffs and write/update frontend documentation in the architecture vault"
allowed-tools:
  - Read
  - Write
  - Edit
  - Glob
  - Grep
  - Bash
  - Task
  - AskUserQuestion
  - EnterPlanMode
  - ExitPlanMode
---

<objective>
Analyze all code changes on the current branch relative to `main`, then write or update frontend documentation in `../../docs/frontend-design/`. This command produces authored page designs, frontend feature designs, and architecture docs — not stubs.

Output documents use the templates in `../../docs/templates/` and follow the conventions in the Obsidian vault's CLAUDE.md files.
</objective>

<execution_context>

## Vault Location

`../../docs/frontend-design/` — relative to the client repo root. Absolute: `/home/jared/dev/riven/docs/frontend-design/`

Symlinked into the Obsidian vault at `2. Areas/2.1 Startup & Business/Riven/3. Frontend Design/`.

## Frontend Design Structure

```
frontend-design/
  architecture/              — cross-cutting concerns (routing, state, design system, auth, errors)
    {ConcernName}.md         — uses Frontend Architecture template
  {route-group}/             — mirrors Next.js App Router structure
    {PageName}.md            — Page Design (route composition)
    {FeatureName}.md         — Frontend Feature Design (component-level)
```

## File-to-Documentation Mapping

Map changed source files to documentation targets:

| Source path pattern | Doc type | Output location |
|---------------------|----------|-----------------|
| `app/{routeGroup}/**/*.tsx` (page/layout files) | Page Design | `frontend-design/{route-group}/` |
| `components/feature-modules/{feature}/**` | Frontend Feature Design | `frontend-design/{route-group}/` (match feature to its primary route) |
| `components/feature-modules/{feature}/hooks/**` | Updates to parent feature's State & Data section | — |
| `components/feature-modules/{feature}/store*/**` | Updates to parent feature's State Management section | — |
| `hooks/**` (shared) | Frontend Architecture doc | `frontend-design/architecture/` |
| `lib/api/**`, `lib/types/**` | Updates to feature docs' Data Fetching sections | — |
| `components/ui/**` | Frontend Architecture doc (Design System) | `frontend-design/architecture/` |
| `components/provider/**` | Frontend Architecture doc (relevant concern) | `frontend-design/architecture/` |
| `lib/auth/**` | Frontend Architecture doc (Auth & Session) | `frontend-design/architecture/` |
| `lib/util/**` | Updates to relevant feature or architecture docs | — |

### Route Group Resolution

Map `app/` directory structure to route groups:

| App directory | Route group |
|---------------|-------------|
| `app/dashboard/workspace/[workspaceId]/entity/**` | `workspace/` |
| `app/dashboard/workspace/[workspaceId]/workflow/**` | `workspace/` |
| `app/dashboard/settings/**` | `settings/` |
| `app/dashboard/templates/**` | `templates/` |
| `app/auth/**` | `auth/` |
| `app/dashboard/**` (top-level) | `dashboard/` |

### Feature-to-Route Resolution

When a feature module changes but no route file changes, determine the primary route by:
1. Grep for imports of the feature's components in `app/` page files
2. The importing page's route group is the feature's route group

## Templates

Located at `../../docs/templates/`:

| Template | Path | Use When |
|----------|------|----------|
| Frontend Feature Design - Full | `Design/Frontend Feature Design - Full.md` | Feature has deep component hierarchy, complex state, multiple interaction patterns, or 3+ non-trivial components |
| Frontend Feature Design - Quick | `Design/Frontend Feature Design - Quick.md` | Contained UI: single component, simple modal, dropdown, filter |
| Page Design | `Design/Page Design.md` | New route/page introduced or existing page composition changes significantly |
| Frontend Architecture | `Documentation/Frontend Architecture.md` | Cross-cutting concern introduced or changed (shared hooks, providers, design system, auth, error handling) |

## Vault Conventions

- **Frontmatter**: YAML with `tags:` array, `Created:` date (YYYY-MM-DD), `Domains:` array
- **Cross-references**: `[[WikiLink]]` syntax — link to backend domain docs, feature designs, and other frontend docs
- **Backend linking**: `Backend-Feature: "[[BackendFeatureName]]"` in frontmatter when a frontend feature corresponds to a backend feature design
- **File naming**: Descriptive name matching the feature/page (e.g., `Entity Data Table.md`, `Workspace Overview.md`)

</execution_context>

<process>

## Step 1: Enter Plan Mode

Call `EnterPlanMode`. All analysis happens in plan mode — no files are written until user approval.

## Step 2: Branch Context

```bash
git branch --show-current
git log main..HEAD --oneline
git diff main...HEAD --stat
git diff main...HEAD -- '*.tsx' '*.ts' '*.css' '*.json' ':!*.test.*' ':!node_modules' ':!.next' ':!lib/types/apis' ':!lib/types/models' ':!lib/types/runtime.ts'
```

If the branch IS `main` or has no commits ahead, inform the user and stop.

## Step 3: Classify Changes

For each changed file:

1. **Classify change type**: Added (A), Modified (M), Deleted (D), Renamed (R)
2. **Map to documentation target** using the file-to-documentation mapping table
3. **Classify component type**: page, layout, feature component, hook (query/mutation/form), store, provider, shared UI, utility, type definition, service
4. **Resolve route group** for page/feature changes

Build a change map:

```
Route Group: workspace/
  Page Changes:
    - app/dashboard/workspace/[workspaceId]/entity/page.tsx (Modified) — page
  Feature Changes:
    Feature: entity (components/feature-modules/entity/)
      - components/entity-data-table.tsx (Modified) — feature component
      - hooks/query/use-entities.ts (Modified) — query hook
      - store/entity.store.ts (Added) — store

Cross-Cutting:
  - hooks/use-debounce.ts (Added) — shared hook
  - components/ui/data-table/columns.tsx (Modified) — design system
```

## Step 4: Read Context

### Templates
Read templates needed based on Step 3 findings (from `../../docs/templates/`).

### Exemplar Docs
For each affected route group, read 1-2 existing frontend design docs to calibrate voice, depth, and conventions. If none exist yet (first frontend docs), read a backend exemplar from `../../docs/system-design/` for general voice calibration.

### Existing Frontend Docs
Glob `../../docs/frontend-design/**/*.md` to understand current state and valid `[[WikiLink]]` targets.

### Backend Cross-References
For features that have backend counterparts, read the relevant backend feature design to understand the API contract. Glob `../../docs/system-design/feature-design/**/*.md` for valid backend cross-reference targets.

### Source Code Context
For each feature being documented, read the actual source files (not just the diff) to understand:
- Component tree structure (what renders what)
- Props interfaces and TypeScript types
- TanStack Query keys, stale times, invalidation patterns
- Zustand store shape and actions
- Route params and URL state

## Step 5: Analysis & Impact Plan

Write a structured plan:

**1. Change Summary**
- Branch purpose (high-level)
- Commits involved
- Scope: files changed, route groups affected, features touched

**2. Existing Documents to Update**

| Document | Location | Sections Affected | What Changed |
|----------|----------|-------------------|--------------|

**3. New Documents to Create**

| Name | Type | Template | Route Group | Rationale |
|------|------|----------|-------------|-----------|

Include Full vs Quick rationale for each frontend feature design.

**4. Cross-Cutting Architecture Changes**

| Concern | Document | Action | Detail |
|---------|----------|--------|--------|
| Shared hooks | `architecture/Hooks.md` | Create/Update | Added `useDebounce` |

**5. Backend Cross-References**

| Frontend Doc | Backend Feature Design | Relationship |
|-------------|----------------------|--------------|

**6. Architecture Impact**
- New state management patterns introduced
- New shared components or hooks
- Changes to data fetching patterns
- New route/navigation changes
- New interaction patterns

**7. Points to Consider**
- Frontend patterns that deviate from CLAUDE.md conventions
- Missing backend feature designs that should exist
- Components that may need their own feature design doc in future

After writing the plan, call `ExitPlanMode`. Do NOT proceed until the user approves.

**CRITICAL:** Once approved, proceed DIRECTLY to Step 6. Do NOT re-invoke this skill or re-enter plan mode.

## Step 6: Generate/Update Documentation

Spawn Task sub-agents (up to 3 parallel) grouped by work type:

### Agent Group 1: Page & Feature Design Docs

For each new or updated doc, the sub-agent must:

- Use the appropriate template as structural skeleton
- Populate every section with real content derived from the source code:
  - **User Flows**: Derived from route structure, navigation patterns, conditional rendering in page/layout files
  - **Component Design**: Actual component tree from source — real component names, real props interfaces (copy TypeScript types from source), real shared component usage
  - **State Management**: Real TanStack Query keys and stale times from hook files, real Zustand store shapes from store files, URL params from route definitions
  - **Data Fetching**: Real endpoint paths from service/API files, real query/mutation hook signatures
  - **Interaction Design**: Derived from event handlers, keyboard listeners, drag-and-drop setup in source
  - **Error/Empty/Loading States**: Derived from conditional rendering in components (`isLoading`, `isError`, empty checks)
- Follow frontmatter conventions: `tags` (pick one status, one priority — remove others), `Created` (today's date), `Domains`, `Backend-Feature` (if applicable), `Route` (for page designs)
- Use `[[WikiLink]]` for all cross-references (backend domains, other frontend docs, feature designs)
- Link to backend feature designs via `Backend-Feature` frontmatter and inline references in Data Fetching sections

### Agent Group 2: Architecture Docs

For cross-cutting changes:
- Read or create the relevant architecture doc in `frontend-design/architecture/`
- Document the pattern/convention with real code examples from the diff
- Link to features that use the pattern

### Agent Group 3: Existing Doc Updates

For docs that need section updates:
- Read the current file
- Update ONLY sections affected by the diff
- Preserve all existing content unchanged
- Add new components, hooks, state, or interactions to relevant sections

**Sub-agent instructions must include:**
- The relevant template content
- The diff content AND full source content for files being documented
- Exemplar doc content for voice calibration
- List of all existing doc names for valid `[[WikiLink]]` targets
- Exact output file paths

## Step 7: Verification

1. **Glob** all created/modified files in `../../docs/frontend-design/`
2. Verify each file:
   - Valid YAML frontmatter (`---` delimiters, `tags:` array, `Created:` date)
   - No template placeholder text (`{{`, `_What`, `_How`, `_One paragraph`, `ComponentName`, `Feature A`)
   - All `[[WikiLinks]]` reference existing documents
   - Mermaid blocks have matching fences and valid diagram types
   - `Backend-Feature` links (if present) reference real backend docs
3. Fix issues before proceeding

## Step 8: Summary Report

### 1. Documents Created
| Name | Type | Path |
|------|------|------|

### 2. Documents Updated
| Name | Sections Edited | Why |
|------|----------------|-----|

### 3. Architecture Impact
- State management changes
- New shared patterns
- Navigation/routing changes
- Backend integration changes

### 4. Points of Consideration
- Documentation gaps needing manual attention
- Areas where diff touched code but existing docs were already accurate
- Frontend patterns worth noting

</process>

<success_criteria>
- All new frontend features from the diff have authored documentation with no template placeholders
- Page designs exist for new routes with accurate composition maps
- Existing docs affected by the diff are updated with targeted section edits
- Cross-cutting architecture docs reflect new shared patterns
- Backend cross-references link to real backend feature designs
- All `[[WikiLinks]]` resolve to existing or newly created documents
- Frontmatter is valid with correct tags, dates, and domains
- User approved the plan via `ExitPlanMode` before any files were written
</success_criteria>
