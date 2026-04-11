---
Created: 2026-03-17
Domains:
  - "[[riven/docs/system-design/domains/Identity Resolution/Identity Resolution]]"
tags:
  - component/active
  - layer/service
  - architecture/component
---

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Identity Resolution/Temporal Integration/Temporal Integration]]

# IdentityMatchActivitiesImpl

## Purpose

Temporal activity implementation providing thin delegation to domain services. Registered on the dedicated `identity.match` task queue, isolated from the default workflow queue.

## Interface: IdentityMatchActivities

Defines three `@ActivityMethod` methods for the identity matching pipeline.

## Implementation: IdentityMatchActivitiesImpl (Spring bean)

### Constructor Dependencies

- `IdentityMatchCandidateService`
- `IdentityMatchScoringService`
- `IdentityMatchSuggestionService`
- `KLogger`

### Methods

All methods are thin delegation — no business logic in the activity layer.

| Method | Signature | Delegates To |
|--------|-----------|-------------|
| `findCandidates` | `(entityId, workspaceId): List<CandidateMatch>` | `candidateService.findCandidates()` |
| `scoreCandidates` | `(triggerEntityId, workspaceId, candidates): List<ScoredCandidate>` | Gets trigger attributes from `candidateService.getTriggerAttributes()`, then `scoringService.scoreCandidates()` |
| `persistSuggestions` | `(workspaceId, scoredCandidates, userId): Int` | `suggestionService.persistSuggestions()` |

## Registration

Registered on `identity.match` task queue via `TemporalWorkerConfiguration` — separate from the default `workflows.default` queue.

## Gotchas

- **Activities run in Spring context** (unlike workflow impl) — they have access to repositories, services, and all Spring-managed beans.
- **The `identity.match` queue is separate from `workflows.default`** to prevent identity matching work from blocking workflow executions.

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Identity Resolution/Temporal Integration/IdentityMatchWorkflow]]
- [[riven/docs/system-design/domains/Identity Resolution/Matching Pipeline/IdentityMatchCandidateService]]
- [[riven/docs/system-design/domains/Identity Resolution/Matching Pipeline/IdentityMatchScoringService]]
- [[riven/docs/system-design/domains/Identity Resolution/Matching Pipeline/IdentityMatchSuggestionService]]
- [[riven/docs/system-design/domains/Workflows/Execution Engine/TemporalWorkerConfiguration]]
