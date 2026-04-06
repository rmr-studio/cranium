---
tags:
  - component/active
  - layer/controller
  - architecture/component
  - domain/identity-resolution
Created: 2026-03-19
Domains:
  - "[[Identity Resolution]]"
Sub-Domains:
  - "[[Clusters]]"
---

# IdentityController

## Purpose

REST API controller for identity resolution — exposes endpoints for suggestions, clusters, and match counts.

---

## Responsibilities

- Routing HTTP requests to the appropriate identity services
- Request validation via `@Valid` on request bodies
- Pure delegation — no business logic in the controller

## Dependencies

- [[IdentityReadService]] — suggestion listing, suggestion detail, match counts
- [[IdentityConfirmationService]] — confirm and reject suggestions
- [[IdentityClusterService]] — cluster listing, cluster detail, add members, rename

## Used By

- External API consumers via REST

---

## Key Logic

### Base Path

`/api/v1/identity/{workspaceId}`

### Endpoints

| Method | Path | Description | Service |
|---|---|---|---|
| GET | /suggestions | List suggestions | IdentityReadService |
| GET | /suggestions/{suggestionId} | Get suggestion detail | IdentityReadService |
| POST | /suggestions/{suggestionId}/confirm | Confirm a suggestion | IdentityConfirmationService |
| POST | /suggestions/{suggestionId}/reject | Reject a suggestion | IdentityConfirmationService |
| GET | /clusters | List clusters | IdentityClusterService |
| GET | /clusters/{clusterId} | Get cluster detail | IdentityClusterService |
| POST | /clusters/{clusterId}/members | Add entity to cluster | IdentityClusterService |
| PATCH | /clusters/{clusterId} | Rename cluster | IdentityClusterService |
| GET | /entities/{entityId}/matches | Pending match count | IdentityReadService |

### OpenAPI

`@Tag(name = "identity")` for API grouping. `@Valid` applied on `AddClusterMemberRequest` and `RenameClusterRequest` request bodies.

---

## Gotchas

- **Thin controller** — all authorization (`@PreAuthorize`) is on the service methods, not the controller. The controller purely delegates.

---

## Related

- [[Clusters]]
- [[Matching Pipeline]]
- [[IdentityReadService]]
- [[IdentityConfirmationService]]
- [[IdentityClusterService]]
