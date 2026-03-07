---
tags:
  - layer/configuration
  - component/active
  - architecture/component
Created: 2026-03-07
Domains:
  - "[[Storage]]"
---
# StorageConfigurationProperties

Part of [[Provider Adapters]]

## Purpose

Typed `@ConfigurationProperties(prefix = "storage")` binding for all storage provider settings, signed URL configuration, and presigned upload configuration.

---

## Responsibilities

- Bind `storage.*` YAML/env properties to typed Kotlin data classes
- Provide default values for all configuration properties
- Serve as the single source of truth for storage configuration across all providers and services

---

## Dependencies

- None (pure data class with Spring binding)

## Used By

- [[StorageService]] — presigned upload expiry
- [[SignedUrlService]] — signed URL secret and expiry settings
- [[LocalStorageProvider]] — `local.basePath`
- [[S3StorageProvider]] — `s3.bucket`
- [[SupabaseStorageProvider]] — `supabase.bucket`
- [[S3Configuration]] — `s3.region`, `s3.accessKeyId`, `s3.secretAccessKey`, `s3.endpointUrl`

---

## Key Logic

**Structure:** Top-level data class with nested data classes for each concern:

```
storage:
  provider: local|s3|supabase        # default: "local"
  local:
    base-path: ./storage             # filesystem path for local provider
  signed-url:
    secret: ${STORAGE_SIGNED_URL_SECRET}  # HMAC signing secret
    default-expiry-seconds: 3600     # 1 hour default
    max-expiry-seconds: 86400        # 24 hour maximum
  presigned-upload:
    expiry-seconds: 900              # 15 minutes for direct upload URLs
  supabase:
    bucket: riven-storage            # Supabase Storage bucket name
  s3:
    bucket: riven-storage            # S3 bucket name
    region: us-east-1                # AWS region
    access-key-id: ${STORAGE_S3_ACCESS_KEY_ID}
    secret-access-key: ${STORAGE_S3_SECRET_ACCESS_KEY}
    endpoint-url: (optional)         # for S3-compatible services
```

**Provider selection:** The `provider` field determines which `StorageProvider` implementation is activated. Only the matching provider's nested config needs to be populated.

---

## Gotchas

- **Secret in dev config:** `signed-url.secret` defaults to `"dev-secret-change-in-production"`. This must be overridden via environment variable in production deployments.
- **S3 endpoint-url:** When set, [[S3Configuration]] forces path-style access (`forcePathStyle = true`). This is required for S3-compatible services like MinIO, Cloudflare R2, and DigitalOcean Spaces.
- **S3 credentials default to empty strings:** `accessKeyId` and `secretAccessKey` default to `""`. If the S3 provider is active without these set, authentication will fail at runtime.

---

## Related

- [[S3Configuration]]
- [[LocalStorageProvider]]
- [[S3StorageProvider]]
- [[SupabaseStorageProvider]]
