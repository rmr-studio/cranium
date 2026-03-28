---
tags:
  - layer/controller
  - component/deprecated
  - architecture/component
Created: 2026-03-09
Domains:
  - "[[Catalog]]"
---
# TemplateController

> [!warning] Removed in Lifecycle Spine (2026-03-26)
> This controller was deleted. Template installation is now handled by `WorkspaceController` in the [[Workspaces & Users]] domain via `POST /api/v1/workspace/{workspaceId}/install-template`. Bundle endpoints were removed entirely.

Part of [[Template Installation]]

## Purpose

REST controller providing the Catalog domain's only HTTP API surface — four endpoints for listing available templates/bundles and installing them into workspaces.

---

## Responsibilities

- Expose `GET /api/v1/templates` for listing available templates
- Expose `POST /api/v1/templates/{workspaceId}/install` for installing a template
- Expose `GET /api/v1/templates/bundles` for listing available bundles
- Expose `POST /api/v1/templates/{workspaceId}/install-bundle` for installing a bundle
- Delegate all business logic to [[ManifestCatalogService]] (reads) and [[TemplateInstallationService]] (writes)

---

## Dependencies

- [[ManifestCatalogService]] — template and bundle listing queries
- [[TemplateInstallationService]] — template and bundle installation orchestration

## Used By

- External API consumers — REST clients

---

## Endpoints

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/v1/templates` | List available templates | Authenticated |
| POST | `/api/v1/templates/{workspaceId}/install` | Install template into workspace | Workspace access |
| GET | `/api/v1/templates/bundles` | List available bundles | Authenticated |
| POST | `/api/v1/templates/{workspaceId}/install-bundle` | Install bundle into workspace | Workspace access |

---

## Gotchas

- **First REST controller in Catalog domain:** This breaks the previous "no REST controllers" decision. The Catalog domain was previously consumed only via direct service injection.
- **Uses `@Valid` on request bodies:** Both install endpoints validate `InstallTemplateRequest` and `InstallBundleRequest` via `@Valid`.

---

## Related

- [[TemplateInstallationService]] — business logic for installation
- [[ManifestCatalogService]] — catalog query surface
- [[Template Installation]] — parent subdomain
