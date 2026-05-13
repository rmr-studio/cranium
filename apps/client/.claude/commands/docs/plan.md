---
name: docs:plan
description: "Generate fully authored frontend documentation from GSD phase plans"
argument-hint: "<phase-number>"
allowed-tools:
  - Read
  - Write
  - Glob
  - Grep
  - Bash
  - Task
  - AskUserQuestion
  - EnterPlanMode
  - ExitPlanMode
---

<objective>
Translate a GSD phase's planning documents into fully authored frontend documentation. Read phase plans, requirements, research, and codebase analysis from `.planning/`, then generate page designs, frontend feature designs, and cross-cutting architecture docs in `../../docs/frontend-design/` using templates from `../../docs/templates/`.

This skill produces frontend-focused documentation: component hierarchies, state management, user flows, interaction design, and data fetching patterns. Backend concerns (data models, API internals, database migrations) belong in the backend `docs:plan` — this skill references backend feature designs for API contracts but does not duplicate them.
</objective>

<execution_context>

## Source Data (read from `.planning/`)

| File | Purpose |
|------|---------|
| `.planning/ROADMAP.md` | Phase definitions, requirement mappings, success criteria |
| `.planning/REQUIREMENTS.md` | Full requirement text by ID |
| `.planning/PROJECT.md` | Key architectural decisions, constraints, existing architecture |
| `.planning/phases/NN-*/PLAN.md` | Phase-specific plans (tasks, components, implementation details) |
| `.planning/research/SUMMARY.md` | Executive summary, recommended approach, pitfalls |
| `.planning/research/ARCHITECTURE.md` | Architectural patterns, component boundaries, data flows |
| `.planning/research/FEATURES.md` | Feature analysis: table stakes, differentiators |
| `.planning/research/STACK.md` | Technology choices and rationale |
| `.planning/research/PITFALLS.md` | Critical risks with prevention strategies |
| `.planning/codebase/ARCHITECTURE.md` | Existing frontend patterns, layers, data flows |
| `.planning/codebase/STRUCTURE.md` | Directory layout, naming conventions |
| `.planning/codebase/CONVENTIONS.md` | Code conventions, component patterns |
| `.planning/codebase/STACK.md` | Current technology stack |

## Output Destination

- Page designs: `../../docs/frontend-design/{route-group}/`
- Frontend feature designs: `../../docs/frontend-design/{route-group}/`
- Architecture docs: `../../docs/frontend-design/architecture/`

Absolute base: `/home/jared/dev/cranium/docs/frontend-design/`

## Route Group Convention

Output directories mirror the Next.js App Router route groups:

| App directory | Route group folder |
|---------------|-------------------|
| `app/dashboard/workspace/[workspaceId]/entity/**` | `workspace/` |
| `app/dashboard/workspace/[workspaceId]/workflow/**` | `workspace/` |
| `app/dashboard/settings/**` | `settings/` |
| `app/dashboard/templates/**` | `templates/` |
| `app/auth/**` | `auth/` |
| `app/dashboard/**` (top-level) | `dashboard/` |

Create route group directories as needed.

## Templates

Located at `../../docs/templates/`:

| Template | Path | Use When |
|----------|------|----------|
| Frontend Feature Design - Full | `Design/Frontend Feature Design - Full.md` | Feature has 3+ of: deep component tree, complex state management, multiple interaction patterns, real-time data, drag-and-drop, inline editing, bulk operations |
| Frontend Feature Design - Quick | `Design/Frontend Feature Design - Quick.md` | Contained feature: single-purpose component, simple modal, filter, dropdown |
| Page Design | `Design/Page Design.md` | Phase introduces a new route or significantly restructures an existing page's composition |
| Frontend Architecture | `Documentation/Frontend Architecture.md` | Phase introduces cross-cutting frontend patterns (new state management approach, shared hooks, design system changes, auth flow changes) |

## Content Derivation — Frontend Patterns

When populating template sections, ground content in the project's established patterns from CLAUDE.md:

- **Component trees**: Follow feature-module structure — `components/feature-modules/{feature}/components/`
- **Props**: Inline types for simple components, named interfaces for complex ones. Reference shared types from `lib/interfaces/interface.ts`
- **State management**: TanStack Query for server state (keys as string arrays, stale time `5 * 60 * 1000`), Zustand for client state (separate State/Actions interfaces, factory pattern, context provider, selector hooks), URL params for filters/navigation state
- **Data fetching**: Hooks in `hooks/query/` named `use{Entity}` or `use{Entity}s`. Mutations in `hooks/mutation/` named `useSave{Entity}Mutation`. Use `toast.loading()`/`.success()`/`.error()` pattern
- **Styling**: Tailwind only, `cn()` for conditional classes, shadcn/ui components, Framer Motion for animation
- **Forms**: react-hook-form + zod + `@hookform/resolvers`. Custom hooks return `{ form, handleSubmit }`
- **Error handling**: `normalizeApiError()` for API errors, toast notifications in mutation hooks, no global error boundary

## Vault Conventions

