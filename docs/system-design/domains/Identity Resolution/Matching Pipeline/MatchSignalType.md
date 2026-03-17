---
Created: 2026-03-17
Domains:
  - "[[Identity Resolution]]"
tags:
  - component/active
  - layer/enum
  - architecture/component
---

Part of [[Matching Pipeline]]

# MatchSignalType

## Purpose

Enumerates the types of identity signals used for matching, with default scoring weights and mapping from `SchemaType`.

## Values

| Value | Default Weight | Description |
|-------|---------------|-------------|
| `EMAIL` | 0.9 | Email address match — highest confidence |
| `PHONE` | 0.85 | Phone number match |
| `NAME` | 0.5 | Name match — contextual, lower confidence |
| `COMPANY` | 0.3 | Company/organization match — lowest weight |
| `CUSTOM_IDENTIFIER` | 0.7 | Any other IDENTIFIER-classified attribute |

## Companion Object

- **`DEFAULT_WEIGHTS: Map<MatchSignalType, Double>`** — maps each enum value to its default weight as listed above.
- **`fromSchemaType(schemaType: SchemaType): MatchSignalType`** — maps `SchemaType` to `MatchSignalType`. Only `EMAIL` and `PHONE` map directly. All others (including NAME, COMPANY which are contextual) map to `CUSTOM_IDENTIFIER`.

## Used By

- `IdentityMatchScoringService` — uses weights for composite score calculation
- `IdentityMatchCandidateService` — annotates candidates with signal type

## Gotchas

- **`NAME` and `COMPANY` are defined as enum values but `fromSchemaType` never returns them.** They exist for future heuristic-based classification. Currently all non-EMAIL/PHONE attributes are classified as `CUSTOM_IDENTIFIER`.

## Related

- [[IdentityMatchScoringService]]
- [[IdentityMatchCandidateService]]
- [[MatchSignal]]
