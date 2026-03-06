# Research Summary: Provider-Agnostic Storage Abstraction

**Domain:** File storage abstraction layer (Spring Boot/Kotlin backend)
**Researched:** 2026-03-05
**Overall confidence:** HIGH

## Executive Summary

The storage abstraction requires no new framework -- only two new dependencies (AWS SDK for Kotlin S3, Apache Tika for content detection) added to an existing Spring Boot 3.5.3/Kotlin 2.1.21 stack. The codebase already has supabase-kt with storage-kt installed and configured, an empty `StorageService` shell, and a proven provider-selection pattern via `@ConditionalOnProperty` (PostHog interface + impl + no-op).

The architecture is a Strategy Pattern: a `StorageProvider` interface with three implementations (Supabase, S3, local filesystem), wired as a singleton bean based on the `riven.storage.provider` environment variable. The service layer handles all business logic (workspace scoping, path construction, content validation, metadata persistence, activity logging), keeping provider adapters thin. File metadata lives in PostgreSQL regardless of storage provider, enabling workspace-scoped queries and audit trails.

The AWS SDK for Kotlin (not the Java SDK) is the right choice for S3 operations because the project already uses coroutines and the Kotlin SDK provides native suspend functions with DSL builders. Custom `endpointUrl` configuration gives automatic support for MinIO, Cloudflare R2, and DigitalOcean Spaces through the same adapter. Apache Tika provides content-type detection via magic bytes, which is essential -- trusting client-provided Content-Type headers is a known security vulnerability.

The highest-risk design decision is the `StorageProvider` interface contract. Designing it against filesystem semantics (the local adapter) rather than object storage semantics (S3's key-prefix model) is the most common mistake in storage abstractions and the hardest to fix retroactively. The interface must be designed for the lowest common denominator first.

## Key Findings

**Stack:** AWS SDK for Kotlin (S3) + existing supabase-kt + JDK NIO (local) + Apache Tika (content detection), wired via Spring @ConditionalOnProperty. Two new dependencies total.

**Architecture:** Strategy Pattern with conditional bean selection -- mirrors the existing PostHog provider pattern in the codebase. Thin adapters, fat service layer, metadata in PostgreSQL.

**Critical pitfall:** Interface designed from first adapter (Supabase) rather than lowest common denominator (S3 object model). Design all three adapter signatures in pseudocode before finalizing the interface contract.

## Implications for Roadmap

Based on research, suggested phase structure:

1. **Interface Design + Foundation** - Define StorageProvider interface, exception hierarchy, configuration properties, metadata entity, enums
   - Addresses: Provider abstraction contract, workspace-scoped path convention, configuration properties
   - Avoids: Leaky abstraction pitfall (design against all three providers simultaneously)

2. **Local Filesystem Adapter + Service Layer + Controller** - Build the simplest adapter first, then the service orchestration and REST API
   - Addresses: Proxied upload/download/delete/list, content-type validation, metadata persistence, activity logging
   - Avoids: Memory exhaustion (stream from day one), path traversal (sanitize in service layer)

3. **Supabase Adapter** - Implement against existing supabase-kt dependency, no new dependencies needed
   - Addresses: Supabase Storage integration, signed URL generation
   - Avoids: Interface-from-first-adapter trap (interface already finalized in Phase 1)

4. **S3 Adapter** - Add AWS SDK for Kotlin dependency, implement S3 adapter with custom endpoint support
   - Addresses: S3/MinIO/R2/Spaces support, presigned URL generation
   - Avoids: Provider-specific exception leaking (map to domain exceptions)

5. **Presigned Upload Flow** - Two-step upload: request URL, direct upload, confirm
   - Addresses: Large file uploads without backend proxy, presigned URL security
   - Avoids: Presigned URL security mismanagement (constrain content-type, short expiry)

6. **Frontend Integration** - StorageProvider TypeScript interface mirroring AuthProvider pattern
   - Addresses: Client-side upload/download, progress indication, presigned upload flow
   - Avoids: Frontend talking directly to storage providers

**Phase ordering rationale:**
- Interface first because it is the hardest to change and informs all adapter work
- Local adapter first because it requires zero external services and enables full-stack testing locally
- Supabase before S3 because the dependency already exists and it is the current production provider
- Presigned URLs last on the backend because the system works fully without them via proxied uploads
- Frontend last because it depends entirely on the backend API being stable

**Research flags for phases:**
- Phase 1: Needs careful design review -- interface contract is the single most consequential decision
- Phase 2: Standard patterns, but must get multipart streaming and path sanitization right from the start
- Phase 3: May need deeper research on supabase-kt storage-kt API specifics (bucket policies, self-hosted differences)
- Phase 4: Standard AWS SDK usage, well-documented
- Phase 5: Presigned URL security constraints need careful implementation
- Phase 6: Standard frontend patterns, low risk

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Only two new dependencies. AWS SDK for Kotlin is GA, Tika is mature. Supabase already present. |
| Features | HIGH | Clear table stakes vs differentiators. MVP scope well-defined in PROJECT.md. |
| Architecture | HIGH | Strategy Pattern + @ConditionalOnProperty is proven in this exact codebase (PostHog). |
| Pitfalls | HIGH | Well-documented domain with extensive prior art. OWASP guidance covers security concerns. |

## Gaps to Address

- **AWS SDK for Kotlin version**: 1.6.16 was found via web search. Verify exact latest on Maven Central before adding to build.gradle.kts.
- **supabase-kt self-hosted differences**: Storage behavior may differ between cloud and self-hosted Supabase. Needs testing during Supabase adapter phase.
- **Coroutine integration with synchronous Spring MVC**: The StorageProvider interface uses suspend functions but the rest of the app is synchronous. Need to decide on `runBlocking` vs reactive bridge in Phase 1.
- **Apache Tika version**: 3.0.0 found via training data. Verify current version before adding dependency.
