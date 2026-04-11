---
tags:
  - architecture/subdomain
  - domain/entity
Created: 2026-02-08
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
---
# Subdomain: Validation

## Overview

Validates entity instances against their type schemas before persistence. Ensures required attributes are present, attribute values conform to their declared property types, and unique constraints are enforced.

## Components

| Component | Purpose | Type |
| --------- | ------- | ---- |
| [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Entities/Validation/EntityValidationService]] | Schema validation for entity save operations | Service |

## Technical Debt

| Issue | Impact | Effort |
| ----- | ------ | ------ |
| None yet | - | - |

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
| 2026-02-08 | Subdomain overview created | [[02-01-PLAN]] |
