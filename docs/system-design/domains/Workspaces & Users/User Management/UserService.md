---
tags:
  - layer/service
  - component/active
  - architecture/component
Domains:
  - "[[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]]"
Created: 2026-02-08
Updated: 2026-03-12
---
# UserService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/User Management/User Management]]

## Purpose

Manages user profile CRUD with session-aware retrieval. Provides optimized workspace membership loading using JOIN queries to avoid N+1 problems.

---

## Responsibilities

- Session-based user retrieval (getUserFromSession)
- User retrieval with workspace memberships in single optimized query (getUserWithWorkspacesFromSession, getUserWithWorkspacesById)
- User profile updates with session ID validation (self-only)
- User deletion with cascading membership cleanup
- Default workspace assignment during profile updates
- Avatar upload via StorageService during profile updates

---

## Dependencies

- `UserRepository` — data access with custom findWorkspaceMembershipsByUserId query
- `WorkspaceRepository` — lookup for default workspace assignment
- [[riven/docs/system-design/domains/Workspaces & Users/Auth & Authorization/AuthTokenService]] — get current user ID from JWT for session validation
- [[riven/docs/system-design/domains/Storage/File Management/StorageService]] — avatar file upload via uploadUserFile()

## Used By

- `UserController` — REST API layer
- [[riven/docs/system-design/domains/Workspaces & Users/Workspace Management/WorkspaceService]] — calls getUserWithWorkspacesFromSession during workspace creation for default workspace logic
- [[riven/docs/system-design/domains/Workspaces & Users/Onboarding/OnboardingService]] — marks onboarding completion via updateUserDetails

---

## Key Logic

**getUserWithWorkspacesFromSession:**
- Extracts user ID from JWT via AuthTokenService
- Fetches UserEntity by ID
- Single optimized query via repository.findWorkspaceMembershipsByUserId — native query with JOIN to avoid N+1
- Maps projection results to WorkspaceMember models via toWorkspaceMember extension
- Returns User model with memberships populated
- Logs membership count at INFO level

**getUserWithWorkspacesById:**
- Same optimization strategy as getUserWithWorkspacesFromSession but accepts explicit userId parameter
- Used when caller needs another user's workspace memberships (not session user)

**updateUserDetails:**
- Retrieves session user ID via `val sessionUserId = authTokenService.getUserId()` (val assignment)
- Validates session user ID matches target user.id (throws AccessDeniedException if mismatch) — self-only enforcement
- Updates name, email, phone, avatarUrl, and `onboardingCompletedAt` fields (onboardingCompletedAt is only set if the incoming value is non-null, otherwise the existing value is preserved)
- Default workspace update: if user.defaultWorkspace.id provided, looks up WorkspaceEntity via WorkspaceRepository
- Saves updated UserEntity
- If optional `avatar: MultipartFile?` is provided: calls `storageService.uploadUserFile(sessionUserId, StorageDomain.AVATAR, file)` to upload, then sets `entity.avatarUrl` to returned storage key and saves again
- Returns updated User model
- Info logging of update

**deleteUserProfile:**
- @Transactional annotation ensures atomic deletion
- Validates user exists via findOrThrow before deletion
- Deletes user record by ID — cascade to WorkspaceMemberEntity handled by database FK constraints
- Info logging of deletion

---

## Public Methods

### `getUserFromSession(): UserEntity`

Retrieves UserEntity for authenticated session user. No workspace memberships loaded.

### `getUserWithWorkspacesFromSession(): User`

Retrieves session user with all workspace memberships in single query. Optimized for profile pages.

### `getUserWithWorkspacesById(userId): User`

Retrieves specified user with all workspace memberships in single query. Optimized batch loading.

### `getUserById(id): UserEntity`

Retrieves UserEntity by ID. No workspace memberships loaded.

### `updateUserDetails(user, avatar?): User`

Updates session user's profile. Validates session match. Updates name, email, phone, avatarUrl, onboardingCompletedAt, defaultWorkspace. If optional avatar MultipartFile is provided, uploads via StorageService and sets avatarUrl to the returned storage key.

### `deleteUserProfile(userId)`

Transactional deletion of user and associated membership records.

---

## Gotchas

- **Double self-only enforcement:** updateUserDetails checks session ID match at service level, AND UserController likely has similar check at controller level — belt and suspenders approach
- **N+1 optimization:** getUserWithWorkspaces* methods use custom repository query with JOIN to fetch all memberships in one query — critical for performance when user has many workspaces
- **Projection mapping:** findWorkspaceMembershipsByUserId returns custom projection interface, requires toWorkspaceMember extension function to convert to model — indirection in mapping layer
- **Default workspace FK:** updateUserDetails looks up workspace via WorkspaceRepository to ensure FK integrity — will throw if workspace ID is invalid
- **Cascade deletion:** deleteUserProfile relies on database FK cascade to remove WorkspaceMemberEntity records — no explicit service-level cascade logic

---

## Related

- [[riven/docs/system-design/domains/Workspaces & Users/Auth & Authorization/AuthTokenService]] — JWT claim extraction
- [[riven/docs/system-design/domains/Workspaces & Users/Workspace Management/WorkspaceService]] — Workspace management
- [[riven/docs/system-design/domains/Storage/File Management/StorageService]] — Avatar upload
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/User Management/User Management]] — Parent subdomain
