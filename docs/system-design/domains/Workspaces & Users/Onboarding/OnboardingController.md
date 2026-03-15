---
tags:
  - layer/controller
  - component/active
  - architecture/component
Created: 2026-03-12
Updated: 2026-03-12
Domains:
  - "[[Workspaces & Users]]"
---
# OnboardingController

Part of [[2. Areas/2.1 Startup & Business/Riven/2. System Design/domains/Workspaces & Users/Onboarding/Onboarding]]

## Purpose

Thin REST controller exposing a single onboarding endpoint under `/api/v1/onboarding`. Delegates all business logic to [[OnboardingService]] and returns `ResponseEntity` with appropriate HTTP status codes.

---

## Dependencies

- [[OnboardingService]] -- all onboarding orchestration logic

## Used By

- REST API clients -- frontend onboarding flow

---

## Endpoints

| Method | Path | Service Method | Status | Description |
|---|---|---|---|---|
| POST | `/complete` | `completeOnboarding` | 201 | Complete user onboarding: create workspace, update profile, install templates, send invitations (multipart/form-data) |

---

## Gotchas

- **Multipart request structure** -- the endpoint consumes `multipart/form-data` with three parts: `request` (JSON, validated with `@Valid`), `profileAvatar` (optional file), and `workspaceAvatar` (optional file). The `request` part is deserialized as `CompleteOnboardingRequest` via `@RequestPart`, not `@RequestBody`.
- **No @PreAuthorize** -- there is no workspace-scoped authorization on this controller or its service. The workspace does not exist yet at the time of the request. Authentication is still enforced via the global security filter.
- **Always returns 201** -- the controller always returns HTTP 201 on success, even when Phase 2 items (templates, invites) partially fail. Clients must inspect `templateResults` and `inviteResults` in the response body to detect partial failures.

---

## Related

- [[OnboardingService]] -- delegated business logic
- [[2. Areas/2.1 Startup & Business/Riven/2. System Design/domains/Workspaces & Users/Onboarding/Onboarding]] -- parent subdomain

---

## Changelog

| Date | Change | Reason |
|------|--------|--------|
| 2026-03-12 | Initial documentation | Onboarding endpoint implementation |
