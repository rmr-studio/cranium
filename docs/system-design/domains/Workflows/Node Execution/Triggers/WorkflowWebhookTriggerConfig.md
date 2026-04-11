---
Created:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
Updated: 2026-02-09
---
# WorkflowWebhookTriggerConfig

## Purpose

Triggers workflow execution when an HTTP webhook is received at the configured endpoint. This is an entry point for workflows — trigger nodes do NOT execute during workflow runtime (execute() throws UnsupportedOperationException by design).

---

## Responsibilities

- Configure HTTP method accepted by webhook endpoint (GET/POST/PUT/DELETE/PATCH)
- Specify authentication mechanism (NONE/API_KEY/BASIC/BEARER)
- Define signature verification configuration for request validation
- Define expected payload schema structure
- Validate configuration at workflow creation time

---

## Dependencies

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Triggers/WorkflowTriggerConfig]] — parent sealed interface
- [[riven/apps/client/lib/types/docs/WorkflowNodeConfig]] — sealed parent class for all node configurations

## Used By

- [[riven/docs/system-design/domains/Workflows/Node Execution/WorkflowNodeConfigRegistry]] — discovered and registered at startup via reflection

---

## Config Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `method` | ENUM | Yes | HTTP method the webhook accepts (GET/POST/PUT/DELETE/PATCH) |
| `authentication` | ENUM | Yes | Authentication method for webhook requests (NONE/API_KEY/BASIC/BEARER) |
| `signature` | JSON | Yes | Request verification configuration |
| `payloadSchema` | JSON | Yes | Expected webhook payload structure definition |

**Method enum values:**
- `GET`, `POST`, `PUT`, `DELETE`, `PATCH`

**Authentication enum values:**
- `NONE` — No authentication
- `API_KEY` — API key authentication
- `BASIC` — Basic HTTP authentication
- `BEARER` — Bearer token authentication

---

## Validation Rules

| Field | Rule | Error |
|-------|------|-------|
| `method` | Valid RequestMethodType enum | Enforced at deserialization |
| `authentication` | Valid AuthenticationType enum | Enforced at deserialization |
| `signature` | Non-null | Enforced by constructor |
| `payloadSchema` | Non-null | Enforced by constructor |

**Additional checks:**
- All fields are non-null in constructor — validate() always returns valid
- Enum values are validated at JSON deserialization time, not in validate() method

---

## Gotchas

> [!warning] Import Alias
>
> **Schema name collision:** This class uses `io.swagger.v3.oas.annotations.media.Schema as SwaggerSchema` to avoid name collision with `riven.core.models.common.validation.Schema` (used for payloadSchema property type). The SwaggerSchema annotation is used for OpenAPI documentation, while Schema<String> is the actual configuration type.

> [!warning] Minimal Validation
>
> **validate() always returns valid:** The validate() method returns `ConfigValidationResult.valid()` because all validation is enforced by the constructor (non-null requirements) and JSON deserialization (enum validation). There are no runtime checks beyond structural validity.

> [!warning] Entry Point Only
>
> **execute() not called during workflow:** Triggers define workflow entry points and are evaluated externally (e.g., by HTTP endpoint handlers). Calling execute() throws UnsupportedOperationException — this is intentional design, not a bug.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Triggers/Trigger Nodes]] — category-level overview of all trigger types
- [[riven/apps/client/lib/types/docs/WorkflowNodeConfig]] — sealed parent class
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Node Execution/Triggers/WorkflowTriggerConfig]] — parent sealed interface
