---
Created: 2026-02-08
Domains:
  - "[[Workspaces & Users]]"
tags:
  - architecture/subdomain
  - domain/workspace
  - domain/team
---
# Subdomain: Team Management

## Overview

Manages the invitation workflow for onboarding new members to workspaces. This subdomain handles the complete invitation lifecycle — creation (with unique token generation), acceptance/rejection by invitees, revocation by workspace admins, and conflict validation (preventing duplicate invitations, existing member checks).

WorkspaceInviteService enforces invitation business rules and updates invitation status (PENDING → ACCEPTED/DECLINED). InviteController exposes REST endpoints for all invitation operations.

> [!info] Future Expansion
> Team Management is forward-looking — currently implements invitations and membership onboarding. Future expansion will encompass broader team features (team groups, permissions delegation, cross-workspace teams, etc.).

## Components

| Component | Purpose | Type |
| --------- | ------- | ---- |
| [[WorkspaceInviteService]] | Invitation CRUD, acceptance/rejection flow, conflict validation | Service |
| InviteController | REST endpoints for invitation operations | Controller |
| WorkspaceInviteRepository | Invitation data access | Repository |

**WorkspaceInviteStatus enum** (documented inline): PENDING (invitation sent), ACCEPTED (invitation accepted), DECLINED (invitation rejected). Tracked in WorkspaceInvite.inviteStatus field.

## Endpoints

### InviteController

| Method | Path | Purpose | Auth |
| ------ | ---- | ------- | ---- |
| POST | /api/v1/workspace/invite/workspace/{workspaceId}/email/{email}/role/{role} | Create invitation for email with specified role | @workspaceSecurity.hasWorkspaceRoleOrHigher(#workspaceId, T(riven.core.enums.workspace.WorkspaceRoles).ADMIN) |
| POST | /api/v1/workspace/invite/accept/{inviteToken} | Accept invitation (requires email match) | authenticated (email must match invitation) |
| POST | /api/v1/workspace/invite/reject/{inviteToken} | Reject invitation (requires email match) | authenticated (email must match invitation) |
| GET | /api/v1/workspace/invite/workspace/{workspaceId} | Get all invitations for workspace | @workspaceSecurity.hasWorkspace(#workspaceId) |
| GET | /api/v1/workspace/invite/user | Get current user's pending invitations | authenticated |
| DELETE | /api/v1/workspace/invite/workspace/{workspaceId}/invitation/{id} | Revoke invitation | @workspaceSecurity.hasWorkspaceRoleOrHigher(#workspaceId, T(riven.core.enums.workspace.WorkspaceRoles).ADMIN) |

---

## Technical Debt

| Issue | Impact | Effort |
| ----- | ------ | ------ |
| None yet | — | — |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-02-08 | Subdomain overview created | [[03-01-PLAN]] |
| 2026-03-12 | Added `createWorkspaceInvitationInternal` method to [[WorkspaceInviteService]] — bypasses `@PreAuthorize` for system-initiated invitations during onboarding | [[2. Areas/2.1 Startup & Business/Riven/2. System Design/domains/Workspaces & Users/Onboarding 1/Onboarding]] |
