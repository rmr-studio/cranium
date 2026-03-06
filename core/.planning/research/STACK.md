# Technology Stack

**Project:** Provider-Agnostic Storage Abstraction
**Researched:** 2026-03-05

## Recommended Stack

### Core Abstraction (No Library -- Custom Interface)

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Custom `StorageProvider` interface | N/A | Provider abstraction contract | The project needs a thin interface (upload, download, delete, list, presignedUrl, signedUrl). No existing Spring library provides a multi-backend storage abstraction that covers Supabase + S3 + local filesystem without imposing its own opinions. Spring Content is the closest but it is JCR/CMIS-oriented and adds unnecessary complexity for blob storage. A 6-method interface is simpler, testable, and fully under your control. |
| Spring `@ConditionalOnProperty` | Spring Boot 3.5.3 | Provider selection at startup | Already used in the codebase (PostHog service pattern). Env-var `riven.storage.provider=supabase|s3|local` selects which `StorageProvider` bean is instantiated. Zero custom framework needed. |
| `@ConfigurationProperties` | Spring Boot 3.5.3 | Typed provider config | Already used (`ApplicationConfigurationProperties`). Add `StorageConfigurationProperties` with nested provider-specific config classes. |

**Confidence: HIGH** -- This is the established pattern in the codebase (PostHogService interface + PostHogServiceImpl + NoOpPostHogService + @ConditionalOnProperty). No external abstraction library needed.

### S3 Provider

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| AWS SDK for Kotlin (`aws.sdk.kotlin:s3`) | 1.6.16 | S3 operations (putObject, getObject, deleteObject, listObjectsV2, presigning) | Native Kotlin SDK with suspend functions and DSL builders. Supports custom `endpointUrl` for S3-compatible services (MinIO, Cloudflare R2, DigitalOcean Spaces). Coroutine-native, unlike the Java SDK which requires adapter wrappers. The project already has `kotlinx-coroutines-reactor` in build.gradle. |
| AWS SDK for Kotlin (`aws.sdk.kotlin:s3-presigner`) | 1.6.16 | Presigned URL generation | Built-in presigning via `presignGetObject` / `presignPutObject` extension methods. No separate library needed -- included in the S3 module. |

**Confidence: MEDIUM** -- Version 1.6.16 was the latest found via web search. Verify on Maven Central before adding to build.gradle. The SDK itself is GA and well-documented.

### Supabase Provider

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| supabase-kt BOM (`io.github.jan-tennert.supabase:bom`) | 3.1.4 (current in build.gradle) | Supabase Storage operations | Already in the project dependency tree. `storage-kt` module already installed. Provides `upload()`, `download()`, `createSignedUrl()`, `createSignedUploadUrl()`, `delete()`, `list()`. Upgrade to 3.4.1 only if needed -- the current version works and avoids unnecessary churn. |

**Confidence: HIGH** -- Already in the project, already configured, already has a `SupabaseConfiguration` bean.

### Local Filesystem Provider

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| `java.nio.file` (JDK 21) | JDK 21 | Local file read/write/delete/list | Zero dependencies. Standard library. For dev/simple self-hosted setups. Store files at a configurable base path (e.g., `./storage/` or `/var/riven/storage/`). |
| Spring `ResourceLoader` | Spring Boot 3.5.3 | Serve files via controller | Already available. Can serve local files as `Resource` responses for download endpoints. |

**Confidence: HIGH** -- Standard JDK APIs, no external dependency.

### File Validation

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Apache Tika (`org.apache.tika:tika-core`) | 3.0.0 | Content-type detection via magic bytes | Do NOT trust `Content-Type` headers from clients -- they can be spoofed. Tika inspects actual file bytes to determine true MIME type. `tika-core` is lightweight (~2MB), no Tika Server needed. Use it to validate uploaded files match allowed types before storing. |

**Confidence: HIGH** -- Industry standard for content detection in JVM. Widely used in Spring Boot projects.

### Multipart Upload Handling

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Spring `MultipartFile` | Spring Boot 3.5.3 | Proxied upload handling | Already available via `spring-boot-starter-web`. Configure `spring.servlet.multipart.max-file-size` and `spring.servlet.multipart.max-request-size` in application.yml. |

