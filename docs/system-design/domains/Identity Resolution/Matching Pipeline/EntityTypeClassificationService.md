---
tags:
  - component/active
  - layer/service
  - architecture/component
  - domain/identity-resolution
Created: 2026-03-19
Domains:
  - "[[Identity Resolution]]"
Sub-Domains:
  - "[[Matching Pipeline]]"
---

# EntityTypeClassificationService

## Purpose

Cached IDENTIFIER-attribute classification lookups for entity types, using an in-memory ConcurrentHashMap to avoid repeated database queries.

---

## Responsibilities

- Looking up which attributes on an entity type are classified as IDENTIFIER
- Caching classification results in memory for fast repeated access
- Providing cache invalidation when attribute classifications change

## Dependencies

- [[EntityTypeSemanticMetadataRepository]] — fetches IDENTIFIER attribute IDs for a given entity type
- KLogger — structured logging

## Used By

- [[IdentityMatchTriggerListener]] — checks whether an entity type has IDENTIFIER attributes before enqueuing
- [[IdentityMatchCandidateService]] — indirectly via EntityService for candidate discovery

---

## Key Logic

### Cache Structure

`ConcurrentHashMap<UUID, Set<UUID>>` — maps `entityTypeId` to the set of attribute IDs classified as IDENTIFIER.

An empty set means the entity type has been looked up but has no IDENTIFIER attributes. A missing key means the entity type has not been looked up yet.

### Public Methods

- `hasIdentifierAttributes(entityTypeId): Boolean` — returns true if the entity type has at least one IDENTIFIER attribute
- `getIdentifierAttributeIds(entityTypeId): Set<UUID>` — returns the set of IDENTIFIER attribute IDs (empty if none)
- `invalidate(entityTypeId)` — removes the cached entry, forcing a fresh lookup on next access

---

## Gotchas

- **No TTL eviction** — the cache relies entirely on explicit `invalidate()` calls. If attribute classifications change without invalidation, stale results will be served indefinitely.
- **Empty set is a valid cached value** — distinguishes "looked up, no IDENTIFIER attributes" from "never looked up". This prevents repeated DB queries for entity types that genuinely have no IDENTIFIER attributes.

---

## Related

- [[Matching Pipeline]]
- [[IdentityMatchTriggerListener]]
