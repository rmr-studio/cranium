# Entity Projection FAQ

## How does an integration entity (e.g. a Zendesk ticket) end up attached to a core entity?

When an integration is connected, the system installs **projection rules** that map integration entity types to core entity types. The mapping is based on `(lifecycleDomain, semanticGroup)` pairs — for example, a Zendesk ticket classified as `SUPPORT / COMMUNICATION` matches `SupportTicketModel` because its `projectionAccepts` declares that same pair.

On each sync, the projection service loads the matching rules and runs **two-phase identity resolution** to decide whether to link to an existing core entity or create a new one:

1. **External ID match** — does a core entity already share the same `sourceExternalId`?
2. **Identifier attribute match** (fallback) — do any `IDENTIFIER`-classified attributes (e.g. email, ticket number) match exactly one core entity?

If a match is found, the integration entity is linked to the existing core entity via a "source-data" relationship and its attributes are synced across. If no match is found and `autoCreate` is enabled, a new core entity is created.

## What happens if a Zendesk ticket arrives but no matching customer exists?

The ticket and the customer are **separate projection chains**. Each integration entity type projects independently into its own matched core model:

- Zendesk tickets project into **Communication / SupportTicket** (based on their semantic classification).
- Zendesk contacts project into **Customer** (based on theirs).

So a ticket arriving without a customer will:

1. Create (or update) a projected Communication core entity for the ticket itself.
2. **Not** create a Customer — tickets don't project into the Customer model.

The customer is only created when Zendesk's contact/customer sync runs and those records project into the Customer core model via their own rule. Cross-entity-type linking (ticket to customer) is handled by separate relationship definitions, not the projection pipeline.

## Does the system ever auto-create core entities?

Yes, when **both** conditions are met:

1. Identity resolution found no match (neither external ID nor identifier attribute).
2. The projection rule has `autoCreate = true` (the default for all current core models).

The new entity is saved with `sourceType = PROJECTED` to distinguish it from user-created (`TEMPLATE`) or raw integration (`INTEGRATION`) entities. Its attributes are copied from the integration entity, and a "source-data" relationship links the two.

If `autoCreate` is `false`, the projection is skipped entirely.

## How are projection rules installed?

Automatically, when an integration is first connected. `TemplateMaterializationService` reads the integration's catalog entity types, matches each one against core models via `CoreModelRegistry.findModelsAccepting(domain, semanticGroup)`, and saves a `ProjectionRuleEntity` plus a backing `RelationshipDefinitionEntity` for each match. No manual wiring is needed.
