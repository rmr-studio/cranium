---
tags:
  - layer/service
  - component/active
  - architecture/component
Domains:
  - "[[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]]"
Created: 2026-03-12
Updated: 2026-03-12
---
# OnboardingService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Onboarding/Onboarding]]

## Purpose

Orchestrates the complete user onboarding flow in a single request: creates a workspace, updates the user profile, installs catalog templates, and sends workspace invitations. Coordinates across six services to deliver a two-phase operation where the core setup is atomic and the optional extras are best-effort.

---

## Responsibilities

- Validate onboarding eligibility (user has not already completed onboarding)
- Create workspace with OWNER membership via [[riven/docs/system-design/domains/Workspaces & Users/Workspace Management/WorkspaceService]]
- Update user profile (name, phone, default workspace, onboarding timestamp) via [[riven/docs/system-design/domains/Workspaces & Users/User Management/UserService]]
- Install catalog templates and bundles via [[riven/docs/system-design/domains/Catalog/Template Installation/TemplateInstallationService]] with per-item error isolation
- Send workspace invitations via [[riven/docs/system-design/domains/Workspaces & Users/Team Management/WorkspaceInviteService]] with per-invite error isolation and validation (no self-invite, no OWNER role)
- Activity logging for the onboarding event

---

## Dependencies

- [[riven/docs/system-design/domains/Workspaces & Users/Workspace Management/WorkspaceService]] -- workspace creation (delegates OWNER membership and default workspace assignment)
- [[riven/docs/system-design/domains/Workspaces & Users/User Management/UserService]] -- user profile retrieval and update
- [[riven/docs/system-design/domains/Catalog/Template Installation/TemplateInstallationService]] -- catalog template and bundle installation into the new workspace
- [[riven/docs/system-design/domains/Workspaces & Users/Team Management/WorkspaceInviteService]] -- workspace invitation creation
- [[riven/docs/system-design/domains/Workspaces & Users/Auth & Authorization/AuthTokenService]] -- current user ID and email from JWT
- `ActivityService` -- audit logging for the onboarding operation
- `TransactionTemplate` -- programmatic transaction boundary for Phase 1

## Used By

- `OnboardingController` -- REST API layer

---

## Key Logic

**Two-phase execution model:**

The onboarding flow is split into two phases with different failure semantics:

- **Phase 1 (atomic):** Workspace creation and user profile update run inside a single programmatic transaction via `TransactionTemplate`. If either fails, the entire phase rolls back. This guarantees the user never ends up with a workspace but no profile update, or vice versa.
- **Phase 2 (best-effort):** Template installation and invitation sending run after the Phase 1 transaction commits. Each item is individually wrapped in try/catch so a single template or invite failure does not block the others. Results are collected and returned to the client with per-item success/failure status.

**completeOnboarding:**
1. Retrieves `userId` from JWT via [[riven/docs/system-design/domains/Workspaces & Users/Auth & Authorization/AuthTokenService]]
2. Validates eligibility -- checks `user.onboardingCompletedAt` is null, throws `ConflictException` if already completed
3. Phase 1 transaction: creates workspace (with optional avatar) via [[riven/docs/system-design/domains/Workspaces & Users/Workspace Management/WorkspaceService]], then updates user profile (name, phone, default workspace, `onboardingCompletedAt` timestamp) via [[riven/docs/system-design/domains/Workspaces & Users/User Management/UserService]], with optional profile avatar upload
4. Phase 2 best-effort: iterates `templateKeys` and `bundleKeys`, installing each via [[riven/docs/system-design/domains/Catalog/Template Installation/TemplateInstallationService]] with individual error isolation; iterates `invites`, sending each via [[riven/docs/system-design/domains/Workspaces & Users/Team Management/WorkspaceInviteService]] with validation and error isolation
5. Returns `CompleteOnboardingResponse` containing the created workspace, updated user, and lists of `TemplateInstallResult` and `InviteResult`

**Invite validation (sendInviteSafely):**
- Rejects self-invitations (compares invite email against current user's email, case-insensitive)
- Rejects OWNER role invitations
- Both return `InviteResult` with `success = false` and descriptive error rather than throwing

**Activity logging:**
- Logs a single `ONBOARDING / CREATE` activity after user profile update, capturing workspace ID, workspace name, and profile name in the details map

---

## Public Methods

### `completeOnboarding(request, profileAvatar?, workspaceAvatar?): CompleteOnboardingResponse`

Executes the full onboarding flow. Phase 1 (workspace + profile) is atomic; Phase 2 (templates + invites) is best-effort. Returns composite response with per-item results for Phase 2 operations.

---

## Gotchas

> [!warning] No @PreAuthorize annotation
> This service intentionally has no `@PreAuthorize` workspace security check. At the point of onboarding, the workspace does not yet exist in the user's JWT claims. Authentication is still enforced (the user must have a valid JWT), but workspace-scoped authorization is not applicable.

> [!warning] Phase 2 runs outside the transaction
> Template installations and invitations execute after the Phase 1 transaction commits. If Phase 2 items fail, the workspace and profile update are already persisted. The client must inspect `templateResults` and `inviteResults` to detect partial failures.

> [!warning] One-time operation enforced by ConflictException
> `validateOnboardingEligibility` checks `onboardingCompletedAt` on the user entity. Once set during Phase 1, any subsequent call throws `ConflictException`. There is no mechanism to reset this flag or re-run onboarding.

> [!warning] Programmatic transaction instead of @Transactional
> Phase 1 uses `TransactionTemplate.execute` rather than `@Transactional` because the method needs to perform non-transactional work (Phase 2) after commit. This is intentional -- `@Transactional` would wrap the entire method.

---

## Related

- [[riven/docs/system-design/domains/Workspaces & Users/Workspace Management/WorkspaceService]] -- Workspace creation and membership
- [[riven/docs/system-design/domains/Workspaces & Users/User Management/UserService]] -- Profile update and onboarding timestamp
- [[riven/docs/system-design/domains/Catalog/Template Installation/TemplateInstallationService]] -- Catalog template/bundle installation
- [[riven/docs/system-design/domains/Workspaces & Users/Team Management/WorkspaceInviteService]] -- Invitation creation
- [[riven/docs/system-design/domains/Workspaces & Users/Auth & Authorization/AuthTokenService]] -- JWT user identity
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Onboarding/Onboarding]] -- Parent subdomain

---

## Changelog

| Date | Change | Reason |
|------|--------|--------|
| 2026-03-12 | Initial documentation | Onboarding endpoint implementation |
