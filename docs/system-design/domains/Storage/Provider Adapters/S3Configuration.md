---
tags:
  - layer/configuration
  - component/active
  - architecture/component
Created: 2026-03-07
Domains:
  - "[[riven/docs/system-design/domains/Storage/Storage]]"
---
# S3Configuration

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/Provider Adapters]]

## Purpose

Conditional S3Client bean creation. Only active when `storage.provider=s3`.

---

## Responsibilities

- Create and configure an `S3Client` bean from [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/StorageConfigurationProperties]]
- Support S3-compatible services via optional endpoint URL override with path-style access
- Manage S3Client lifecycle — close on Spring context shutdown via `DisposableBean`
- Provide static AWS credentials via a private inner `CredentialsProvider`

---

## Dependencies

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/StorageConfigurationProperties]] — `s3.region`, `s3.accessKeyId`, `s3.secretAccessKey`, `s3.endpointUrl`

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/S3StorageProvider]] — injects the `S3Client` bean

---

## Key Logic

**Conditional activation:** `@ConditionalOnProperty(name = ["storage.provider"], havingValue = "s3")` ensures this configuration class and its beans are only loaded when S3 is the active provider.

**S3Client construction:**
- Region from `storageConfig.s3.region`
- Endpoint URL parsed via `Url.parse()` when `endpointUrl` is non-null
- `forcePathStyle = true` when `endpointUrl` is set (required for MinIO, R2, Spaces)
- Static credentials via `StaticS3CredentialsProvider` (private inner class implementing `CredentialsProvider`)

**Lifecycle:** Implements `DisposableBean.destroy()` to call `s3Client.close()` on shutdown, releasing HTTP connections and thread pools.

---

## Gotchas

- **Path-style access is automatic:** When `endpointUrl` is set, `forcePathStyle` is enabled. This is correct for S3-compatible services but would be incorrect if pointing at actual AWS S3 via a custom endpoint.
- **Credentials are static:** No credential rotation or IAM role assumption. The `StaticS3CredentialsProvider` returns the same access key pair for the lifetime of the application.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/S3StorageProvider]]
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/StorageConfigurationProperties]]
