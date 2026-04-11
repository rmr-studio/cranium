---
tags:
  - layer/service
  - component/active
  - architecture/component
  - tools/nango
Created: 2026-03-18
Domains:
  - "[[riven/docs/system-design/domains/Integrations/Integrations]]"
---
# NangoClientWrapper

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Connection Management/Connection Management]]

## Purpose

Spring WebClient-based HTTP client for the Nango REST API. Provides connection management operations (get, list, delete), sync operations (fetch records, trigger sync), and standardized error handling with retry logic for transient failures and rate limiting.

---

## Responsibilities

- Get a specific Nango connection by provider config key and connection ID
- List all Nango connections
- Delete a Nango connection (used during disconnect flow)
- Fetch paginated records from Nango for a specific sync model
- Trigger sync execution for specific sync names on a connection
- Handle HTTP error responses with typed exceptions (`NangoApiException`, `RateLimitException`, `TransientNangoException`)
- Retry transient failures with exponential backoff (3 retries, 2-second base)
- Validate that the Nango secret key is configured before API calls

---

## Dependencies

- `@Qualifier("nangoWebClient") WebClient` — Pre-configured WebClient with Authorization header and 16MB codec buffer (from `NangoClientConfiguration`)
- `NangoConfigurationProperties` — Secret key validation

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Connection Management/IntegrationConnectionService]] — Connection deletion during disconnect flow
- Future sync orchestration services — Record fetching and sync triggering (Phase 3+)

---

## Key Logic

### Error handling hierarchy

All API responses pass through `withNangoErrorHandling()`, which maps HTTP status codes to typed exceptions:

| HTTP Status | Exception | Retryable |
|---|---|---|
| 429 | `RateLimitException` | Yes — retried with backoff |
| 5xx | `TransientNangoException` | Yes — retried with backoff |
| 4xx (non-429) | `NangoApiException` | No — fails immediately |

Error response bodies are deserialized as `NangoErrorResponse` to extract Nango's error message.

### Retry logic

`withNangoRetry()` applies Reactor `Retry.backoff()`:

- **Max retries:** 3
- **Base backoff:** 2 seconds (exponential)
- **Retryable exceptions:** `RateLimitException`, `TransientNangoException`
- **Non-retryable:** `NangoApiException` (4xx client errors)

### Records API

`fetchRecords()` calls Nango's `/records` endpoint with headers (`Connection-Id`, `Provider-Config-Key`) and optional query parameters:

- `model` — Required sync model name (e.g. "Contact", "Deal")
- `cursor` — Pagination cursor from previous response
- `modified_after` — ISO timestamp for incremental sync
- `limit` — Maximum records per page

Returns `NangoRecordsPage` with records and optional `nextCursor` for pagination. Caller is responsible for pagination loop.

### Sync trigger

`triggerSync()` calls `POST /sync/trigger` with a `NangoTriggerSyncRequest` containing provider config key, optional connection ID, and list of sync names.

---

## Public Methods

### `getConnection(providerConfigKey: String, connectionId: String): NangoConnection`

Get a specific connection by provider config key and connection ID. Throws on API errors.

### `listConnections(): NangoConnectionList`

List all Nango connections. Returns empty list on empty response.

### `deleteConnection(providerConfigKey: String, connectionId: String)`

Delete a connection. Used during the disconnect flow. Throws on API errors.

### `fetchRecords(providerConfigKey: String, connectionId: String, model: String, cursor: String?, modifiedAfter: String?, limit: Int?): NangoRecordsPage`

Fetch a single page of records for a sync model. Caller handles pagination via `nextCursor`.

### `triggerSync(providerConfigKey: String, connectionId: String?, syncs: List<String>)`

Trigger sync execution for specified sync names. If `connectionId` is null, triggers for all connections.

---

## Gotchas

> **Uses module-level `KotlinLogging.logger {}` instead of injected KLogger.** This is a known inconsistency (see CLAUDE.md Known Inconsistencies). The service predates the KLogger convention.

> **`ensureConfigured()` is called on every API method.** This validates the Nango secret key is set before making requests. It does NOT check if the key is valid — invalid keys produce 401 from Nango, caught as `NangoApiException`.

> **WebClient is blocking (`.block()`).** Despite using Reactor under the hood, all methods call `.block()` to return synchronous results. This is intentional — the calling services are not reactive.

> **Record fetching uses headers not path parameters.** Nango's `/records` endpoint requires `Connection-Id` and `Provider-Config-Key` as headers, not query parameters. This differs from the connection endpoints which use path/query parameters.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Connection Management/IntegrationConnectionService]] — Primary consumer for connection operations
- `NangoClientConfiguration` — WebClient bean configuration with auth header
- `NangoConfigurationProperties` — Secret key and base URL configuration
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Integrations/Connection Management/Connection Management]] — Parent subdomain

---

## Changelog

### 2026-03-18

- Added `fetchRecords()` method for paginated record retrieval from Nango
- Added `triggerSync()` method for triggering sync execution
- Documented full API surface including existing connection management methods

### 2025-07-17

- Initial implementation — `getConnection()`, `listConnections()`, `deleteConnection()` with error handling and retry logic
