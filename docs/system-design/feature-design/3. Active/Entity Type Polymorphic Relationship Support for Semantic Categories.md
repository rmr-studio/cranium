---
tags:
  - status/superseded
  - architecture/feature
Created:
Updated: 2026-02-28
Domains:
  - "[[Knowledge]]"
  - "[[Entities]]"
  - "[[Integrations]]"
---
# Quick Design: Entity Type Polymorphic Relationship Support for Semantic Categories

> [!important] Superseded
> This quick design has been superseded by the full feature design: [[Semantic Entity Groups]]
> That document covers the complete scope — semantic group enum, relationship target rule constraints (array-based matching), knowledge layer integration, `allowPolymorphic` bypass behavior, and re-enrichment triggers.

## Original Context

With the introduction of [[Semantically Imbued Entity Attributes]], this would allow us to give each entity type a specific category, ie.
- Customer
- Transaction
- Communication
- etc

Data synced from integrations and converted into entity models would also define entity types with these semantic types to allow for better relationship linking. The `Connected Accounts` example — a customer entity relationship that only accepts `User` entity types from integration syncs — is now covered by `semanticTypeConstraints` array support on [[RelationshipTargetRuleEntity]] in the full design.