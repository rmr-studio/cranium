---
Created: 2026-02-06
Domains:
  - "[[riven/docs/system-design/domains/Knowledge/Knowledge]]"
tags:
  - domain/knowledge
  - tool/postgres
  - tool/temporal
---
# Subdomain: Enrichment Pipeline

## Overview

When entity data is created, updated, or synced from integrations, it flows through an async pipeline that enriches it with schema context and generates a vector embedding stored in pgvector. The pipeline runs through Temporal on a dedicated `enrichment.embed` task queue (isolated from workflow execution and identity matching so embedding-API latency cannot stall other workloads), uses the centralised `execution_queue` table with the new `ENRICHMENT` job type, and produces one row per entity in `entity_embeddings` (a `vector(1536)` column with an HNSW cosine index).

The embedding model is pluggable via `riven.enrichment.provider` — OpenAI by default, Ollama as an alternative for local development.

## Components

| Component | Purpose | Type |
| --------- | ------- | ---- |
| [[EnrichmentService]] | Pipeline orchestration: queue claim, batched context assembly, embedding storage | Service |
| [[SemanticTextBuilderService]] | Renders entity context into 6-section enriched text with progressive truncation under a 27 k character budget | Service |
| [[EnrichmentWorkflow]] | Temporal workflow orchestrating the 4-step pipeline with per-activity retry | Temporal Workflow |
| [[EnrichmentActivitiesImpl]] | Spring-managed activity layer delegating to services on the dedicated `enrichment.embed` queue | Temporal Activity |
| [[EmbeddingProvider]] | Pluggable embedding API client — OpenAI default, Ollama alternative, selected by `riven.enrichment.provider` | Service / Provider |
| [[EnrichmentClientConfiguration]] | `@Configuration` wiring qualified `WebClient` beans plus typed `@ConfigurationProperties` for both providers | Configuration |
| [[EntityEmbeddingEntity]] | JPA entity for `entity_embeddings` (`vector(1536)`, system-managed — no audit, no soft-delete) | Entity |
| [[EntityEmbeddingRepository]] | JPA repository — `findByEntityId`, `findByWorkspaceId`, `deleteByEntityId` (no similarity query yet) | Repository |

## Flows

| Flow | Type | Description |
| ---- | ---- | ----------- |
| [[Flow - Entity Enrichment Pipeline]] | Background | End-to-end async pipeline from entity mutation through queue → workflow → embed → store |

## Technical Debt

| Issue | Impact | Effort |
| --- | --- | --- |
| Hard-coded `vector(1536)` in both schema and `@Array(length = 1536)` annotation; `vectorDimensions` config property exists but is informational only | Med | Med |
| `schemaVersion` is persisted but never read; re-batching workflow on schema drift is not yet implemented | Med | High |
| `EntityEmbeddingRepository.findByWorkspaceId` is unpaginated | Low | Low |
| No similarity-search query method on `EntityEmbeddingRepository` despite the HNSW index existing | Med | Low |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-04-10 | Initial enrichment pipeline introduced — queue → workflow → embed → store, with OpenAI/Ollama providers and pgvector HNSW index | Enrichment Pipeline |
