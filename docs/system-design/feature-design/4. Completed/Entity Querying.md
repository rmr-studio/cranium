---
tags:
  - priority/medium
  - status/draft
Created:
Updated:
Domains:
  - "[[riven/docs/system-design/domains/Entities/Entities]]"
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
---
# Quick Design: Entity Querying

## What & Why

A centralised platform to query entities based on
- Entity Type
- Specific Entity Conditions (Ie. *status == xx* and *arr >= yy*)
- Specific Entity Relationships (Ie. *entity.xx is linked to yy*)
- Specific Relationship Conditions (Ie. *entity.yy has zz where zz.xx == dd*)
The data models and service functionality should then be commonly accessed and used throughout every other feature that requires specific entity querying
	- Reporting
	- Workflows
	- Other external integration capabilities

---

## Data Changes

**New/Modified Entities:**
- *Modified* WorkflowEntityQueryConfig - Move Query Structure out into a commonly accessed model
- *EntityQueryService* - Service development to take in defined query from external sources and fetches all related entities (Paginated based)

**New/Modified Fields:**

---

## Components Affected

- [[Component1]] - what changes
- [[Component2]] - what changes

---

## API Changes

_New endpoints or modifications (if any)_

---

## Failure Handling

_What happens if this fails? Any new failure modes?_

---

## Gotchas & Edge Cases

_Things to watch out for_

---

## Tasks

- [ ]
- [ ]
- [ ]

---

## Notes

_Anything else relevant_