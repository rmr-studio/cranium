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

Manages identity clusters — groups of entities confirmed as representing the same real-world identity. Fully implemented with confirmation service (5-case cluster resolution), manual cluster mutations (add member, rename), and a read API for suggestions and clusters with member enrichment. Phase 4 feature design covers the confirmation state machine and Union-Find cluster management — see [[Identity Cluster Confirmation and Union-Find Management]].

## Components

| Component | Purpose | Type |
|---|---|---|
| IdentityClusterEntity | Workspace-scoped cluster container with member count tracking, soft-deletable | Entity |
| IdentityClusterMemberEntity | Join table linking entities to clusters — hard-deleted (not AuditableSoftDeletableEntity) | Entity |
| IdentityClusterRepository | Basic CRUD for clusters | Repository |
| IdentityClusterMemberRepository | Basic CRUD for cluster members | Repository |
| [[IdentityConfirmationService]] | Human decision path — confirm/reject suggestions with 5-case cluster resolution | Service |
| [[IdentityClusterService]] | Manual cluster mutations — add member to cluster, rename cluster | Service |
| [[IdentityReadService]] | Read API for suggestions and clusters with member enrichment | Service |
| [[IdentityController]] | REST controller — 9 endpoints for suggestion review and cluster management | Controller |

## Technical Debt

- Unique index on `entity_id` enforces one-cluster-per-entity constraint at the database level.

## Recent Changes

| Date | Change | Domains |
|---|---|---|
| 2026-03-17 | Entity scaffolding for cluster system | Identity Resolution |
| 2026-03-18 | Phase 4 feature design: confirmation and Union-Find management | Identity Resolution, Entities |
| 2026-03-19 | Phase 4/5: confirmation service, cluster service, read service, REST controller | Identity Resolution, Entities |