- **Frontmatter tags** (pick applicable, remove others):
  - Features: `architecture/feature`, `architecture/frontend`, one `status/*`, one `priority/*`
  - Pages: `architecture/page`, `architecture/frontend`, one `status/*`, one `priority/*`
  - Architecture: `architecture/frontend`
- **Dates**: `Created: YYYY-MM-DD`
- **Domain links**: `Domains: ["[[DomainName]]"]` — use domain names from `../../docs/system-design/domains/`
- **Backend links**: `Backend-Feature: "[[BackendFeatureName]]"` — link to companion backend design
- **Route**: `Route: /app/...` — Next.js App Router path for page designs
- **Cross-references**: `[[Document Name]]` wiki-link syntax (exact filenames without path)

</execution_context>

<process>

## Step 1: Enter Plan Mode

Call `EnterPlanMode`. All analysis happens in plan mode — no files are written until user approval.

## Step 2: Validate Phase

Parse `$ARGUMENTS` as the phase number. If empty or non-numeric, ask the user which phase to document.

Read `.planning/ROADMAP.md` and locate the matching phase. Extract:
- Phase name and goal
- Requirement IDs
- Success criteria
- Dependencies on prior phases

If the phase doesn't exist, report the error and list available phases.

## Step 3: Gather Context

Read the following files (skip any that don't exist):

**Phase-specific:**
- All `PLAN.md` files matching `.planning/phases/{phase_number}-*/PLAN.md`

**Requirements and project context:**
- `.planning/REQUIREMENTS.md` — full text for each requirement ID in this phase
- `.planning/PROJECT.md` — decisions, constraints, architecture context
- `.planning/research/SUMMARY.md`, `ARCHITECTURE.md`, `FEATURES.md`, `STACK.md`, `PITFALLS.md`
- `.planning/codebase/ARCHITECTURE.md`, `STRUCTURE.md`, `CONVENTIONS.md`

**Existing documentation state:**
- Glob `../../docs/frontend-design/**/*.md` — existing frontend docs and valid `[[WikiLink]]` targets
- Glob `../../docs/system-design/feature-design/**/*.md` — backend feature designs for cross-referencing
- Glob `../../docs/system-design/domains/**/*.md` — backend domain docs for cross-referencing

**Existing source code (for grounding):**
- Read `app/` route structure to understand current navigation
- Read relevant `components/feature-modules/` to understand existing component patterns
- Read relevant stores and hooks to understand current state management

**Templates:**
- Read templates from `../../docs/templates/` that will be needed based on analysis

## Step 4: Identify Documentation Artifacts

### Pages

Identify new pages by looking for:
- Routes mentioned in the phase plan
- Navigation flows described in requirements
- New `app/` directory structure implied by the phase

### Frontend Features

Identify features by looking for:
- Distinct UI capabilities described in requirements (data table, workflow builder, dashboard widget, form wizard)
- Feature modules that will be created or significantly extended
- Complex components that need their own design doc

For each feature, determine template:
- **Full**: Deep component tree, complex state (multiple query keys + Zustand store + URL params), multiple interaction patterns (keyboard shortcuts, drag-and-drop, inline editing, bulk actions), or real-time data
- **Quick**: Single-purpose component, simple state, straightforward interactions

### Architecture Docs

Create architecture docs when the phase introduces:
- A new shared hook pattern used by multiple features
- Changes to the auth flow or provider setup
- New design system components or patterns
- New state management conventions
- New error handling or loading patterns

### Backend Cross-References

For each frontend feature, identify the backend feature design it depends on. If the backend design doesn't exist yet, note this in the plan — frontend can describe the API contract it expects, but should link to the backend design once created.

## Step 5: Analysis & Impact Plan

Write a structured plan:

**1. Phase Summary**
- Phase number, name, goal
- Requirements covered
- Success criteria
- Prior phase dependencies

**2. Existing Documents to Update**

| Document | Location | What Changes | Why |
|----------|----------|--------------|-----|

**3. New Documents to Create**

#### Pages
| # | Name | Route | Route Group | Features Composed |
|---|------|-------|-------------|-------------------|

#### Frontend Features
| # | Name | Template | Route Group | Backend Feature | Rationale |
|---|------|----------|-------------|-----------------|-----------|

Include Full vs Quick template rationale for each.

#### Architecture Docs
| # | Name | Concern | Trigger |
|---|------|---------|---------|

**4. Proposed File Structure**

```
CREATE: frontend-design/workspace/Entity Detail.md (Page)
CREATE: frontend-design/workspace/Entity Data Table.md (Feature - Full)
CREATE: frontend-design/architecture/State Management.md
```

**5. Backend Dependencies**

| Frontend Doc | Expected Backend Feature | Status |
|-------------|------------------------|--------|
| Entity Data Table | [[Entity Query Engine]] | Exists / Missing |

**6. Architecture Impact**
- New component patterns being established
- State management patterns being introduced
- Navigation/routing changes
- Shared component additions
- Design system implications

**7. Points to Consider**
- Gaps in planning docs (requirements lacking UI detail, missing wireframes)
- Decisions needing human input (visual design direction, interaction preferences)
- Assumptions about backend API shape
- Open questions

After writing the plan, call `ExitPlanMode`. Do NOT proceed until approved.

**CRITICAL:** Once approved, proceed DIRECTLY to Step 6. Do NOT re-invoke this skill or re-enter plan mode.

## Step 6: Generate Documents

Spawn Task sub-agents (up to 3 parallel):

### Agent 1: Page Design Docs

For each page doc, the sub-agent must:

- Use the Page Design template as skeleton
- Populate every section with content derived from the phase plan and existing codebase:
  - **Purpose**: From requirements — who uses this page, what they accomplish
  - **Route & Layout**: Real App Router path, real layout hierarchy from existing `app/` structure
  - **Page Composition**: ASCII wireframe showing feature zones, with `[[WikiLink]]` to each feature design. Derive layout from requirements, wireframes in planning docs, or analogous existing pages
  - **Page-Level Data**: Route param resolution, metadata queries. Reference real query patterns from codebase
  - **Navigation**: Inbound/outbound routes derived from the app's route structure and phase requirements
  - **Design Direction**: Reference `3.1. Visual Design Inspiration/` docs if applicable. Note density, tone, animation stance
  - **Responsive Layout**: Derive from existing breakpoint patterns in the codebase
- Frontmatter: `Route`, `Layout`, `Features` (wiki links to feature designs), `Domains` (backend domains this page surfaces)

### Agent 2: Frontend Feature Design Docs

For each feature doc, the sub-agent must:

- Use Full or Quick template as skeleton
- Populate every section from planning context and codebase patterns:
  - **User Flows**: Mermaid flowcharts — entry points from the page, happy path through the feature, error/empty states, exit points. Derived from requirements
  - **Component Design**: Propose component tree following the feature-module pattern. Name components descriptively. Write real TypeScript props interfaces. Reference shared components from `components/ui/`
  - **State Management**: Concrete TanStack Query keys (string array format), stale times, Zustand store shapes (if needed), URL params. Follow the project's established patterns
  - **Data Fetching**: Reference backend API endpoints (link to backend feature design). Name query/mutation hooks following convention (`use{Entity}`, `useSave{Entity}Mutation`). Describe loading/skeleton strategy
  - **Interaction Design**: Keyboard shortcuts, drag-and-drop, modals/drawers, inline editing — only what the feature requires
  - **Error/Empty/Loading States**: Every non-happy-path state with what the user sees and what action they can take
- Frontmatter: `Backend-Feature` (wiki link to backend design), `Pages` (wiki links to page designs using this feature), correct tags

### Agent 3: Architecture Docs

For cross-cutting docs:
- Use Frontend Architecture template
- Document the pattern with real conventions, do/don't rules, and code examples grounded in the project's stack
- Link to features that will use the pattern
- Reference existing CLAUDE.md conventions where applicable

**Sub-agent instructions must include:**
- Relevant template content
- Phase plan content, requirement text, and research excerpts
- Existing codebase patterns (from CLAUDE.md and read source files)
- Cross-reference manifest (all existing + planned doc names)
- Exact output file paths
- Backend feature design content for API contract grounding

## Step 7: Verification

1. **Glob** all created/modified files in `../../docs/frontend-design/`
2. Verify each file:
   - Valid YAML frontmatter (`---`, `tags:`, `Created:`)
   - No template placeholders (`{{`, `_What`, `_How`, `_One paragraph`, `ComponentName`, `Feature A`, `[[]]`)
   - All `[[WikiLinks]]` reference real documents
   - Mermaid blocks valid
   - `Backend-Feature` links reference real backend docs (or are noted as pending)
   - TypeScript interfaces in Component Design sections are syntactically valid
3. Fix issues before proceeding

## Step 8: Summary Report

### 1. Documents Created
| Name | Type | Route Group | Path |
|------|------|-------------|------|

### 2. Documents Updated
| Name | What Changed | Why |
|------|-------------|-----|

### 3. Backend Dependencies
| Frontend Doc | Backend Design | Status |
|-------------|---------------|--------|

### 4. Verification Results
- Total documents created/updated
- Issues found and resolved
- Cross-references validated

### 5. Next Steps
- Suggest running for subsequent phases
- Note backend designs that need to be created first
- Flag decisions needing human input (visual design, interaction details)

</process>

<success_criteria>
- All frontend features identified in the phase have authored design documents with no template placeholders
- Page designs exist for new routes with accurate composition maps and feature zone links
- Frontend feature designs contain real component trees, TypeScript interfaces, TanStack Query patterns, and interaction specs
- Architecture docs capture cross-cutting patterns with conventions and examples
- Backend cross-references link to real backend feature designs (or are flagged as pending)
- All `[[WikiLinks]]` resolve to existing or newly created documents
- Frontmatter is valid with correct tags, dates, domains, and backend links
- Route groups in output paths match the App Router structure
- User approved the plan via `ExitPlanMode` before any documents were written
</success_criteria>
