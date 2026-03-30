---
Created: 2026-03-26
Domains:
  - "[[Catalog]]"
tags:
  - architecture/subdomain
  - domain/catalog
---
# Subdomain: Core Model Definitions

## Overview

Compile-time Kotlin object definitions that describe business-type-specific entity type templates for the lifecycle spine. Unlike the [[Manifest Pipeline]] (which scans classpath JSON files at boot time), core model definitions are abstract classes and singleton registries that define entity type schemas, attributes, relationships, and projections for each business type (B2C SaaS, DTC E-commerce, etc.). At boot time, [[CoreModelCatalogService]] converts these definitions to `ResolvedManifest` objects and feeds them to [[ManifestUpsertService]] for catalog population, converging with the JSON manifest path at the same idempotent persistence layer. Each model also declares projection accept rules (`projectionAccepts`) that specify which integration entity types can project into it, enabling automatic projection rule installation during template materialization.

The key design decision is **Kotlin objects over JSON manifests** -- compile-time type safety, IDE discoverability, and fail-fast validation replace the previous JSON manifest approach. Models are pure Kotlin with no Spring dependency injection, validated lazily on first access, and protected from user modification once installed in the catalog.

## Components

| Component | Purpose | Type |
|-----------|---------|------|
| [[CoreModelDefinition]] | Abstract base class for all core lifecycle model definitions | Model |
| [[CoreModelRegistry]] | Singleton registry that collects, validates, and converts all model sets | Utility |
| [[CoreModelCatalogService]] | Spring service that populates the manifest catalog from core models at boot time | Service |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-03-29 | Added projection accept rules to all core model definitions — each model declares which integration entity types route to it via (LifecycleDomain, SemanticGroup) pairs. CoreModelRegistry gains `findModelsAccepting()` for routing lookups. | Entity Ingestion Pipeline |
| 2026-03-26 | Initial core model definitions subdomain — Kotlin object-based model definitions replacing JSON manifests | Lifecycle Spine |
