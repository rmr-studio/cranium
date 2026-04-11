---
tags:
  - adr/accepted
  - architecture/decision
Created: 2026-03-06
Updated: 2026-03-06
---
# ADR-005: Strategy Pattern with Conditional Bean Selection for Storage Providers

---

## Context

The platform requires pluggable file storage backends to support multiple deployment contexts:

- **Self-hosted deployments** use local filesystem storage to avoid external service dependencies
- **Cloud deployments** use Supabase Storage or S3-compatible backends for scalability and managed infrastructure
- **Development environments** use local filesystem for simplicity

Only one storage backend should be active per deployment — there is no use case for writing to multiple backends simultaneously or switching backends at runtime. The active backend is a deployment-time decision controlled by environment configuration.

The storage layer must expose a uniform interface to consuming services (`StorageService`, `StorageController`) so that business logic is completely decoupled from the underlying storage implementation. Adding a new storage backend should require only implementing the interface and setting a configuration property — no changes to existing services or controllers.

---

## Decision

Use the **Strategy pattern** implemented via Spring's `@ConditionalOnProperty` bean selection:

1. Define a `StorageProvider` interface in `riven.core.models.storage` with blocking methods: `upload`, `download`, `delete`, `exists`, `generateSignedUrl`, `healthCheck`.

2. Each concrete provider implements `StorageProvider` and is annotated with `@ConditionalOnProperty(name = ["storage.provider"], havingValue = "<provider-name>")`. Only one provider bean is instantiated at runtime based on the `storage.provider` configuration property.

3. All consuming services depend on the `StorageProvider` interface via constructor injection. They never reference concrete provider classes.

4. The `storage.provider` property defaults to `"local"`. Available values: `local` (Phase 1), `supabase` and `s3` (Phase 2).

Example:

```kotlin
// Interface
interface StorageProvider {
    fun upload(workspaceId: UUID, storageKey: String, content: InputStream, contentType: String): StorageResult
    fun download(storageKey: String): DownloadResult
    fun delete(storageKey: String)
    fun exists(storageKey: String): Boolean
    fun generateSignedUrl(storageKey: String, expirySeconds: Long): String
    fun healthCheck(): Boolean
}

// Concrete provider
@Service
@ConditionalOnProperty(name = ["storage.provider"], havingValue = "local")
class LocalStorageProvider(
    private val storageConfig: StorageConfigurationProperties,
    private val logger: KLogger
) : StorageProvider {
    // filesystem implementation
}
```

---

## Rationale

- **Single active provider per deployment** matches the operational model — a self-hosted instance uses local storage, a cloud instance uses Supabase or S3. There is no scenario where both are active simultaneously.
- **`@ConditionalOnProperty` is idiomatic Spring** — it is the standard mechanism for conditional bean registration. It is well-understood, well-documented, and integrates cleanly with Spring's dependency injection lifecycle.
- **Zero runtime overhead** — the conditional is evaluated once at startup during bean registration. At runtime, there is no factory lookup, no if/else branching, no reflection. The injected bean is the concrete provider directly.
- **Adding a new provider is mechanical** — implement the interface, annotate with `@ConditionalOnProperty(havingValue = "new-provider")`, done. No changes to `StorageService`, `StorageController`, or any configuration class.
- **The interface is designed against the S3 object model** (lowest common denominator) — upload takes a key and stream, download returns a stream, delete takes a key. This ensures any object-storage-compatible backend can implement it.

---

## Alternatives Considered

### Option 1: Pure If/Else Factory

A `StorageProviderFactory` bean that reads `storage.provider` from configuration and returns the appropriate implementation via if/else or when() branching.

- **Pros:** Simple to understand. All provider selection logic in one place.
- **Cons:** Every new provider requires modifying the factory. The factory must hold references to (or know how to construct) every provider, even those not in use. Violates the open-closed principle.
- **Why rejected:** `@ConditionalOnProperty` achieves the same result with zero custom code and no modification required when adding providers.

### Option 2: Abstract Class Hierarchy

An abstract `AbstractStorageProvider` base class with shared logic (e.g., key validation, logging), concrete subclasses for each provider.

- **Pros:** Shared logic avoids duplication across providers.
- **Cons:** Inheritance couples providers to the base class. Changes to the abstract class affect all providers. Shared logic is minimal in practice — providers differ in their I/O implementation, not in surrounding logic. Kotlin's single inheritance limits future flexibility.
- **Why rejected:** The `StorageProvider` interface is sufficient. Shared logic (content validation, signed URL generation) is in separate services (`ContentValidationService`, `SignedUrlService`), not in the provider. Composition over inheritance.

### Option 3: Runtime Provider Switching via Database Configuration

Store the active provider in a database configuration table, allowing workspace-level or system-level runtime switching without restart.

- **Pros:** Maximum flexibility. Could support per-workspace storage backends (e.g., workspace A uses S3, workspace B uses local).
- **Cons:** Significant complexity — requires lazy provider initialization, connection pool management for multiple backends, handling of in-flight requests during switches. Per-workspace storage creates operational complexity (monitoring, debugging, capacity planning across providers). No current use case justifies this complexity.
- **Why rejected:** Premature flexibility. The deployment model is one provider per instance. If per-workspace storage becomes a requirement, it can be layered on top of the interface pattern later by introducing a `StorageProviderRouter` that delegates to workspace-specific providers.

---

## Consequences

### Positive

- Adding a new storage provider requires only a new class implementing `StorageProvider` with a `@ConditionalOnProperty` annotation — zero changes to existing code
- `StorageService` and `StorageController` are fully decoupled from storage implementation details
- Configuration is a single property (`storage.provider`), trivially overridable via environment variables
- Testing is straightforward — mock the `StorageProvider` interface in service tests

### Negative

- Only one provider is active per JVM instance — cannot support per-workspace provider selection without additional abstraction
- Misconfiguration (setting `storage.provider` to a value with no matching bean) causes startup failure — mitigated by defaulting to `"local"` and clear error messages
- `@ConditionalOnProperty` evaluation is opaque at runtime — if the wrong provider activates, debugging requires checking Spring bean registration logs

### Neutral

- The `StorageProvider` interface methods are blocking (non-suspend) to match the synchronous Spring MVC architecture. If the application migrates to WebFlux in the future, the interface would need a reactive variant.
- `LocalStorageProvider.generateSignedUrl()` throws `UnsupportedOperationException` because local filesystem has no native signed URL mechanism. Signed URL generation is delegated to `SignedUrlService` instead. Cloud providers may implement `generateSignedUrl` natively.

---

## Related

- [[riven/docs/system-design/feature-design/2. Planned/Provider-Agnostic File Storage]] -- Feature design using this pattern
- [[riven/docs/system-design/feature-design/_Sub-Domain Plans/File Storage]] -- Sub-domain plan for the Storage domain
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/decisions/ADR-006 HMAC-Signed Download Tokens for File Access]] -- Signed URL mechanism that complements the provider abstraction
