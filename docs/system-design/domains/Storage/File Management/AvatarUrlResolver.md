---
tags:
  - layer/utility
  - component/active
  - architecture/component
Created: 2026-03-16
Domains:
  - "[[Storage]]"
---
# AvatarUrlResolver

Part of [[File Management]]

## Purpose

Stateless utility object that converts stored avatar storage keys into API-relative URLs at read time. Called from `toModel()` methods on [[UserEntity]], [[WorkspaceEntity]], and [[UserWorkspaceMembershipProjection]] so that API responses contain usable endpoint paths (`/api/v1/avatars/workspace/{id}`) rather than raw storage keys.

---

## Dependencies

None — pure functions with no Spring context or injected dependencies.

## Used By

- `UserEntity.toModel()` / `UserEntity.toDisplay()` — Resolves user avatar URLs
- `WorkspaceEntity.toModel()` — Resolves workspace avatar URLs
- `UserWorkspaceMembershipProjection.toWorkspaceMember()` — Resolves both user and workspace avatar URLs in membership projections

---

## Key Logic

Both methods follow the same pattern: if the storage key is `null`, return `null` (no avatar set); otherwise, return the API-relative path containing the entity ID.

### `workspaceAvatarUrl(workspaceId, storageKey): String?`

Returns `/api/v1/avatars/workspace/{workspaceId}` if `storageKey` is non-null, `null` otherwise.

### `userAvatarUrl(userId, storageKey): String?`

Returns `/api/v1/avatars/user/{userId}` if `storageKey` is non-null, `null` otherwise.

---

## Gotchas

- **Storage keys never leak to API consumers** — the resolver deliberately discards the storage key value and returns only the entity ID-based path. The actual storage key is resolved again at download time by [[AvatarService]].
- **Object, not class** — declared as a Kotlin `object` (singleton). No Spring bean registration needed; called via static-style invocation.

---

## Related

- [[AvatarService]] — Resolves the entity ID back to a storage key and streams the file
- [[AvatarController]] — Serves the avatar images at the URLs this resolver generates
- [[File Management]] — Parent subdomain
