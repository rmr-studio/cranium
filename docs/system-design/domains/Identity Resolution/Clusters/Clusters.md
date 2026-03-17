---
tags:
  - architecture/subdomain
  - domain/identity-resolution
Created: 2026-03-17
Domains:
  - "[[Identity Resolution]]"
---

# Clusters

## Overview

Manages identity clusters — groups of entities confirmed as representing the same real-world identity. Currently scaffolded only: entities and repositories exist but no services or business logic. Cluster functionality will be built in a future phase when confirmed match suggestions need to be grouped.

## Components

| Component | Purpose | Type |
|---|---|---|
| IdentityClusterEntity | Workspace-scoped cluster container with member count tracking, soft-deletable | Entity |
| IdentityClusterMemberEntity | Join table linking entities to clusters — hard-deleted (not AuditableSoftDeletableEntity) | Entity |
| IdentityClusterRepository | Basic CRUD for clusters | Repository |
| IdentityClusterMemberRepository | Basic CRUD for cluster members | Repository |

## Technical Debt

- No services implemented — entities are scaffolded for the next phase.
- Unique index on `entity_id` enforces one-cluster-per-entity constraint at the database level.

## Recent Changes

| Date | Change | Domains |
|---|---|---|
| 2026-03-17 | Entity scaffolding for cluster system | Identity Resolution |
