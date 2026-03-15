---
Created: 2026-02-08
Domains:
  - "[[Workspaces & Users]]"
tags:
  - architecture/subdomain
  - domain/auth
  - domain/security
---
# Subdomain: Auth & Authorization

## Overview

Handles the full authentication and authorization pipeline for the application. This subdomain bridges external authentication (Supabase Auth JWTs) with internal authorization (Spring Security @PreAuthorize checks) and workspace-scoped data access (PostgreSQL RLS).

**Authentication flow:** SecurityConfig sets up Spring Security with JWT-based OAuth2 resource server. CustomAuthenticationTokenConverter (in TokenDecoder file) extracts workspace roles from JWT claims and builds Spring Security authorities in `ROLE_{workspaceId}_{role}` format.

**Authorization enforcement:** WorkspaceSecurity is the core authorization component, used across the entire application via @PreAuthorize expressions. It provides role-based checks (exact role match, role-or-higher, hierarchical comparison) and specialized membership update authorization.

**Audit integration:** SecurityAuditorAware provides the current user UUID for JPA auditing fields (createdBy, updatedBy), automatically populating audit columns on entity saves.

## Components

| Component | Purpose | Type |
| --------- | ------- | ---- |
| [[WorkspaceSecurity]] | Role-based authorization checks for @PreAuthorize expressions | Security Bean |
| [[AuthTokenService]] | JWT claim extraction (userId, email, authorities) | Service |
| [[TokenDecoder]] | JWT-to-Spring-Security authority conversion | Configuration |
| SecurityConfig | Security filter chain, JWT decoder, CORS configuration | Configuration |
| SecurityAuditorAware | JPA audit field population from JWT subject | Configuration |
| SecurityConfigurationProperties | JWT secret, issuer, allowed origins | Properties |

**WorkspaceRoles enum** (documented inline):
- OWNER (authority: 3) — Full workspace control
- ADMIN (authority: 2) — Member management, invitations
- MEMBER (authority: 1) — Basic workspace access

Used by WorkspaceSecurity for hierarchical role comparison via numeric authority values.

## Authorization Patterns

No controller endpoints in this subdomain — these are cross-cutting infrastructure components used by all other controllers via @PreAuthorize expressions and dependency injection.

**Common @PreAuthorize patterns across the application:**

- `@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")` — User has any role in workspace
- `@PreAuthorize("@workspaceSecurity.hasWorkspaceRole(#workspaceId, T(riven.core.enums.workspace.WorkspaceRoles).ADMIN)")` — User has exact role
- `@PreAuthorize("@workspaceSecurity.hasWorkspaceRoleOrHigher(#workspaceId, T(riven.core.enums.workspace.WorkspaceRoles).ADMIN)")` — User has role or higher (ADMIN or OWNER)
- `@PreAuthorize("@workspaceSecurity.isUpdatingWorkspaceMember(#workspaceId, #member)")` — User can update member (OWNER always, ADMIN if target role lower)

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
| 2026-03-14 | SecurityConfig: `/ws/**` added to `permitAll` — WebSocket upgrade handled by STOMP-level [[WebSocketSecurityInterceptor]], not HTTP filter chain | WebSocket Notifications |
