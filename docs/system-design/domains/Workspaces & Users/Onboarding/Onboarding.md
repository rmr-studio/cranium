---
Created: 2026-03-12
Domains:
  - "[[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]]"
tags:
  - architecture/subdomain
  - domain/workspace
---
# Subdomain: Onboarding

## Overview

Handles the single-request onboarding flow that transitions a newly authenticated user into a fully provisioned workspace. The flow orchestrates workspace creation, user profile update, catalog template/bundle installation, and team invitation sending -- all from one API call.

The subdomain introduces a two-phase execution model: Phase 1 (workspace + profile) runs atomically inside a programmatic transaction, while Phase 2 (templates + invites) runs best-effort after commit, collecting per-item success/failure results for the client.

OnboardingService is the sole orchestrator. It has no repository of its own and no persistent domain entities -- it coordinates across existing services from [[riven/docs/system-design/domains/Workspaces & Users/Workspace Management/Workspace Management]], [[riven/docs/system-design/domains/Workspaces & Users/User Management/User Management]], [[riven/docs/system-design/domains/Catalog/Catalog]], and [[riven/docs/system-design/domains/Workspaces & Users/Team Management/Team Management]].

## Components

| Component | Purpose | Type |
| --------- | ------- | ---- |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Onboarding/OnboardingService]] | Orchestrates two-phase onboarding flow across workspace, user, catalog, and invite services | Service |
| OnboardingController | REST endpoint for the onboarding request | Controller |

## Endpoints

### OnboardingController

| Method | Path | Purpose | Auth |
| ------ | ---- | ------- | ---- |
| POST | /api/v1/onboarding/complete | Complete user onboarding (workspace + profile + templates + invites) | authenticated (no workspace security) |

## Key Patterns

### Two-Phase Execution

The onboarding flow splits work into two phases with different failure semantics:

- **Phase 1 (atomic):** Workspace creation and user profile update execute inside a single `TransactionTemplate` boundary. Failure in either operation rolls back both. Uses programmatic transactions rather than `@Transactional` so that Phase 2 can run outside the transaction scope.
- **Phase 2 (best-effort):** Template/bundle installation and invitation sending run after the Phase 1 commit. Each item is individually try/caught so one failure does not block the others. Results are returned to the client as `TemplateInstallResult` and `InviteResult` lists with per-item success/error fields.

### `*Internal` Method Pattern

OnboardingService calls `installTemplateInternal`, `installBundleInternal`, and `createWorkspaceInvitationInternal` on downstream services instead of their standard public counterparts. These `*Internal` variants skip the `@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")` check. This is necessary because during onboarding, the newly created workspace does not yet exist in the user's JWT claims -- the token was issued before the workspace was created. Authentication is still enforced (valid JWT required), but workspace-scoped authorization cannot apply until the user's next token refresh.

### One-Time Enforcement

Onboarding eligibility is gated by `user.onboardingCompletedAt`. The timestamp is set during Phase 1, and any subsequent call throws `ConflictException`. There is no reset mechanism.

## Cross-Domain Dependencies

| Target Domain | Service Used | Mechanism | Purpose |
| ------------- | ------------ | --------- | ------- |
| [[riven/docs/system-design/domains/Workspaces & Users/Workspace Management/Workspace Management]] | [[riven/docs/system-design/domains/Workspaces & Users/Workspace Management/WorkspaceService]] | Direct injection | Create workspace with OWNER membership |
| [[riven/docs/system-design/domains/Workspaces & Users/User Management/User Management]] | [[riven/docs/system-design/domains/Workspaces & Users/User Management/UserService]] | Direct injection | Profile update, default workspace assignment, onboarding timestamp |
| [[riven/docs/system-design/domains/Catalog/Catalog]] | [[riven/docs/system-design/domains/Catalog/Template Installation/TemplateInstallationService]] | Direct injection (`*Internal` methods) | Install catalog templates and bundles into the new workspace |
| [[riven/docs/system-design/domains/Workspaces & Users/Team Management/Team Management]] | [[riven/docs/system-design/domains/Workspaces & Users/Team Management/WorkspaceInviteService]] | Direct injection (`*Internal` method) | Send workspace invitations |
| [[riven/docs/system-design/flows/Auth & Authorization]] | [[riven/docs/system-design/domains/Workspaces & Users/Auth & Authorization/AuthTokenService]] | Direct injection | Retrieve user ID and email from JWT |

---

## Technical Debt

| Issue | Impact | Effort |
| ----- | ------ | ------ |
| None yet | --- | --- |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-03-12 | Subdomain overview created | Onboarding endpoint implementation |
