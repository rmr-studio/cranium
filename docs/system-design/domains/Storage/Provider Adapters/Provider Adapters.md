---
Created: 2026-03-07
Domains:
  - "[[Storage]]"
tags:
  - architecture/subdomain
---
# Subdomain: Provider Adapters

## Overview

Provider Adapters implements the strategy pattern for pluggable storage backends. A single [[StorageProvider]] interface defines the contract for file operations (upload, download, delete, exists, presigned URLs, health check). Three concrete implementations — [[LocalStorageProvider]], [[S3StorageProvider]], and [[SupabaseStorageProvider]] — are activated at runtime via `@ConditionalOnProperty("storage.provider")`. Only one provider bean is active at any time.

---

## Components

| Component | Purpose | Type |
|---|---|---|
| [[StorageProvider]] | Interface defining the storage backend contract | Interface |
| [[LocalStorageProvider]] | Filesystem-based storage with path traversal prevention | Service |
| [[S3StorageProvider]] | AWS S3 / S3-compatible storage with presigned URL support | Service |
| [[SupabaseStorageProvider]] | Supabase Storage API implementation with presigned URL support | Service |
| [[StorageConfigurationProperties]] | Typed configuration for all providers, signed URLs, and presigned uploads | Configuration |
| [[S3Configuration]] | Conditional S3Client bean creation | Configuration |

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