**Confidence: HIGH** -- Built into Spring Boot.

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| Storage abstraction | Custom interface | Spring Content (spring-content-s3, spring-content-fs) | Spring Content is designed for content management (JCR/CMIS patterns) with repository-style annotations. Overkill for blob storage. Adds a framework with its own opinions about entities, stores, and content associations. The project needs a simple put/get/delete/list/presign interface, not a content management system. |
| Storage abstraction | Custom interface | Apache jclouds BlobStore | jclouds is effectively in maintenance mode. Last release cadence slowed. Heavy dependency tree. Does not support Supabase. The S3 compatibility covers MinIO/R2 anyway. |
| S3 SDK | AWS SDK for Kotlin | AWS SDK for Java v2 (`software.amazon.awssdk:s3`) | Java SDK works but requires `CompletableFuture` wrappers or `kotlinx-coroutines-jdk8` bridge for async. Kotlin SDK provides native suspend functions, DSL builders, and idiomatic Kotlin API. Since coroutines are already in the project, use the Kotlin-native SDK. |
| S3 SDK | AWS SDK for Kotlin | MinIO Java SDK (`io.minio:minio`) | MinIO SDK is S3-compatible but is a third-party client. AWS SDK for Kotlin already supports custom endpoints for MinIO/R2/Spaces. Using the official AWS SDK means one client for all S3-compatible backends. MinIO SDK adds a redundant dependency. |
| Content detection | Apache Tika | Manual `Content-Type` header check | Headers can be spoofed. A user can upload a `.exe` with `Content-Type: image/png`. Tika reads magic bytes from the actual file content. |
| Content detection | Apache Tika | `java.net.URLConnection.guessContentTypeFromStream` | JDK built-in but supports far fewer MIME types and is less reliable than Tika. |

## Dependencies to Add

```kotlin
// Storage - S3 Provider
implementation("aws.sdk.kotlin:s3:1.6.16")

// Storage - Content Type Detection
implementation("org.apache.tika:tika-core:3.0.0")
```

**Dependencies already present (no changes needed):**
```kotlin
// Supabase (already in build.gradle.kts)
implementation(platform("io.github.jan-tennert.supabase:bom:3.1.4"))
implementation("io.github.jan-tennert.supabase:storage-kt")
implementation("io.ktor:ktor-client-cio:3.0.0")
```

**No new dependencies for local filesystem provider** -- uses JDK `java.nio.file`.

## Configuration Properties Shape

```yaml
riven:
  storage:
    provider: supabase  # supabase | s3 | local
    max-file-size: 50MB
    allowed-content-types:
      - image/jpeg
      - image/png
      - image/webp
      - image/gif
      - image/svg+xml
      - application/pdf
      - application/vnd.openxmlformats-officedocument.wordprocessingml.document
      - application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
    signed-url-expiry: 3600  # seconds
    s3:
      bucket: riven-storage
      region: us-east-1
      access-key-id: ${S3_ACCESS_KEY_ID:}
      secret-access-key: ${S3_SECRET_ACCESS_KEY:}
      endpoint: ${S3_ENDPOINT:}  # empty = AWS, set for MinIO/R2/Spaces
      path-style-access: false  # true for MinIO
    local:
      base-path: ./storage
      serve-base-url: ${ORIGIN_API_URL}/api/v1/storage/files
```

Supabase config already exists in `ApplicationConfigurationProperties` -- no new properties needed for that provider.

## Sources

- [AWS SDK for Kotlin -- S3Client](https://docs.aws.amazon.com/sdk-for-kotlin/api/latest/s3/aws.sdk.kotlin.services.s3/-s3-client/)
- [AWS SDK for Kotlin -- Presign Requests](https://docs.aws.amazon.com/sdk-for-kotlin/latest/developer-guide/presign-requests.html)
- [AWS SDK for Kotlin -- Custom Endpoints](https://docs.aws.amazon.com/sdk-for-kotlin/latest/developer-guide/config-endpoint.html)
- [AWS SDK for Kotlin -- Maven Central](https://mvnrepository.com/artifact/aws.sdk.kotlin/s3)
- [Supabase Kotlin -- Storage Signed URLs](https://supabase.com/docs/reference/kotlin/storage-from-createsignedurl)
- [Supabase Kotlin -- GitHub](https://github.com/supabase-community/supabase-kt)
- [Cloudflare R2 -- S3 API Compatibility](https://developers.cloudflare.com/r2/api/s3/api/)
- [Spring Boot File Upload Guide](https://spring.io/guides/gs/uploading-files/)
