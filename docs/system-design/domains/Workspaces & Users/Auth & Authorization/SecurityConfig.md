---
tags:
  - layer/configuration
  - component/active
  - architecture/component
Created: 2026-02-09
Updated: 2026-03-16
Domains:
  - "[[riven/docs/system-design/domains/Workspaces & Users/Workspaces & Users]]"
---
# SecurityConfig

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Auth & Authorization/Auth & Authorization]]

## Purpose

Central Spring Security configuration. Defines the HTTP security filter chain — CORS policy, session management, route-level authorization rules, JWT resource server integration, and exception handling. Wires the [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Auth & Authorization/CustomAuthenticationTokenConverter]] for Supabase JWT decoding and produces the `JwtDecoder` bean.

---

## Responsibilities

- Configure stateless session management (no HTTP sessions)
- Define route-level `permitAll` rules for public endpoints
- Require authentication for all other requests
- Enable CORS with configurable allowed origins, methods, and headers
- Configure OAuth2 resource server with HMAC-SHA256 JWT decoding
- Register [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Auth & Authorization/CustomAuthenticationTokenConverter]] for JWT-to-authentication mapping
- Map authentication/authorization failures to 401/403 HTTP responses
- Produce the `JwtDecoder` bean used by Spring Security's OAuth2 resource server
- Produce the `CorsConfigurationSource` bean

---

## Dependencies

- `SecurityConfigurationProperties` — JWT secret key, allowed origins
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Auth & Authorization/CustomAuthenticationTokenConverter]] — Converts decoded JWTs into Spring Security authentication tokens with workspace-scoped authorities
- [[riven/docs/system-design/domains/Workspaces & Users/Real-time Events/WebSocketConfigurationProperties]] — WebSocket endpoint path (for `permitAll` rule)

## Used By

- Spring Security framework — auto-detected via `@Configuration` + `@EnableWebSecurity`
- All controllers — filter chain applies to every HTTP request

---

## Key Logic

### Security filter chain

The `securityFilterChain` bean configures:

1. **CORS** — delegates to `corsConfig()` with origins from `SecurityConfigurationProperties`
2. **CSRF** — disabled (stateless API, no browser session cookies)
3. **Session management** — `STATELESS` (no `JSESSIONID`, no session store)
4. **Route authorization:**

| Route Pattern | Access | Rationale |
|---|---|---|
| `/api/auth/**` | `permitAll` | Authentication endpoints (login, register) |
| `/actuator/**` | `permitAll` | Health checks, metrics |
| `/docs/**` | `permitAll` | OpenAPI / Swagger UI |
| `/public/**` | `permitAll` | Public API endpoints |
| `/api/v1/storage/download/{token}` | `permitAll` | Signed URL downloads — token IS the auth |
| `/api/v1/avatars/**` | `permitAll` | Avatar serving — entity ID is the lookup key |
| `{wsProperties.endpoint}/**` | `permitAll` | WebSocket upgrade — STOMP interceptor handles auth |
| Everything else | `authenticated` | Requires valid JWT |

5. **OAuth2 resource server** — JWT mode with [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Auth & Authorization/CustomAuthenticationTokenConverter]]
6. **Exception handling** — 401 for unauthenticated, 403 for access denied

### JWT decoder

`jwtDecoder()` creates a `NimbusJwtDecoder` using HMAC-SHA256 with the secret from `SecurityConfigurationProperties.jwtSecretKey`.

### CORS configuration

`corsConfig()` returns a `UrlBasedCorsConfigurationSource` registered for `/**` with:
- Allowed origins from `SecurityConfigurationProperties.allowedOrigins`
- Methods: GET, POST, PUT, DELETE, OPTIONS
- Headers: Authorization, Content-Type, Accept, Origin
- Exposed headers: Authorization, Content-Type
- Credentials: enabled

---

## Annotations

- `@EnableWebSecurity` — activates Spring Security's web security support
- `@EnableMethodSecurity(prePostEnabled = true)` — enables `@PreAuthorize` / `@PostAuthorize` on service methods. This is the mechanism used for workspace-scoped access control across all domains.

---

## Gotchas

- **Method security is the primary access control** — route-level rules in the filter chain only distinguish public vs authenticated. Fine-grained workspace-scoped authorization is enforced by `@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")` on service methods.
- **WebSocket endpoint is `permitAll`** — the HTTP upgrade is unauthenticated; STOMP-level auth is handled by [[riven/docs/system-design/domains/Workspaces & Users/Real-time Events/WebSocketSecurityInterceptor]] on the inbound channel, not by the security filter chain.
- **Avatar endpoints are public** — like signed URL downloads, the entity UUID in the URL is the only access control. This is intentional — avatar URLs are embedded in API responses and may be loaded by browsers without JWT headers.
- **JWT secret is symmetric** — uses `HmacSHA256` with a shared secret (not asymmetric RSA/EC keys). The same secret must be configured in Supabase and the API.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Auth & Authorization/Auth & Authorization]] — Parent subdomain
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Auth & Authorization/CustomAuthenticationTokenConverter]] — JWT-to-authentication mapping
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Auth & Authorization/WorkspaceSecurity]] — `@PreAuthorize` SpEL target for workspace membership checks
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workspaces & Users/Auth & Authorization/AuthTokenService]] — Extracts user ID from the authenticated JWT principal
- [[riven/docs/system-design/domains/Workspaces & Users/Real-time Events/WebSocketSecurityInterceptor]] — STOMP-level auth (not handled by this filter chain)
- [[riven/docs/system-design/domains/Storage/File Management/AvatarController]] — Public endpoints added to `permitAll` rules

---

## Changelog

### 2026-03-16 — Avatar endpoints added to permitAll

- Added `/api/v1/avatars/**` to `permitAll` rules for unauthenticated avatar serving

### 2026-02-09 — Initial implementation

- Security filter chain with CORS, CSRF disabled, stateless sessions
- Route-level authorization rules
- OAuth2 resource server with HMAC-SHA256 JWT decoding
- Method security enabled for `@PreAuthorize` / `@PostAuthorize`
