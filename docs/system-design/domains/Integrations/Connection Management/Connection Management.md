---
tags:
  - architecture/subdomain
  - domain/integration
  - tools/nango
Created: 2025-07-17
Domains:
  - "[[Integrations]]"
---
# Subdomain: Connection Management

## Overview

Manages the Nango connection lifecycle for workspace integrations. Enforces a 10-state connection state machine with validated transitions on all status changes. Provides connection CRUD with workspace security, handles OAuth post-authorization via `enableConnection()`, and manages graceful disconnect with programmatic transaction management to avoid holding DB transactions open during external Nango API calls.

## Components

| Component | Purpose | Type |
| --------- | ------- | ---- |
| [[IntegrationConnectionService]] | Manages connection state machine, Nango API interaction, connect/disconnect lifecycle | Service |
| [[IntegrationConnectionEntity]] | Per-workspace Nango connection with status, metadata, and provider key | Entity |
| [[IntegrationConnectionRepository]] | Queries connections by workspace and integration, state-based lookups | Repository |
| [[NangoClientWrapper]] | HTTP client wrapper for Nango API operations (delete connection) | Service |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2025-07-17 | Added `enableConnection()` for integration enablement — creates CONNECTED connection or reconnects DISCONNECTED | Integration Enablement |
