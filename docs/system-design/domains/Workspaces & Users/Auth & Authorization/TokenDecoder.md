---
tags:
  - layer/configuration
  - component/active
  - architecture/component
Domains:
  - "[[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]]"
Created: 2026-02-08
Updated: 2026-02-08
---
# TokenDecoder

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Auth & Authorization/Auth & Authorization]]

**Class name:** `CustomAuthenticationTokenConverter`

## Purpose

Converts JWT tokens into Spring Security authentication tokens with workspace-scoped authorities. This is the bridge between Supabase-signed JWTs and Spring Security's authority model.

---

## Responsibilities

- Implement `Converter<Jwt, AbstractAuthenticationToken>` for Spring Security integration
- Extract standard OAuth2 scope claims and convert to SCOPE_* authorities
- Extract custom `roles` claim containing workspace role assignments
- Build authorities in `ROLE_{workspaceId}_{ROLE}` format (e.g., `ROLE_550e8400-e29b-41d4-a716-446655440000_OWNER`)
- Create CustomJwtPrincipal with userId, email, and workspace roles
- Handle malformed JWT claims gracefully (returns empty roles on parse failure)

---

## Dependencies

- Spring OAuth2 `Jwt` — input token type
- `WorkspaceRoles` enum — role parsing via fromString
- Jackson `@JsonProperty` — JSON field mapping for workspace_id

## Used By

- `SecurityConfig` — registered as jwtAuthenticationConverter in OAuth2 resource server config
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Auth & Authorization/WorkspaceSecurity]] — consumes the authorities this component produces (indirect consumer)

---

## Key Logic

**convert(jwt):**
- Main entry point implementing Converter interface
- Extracts standard scopes: gets `scope` claim as space-delimited string, splits and maps to `SCOPE_{scope}` authorities
- Calls extractCustomClaims to parse custom roles claim
- For each WorkspaceRole: creates authority string `ROLE_{workspaceId}_{ROLE}` (uppercase role name)
- Creates CustomJwtPrincipal with userId (jwt.subject), email, and workspaceRoles list
- Returns JwtAuthenticationToken with JWT, authorities, and principal.toString() (which returns userId)

**extractCustomClaims(jwt):**
- Parses the `roles` claim which is expected to be JSON array: `[{"workspace_id": "uuid", "role": "OWNER"}]`
- Validates claim is a List
- Maps each element (expected to be Map) to WorkspaceRole data class
- Extracts `workspace_id` and `role` fields from map
- Converts workspace_id to UUID via UUID.fromString
- Converts role string to WorkspaceRoles enum via WorkspaceRoles.fromString
- Wraps in try-catch: any parse error (malformed UUID, invalid role string, wrong structure) returns CustomClaims with empty roles list
- No exceptions thrown — graceful degradation to empty authorities

**Authority format:**
- Pattern: `ROLE_{workspaceId}_{ROLE_NAME}`
- Example: `ROLE_550e8400-e29b-41d4-a716-446655440000_OWNER`
- This format is consumed by [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Auth & Authorization/WorkspaceSecurity]] which checks `hasAuthority("ROLE_$workspaceId_OWNER")` and `authorities.any { it.startsWith("ROLE_$workspaceId") }`

**Supporting data classes:**
- **WorkspaceRole:** Holds `workspaceId: UUID` + `role: WorkspaceRoles`. Uses @JsonProperty("workspace_id") for field mapping.
- **CustomClaims:** Holds `roles: List<WorkspaceRole>`, defaults to empty list
- **CustomJwtPrincipal:** Holds `userId: String`, `email: String?`, `workspaceRoles: List<WorkspaceRole>`. toString() returns userId (used as principal name in authentication token).

---

## Public Methods

### `convert(jwt: Jwt): AbstractAuthenticationToken`

Implements Converter interface. Converts JWT to JwtAuthenticationToken with workspace-scoped authorities and custom principal.

---

## Gotchas

- **Graceful degradation:** Malformed JWT claims result in empty authorities, not exceptions. User will be authenticated but unauthorized for any workspace.
- **Exact claim structure required:** The `roles` claim must be `[{"workspace_id": "uuid", "role": "OWNER"}]` — no validation error if structure differs, just empty roles and silent auth failure
- **Authority format coupling:** [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Auth & Authorization/WorkspaceSecurity]] is tightly coupled to `ROLE_{workspaceId}_{ROLE}` format — changing this format breaks authorization
- **Principal toString:** CustomJwtPrincipal.toString() returns userId, which becomes the principal string in JwtAuthenticationToken — services expecting principal to be userId depend on this
- **No user existence check:** Converter creates authorities based purely on JWT claims — does not verify user or workspace exists in database
- **Uppercase role names:** Authority uses `.uppercase()` on role (line 30) — WorkspaceRoles enum must match this convention

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Auth & Authorization/WorkspaceSecurity]] — Authorization component consuming these authorities
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Auth & Authorization/AuthTokenService]] — Claim extraction service
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Auth & Authorization/Auth & Authorization]] — Parent subdomain
