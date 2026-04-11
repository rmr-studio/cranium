---
Created: 2026-02-06
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
  - "[[riven/docs/system-design/domains/Knowledge/Knowledge]]"
tags:
---
# Subdomain: Entity Semantics
## Overview

Entity types, attributes, and relationships carry semantic metadata that defines what they mean, not just what they're called.

- Each entity type carries a semantic definition (natural language description of what this entity represents in the business model)
- Each attribute carries a semantic type classification (identifier, categorical, quantitative, temporal, freetext, relational reference) and natural language description
- Each relationship carries semantic context (the nature of the connection, e.g., "customer purchased product" vs "customer viewed product")
- Semantic metadata is inherited from templates but user-editable
- Changes to semantic metadata trigger re-enrichment of affected entities 
	- [[riven/docs/system-design/domains/Knowledge/Schema Change Handling/Schema Change Handling]]
## Components

| Component | Purpose | Type |
| --------- | ------- | ---- |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Semantics/EntityTypeSemanticMetadataService]] | CRUD operations and lifecycle hooks for semantic metadata | Service |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Entity Semantics/EntityTypeSemanticMetadataRepository]] | Data access with custom JPQL for hard-delete and cascade soft-delete | Repository |
| [[riven/docs/system-design/domains/Knowledge/KnowledgeController]] | 8 REST endpoints at `/api/v1/knowledge/` for semantic metadata management | Controller |
| EntityTypeSemanticMetadataEntity | Database mapping for `entity_type_semantic_metadata` table | JPA Entity |
| EntityTypeSemanticMetadata | Immutable domain model for semantic metadata | Model |
| SemanticMetadataTargetType | Discriminator enum: ENTITY_TYPE, ATTRIBUTE, RELATIONSHIP | Enum |
| SemanticAttributeClassification | Classification enum: identifier, categorical, quantitative, temporal, freetext, relational_reference | Enum |
| SemanticGroup | Categorical classification for entity types — used for organizational grouping and catalog metadata | Enum |

## SemanticGroup Enum

Categorical classification for entity types, used for organizational grouping and catalog metadata. Stored on `EntityTypeEntity.semanticGroup`.

| Value | Description |
|---|---|
| `CUSTOMER` | Customer, contact, lead, or person entities |
| `PRODUCT` | Product, service, or offering entities |
| `TRANSACTION` | Order, invoice, payment, or exchange entities |
| `COMMUNICATION` | Email, message, note, or interaction entities |
| `SUPPORT` | Ticket, case, or issue entities |
| `FINANCIAL` | Account, budget, or monetary entities |
| `OPERATIONAL` | Task, project, process, or workflow entities |
| `CUSTOM` | User-defined classification that doesn't fit standard groups |
| `UNCATEGORIZED` | Default for entity types without explicit classification |

**Package:** `riven.core.enums.entity.semantics.SemanticGroup`

**Usage:**
- `EntityTypeEntity.semanticGroup` — classifies each entity type (defaults to `UNCATEGORIZED`)
- Catalog manifest resolution — resolves `semanticGroup` string values from manifest JSON into entity type definitions

**Design note:** SemanticGroup is purely organizational metadata. It is not used for relationship targeting — all relationship target rules require explicit `targetEntityTypeId` values.

## Technical Debt

|Issue|Impact|Effort|
|---|---|---|
||High/Med/Low|High/Med/Low|

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-02-19 | Phase 1 implementation — service, repository, JPA entity, domain model, enums, lifecycle hooks, KnowledgeController, 12 unit tests | Semantic Metadata Foundation |
| 2026-03-01 | SemanticGroup enum added — categorical classification for entity types | Semantic Entity Groups |
| 2026-03-09 | Removed `semanticTypeConstraint` from RelationshipTargetRuleEntity — SemanticGroup no longer used for relationship targeting, now purely organizational metadata | Relationship Simplification |
