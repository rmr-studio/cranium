---
phase: 05-attribute-semantic-configuration
plan: 01
subsystem: entity-type-semantics
tags: [knowledge-api, semantic-metadata, attribute-form, classification]
dependency_graph:
  requires: []
  provides: [knowledge-api-factory, knowledge-service, semantic-metadata-hook, semantic-form-fields]
  affects: [schema-form, use-schema-form]
tech_stack:
  added: []
  patterns: [api-factory, static-service-class, tanstack-query-hook, zod-schema-extension]
key_files:
  created:
    - lib/api/knowledge-api.ts
    - components/feature-modules/entity/service/knowledge.service.ts
    - components/feature-modules/entity/hooks/query/type/use-semantic-metadata.ts
  modified:
    - components/feature-modules/entity/hooks/form/type/use-schema-form.ts
    - components/feature-modules/entity/components/forms/type/attribute/schema-form.tsx
decisions:
  - "Classification suggestions shown as hint text, never auto-filled per CONTEXT.md locked decision"
  - "Tags field deferred -- always sends tags:[] in semantics payload"
  - "Definition character limit is soft (UI-only amber counter at 500) with no hard validation"
metrics:
  duration: "~3 minutes"
  completed: "2026-03-05"
---

# Phase 5 Plan 01: Knowledge API + Semantic Form Fields Summary

Knowledge API infrastructure (factory, service, query hook) plus semantic classification dropdown and definition textarea integrated into the attribute schema form.

## What Was Done

### Task 1: Knowledge API Infrastructure
- Created `createKnowledgeApi` factory function in `lib/api/knowledge-api.ts` following the exact `createEntityApi` pattern
- Created `KnowledgeService.getAllMetadata` static method with session/UUID validation and error normalization
- Created `useSemanticMetadata` TanStack Query hook returning `AuthenticatedQueryResult<SemanticMetadataBundle>` with 5-minute stale time and auth gating

### Task 2: Semantic Form Fields
- Extended `attributeFormSchema` with `classification` (nativeEnum, optional/nullable) and `definition` (string, optional/nullable) fields
- Added `semanticMetadata` parameter to `useEntityTypeAttributeSchemaForm` for edit-mode pre-population
- Updated `handleSubmit` to conditionally attach `semantics` to `SaveAttributeDefinitionRequest` only when user has set classification or definition
- Added "Semantic Context" UI section to SchemaForm with:
  - Classification `Select` dropdown (6 options, each with label + description)
  - Suggestion hint for new attributes based on schema type (e.g., Number -> "Suggested: Quantitative")
  - Definition `Textarea` with auto-expanding (`fieldSizing: content`), 500-char soft counter turning amber past limit
- Added `semanticMetadata` prop to SchemaForm Props interface for callers to pass attribute-specific metadata

## Deviations from Plan

None - plan executed exactly as written.

## Verification

- `npx tsc --noEmit` passes (2 pre-existing errors in unrelated blocks files, 0 new errors)
- `npx next lint` passes (all warnings pre-existing, 0 new warnings)
- All new files use kebab-case naming
- All imports use domain barrels (`@/lib/types/entity`)
- No `any` types introduced

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | 7d3850b46 | Knowledge API factory, service, and query hook |
| 2 | 7df4d5ca7 | Semantic classification and definition form fields |

## Self-Check: PASSED

All 5 files verified on disk. Both commit hashes found in git log.
