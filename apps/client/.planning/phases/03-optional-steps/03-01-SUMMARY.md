---
phase: 03-optional-steps
plan: "01"
subsystem: ui
tags: [react, tanstack-query, zustand, framer-motion, shadcn, onboarding, templates]

# Dependency graph
requires:
  - phase: 02-required-steps
    provides: formTrigger bridge pattern, liveData/setLiveData store actions, onboard-step-form STEP_FORMS map, workspace-preview crossfade pattern
provides:
  - createTemplatesApi factory function (lib/api/templates-api.ts)
  - useBundles TanStack Query hook fetching BundleDetail[] and ManifestSummary[]
  - TemplateStepForm with radio bundle selection and inline expansion
  - toggleBundleSelection pure helper (exported for tests)
  - TemplatesPreview with AnimatePresence crossfade on bundle selection
  - shadcn Select component (components/ui/select.tsx)
affects:
  - onboard-form-panel (uses STEP_FORMS map, already wired)
  - submission phase (templates validatedData will contain selectedBundleKey)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - toggleBundleSelection pure helper exported from form component for testability
    - formTrigger registered as async () => true for optional steps
    - liveData sync via useEffect on selected state + query data
    - AnimatePresence keyed on selectedBundleKey for crossfade on bundle switch

key-files:
  created:
    - lib/api/templates-api.ts
    - components/feature-modules/onboarding/hooks/query/use-bundles.ts
    - components/feature-modules/onboarding/components/forms/template-step-form.tsx
    - components/feature-modules/onboarding/components/forms/template-step-form.test.ts
    - components/ui/select.tsx
  modified:
    - components/feature-modules/onboarding/components/onboard-step-form.tsx
    - components/feature-modules/onboarding/components/previews/templates-preview.tsx

key-decisions:
  - "toggleBundleSelection exported as pure helper rather than embedded in component for test isolation without React/DOM setup"
  - "formTrigger always resolves true for optional steps — validation is not needed, Next still commits liveData to validatedData"
  - "useBundles fetches both listBundles and listTemplates in a single Promise.all — single query key ['bundles'] covers both"

patterns-established:
  - "Optional step pattern: register async () => true formTrigger, use liveData for preview reactivity without validation"
  - "Cross-referencing BundleDetail.templateKeys against ManifestSummary[] by key for display enrichment"

requirements-completed: [TMPL-01, TMPL-02, TMPL-03, TMPL-04, TMPL-05]

# Metrics
duration: 8min
completed: 2026-03-13
---

# Phase 3 Plan 01: Template Step — Bundle Catalog, Selection UI, and Live Preview Summary

**Template catalog integration with radio-style bundle selection, inline detail expansion, and AnimatePresence crossfade preview driven by liveData['templates'] store sync**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-13T06:53:15Z
- **Completed:** 2026-03-13T07:01:00Z
- **Tasks:** 3 (0: API/hook/test scaffold, 1: TemplateStepForm, 2: TemplatesPreview)
- **Files modified:** 7

## Accomplishments

- `createTemplatesApi` factory and `useBundles` hook fetching both bundle catalog and template manifests
- `TemplateStepForm` with radio-style toggle, inline animated expansion showing template names and entity type counts, always-true formTrigger for optional step
- `TemplatesPreview` upgraded from static skeleton to store-driven crossfade between skeleton grid and entity type cards on bundle selection
- All 102 tests passing including 4 new template step tests

## Task Commits

1. **Task 0: API factory, query hook, test scaffold (RED)** - `b46fc4007` (test)
2. **Task 1: TemplateStepForm + STEP_FORMS wire-up (GREEN)** - `35e915314` (feat)
3. **Task 2: TemplatesPreview crossfade** - `3c2cc7928` (feat)
4. **Auto-fix: missing Select component** - `e2259c02b` (fix)

## Files Created/Modified

- `lib/api/templates-api.ts` - Factory function creating TemplatesApi with session auth, mirrors workspace-api.ts
- `components/feature-modules/onboarding/hooks/query/use-bundles.ts` - TanStack Query hook, Promise.all for bundles+templates
- `components/feature-modules/onboarding/components/forms/template-step-form.tsx` - Bundle selection form with toggleBundleSelection helper
- `components/feature-modules/onboarding/components/forms/template-step-form.test.ts` - Tests for toggleBundleSelection and formTrigger
- `components/feature-modules/onboarding/components/previews/templates-preview.tsx` - Store-driven live preview with crossfade
- `components/feature-modules/onboarding/components/onboard-step-form.tsx` - Added templates: TemplateStepForm to STEP_FORMS
- `components/ui/select.tsx` - Standard shadcn/ui new-york Select component (auto-fix)

## Decisions Made

- Exported `toggleBundleSelection` as a pure helper for test isolation — testing selection logic without React/DOM reduces test brittleness
- `useBundles` uses a single `Promise.all` and single query key `['bundles']` — both datasets are always needed together
- formTrigger registered as `async () => true` to match optional step contract — step can be skipped or passed without validation

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added missing shadcn Select component**
- **Found during:** Task 1 (TemplateStepForm implementation) — tests passed during Task 0 but failed after Task 1 created the form file
- **Issue:** `team-step-form.tsx` imports `@/components/ui/select` which did not exist. Once `template-step-form.tsx` existed, Jest resolved the full module graph: `template-step-form` → `use-onboard-store` → `onboard.store` → `onboard-steps` → `team-preview` → `team-step-form` → missing `select`
- **Fix:** Created standard shadcn/ui new-york Select component wrapping `@radix-ui/react-select` (which was already installed)
- **Files modified:** `components/ui/select.tsx`
- **Verification:** All 102 tests pass
- **Committed in:** `e2259c02b`

---

**Total deviations:** 1 auto-fixed (Rule 3 — blocking missing dependency)
**Impact on plan:** Auto-fix unblocked tests and fixed a pre-existing broken import. No scope creep.

## Issues Encountered

None beyond the auto-fixed blocking issue above.

## Next Phase Readiness

- Template step fully integrated into onboarding flow with API integration, selection UI, and live preview
- `validatedData['templates']` will receive `{ selectedBundleKey, bundles, templates }` on Next click
- Submission phase can read `selectedBundleKey` from `validatedData['templates']` to install selected bundle
- Team step (Phase 3 Plan 02) can proceed independently

---
*Phase: 03-optional-steps*
*Completed: 2026-03-13*

## Self-Check: PASSED

All files exist on disk. All commits verified in git log.
