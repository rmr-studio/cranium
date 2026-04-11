---
Created: 2026-02-08
Domains:
  - "[[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]]"
tags:
  - architecture/subdomain
  - domain/workspace
---
# Subdomain: Workspace Management

## Overview

Manages the complete lifecycle of workspaces — the multi-tenant containers that scope all application data. This subdomain handles workspace creation (with automatic OWNER membership for the creator), updates to workspace properties (name, plan, currency), soft-deletion, and member role management.

WorkspaceService is the single service here, enforcing authorization rules via WorkspaceSecurity and logging all workspace activities. WorkspaceController exposes REST endpoints for workspace operations.

## Components

| Component | Purpose | Type |
| --------- | ------- | ---- |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Workspace Management/WorkspaceService]] | Workspace CRUD, member management, activity logging | Service |
| WorkspaceController | REST endpoints for workspace operations | Controller |
| WorkspaceRepository | Workspace data access | Repository |
| WorkspaceMemberRepository | Membership data access | Repository |

Cross-references: [[riven/docs/system-design/domains/Workspaces & Users/Auth & Authorization/WorkspaceSecurity]] (from Auth & Authorization subdomain) — used by WorkspaceService via @PreAuthorize for authorization checks.

## Endpoints

### WorkspaceController

| Method | Path | Purpose | Auth |
| ------ | ---- | ------- | ---- |
| GET | /api/v1/workspace/{workspaceId} | Get workspace by ID with optional metadata | @workspaceSecurity.hasWorkspace(#workspaceId) |
| POST | /api/v1/workspace/ | Create or update workspace | authenticated |
| DELETE | /api/v1/workspace/{workspaceId} | Soft-delete workspace | @workspaceSecurity.hasWorkspaceRole(#workspaceId, T(riven.core.enums.workspace.WorkspaceRoles).OWNER) |
| DELETE | /api/v1/workspace/{workspaceId}/member/{memberId} | Remove member from workspace | @workspaceSecurity.isUpdatingWorkspaceMember(#workspaceId, #member) |
| PUT | /api/v1/workspace/{workspaceId}/member/{memberId}/role/{role} | Update member role | @workspaceSecurity.isUpdatingWorkspaceMember(#workspaceId, #member) |

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
| 2026-03-12 | `saveWorkspace` refactored: logic extracted into private methods; now uses `storageService.uploadFileInternal` for avatar uploads | [[2. Areas/2.1 Startup & Business/Riven/2. System Design/domains/Workspaces & Users/Onboarding 1/Onboarding]] |
