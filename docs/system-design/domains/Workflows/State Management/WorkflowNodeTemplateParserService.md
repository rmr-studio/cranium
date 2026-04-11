---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-02-08
Updated: 2026-02-08
Domains:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
---
# WorkflowNodeTemplateParserService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/State Management/State Management]]

## Purpose

Parses template strings with `{{ path.to.data }}` syntax, extracting path segments for later resolution against workflow execution context.

---

## Responsibilities

- Parse template syntax: `{{ steps.node_name.output.field }}`
- Extract path segments: `["steps", "node_name", "output", "field"]`
- Handle embedded templates: `"Welcome {{ steps.user.name }}"`
- Validate template syntax and path segments
- Distinguish exact templates from embedded templates from static strings

---

## Dependencies

None (pure parsing logic, no external dependencies)

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/State Management/WorkflowNodeInputResolverService]] — Uses parser to identify and resolve templates

---

## Key Logic

**Template patterns:**

| Input | Type | Result |
|---|---|---|
| `{{ steps.node.output }}` | Exact template | `path = ["steps", "node", "output"]` |
| `"Welcome {{ steps.user.name }}"` | Embedded template | `embeddedTemplates[0].path = ["steps", "user", "name"]` |
| `"static value"` | Static string | `isTemplate = false, rawValue = "static value"` |
| `{{ }}` | Invalid (empty) | Throws `IllegalArgumentException` |
| `{{ steps..output }}` | Invalid (empty segment) | Throws `IllegalArgumentException` |

**Validation rules:**

- Path segments must be alphanumeric + underscore only
- No consecutive dots (empty segments)
- No invalid characters (hyphens, spaces, special chars)

**Regex pattern:**

`\{\{\s*([a-zA-Z0-9_.]+)\s*\}\}` — Matches braces, optional whitespace, alphanumeric path with dots

---

## Public Methods

### `parse(input: String): ParsedTemplate`

Parses input string. Returns `ParsedTemplate` with:
- `isTemplate`: true if contains `{{ }}`
- `path`: List of path segments if exact template
- `rawValue`: Original string if static
- `isEmbeddedTemplate`: true if template embedded in larger string
- `embeddedTemplates`: List of templates found if embedded

Throws `IllegalArgumentException` for invalid syntax.

### `isTemplate(input: String): Boolean`

Quick check if string contains template syntax. Faster than full parsing when only checking presence.

---

## Gotchas

- **Embedded templates vs exact templates:** `"{{ steps.node }}"` is exact (returns single path). `"Welcome {{ steps.node }}"` is embedded (returns list of `EmbeddedTemplateInfo`).
- **Multiple embedded templates:** Single string can contain multiple templates: `"Hello {{ steps.user.name }}, you have {{ steps.inbox.count }} messages"`.
- **Whitespace trimmed:** `{{  steps.node  }}` is valid, whitespace around path is trimmed.
- **Malformed template detection:** If template has `{{ }}` syntax but invalid content, provides specific error (empty template, invalid characters, empty segments).

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/State Management/WorkflowNodeInputResolverService]] — Resolves parsed templates against data
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/State Management/State Management]] — Parent subdomain
