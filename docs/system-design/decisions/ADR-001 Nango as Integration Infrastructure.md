---
tags:
  - adr/proposed
  - architecture/decision
Created: 2026-02-13
---
# ADR-001: Nango as Integration Infrastructure

---

## Context

The Entity Integration Sync sub-domain requires connecting to multiple third-party SaaS tools (HubSpot, Salesforce, Stripe, Zendesk, Intercom, Gmail) for OAuth-based authentication, token management, webhook handling, and data syncing. Building this infrastructure from scratch is a massive undertaking -- each provider has unique OAuth flows (OAuth 1.0a, 2.0, API keys), token refresh and expiry patterns, rate limits, webhook payload formats, and error handling quirks. The existing Kotlin/Spring Boot 3.5.3 application needs a way to manage these integrations without building bespoke connector logic for each provider.

The v1 roadmap targets six integrations across four categories (CRM, payments, support, email), with the integration catalog designed to scale to additional providers. The core engineering value of the system lies in identity resolution, source tracking, and schema mapping -- not in OAuth plumbing. Every hour spent building OAuth infrastructure is an hour not spent on the differentiating features.

---

## Decision

Use Nango as the centralized integration infrastructure layer. The system communicates with Nango via its REST API using a Spring WebClient wrapper (`NangoClientWrapper`). Nango handles OAuth flows, token refresh and encrypted storage, webhook management, rate limiting, and provides a unified connection model across 600+ supported integrations.

No official Java/Kotlin SDK exists for Nango -- integration is via direct REST API calls using Spring WebClient with retry logic and rate limit handling. The wrapper uses blocking `.block()` calls since the rest of the application is servlet-based Spring MVC; WebClient is chosen for its retry and error handling capabilities, not for reactive streams.

---

## Rationale

- **Nango commoditizes OAuth complexity.** Each provider has unique flows (OAuth 1.0a, 2.0, API keys), token expiry patterns, and refresh mechanisms. Nango maintains these per-provider configurations so the application does not need to.
- **Handles token storage and encryption.** Credentials never touch our database -- only Nango connection IDs are stored in the `integration_connections` table. This reduces the security surface area and eliminates the need for an application-level secrets vault for integration credentials.
- **Built-in rate limit handling and retry logic at the infrastructure level.** Provider-specific rate limit patterns (sliding window, token bucket, concurrent request limits) are handled by Nango rather than implemented per-provider in application code.
- **Supports 600+ integrations with maintained provider configurations.** Provider API changes, new OAuth scopes, and breaking changes are handled by Nango's maintained configuration library. The application only needs to add a row to `integration_definitions` to support a new provider.
- **Webhook management with signature verification built in.** Nango standardizes webhook delivery across providers and handles per-provider signature verification formats.
- **Reduces integration time from weeks per provider to days.** The primary engineering effort shifts from integration plumbing (OAuth, tokens, webhooks) to domain-specific logic (schema mapping, identity resolution, source tracking).
- **Allows the team to focus on domain-specific value.** Identity resolution, conflict resolution, and schema mapping are the differentiating capabilities of the system. These are where engineering effort should concentrate.

---

## Alternatives Considered

### Option 1: Build Custom OAuth + Connection Management

