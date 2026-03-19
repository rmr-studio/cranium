# Nango Sync Script Guide

<!-- Practical reference for writing Nango sync scripts that feed into the Riven integration pipeline -->

**Audience:** Engineers writing Nango TypeScript sync scripts that produce records consumed by the Riven sync pipeline.
**Scope:** Sync config, record shape, batchSave checkpointing, relationship IDs, and metadata semantics.

---

## 1. Sync Config Structure (`nango.yaml`)

Each sync is declared in `nango.yaml` under the provider config block:

```yaml
integrations:
  hubspot:
    syncs:
      contacts:
        runs: every 30min
        auto_start: true
        track_deletes: true
        output: Contact
```

**Key fields:**

| Field | Description |
|-------|-------------|
| `runs` | Cron-style or interval schedule (`every 1h`, `every 30min`, `every day`) |
| `auto_start` | If `true`, sync starts automatically after connection is created |
| `track_deletes` | If `true`, Nango adds DELETED records when source removes an item |
| `output` | Model name — **must match the `key` field on `CatalogEntityTypeEntity`** |

**Model naming is critical.** The Riven Temporal workflow resolves the entity type by matching `model` to `CatalogEntityTypeEntity.key`. If the names don't match, the sync will log an error and skip the batch.

```yaml
# Correct: model name "contacts" matches CatalogEntityTypeEntity.key = "contacts"
integrations:
  hubspot:
    syncs:
      contacts:
        output: Contact   # <-- this is the model name sent to Riven
```

---

## 2. Model and Record Format

Each sync script produces records conforming to its model. The Riven pipeline is schema-agnostic — it maps fields via `CatalogFieldMapping` entries, so the record shape is flexible.

**Minimum required field:** `id` (string) — used as the dedup key by default.

```typescript
// Minimal valid record
export interface Contact {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  companyId: string;
}
```

**Custom dedup key:** If the external system uses a different field (e.g., `externalId`, `uid`), set `_externalIdField` in the `CatalogFieldMapping` for that integration. The pipeline uses whichever field is configured as the dedup key — ensure the sync script always populates it.

**Relationship fields:** Model relationship targets as lists of external IDs, keyed by the relationship definition name. Do not resolve to internal UUIDs — the pipeline handles resolution in Pass 2.

```typescript
export interface Contact {
  id: string;
  email: string;
  // Relationship: Contact belongs to many Companies
  // Key must match CatalogRelationshipDefinition.name
  companies: string[];   // external IDs, e.g. ["ext-company-42", "ext-company-99"]
  // Single relationship (still an array)
  owner: string[];       // ["user-ext-id-7"]
}
```

---

## 3. batchSave and Checkpointing

Use `nango.batchSave(records, model)` to persist records in batches. Checkpointing is automatic — every `batchSave` call creates a resume point.

```typescript
export default async function fetchData(nango: NangoSync) {
  let page = 1;
  let hasMore = true;

  while (hasMore) {
    const response = await nango.get({
      endpoint: '/crm/v3/objects/contacts',
      params: { limit: 100, after: page > 1 ? cursor : undefined },
    });

    const records: Contact[] = response.data.results.map(toContact);

    // Checkpoint every batch — on retry, Nango resumes from here
    await nango.batchSave(records, 'Contact');

    hasMore = response.data.paging?.next != null;
    cursor = response.data.paging?.next?.after;
    page++;
  }
}
```

**Checkpoint behavior:**

- Each `batchSave` call is a checkpoint. On retry, Nango resumes from the **last successful checkpoint**.
- Records before the checkpoint are **not re-sent** — Riven will not see duplicates from retry.
- Recommended batch size: **100–500 records**. Larger batches delay checkpoint creation and increase re-work on retry.

**Soft-delete propagation:** For integrations using `track_deletes: true`, Nango handles DELETED record generation automatically. For manual delete handling:

```typescript
await nango.batchDelete(deletedRecords, 'Contact');
```

