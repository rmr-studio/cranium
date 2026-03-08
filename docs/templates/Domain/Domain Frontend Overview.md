---
tags:
  - architecture/domain
  - architecture/frontend
Created:
Updated:
Domains:
  - "[[Domain]]"
Backend-Domain: "[[]]"
---
# Frontend: {{title}}

---

## Overview

_How does this domain surface in the UI? Which parts are user-facing vs. background-only?_

### User-Facing Surface

_Capabilities from this domain that users directly interact with_

-

### Background / Invisible

_Domain capabilities that run without user awareness (but may surface status/results)_

-

---

## Pages & Views

_Where does this domain appear in the application?_

| Page | Route | Role | Page Design |
| ---- | ----- | ---- | ----------- |
|      |       | Primary / Supporting | [[]] |

---

## UI Patterns

_Component patterns specific to this domain's data shape and user needs_

### Primary Pattern

_The dominant interaction model for this domain (e.g., data table, canvas, form wizard, timeline)_

### Shared Components Used

| Component | Purpose | Customisation |
| --------- | ------- | ------------- |
|           |         |               |

### Domain-Specific Components

| Component | Purpose | Feature Design |
| --------- | ------- | -------------- |
|           |         | [[]]           |

---

## State Architecture

### Query Keys & Cache Strategy

| Query | Key Pattern | Stale Time | Invalidated By |
| ----- | ----------- | ---------- | -------------- |
|       |             |            |                |

### Real-Time / Subscriptions

_Does this domain use WebSockets, SSE, or polling? What data updates live?_

### Optimistic Updates

_Which mutations update the UI before server confirmation? Rollback strategy?_

### Cross-Domain State Dependencies

_State from other domains this frontend depends on (e.g., workspace context, user permissions)_

| Dependency | Source Domain | How Accessed |
| ---------- | ------------ | ------------ |
|            |              | Query / Context / URL |

---

## API Consumption

### Endpoints Used

| Endpoint | Method | Purpose | Response Shape Notes |
| -------- | ------ | ------- | -------------------- |
|          |        |         |                      |

### Response → UI Mapping

_Where the API response shape drives UI decisions — transformations, derived state, computed fields_

---

## Key Interactions

_Interactions unique to this domain that shape component architecture_

| Interaction | Description | Complexity |
| ----------- | ----------- | ---------- |
|             |             | Low / Med / High |

---

## Constraints

| Constraint | Detail | Impact on Frontend |
| ---------- | ------ | ------------------ |
| Data volume |       |                    |
| Latency    |        |                    |
| Permissions |       |                    |
| Real-time  |        |                    |

---

## Technical Debt

| Issue | Impact | Effort |
| ----- | ------ | ------ |
|       | High/Med/Low | High/Med/Low |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
|      |        | [[]]        |
