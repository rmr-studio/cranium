---
tags:
  - architecture/subdomain
  - domain/identity-resolution
Created: 2026-03-17
Domains:
  - "[[Identity Resolution]]"
---

# Temporal Integration

## Overview

Temporal workflow and activity definitions for the identity matching pipeline. The workflow orchestrates three independently retryable activities (FindCandidates, ScoreCandidates, PersistSuggestions) with short-circuit on empty results. Activities are thin delegation layers to domain services, registered on a dedicated `identity.match` task queue isolated from the default workflow queue.

## Components

| Component | Purpose | Type |
|---|---|---|
| IdentityMatchWorkflow / IdentityMatchWorkflowImpl | Temporal workflow — deterministic orchestration of 3-activity pipeline with short-circuit on empty candidates or scores | Temporal Workflow |
| IdentityMatchActivities / IdentityMatchActivitiesImpl | Temporal activities — thin delegation to CandidateService, ScoringService, SuggestionService | Temporal Activity |

## Technical Debt

None identified.

## Recent Changes

| Date | Change | Domains |
|---|---|---|
| 2026-03-17 | Initial Temporal integration | Identity Resolution |
