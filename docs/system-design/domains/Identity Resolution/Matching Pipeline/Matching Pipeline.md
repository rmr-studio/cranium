---
tags:
  - architecture/subdomain
  - domain/identity-resolution
Created: 2026-03-17
Domains:
  - "[[Identity Resolution]]"
---

# Matching Pipeline

## Overview

Core pipeline for identity resolution. Implements three-phase matching: candidate finding via pg_trgm trigram similarity on IDENTIFIER-classified attributes, weighted scoring with configurable per-signal-type weights, and idempotent suggestion persistence with re-suggestion logic. Also includes queue management services for dispatching IDENTITY_MATCH jobs and a classification cache service.

## Components

| Component | Purpose | Type |
|---|---|---|
| [[IdentityMatchCandidateService]] | Two-phase pg_trgm blocking — GIN index leverage with `%` operator + similarity threshold for finding candidate matches | Service |
| [[IdentityMatchScoringService]] | Pure computation — weighted average scoring with configurable per-signal-type weights | Service |
| [[IdentityMatchSuggestionService]] | Suggestion persistence — idempotent creation, re-suggestion on improved score, rejection with signal snapshot | Service |
| EntityTypeClassificationService | Cached IDENTIFIER attribute lookup for entity types using ConcurrentHashMap | Service |
| IdentityMatchQueueService | Enqueues IDENTITY_MATCH jobs with deduplication via partial unique index | Service |
| IdentityMatchDispatcherService | Scheduled queue polling (every 5s) with ShedLock distributed locking | Service |
| IdentityMatchQueueProcessorService | Per-item processing with REQUIRES_NEW transactions — dispatches to Temporal | Service |
| MatchSuggestionEntity | Candidate pair for human review — JSONB signals, canonical UUID ordering, soft-delete | Entity |
| MatchSuggestionRepository | Native SQL queries for active/rejected suggestions (bypasses @SQLRestriction) | Repository |
| MatchSignalType | Signal type enum with default weights and SchemaType mapping | Enum |

## Technical Debt

- Fixed similarity threshold not configurable per workspace.
- Candidate limit hardcoded at 50 per attribute query.
- `MatchSignalType.fromSchemaType` maps most IDENTIFIER schema types to CUSTOM_IDENTIFIER rather than specific signal types.

## Recent Changes

| Date | Change | Domains |
|---|---|---|
| 2026-03-17 | Initial matching pipeline implementation | Identity Resolution |
