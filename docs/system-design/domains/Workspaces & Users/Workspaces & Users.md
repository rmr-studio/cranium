---
tags:
  - architecture/domain
  - domain/workspace
  - domain/user
Created: 2026-02-08
Updated: 2026-03-07
---
# Domain: Workspaces & Users

---

## Overview

The Workspaces & Users domain is an **infrastructure-style foundational domain** that provides multi-tenancy, authentication, and authorization capabilities for the entire application. Unlike peer domains such as Workflows and Entities, this domain is the foundation they build upon — every domain in the system depends on Workspaces & Users for workspace scoping, user identity, and access control.

This domain manages the complete lifecycle of workspaces (multi-tenant containers), user profiles, workspace memberships with role-based permissions, and the authentication/authorization pipeline that enforces access control across all application operations.

## How Workspace Scoping Works

```mermaid
flowchart TD
    A[HTTP Request with JWT] --> B[SecurityConfig/TokenDecoder]
    B --> C[JWT Decoded]
    C --> D[Extract workspace roles from JWT claims]
    D --> E[Build Spring Security authorities<br/>ROLE_workspaceId_role]
    E --> F[@PreAuthorize checks via WorkspaceSecurity]
    F --> G{Authorized?}
    G -->|Yes| H[Database Query]
    G -->|No| I[403 Forbidden]
    H --> J[PostgreSQL RLS Activated]
    J --> K[Filter by workspace_members<br/>WHERE user_id = auth.uid]
    K --> L[Return workspace-scoped data only]
```

---

## Boundaries

### This Domain Owns

- Workspace lifecycle (create, update, soft-delete)
- Workspace membership management (add, remove, role updates)
- Invitation workflow (create, accept, reject, revoke)
- User profile management (CRUD operations)
- JWT authentication and authority extraction (token decoding, claims parsing)
- Role-based authorization (@PreAuthorize expressions via WorkspaceSecurity)
- Row-level security activation via workspace_members table
- Real-time event broadcasting (WebSocket/STOMP infrastructure, event listener, security interceptor)

### This Domain Does NOT Own

- Domain-specific business logic (Workflows, Entities, Blocks own their own logic)
- Database table-level RLS policy definitions (owned by database schema)
- External identity provider (Supabase Auth manages user creation and JWT signing)
- Workspace billing/payment processing (future domain)

---

## Sub-Domains

| Component | Purpose |
| --------- | ------- |
| [[Workspace Management]] | Workspace CRUD, membership management, activity logging |
| [[Team Management]] | Invitation workflow, member onboarding (future: team features) |
| [[User Management]] | User profile CRUD, session-based user retrieval, default workspace |
| [[2. Areas/2.1 Startup & Business/Riven/2. System Design/domains/Workspaces & Users/Onboarding/Onboarding]] | Single-request onboarding flow: workspace creation, profile setup, template installation, team invites |
| [[Auth & Authorization]] | JWT decoding, authority extraction, role-based access control, workspace security checks |
| [[Real-time Events]] | WebSocket/STOMP infrastructure for broadcasting workspace-scoped domain events to connected clients |

### Integrations

| Component | External System |
| --------- | --------------- |
| SecurityConfig | Supabase Auth (JWT signing/issuing) |

---

## Flows

| Flow        | Type                     | Description |
| ----------- | ------------------------ | ----------- |
| [[Auth & Authorization]] | User-facing | JWT decode -> authority extraction -> @PreAuthorize -> RLS (Phase 4) |
| [[Invitation Acceptance]] | User-facing | Create invite -> token -> accept -> add member (Phase 4) |
| [[Flow - Domain Event Broadcasting]] | Internal | Service → ApplicationEventPublisher → WebSocketEventListener → STOMP topic (cross-domain) |

---

## Data

### Owned Entities

| Entity | Purpose | Key Fields |
| ------ | ------- | ---------- |
| Workspace | Multi-tenant container | id, name, plan, defaultCurrency, memberCount, deleted |
| WorkspaceMember | User-workspace association with role | workspaceId, userId, role |
| WorkspaceInvite | Pending invitation | workspaceId, email, role, inviteStatus, token, invitedBy |
| User | Application user profile | id, name, email, phone, avatarUrl, defaultWorkspace, onboardingCompletedAt |

### Database Tables

| Table | Entity | Notes |
| ----- | ------ | ----- |
| workspaces | Workspace | Multi-tenant container with plan/tier information |
| workspace_members | WorkspaceMember | Junction table for user-workspace associations with role, enables RLS |
| workspace_invites | WorkspaceInvite | Pending invitations with unique token and status |
| users | User | Application user profiles (created via Supabase Auth trigger) |

---

## External Dependencies

| Dependency | Purpose | Failure Impact |
| ---------- | ------- | -------------- |
| Supabase Auth | JWT signing and user identity management | No authentication possible — all requests rejected |
| PostgreSQL RLS | Row-level security for multi-tenant isolation | Data leakage across workspaces |

---

## Domain Interactions

### Depends On

| Domain | What We Consume | Via Component | Related Flow |
|--------|----------------|---------------|--------------|
| [[Storage]] | File upload for workspace and user avatars | [[StorageService]] | [[Flow - File Upload]] |
| [[Catalog]] | Template and bundle installation during onboarding | [[TemplateInstallationService]] | [[2. Areas/2.1 Startup & Business/Riven/2. System Design/domains/Workspaces & Users/Onboarding/Onboarding]] |

### Consumed By

| Consumer | What They Consume | Via Component | Related Flow |
|----------|------------------|---------------|--------------|
| [[Workflows]] | Workspace scoping (RLS), @PreAuthorize authorization | [[WorkspaceSecurity]], RLS via workspace_members | [[Auth & Authorization]], [[Queue Processing]] |
| [[Entities]] | Workspace scoping (RLS), @PreAuthorize authorization, user context | [[WorkspaceSecurity]], [[AuthTokenService]], RLS via workspace_members | [[Auth & Authorization]], [[Entity CRUD]] |
| [[Storage]] | User identity, workspace authorization | [[AuthTokenService]], [[WorkspaceSecurity]] | [[Flow - File Upload]] |

---

## Key Decisions

| Decision | Summary |
| -------- | ------- |
| Infrastructure-style domain | Workspaces & Users is foundational — other domains depend on it for scoping and auth |
| JWT-based authentication | Supabase Auth signs JWTs, SecurityConfig validates and extracts authorities |
| ROLE_{workspaceId}_{role} authority format | Spring Security authorities encode workspace and role for @PreAuthorize checks |
| PostgreSQL RLS for workspace isolation | Database-level row filtering via workspace_members table ensures multi-tenant data security |
| Team Management as forward-looking subdomain | Current invitation/membership logic grouped under Team Management for future expansion |

---

## Technical Debt

| Issue | Impact | Effort |
| ----- | ------ | ------ |
| None yet | — | — |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-02-08 | Domain structure created | [[03-01-PLAN]] |
| 2026-03-12 | Added [[2. Areas/2.1 Startup & Business/Riven/2. System Design/domains/Workspaces & Users/Onboarding/Onboarding]] subdomain; `onboardingCompletedAt` field on User; new dependency on [[Catalog]] domain | [[2. Areas/2.1 Startup & Business/Riven/2. System Design/domains/Workspaces & Users/Onboarding/Onboarding]] |
| 2026-03-14 | Added [[Real-time Events]] subdomain — WebSocket/STOMP infrastructure with JWT security interceptor, transactional event listener, workspace-scoped topic broadcasting | WebSocket Notifications |
