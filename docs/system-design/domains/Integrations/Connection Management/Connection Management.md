---
tags:
  - architecture/subdomain
  - domain/integration
  - tools/nango
Created: 2025-07-17
Updated: 2026-03-18
Domains:
  - "[[Integrations]]"
---
# Subdomain: Connection Management

## Overview

Manages the Nango connection lifecycle for workspace integrations. Enforces an 8-state connection state machine with validated transitions on all status changes. Connections are created exclusively by the webhook handler after OAuth completion via the `createOrReconnect` internal method — there is no public connection creation endpoint. Provides connection status queries, status updates with state machine validation, and graceful disconnect with programmatic transaction management to avoid holding DB transactions open during external Nango API calls.

## Components

| Component | Purpose | Type |
| --------- | ------- | ---- |
| [[IntegrationConnectionService]] | Manages connection state machine, Nango API interaction, connect/disconnect lifecycle | Service |
| [[IntegrationConnectionEntity]] | Per-workspace Nango connection with status, metadata, and provider key | Entity |
| [[IntegrationConnectionRepository]] | Queries connections by workspace and integration, state-based lookups | Repository |
| [[NangoClientWrapper]] | HTTP client wrapper for Nango REST API — connection management, record fetching, and sync triggering with retry logic and error handling | Service |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2025-07-17 | Added `enableConnection()` for integration enablement — creates CONNECTED connection or reconnects DISCONNECTED | Integration Enablement |
| 2026-03-18 | Simplified to 8-state model (removed PENDING_AUTHORIZATION, AUTHORIZING). Removed `enableConnection`/`createConnection` public methods — connection creation now exclusively webhook-driven via `createOrReconnect` internal method. NangoClientWrapper extended with `fetchRecords` and `triggerSync` | Integration Sync Phase 2 |
