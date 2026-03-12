---
tags:
  - layer/service
  - component/active
  - architecture/component
Domains:
  - "[[Workspaces & Users]]"
Created: 2026-02-08
Updated: 2026-03-12
---
# WorkspaceInviteService

Part of [[Team Management]]

## Purpose

Manages the workspace invitation workflow — creating invitations, handling acceptance/rejection, listing invitations, and revoking pending invitations.

---

## Responsibilities

- Create workspace invitations with role assignment (never OWNER)
- Validate no duplicate pending invitations for same email
- Validate invitee is not already a workspace member (ConflictException)
- Handle invitation acceptance (update status + add member via WorkspaceService)
- Handle invitation rejection (update status to DECLINED)
- List invitations by workspace or by user email
- Revoke pending invitations (ADMIN+ only)
- Activity logging for invitation creation

---

## Dependencies

- [[WorkspaceService]] — addMemberToWorkspace on acceptance
- `WorkspaceInviteRepository` — invitation data access
- `WorkspaceMemberRepository` — check existing membership
- [[AuthTokenService]] — get current user ID and email
- `ActivityService` — audit logging

## Used By

- `InviteController` — REST API layer
- [[OnboardingService]] — sends workspace invitations during onboarding flow

---

## Key Logic

**createWorkspaceInvitation:**
- Requires workspace access + ADMIN+ role via `@PreAuthorize`
- Retrieves `userId` via `authTokenService.getUserId()` as `val` assignment
- Delegates to `createWorkspaceInvitationInternal(workspaceId, email, role, userId)`

**createWorkspaceInvitationInternal:**
- Internal method without `@PreAuthorize` — used by [[OnboardingService]] when workspace role is not yet in the JWT
- Accepts explicit `invitedBy` UUID parameter instead of extracting from auth token
- Validates role != OWNER (throws IllegalArgumentException — directs to "transfer ownership methods")
- Checks no existing member with target email via WorkspaceMemberRepository join to user table (throws ConflictException if already member)
- Checks no pending invite for email (throws IllegalArgumentException if duplicate pending)
- Creates WorkspaceInviteEntity with status=PENDING, invitedBy=invitedBy parameter
- Logs WORKSPACE_MEMBER_INVITE CREATE activity
- Returns WorkspaceInvite model

**handleInvitationResponse:**
- Token-based lookup via WorkspaceInviteRepository.findByToken
- Validates authenticated user email matches invitation email (throws AccessDeniedException if mismatch)
- Validates invitation status is PENDING (throws IllegalArgumentException if not)
- On accept: updates status to ACCEPTED, calls workspaceService.addMemberToWorkspace with invitation role + authenticated user ID, TODO send acceptance email
- On reject: updates status to DECLINED, TODO send rejection email
- @Transactional — status update + member addition in single transaction

**revokeWorkspaceInvite:**
- Requires ADMIN+ role via @PreAuthorize
- Validates invitation is PENDING (throws IllegalArgumentException if not)
- Deletes invitation record from repository

**getUserInvites:**
- Extracts authenticated user's email via AuthTokenService
- Returns all invitations (any status) for that email

**getWorkspaceInvites:**
- Requires workspace access via @PreAuthorize
- Returns all invitations (any status) for specified workspace

---

## Public Methods

### `createWorkspaceInvitation(workspaceId, email, role): WorkspaceInvite`

Creates pending invitation. ADMIN+ required via `@PreAuthorize`. Delegates to `createWorkspaceInvitationInternal`.

### `createWorkspaceInvitationInternal(workspaceId, email, role, invitedBy): WorkspaceInvite`

Creates pending invitation without workspace access check. Validates role != OWNER, no existing member, no duplicate pending invite. Used by [[OnboardingService]] when workspace role is not yet in the JWT.

### `handleInvitationResponse(token, accepted: Boolean)`

Handles user response to invitation. Validates email match and PENDING status. On accept: adds member. On reject: updates status.

### `getUserInvites(): List<WorkspaceInvite>`

Returns all invitations addressed to authenticated user's email.

### `getWorkspaceInvites(workspaceId): List<WorkspaceInvite>`

Returns all invitations for specified workspace. Workspace access required.

### `revokeWorkspaceInvite(workspaceId, id)`

Revokes pending invitation. ADMIN+ required. Only works on PENDING invitations.

---

## Gotchas

- **Email sending TODO:** Three TODO comments for email notifications (invitation creation line 84, acceptance line 145, rejection line 155) — invitations are created but no notification is sent
- **Token-based acceptance:** handleInvitationResponse uses token parameter to locate invitation, but token must be communicated out-of-band until email sending is implemented
- **Email-to-user matching:** Invitation is tied to email string, not user ID — user accepting must have matching email in JWT claims
- **Duplicate prevention scope:** Duplicate check only prevents multiple PENDING invites for same email — ACCEPTED or DECLINED invitations don't block new invites
- **ConflictException vs IllegalArgumentException:** Already-a-member throws ConflictException, duplicate-pending-invite throws IllegalArgumentException — inconsistent exception types for similar validation failures

---

## Related

- [[WorkspaceService]] — Workspace and member management
- [[AuthTokenService]] — JWT claim extraction
- [[Team Management]] — Parent subdomain
