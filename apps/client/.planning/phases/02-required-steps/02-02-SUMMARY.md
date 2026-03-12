---
phase: 02-required-steps
plan: "02"
subsystem: onboarding
tags: [workspace, form, preview, plan-selector, avatar, live-data, crossfade]
dependency_graph:
  requires: [02-01]
  provides: [workspace-step-form, workspace-preview]
  affects: [onboard-step-form, profile-preview]
tech_stack:
  added: []
  patterns: [zod-nativeEnum, plan-card-selector, avatar-helpers-util, AnimatePresence-crossfade]
key_files:
  created:
    - components/feature-modules/onboarding/components/forms/workspace-step-form.tsx
    - components/feature-modules/onboarding/components/forms/workspace-step-form.test.ts
    - components/feature-modules/onboarding/utils/avatar-helpers.ts
  modified:
    - components/feature-modules/onboarding/components/previews/workspace-preview.tsx
    - components/feature-modules/onboarding/components/previews/profile-preview.tsx
    - components/feature-modules/onboarding/components/onboard-step-form.tsx
decisions:
  - "avatar-helpers extracted to utils/ to avoid duplication between profile-preview and workspace-preview"
  - "Plan selector uses clickable cards with setValue({shouldValidate:false}) per user decision to only validate on Next"
  - "No currency field in workspace step — WORK-02 satisfied by silent browser-locale default"
metrics:
  duration: "~4 minutes"
  completed: "2026-03-12"
  tasks_completed: 3
  files_changed: 6
---

# Phase 02 Plan 02: Workspace Step Form and Live Preview Summary

**One-liner:** Workspace step form with 4-tier plan card selector, avatar upload, zod validation, and live preview crossfade using shared avatar helpers.

## What Was Built

### Task 0: Schema test scaffold (RED)
Created `workspace-step-form.test.ts` with 8 boundary tests for `workspaceStepSchema`. Tests run in RED because the implementation file did not exist yet.

### Task 1: WorkspaceStepForm (GREEN)
- `workspaceStepSchema` with `displayName` (min 3) and `plan` (`z.nativeEnum(WorkspacePlan)`) — no currency field
- `WorkspaceStepForm` component mirrors `ProfileStepForm` pattern exactly: `useForm` + `zodResolver`, `registerFormTrigger` on mount, `form.watch` subscription writes to `setLiveData('workspace', ...)`, back-navigation restore from `liveData['workspace']`
- Plan selector: 4 clickable card buttons (Free/$0/mo, Startup/$29/mo, Scale/$79/mo, Enterprise/Custom) with `ring-2 ring-primary` on selection, `setValue` with `shouldValidate: false` on click
- `AvatarUploader` with 5MB JPEG/PNG/WebP validation
- `onboard-step-form.tsx` STEP_FORMS map updated with `workspace: WorkspaceStepForm`
- All 8 schema tests pass GREEN

### Task 2: WorkspacePreview live crossfade
- Reads `liveData['workspace']` from onboard store
- `AnimatePresence mode="wait"` crossfade for avatar (skeleton -> initials -> image), name (skeleton -> text), and plan badge (skeleton -> colored pill)
- Plan badge color variants: Free=muted, Startup=blue, Scale=violet, Enterprise=amber
- Initials circle uses `rounded-lg` (workspace style, matches switcher) vs `rounded-full` (profile style)
- Description and stat block skeletons kept as wireframe decoration
- Extracted `getInitials` / `getPaletteColor` to `utils/avatar-helpers.ts`; `profile-preview.tsx` updated to import from shared util

## Verification Results

- TypeScript: Only pre-existing errors in unrelated `blocks/` files — no new errors
- Tests: 68 passed across all onboard test suites
- Lint: No warnings/errors on new or modified files

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical Functionality] Extracted avatar helpers to shared util**
- **Found during:** Task 2
- **Issue:** Plan specified "prefer extracting to avoid duplication" — `getInitials`/`getPaletteColor` were inline in profile-preview.tsx
- **Fix:** Created `utils/avatar-helpers.ts`, updated profile-preview to import from it, workspace-preview uses the same shared util
- **Files modified:** `utils/avatar-helpers.ts`, `components/previews/profile-preview.tsx`
- **Commit:** ac87e6474

## Key Links

- `WorkspaceStepForm` → `useOnboardStore` via `setLiveData('workspace', ...)` on every watch event
- `WorkspacePreview` → `useOnboardStore` via `liveData['workspace']` selector
- `OnboardStepForm` → `WorkspaceStepForm` via STEP_FORMS lookup for 'workspace' step ID

## Self-Check: PASSED

All created files found on disk. All task commits verified in git log.
