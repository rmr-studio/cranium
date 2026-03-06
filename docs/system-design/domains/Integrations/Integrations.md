---
tags:
  - architecture/domain
  - domain/integration
  - tools/nango
  - tools/temporal
Created:
---
# Domain: Third Party Integrations
---

## Overview

_What business capability does this domain represent? What problem space does it own?_

---

## Boundaries

### This Domain Owns

_Be explicit about what belongs here_

### This Domain Does NOT Own

_Prevent scope creep by being clear about boundaries_

---

## Components

| Component | Purpose | Type                            |
| --------- | ------- | ------------------------------- |
| [[]]      |         | Service/ Controller /Repository |

### Integrations

|Component|External System|
|---|---|
|[[]]||

---

## Flows

| Flow        | Type                     | Description |
| ----------- | ------------------------ | ----------- |
| [[Flow - ]] | User-facing / Background |             |

---

## Data

### Owned Entities

|Entity|Purpose|Key Fields|
|---|---|---|
||||

### Database Tables

|Table|Entity|Notes|
|---|---|---|
||||

---

## External Dependencies

|Dependency|Purpose|Failure Impact|
|---|---|---|
||||

---

## Domain Interactions

### Depends On

|Domain|What We Need|How We Access|
|---|---|---|
|[[Domain - ]]||API / Event / Direct|

### Consumed By

|Domain|What They Need|How They Access|
|---|---|---|
|[[Catalog]]|Stale flag propagation — [[IntegrationDefinitionStaleSyncService]] writes `integration_definitions.stale` to match the catalog entry's stale status after each manifest load cycle|Direct service injection|

---

## Key Decisions

|Decision|Summary|
|---|---|
|[[ADR-xxx]]||

---

## Technical Debt

|Issue|Impact|Effort|
|---|---|---|
||High/Med/Low|High/Med/Low|

---

## Recent Changes

| Date | Change | Feature/ADR |
| ---- | ------ | ----------- |
|      |        | [[]]        |