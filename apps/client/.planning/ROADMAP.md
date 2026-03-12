# Roadmap: Riven Onboarding Flow & Template Installation

## Overview

This roadmap delivers a unified multi-step onboarding experience that takes new users from account creation to a fully configured workspace. The work progresses from structural skeleton (step framework and camera system) through the form steps themselves (required first, optional second) to final submission and completion. Each phase delivers a verifiable vertical slice of the onboarding flow.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Foundation & Camera System** - Step framework, split-panel layout, and camera-style viewport transitions
- [x] **Phase 2: Required Steps** - User profile and workspace setup forms with live preview mockups (completed 2026-03-12)
- [ ] **Phase 3: Optional Steps** - Template selection and team invite steps with previews
- [ ] **Phase 4: Submission & Completion** - Consolidated payload, sequential API calls, celebration screen, and redirect

## Phase Details

### Phase 1: Foundation & Camera System
**Goal**: Users see a working multi-step onboarding shell with split-panel layout and animated camera transitions between steps
**Depends on**: Nothing (first phase)
**Requirements**: STEP-01, STEP-02, STEP-03, STEP-04, STEP-05, ANIM-01, ANIM-02, ANIM-03, ANIM-04, ANIM-05
**Success Criteria** (what must be TRUE):
  1. User can navigate forward and backward through placeholder steps and sees a progress indicator
  2. User sees a split-panel layout with a form area on the left and an animated preview area on the right
  3. Navigating between steps triggers smooth camera-style zoom/pan transitions on the preview panel
  4. Optional steps show a visible "Skip" affordance and required steps validate before allowing forward navigation
  5. Adding a new step requires only a config entry and components, not animation/navigation rewrites
**Plans:** 3/3 plans executed

Plans:
- [x] 01-01: Onboarding store, step config, and step navigation framework
- [x] 01-02: Split-panel layout and virtual canvas camera system
- [x] 01-03: Step transitions, validation gating, and progress indicator

### Phase 2: Required Steps
**Goal**: Users can fill out their profile and workspace details with real-time preview feedback
**Depends on**: Phase 1
**Requirements**: PROF-01, PROF-02, PROF-03, PROF-04, WORK-01, WORK-02, WORK-03, WORK-04, WORK-05
**Success Criteria** (what must be TRUE):
  1. User can enter name, phone, and avatar on the profile step with inline validation errors
  2. User can enter workspace name, currency, plan, and avatar on the workspace step with inline validation
  3. Profile preview mockup updates live as the user types their name and uploads an avatar
  4. Workspace preview mockup updates live with workspace name and avatar
**Plans:** 2/2 plans complete

Plans:
- [ ] 02-01-PLAN.md — Store extension, nav validation wiring, profile step form and live preview
- [ ] 02-02-PLAN.md — Workspace step form, plan selector, and live preview

### Phase 3: Optional Steps
**Goal**: Users can optionally select templates and invite teammates before completing onboarding
**Depends on**: Phase 2
**Requirements**: TMPL-01, TMPL-02, TMPL-03, TMPL-04, TMPL-05, INVT-01, INVT-02, INVT-03, INVT-04
**Success Criteria** (what must be TRUE):
  1. User sees a grid of templates fetched from the backend catalog and can select one or more
  2. User can preview what entity types each template will create before selecting
  3. User can skip the template step entirely and set up entity types later
  4. User can enter teammate emails with assigned roles and sees a team roster preview
  5. User can skip the invite step and invite teammates later
**Plans:** TBD

Plans:
- [ ] 03-01: Template catalog integration, selection UI, and preview mockup
- [ ] 03-02: Team invite form, role assignment, and roster preview mockup

### Phase 4: Submission & Completion
**Goal**: Users complete onboarding with a single submission that creates their workspace, installs templates, sends invites, and updates their profile
**Depends on**: Phase 3
**Requirements**: SUBT-01, SUBT-02, SUBT-03, SUBT-04, SUBT-05
**Success Criteria** (what must be TRUE):
  1. All step data is submitted as a consolidated payload with loading/progress indication
  2. Profile update happens last so the onboarding gate does not open until everything else succeeds
  3. User sees a celebration screen with animation after successful completion
  4. User is automatically redirected to their new workspace dashboard after the celebration
**Plans:** TBD

Plans:
- [ ] 04-01: Consolidated payload assembly, sequential API submission, and error handling
- [ ] 04-02: Success celebration screen and workspace redirect

## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2 -> 3 -> 4

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation & Camera System | 3/3 | Complete | 2026-03-12 |
| 2. Required Steps | 2/2 | Complete   | 2026-03-12 |
| 3. Optional Steps | 0/2 | Not started | - |
| 4. Submission & Completion | 0/2 | Not started | - |
