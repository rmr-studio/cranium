---
tags:
  - architecture/subdomain
  - domain/integration
Created: 2026-03-17
Domains:
  - "[[Integrations]]"
---
# Subdomain: Data Sync

## Overview

Tracks per-connection per-entity-type sync progress for integration data ingestion. Each sync state row records the current status, cursor position for incremental sync, failure counts, and record metrics. Sync state rows are system-managed — they are created when sync begins and CASCADE-deleted when the parent connection or entity type is removed. This subdomain provides the persistence foundation; sync execution and orchestration are handled by Temporal workflows (not yet implemented).

## Components

| Component | Purpose | Type |
| --------- | ------- | ---- |
| [[IntegrationSyncStateEntity]] | Per-connection per-entity-type sync progress tracking with cursor, failure counts, and record metrics | Entity |
| [[IntegrationSyncStateRepository]] | Queries sync state by connection and connection+entity type | Repository |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-03-17 | Initial implementation — sync state entity, repository, SyncStatus enum | Integration Sync Persistence Foundation |
