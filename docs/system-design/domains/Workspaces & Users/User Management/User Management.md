---
Created: 2026-02-08
Domains:
  - "[[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]]"
tags:
  - architecture/subdomain
  - domain/user
---
# Subdomain: User Management

## Overview

Manages user profiles independently of workspaces. Users exist at the application level (authenticated via Supabase Auth, profile stored locally in the users table). This subdomain provides CRUD operations for user profiles, session-aware retrieval with optimized workspace membership loading (avoiding N+1 queries), and default workspace management.

UserService provides session-based user retrieval (extracting user ID from JWT) and custom repository queries to load user workspaces efficiently. UserController enforces self-only access at the controller level — users can only view/update/delete their own profiles.

## Components

| Component | Purpose | Type |
| --------- | ------- | ---- |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/User Management/UserService]] | User CRUD, session-based retrieval, workspace membership loading | Service |
| UserController | REST endpoints for user profile operations | Controller |
| UserRepository | User data access with custom membership query | Repository |

## Endpoints

### UserController

| Method | Path | Purpose | Auth |
| ------ | ---- | ------- | ---- |
| GET | /api/v1/user/ | Get current user profile with workspaces | authenticated |
| GET | /api/v1/user/{userId} | Get user by ID (self-only, enforced in controller) | authenticated (self check: userId must match session user) |
| PUT | /api/v1/user/ | Update current user profile | authenticated (self check: request userId must match session user) |
| DELETE | /api/v1/user/{userId} | Delete user profile (self-only, enforced in controller) | authenticated (self check: userId must match session user) |

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
| 2026-03-12 | Added `onboarding_completed_at` (nullable timestamp) field to User entity and model — tracks whether a user has completed the onboarding flow | [[2. Areas/2.1 Startup & Business/Riven/2. System Design/domains/Workspaces & Users/Onboarding 1/Onboarding]] |
