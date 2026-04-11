---
Created: 2026-03-07
Domains:
  - "[[riven/docs/system-design/domains/Storage/Storage]]"
tags:
  - architecture/subdomain
---
# Subdomain: Provider Adapters

## Overview

Provider Adapters implements the strategy pattern for pluggable storage backends. A single [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/StorageProvider]] interface defines the contract for file operations (upload, download, delete, exists, presigned URLs, health check). Three concrete implementations — [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/LocalStorageProvider]], [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/S3StorageProvider]], and [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/SupabaseStorageProvider]] — are activated at runtime via `@ConditionalOnProperty("storage.provider")`. Only one provider bean is active at any time.

---

## Components

| Component | Purpose | Type |
|---|---|---|
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/StorageProvider]] | Interface defining the storage backend contract | Interface |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/LocalStorageProvider]] | Filesystem-based storage with path traversal prevention | Service |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/S3StorageProvider]] | AWS S3 / S3-compatible storage with presigned URL support | Service |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/SupabaseStorageProvider]] | Supabase Storage API implementation with presigned URL support | Service |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/StorageConfigurationProperties]] | Typed configuration for all providers, signed URLs, and presigned uploads | Configuration |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Storage/Provider Adapters/S3Configuration]] | Conditional S3Client bean creation | Configuration |

---

## Technical Debt

| Issue | Impact | Effort |
|---|---|---|
| S3 and Supabase providers use `runBlocking` to bridge suspend calls | Blocks servlet threads under load | Med |

---

## Recent Changes

| Date | Change | Feature/ADR |
|---|---|---|
| 2026-03-07 | Initial provider adapter implementation | Storage domain |