- **Pros:** Full control over OAuth flows. No external service dependency. No vendor lock-in. Can optimize token refresh timing for each provider.
- **Cons:** Massive engineering effort (months of work). Must implement OAuth for each provider individually -- each with unique quirks (Salesforce's instance URLs, HubSpot's granular scopes, Stripe's API key rotation). Must build token refresh/rotation with provider-specific expiry handling. Must build webhook registration and signature verification per provider. Must implement rate limit detection and backoff per provider's specific rate limit scheme.
- **Why rejected:** The integration plumbing is not a differentiator. Identity resolution and schema mapping are where the system creates value. Building OAuth from scratch diverts months of engineering effort from core capabilities. With six v1 target integrations, the effort multiplies.

### Option 2: Use Merge.dev Unified API

- **Pros:** Provides unified API abstraction across CRM, ATS, and HRIS categories. Pre-built data models for common SaaS categories. Handles OAuth and sync infrastructure.
- **Cons:** Higher cost at scale. More opinionated data model -- Merge.dev defines a "Common Model" that may not map cleanly to our entity types and attribute schema. Less control over sync frequency and webhook handling. Vendor lock-in extends to their data model, not just their infrastructure. Limited control over which fields are synced and how raw data is exposed.
- **Why rejected:** Merge.dev's unified data model abstracts too much. The system needs raw provider data to apply its own schema mapping (via `IntegrationSchemaMappingEntity` with JSONPath extraction) and identity resolution (via signal extraction from raw fields). Their Common Model removes the control needed for custom field mapping and entity-level source tracking. The system's value proposition depends on transforming raw integration data through its own pipeline -- a pre-normalized unified API undermines that.

### Option 3: Use Paragon (Embedded Integration Platform)

- **Pros:** Embeddable connection UI components. Pre-built workflows for common integration patterns. Visual workflow builder for integration logic.
- **Cons:** Focus on embedded workflows and customer-facing integration UIs rather than backend data sync infrastructure. Less flexibility for custom sync pipelines. Pricing model oriented toward embedded use cases (per-connected-user). The visual workflow builder overlaps with the existing Temporal-based workflow engine.
- **Why rejected:** Paragon is designed for embedding integration workflows in customer-facing product UIs. The use case here is backend data sync with custom pipeline orchestration (schema mapping, identity resolution, provenance). The system already has Temporal for workflow orchestration. Nango's infrastructure-first approach -- providing OAuth, connections, and webhooks without prescribing how data flows through the application -- is a better architectural fit.

---

## Consequences

### Positive

- Dramatically reduces integration time per provider (days vs. weeks). Adding a new provider requires a database row in `integration_definitions` and a schema mapping definition, not a custom OAuth implementation.
- OAuth, token refresh, and rate limiting handled out of the box. No application-level token storage or refresh logic.
- Credentials never stored in the application database. Only Nango connection IDs are persisted in `integration_connections.nango_connection_id`. This reduces the security surface and eliminates the need for credential encryption at the application layer.
- 600+ pre-built provider configurations with maintained updates. When providers change their OAuth flows or API versions, Nango's maintained configuration library handles the update.
- Team can focus on domain-specific value: schema mapping (transforming provider payloads to entity attributes), identity resolution (matching incoming records to existing entities), and conflict resolution (handling competing updates from multiple sources).

### Negative

- External dependency on Nango as a SaaS service. If Nango experiences downtime, new OAuth connections cannot be established and webhook delivery may be delayed. Existing connections with valid tokens continue to work for API calls made directly.
- No official Java/Kotlin SDK exists. The team must maintain a custom REST API wrapper (`NangoClientWrapper`) that handles serialization, error mapping, retry logic, and rate limit detection. This wrapper must be updated when Nango's API evolves.
- Nango Cloud rate limits may constrain high-volume sync scenarios. For workspaces with many active integrations syncing simultaneously, Nango's API rate limits become the bottleneck rather than application-level capacity.
- Vendor lock-in for connection management. Migrating away from Nango would require rebuilding OAuth flows, token storage, and webhook management for every supported provider. The `integration_connections` table would need to store actual credentials rather than connection references.
- Nango webhook payload format may change without notice across API versions. The `NangoModels.kt` response classes use `@JsonIgnoreProperties(ignoreUnknown = true)` to mitigate this, but breaking changes to required fields would require application updates.

### Neutral

- Nango connection IDs stored in the `integration_connections` table are lightweight string references. If migration is ever needed, the table structure remains useful -- only the connection resolution mechanism changes.
- REST API integration via Spring WebClient is a standard pattern well-understood by the team. The same approach is used for workflow HTTP request actions (`WorkflowHttpRequestActionConfig`).
- `NangoClientWrapper` can be extended incrementally as Nango API features are needed. The v1 surface is minimal: `getConnection`, `listConnections`, `deleteConnection`. Additional methods (trigger sync, update metadata) can be added as the sync orchestration phase (Phase 4) is built.

---

## Implementation Notes

- **NangoClientWrapper service** at `riven.core.service.integration.NangoClientWrapper`. Provides `getConnection()`, `listConnections()`, and `deleteConnection()` methods wrapping Nango REST API calls.
- **Configuration via environment variables:** `NANGO_SECRET_KEY` (required for API authentication), `NANGO_BASE_URL` (defaults to `https://api.nango.dev`), `NANGO_WEBHOOK_SECRET` (for webhook signature verification). Bound via `NangoConfigurationProperties` with `@ConfigurationProperties(prefix = "riven.nango")`.
- **Uses qualified WebClient bean** (`@Qualifier("nangoWebClient")`) configured in `NangoClientConfiguration` to avoid conflicts with other HTTP clients in the application (e.g., Supabase, workflow HTTP actions).
- **Retry logic:** 3 retries with exponential backoff (2-second base interval), filtered on `RateLimitException`. Non-rate-limit errors (4xx, 5xx) are not retried -- they are mapped to `NangoApiException` with the HTTP status code for upstream error classification.
- **Runtime validation** of secret key via `ensureConfigured()` rather than startup validation. This allows the application to start without Nango configured (for existing deployments or environments where integration features are not yet enabled). The first actual API call will fail with a clear error message if the key is missing.
- **Custom exceptions:** `RateLimitException` (for 429 responses, triggers retry), `NangoApiException` (for other API errors, includes HTTP status code), `InvalidStateTransitionException` (for connection state machine violations, maps to 409 Conflict).
- **Connection lifecycle** managed by `IntegrationConnectionService` with a 10-state `ConnectionStatus` enum. State transitions validated via `canTransitionTo()` before any status update. See [[Integration Connection Lifecycle]] for the full state machine.

---

## Related

- [[Integration Access Layer]]
- [[Entity Integration Sync]]
- [[Integration Schema Mapping]]
- [[Integration Connection Lifecycle]]
