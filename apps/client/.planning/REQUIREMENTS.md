# Requirements: Riven Onboarding Flow & Template Installation

**Defined:** 2026-03-08
**Core Value:** New users go from account creation to a fully configured workspace with pre-built entity types in one seamless, visually engaging flow.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Step Framework

- [x] **STEP-01**: User can navigate forward and backward through onboarding steps
- [ ] **STEP-02**: User sees a progress indicator showing current step and total steps
- [x] **STEP-03**: User can skip optional steps (template selection, team invites) via a visible "Skip" affordance
- [ ] **STEP-04**: User sees animated slide/fade transitions between form steps in the left panel
- [ ] **STEP-05**: Each step validates inline with per-field error messages before allowing forward navigation

### User Profile

- [x] **PROF-01**: User can enter their display name (required, min 3 chars)
- [x] **PROF-02**: User can enter their phone number (required, validated as mobile)
- [x] **PROF-03**: User can upload an avatar image (optional, max 2MB, JPEG/PNG/GIF)
- [x] **PROF-04**: Profile preview mockup on the right panel updates live as user types name and uploads avatar

### Workspace Setup

- [x] **WORK-01**: User can enter a workspace name (required, min 3 chars)
- [x] **WORK-02**: User can select a default currency from a dropdown
- [x] **WORK-03**: User can select a workspace plan (FREE, STARTUP, SCALE, ENTERPRISE)
- [x] **WORK-04**: User can upload a workspace avatar (optional, max 5MB, JPEG/PNG/WebP)
- [x] **WORK-05**: Workspace preview mockup on the right panel updates live with workspace name and avatar

### Split-Panel & Camera

- [x] **ANIM-01**: Onboarding uses a split-panel layout with form on the left and animated preview on the right
- [x] **ANIM-02**: Preview panel uses a virtual canvas with all preview sections positioned spatially
- [x] **ANIM-03**: Camera transitions between steps use Framer Motion zoom-out -> pan -> zoom-in animations
- [x] **ANIM-04**: Each step has a contextual preview mockup (profile card, workspace header, entity type grid, team roster)
- [x] **ANIM-05**: Camera coordinates are defined per step in a config, making new steps addable without rewriting animations

### Template Installation

- [ ] **TMPL-01**: User sees a grid of available templates fetched from the backend catalog API
- [ ] **TMPL-02**: User can select one or more templates to install
- [ ] **TMPL-03**: User can preview what entity types each template will create before selecting
- [ ] **TMPL-04**: Selected templates seed workspace entity types when onboarding completes
- [ ] **TMPL-05**: Template step is skippable -- user can set up entity types manually later

### Team Invites

- [ ] **INVT-01**: User can enter teammate email addresses to invite
- [ ] **INVT-02**: User can assign a role (admin, member, viewer) per invite
- [ ] **INVT-03**: Invite step is skippable -- user can invite teammates later
- [ ] **INVT-04**: Invite preview mockup shows team roster as emails are entered

### Submission & Completion

- [ ] **SUBT-01**: All step data is collected into a consolidated payload and submitted at the end
- [ ] **SUBT-02**: User sees loading/progress state during submission
- [ ] **SUBT-03**: Profile update is the last API call (since user.name is the onboarding gate)
- [ ] **SUBT-04**: User sees a success celebration screen with animation after completion
- [ ] **SUBT-05**: User is redirected to their new workspace dashboard after the celebration screen

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Extensibility

- **EXT-01**: Steps defined as a config array -- add/reorder steps without rewriting animation or navigation logic
- **EXT-02**: LocalStorage draft persistence -- page refresh doesn't lose progress (7-day staleness)

### Polish

- **POL-01**: Basic responsive layout -- split panel collapses to stacked on small screens, preview hidden below md
- **POL-02**: ARIA announcements for step changes and form errors
- **POL-03**: Focus management -- auto-focus first field on step change
- **POL-04**: Template personalization -- suggest templates based on workspace type/industry

### Analytics

- **ANLY-01**: Track step completion rates and drop-off points
- **ANLY-02**: Track template selection frequency

## Out of Scope

| Feature | Reason |
|---------|--------|
| Email verification gate before onboarding | Blocks users before they see value; Supabase handles verification separately |
| Mandatory product tour / tooltip walkthrough | Users want to set up, not read; the flow itself teaches |
| Per-step save to backend | Creates orphaned partial states; single submit is simpler |
| Role/function survey for personalization | Not enough template variety to justify the added step |
| Social login emphasis during onboarding | Auth is already handled before onboarding begins |
| Custom template creation by users | Only pre-made backend-served templates for now |
| Mobile-optimized onboarding layout | Desktop-first; basic responsive is v2 |
| Onboarding analytics | Add after flow stabilises |
| Non-linear step navigation (jump to any step) | Adds complexity; Linear/Asana both use strictly linear flows |
| Animated Lottie illustrations | Live preview mockups ARE the visual engagement |
| OTP phone verification during onboarding | Infrastructure exists but not wired for this flow |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| STEP-01 | Phase 1 | Complete |
| STEP-02 | Phase 1 | Pending |
| STEP-03 | Phase 1 | Complete |
| STEP-04 | Phase 1 | Pending |
| STEP-05 | Phase 1 | Pending |
| PROF-01 | Phase 2 | Complete |
| PROF-02 | Phase 2 | Complete |
| PROF-03 | Phase 2 | Complete |
| PROF-04 | Phase 2 | Complete |
| WORK-01 | Phase 2 | Complete |
| WORK-02 | Phase 2 | Complete |
| WORK-03 | Phase 2 | Complete |
| WORK-04 | Phase 2 | Complete |
| WORK-05 | Phase 2 | Complete |
| ANIM-01 | Phase 1 | Complete |
| ANIM-02 | Phase 1 | Complete |
| ANIM-03 | Phase 1 | Complete |
| ANIM-04 | Phase 1 | Complete |
| ANIM-05 | Phase 1 | Complete |
| TMPL-01 | Phase 3 | Pending |
| TMPL-02 | Phase 3 | Pending |
| TMPL-03 | Phase 3 | Pending |
| TMPL-04 | Phase 3 | Pending |
| TMPL-05 | Phase 3 | Pending |
| INVT-01 | Phase 3 | Pending |
| INVT-02 | Phase 3 | Pending |
| INVT-03 | Phase 3 | Pending |
| INVT-04 | Phase 3 | Pending |
| SUBT-01 | Phase 4 | Pending |
| SUBT-02 | Phase 4 | Pending |
| SUBT-03 | Phase 4 | Pending |
| SUBT-04 | Phase 4 | Pending |
| SUBT-05 | Phase 4 | Pending |

**Coverage:**
- v1 requirements: 33 total
- Mapped to phases: 33
- Unmapped: 0

---
*Requirements defined: 2026-03-08*
*Last updated: 2026-03-08 after roadmap creation*
