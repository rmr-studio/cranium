---
tags:
  - priority/high
  - status/draft
  - architecture/design
Created: 2026-02-10
Updated:
Domains:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
blocked by:
  - "[[riven/docs/system-design/feature-design/5. Backlog/Workflow Node Output State Management Handling]]"
---
# Quick Design: Data Registry UI Implementation

## What & Why

When a user is a building a workflow. It is critical that they are able to use the executed results from previous nodes when performing actions.
- Such as querying a set of entities, and for each entity performing an action, where the action takes in a single entity

Currently the Workflow UI is not aware of the data registry, and would not be aware of the results a node would produce, and how another node could access this.

[[riven/docs/system-design/feature-design/5. Backlog/Workflow Node Output State Management Handling]] should add the functionality that exposes both
	- The type of data that a node should produce (ie. Entity[])
	- The data that is produced to the registry, and the link to reference it.
When creating new nodes and adjusting a nodes input configuration. A user should then have access to a UI component that maps all data from the registry to a node, where each node has a unique identifier (A user could rename each node)
Each output to the data registry could be Labelled
	- Ie. A node (`Create new Lead`) could produce a new entity, the payload of that new entity would be recorded in the data registry as `lead`
	- The UI could then access this in other nodes using `Create New Lead`.`Lead`
	

---

## Data Changes

**New/Modified Entities:**

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