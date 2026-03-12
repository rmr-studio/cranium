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
# WorkspaceService

Part of [[Workspace Management]]

## Purpose

Manages workspace lifecycle (CRUD) and workspace membership. On workspace creation, automatically adds the creator as OWNER and sets it as default workspace for first-time users.

---

## Responsibilities

- Workspace CRUD (create, read, update, soft-delete)
- Automatic OWNER membership creation on workspace creation
- Default workspace assignment for first workspace creation
- Member management (add, remove, role update) with ownership protection
- Activity logging for all workspace and member operations
- Currency validation for workspace defaultCurrency
- Workspace avatar upload via StorageService during workspace creation

---

## Dependencies

- `WorkspaceRepository` — workspace data persistence
- `WorkspaceMemberRepository` — membership data persistence
- [[UserService]] — retrieve user for default workspace assignment
- [[AuthTokenService]] — get current user ID from JWT
- `ActivityService` — audit logging
- [[StorageService]] — avatar file upload for workspace creation/update
- [[WorkspaceSecurity]] — used indirectly via @PreAuthorize on methods

## Used By

- `WorkspaceController` — REST API layer
- [[WorkspaceInviteService]] — calls addMemberToWorkspace on invitation acceptance
- [[OnboardingService]] — creates workspace during onboarding flow

---

## Key Logic

**saveWorkspace:**
- Creates workspace + OWNER member in single transaction
- If request.id is null, creates new; otherwise updates existing
- Refactored into extracted private methods for readability: `createOrUpdateWorkspaceEntity`, `createOwnerMember`, `uploadWorkspaceAvatar`, `logWorkspaceActivity`, `publishWorkspaceAnalytics`, `setDefaultWorkspaceIfNeeded`
- On creation: adds creator as OWNER via `createOwnerMember`
- Default workspace logic: `setDefaultWorkspaceIfNeeded` checks if user has no memberships OR request.isDefault flag set, assigns workspace as user's defaultWorkspace via UserService
- Currency validation: uses Java Currency.getInstance, throws IllegalArgumentException for invalid codes
- Avatar upload: if avatar file provided, `uploadWorkspaceAvatar` calls `storageService.uploadFileInternal(workspaceId, StorageDomain.AVATAR, file)` (uses internal method to bypass @PreAuthorize, since workspace role may not yet be in JWT during onboarding). Sets `entity.avatarUrl = uploadResponse.file.storageKey` and saves again
- Analytics publishing: `publishWorkspaceAnalytics` publishes `WorkspaceCreatedEvent` or `WorkspaceUpdatedEvent` via ApplicationEventPublisher

**deleteWorkspace:**
- Soft-delete (sets deleted=true, deletedAt=now)
- Requires OWNER role via @PreAuthorize
- Logs DELETE activity
- Note: Comment mentions "Delete all members" but no cascade implementation present

**removeMemberFromWorkspace:**
- Cannot remove OWNER — throws IllegalArgumentException
- Validates member exists for workspaceId + userId combination
- Deletes membership record
- Activity logged with WORKSPACE_MEMBER DELETE operation

**updateMemberRole:**
- Cannot assign or remove OWNER role — throws IllegalArgumentException ("Transfer of ownership must be done through a dedicated transfer ownership method")
- Updates member role in database
- Activity logged with WORKSPACE_MEMBER UPDATE operation

**addMemberToWorkspace:**
- Package-internal visibility (no explicit modifier, called by WorkspaceInviteService)
- Creates WorkspaceMemberEntity with provided workspaceId, userId, role
- Saves to repository and returns WorkspaceMember model
- Info logging of member addition

---

## Public Methods

### `getWorkspaceById(workspaceId, includeMetadata): Workspace`

Retrieves single workspace by ID. `includeMetadata` flag controls whether to include audit info and team members.

### `getEntityById(workspaceId): WorkspaceEntity`

Retrieves WorkspaceEntity for internal service use. Enforces workspace access via @PreAuthorize.

### `saveWorkspace(request, avatar): Workspace`

Creates or updates workspace. Handles OWNER membership creation on new workspace. Manages default workspace assignment for first-time users.

### `deleteWorkspace(workspaceId)`

Soft-deletes workspace. OWNER role required.

### `addMemberToWorkspace(workspaceId, userId, role): WorkspaceMember`

Internal method called by WorkspaceInviteService. Adds user as workspace member with specified role.

### `removeMemberFromWorkspace(workspaceId, memberId)`

Removes member from workspace. Cannot remove OWNER.

### `updateMemberRole(workspaceId, memberId, role): WorkspaceMember`

Updates member's role. Cannot assign or remove OWNER role.

---

## Gotchas

- **Ownership transfer missing:** No dedicated method for ownership transfer — updateMemberRole explicitly blocks OWNER role changes with error message directing to "dedicated transfer ownership method"
- **Soft delete incomplete:** deleteWorkspace does soft-delete but comment on line 160 says "Delete all members" with no implementation — member cascade unclear
- **Transactional boundaries:** saveWorkspace is @Transactional, ensuring workspace + OWNER member creation is atomic
- **Default workspace race condition:** saveWorkspace reads user memberships after workspace save to check if first workspace — relies on transaction isolation

---

## Related

- [[WorkspaceSecurity]] — Authorization component
- [[WorkspaceInviteService]] — Invitation workflow
- [[StorageService]] — File storage for avatars
- [[UserService]] — User profile management