The pipeline treats `batchDelete` records with `lastAction: DELETED` and soft-deletes the corresponding entity.

---

## 4. Relationship ID Patterns

Relationship fields contain **external IDs**, never internal Riven UUIDs. The pipeline resolves them in Pass 2 (after all entity upserts complete in Pass 1).

```typescript
// Contact record with relationships
const contact: Contact = {
  id: 'hs-contact-123',
  email: 'alice@example.com',
  firstName: 'Alice',
  lastName: 'Smith',
  // External IDs of related companies
  companies: ['hs-company-456', 'hs-company-789'],
  // External ID of owner user
  owner: ['hs-user-42'],
};
```

**Why external IDs?** The sync script runs against the provider's API — internal Riven UUIDs don't exist in that context. The pipeline looks up `source_external_id` in the entity table and writes the resolved relationship after all entities are upserted.

**What happens if a target doesn't exist?** Pass 2 resolution is best-effort and per-record. If `hs-company-456` hasn't synced yet (e.g., a company sync is in progress), the relationship is skipped for this cycle and will be picked up on the next sync run.

---

## 5. Metadata and Actions (`_nango_metadata`)

Nango appends `_nango_metadata` to every record. The Riven pipeline reads this to determine upsert vs delete semantics.

```typescript
interface NangoMetadata {
  lastAction: 'ADDED' | 'UPDATED' | 'DELETED';
  firstSeenAt: string;      // ISO timestamp
  lastModifiedAt: string;   // ISO timestamp
  cursor: string | null;    // Used for incremental sync (modifiedAfter)
}
```

**Pipeline behavior by `lastAction`:**

| `lastAction` | Entity exists? | Pipeline action |
|---|---|---|
| `ADDED` | No | Create entity |
| `ADDED` | Yes | Update entity (idempotent) |
| `UPDATED` | Yes | Replace mapped attributes |
| `UPDATED` | No | Create entity (treat as ADD) |
| `DELETED` | Yes | Soft-delete entity |
| `DELETED` | No | No-op |

**Incremental sync and `cursor`:** For initial syncs, `cursor` is null. For incremental runs, Nango sets `cursor` to the last seen modification timestamp. If your sync script supports `modifiedAfter` filtering, use `nango.lastSyncDate` to limit the response:

```typescript
const modifiedAfter = nango.lastSyncDate?.toISOString();
const response = await nango.get({
  endpoint: '/crm/v3/objects/contacts',
  params: modifiedAfter ? { modifiedAfter } : {},
});
```

**Error isolation:** The pipeline wraps each record in a per-record try-catch. A single malformed record does not fail the batch. Failed records are logged with the sync state. Check `IntegrationSyncStateEntity.consecutiveFailureCount` for per-entity-type health signals.

---

## Quick Reference

```typescript
// Minimal sync script template
import type { NangoSync } from '../../models';

export default async function fetchData(nango: NangoSync) {
  let cursor: string | undefined;

  do {
    const response = await nango.get({
      endpoint: '/api/v1/records',
      params: { cursor, limit: 200 },
    });

    const records = response.data.items.map((item) => ({
      id: item.externalId,               // dedup key
      name: item.name,
      relatedEntities: item.relatedIds,  // external IDs, array
    }));

    await nango.batchSave(records, 'ModelName');  // checkpoint

    cursor = response.data.nextCursor;
  } while (cursor);
}
```

**Checklist before shipping a sync script:**

- [ ] Model name in `nango.yaml` matches `CatalogEntityTypeEntity.key` exactly
- [ ] Every record has the dedup field populated (`id` or custom `_externalIdField`)
- [ ] Relationship fields are arrays of external IDs (not UUIDs)
- [ ] `batchSave` called every 100–500 records for checkpointing
- [ ] `track_deletes: true` set if soft-delete propagation is needed
- [ ] Incremental sync uses `nango.lastSyncDate` to avoid full re-fetch on every run
