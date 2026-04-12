# External Integrations

**Analysis Date:** 2026-04-12

## APIs & External Services

**Authentication & Identity:**
- Supabase Auth - JWT-based authentication provider
  - SDK: `io.github.jan-tennert.supabase:auth-kt`
  - Auth endpoint: `${JWT_AUTH_URL}` (e.g., `https://project.supabase.co/auth/v1`)
  - Token handling: Spring Security OAuth2 resource server decodes JWT via `JwtAuthenticationToken`
  - Config: `configuration/auth/TokenDecoder.kt`, `configuration/auth/SecurityConfig.kt`
  - Frontend adapter: `lib/auth/adapters/supabase-auth-adapter.ts` in client app

**Third-Party Integrations Platform:**
- Nango - Unified API integration platform for syncing data from external sources
  - SDK: Custom WebClient-based client via `NangoClientConfiguration.kt`
  - Endpoint: `${NANGO_BASE_URL}` (default: `https://api.nango.dev`)
  - Auth: Bearer token via `${NANGO_SECRET_KEY}` header
  - Webhook receiver: `controller/integration/NangoWebhookController.kt` at `/api/v1/integrations/nango/webhook`
  - Webhook security: HMAC-SHA256 validation via `NangoWebhookHmacFilter.kt`
  - Connection entities: `IntegrationConnectionEntity.kt`, `IntegrationDefinitionEntity.kt`, `WorkspaceIntegrationInstallationEntity.kt`
  - Sync state tracking: `IntegrationSyncStateEntity.kt`
  - Use case: Centralised syncing from CRM, HRM, project management, and other SaaS platforms

**Vector/Embedding AI:**
- OpenAI Embeddings API - Optional vector embeddings for semantic search
  - Provider type: `openai` (configurable)
  - Config: `EnrichmentConfigurationProperties.kt`, `EnrichmentClientConfiguration.kt`
  - Base URL: `${OPENAI_BASE_URL}` (default: `https://api.openai.com/v1`)
  - Model: `${OPENAI_EMBEDDING_MODEL}` (default: `text-embedding-3-small`)
  - Vector dimensions: `${ENRICHMENT_VECTOR_DIMENSIONS}` (default: 1536)
  - DB column: PostgreSQL `vector` type via `hibernate-vector:6.6.18.Final`

- Ollama - Alternative local embeddings provider
  - Provider type: `ollama`
  - Base URL: `${OLLAMA_BASE_URL}` (default: `http://localhost:11434`)
  - Model: `${OLLAMA_EMBEDDING_MODEL}` (default: `nomic-embed-text`)
  - Use case: Local, offline embeddings for privacy-sensitive deployments

**Analytics:**
- PostHog - Product analytics and feature flags (optional)
  - SDK: `com.posthog:posthog-server:2.3.2`
  - Config: `PostHogConfiguration.kt`, `PostHogConfigurationProperties.kt`
  - Endpoint: `${POSTHOG_HOST}` (default: `https://us.i.posthog.com`)
  - API key: `${POSTHOG_API_KEY}`
  - Enabled: `${POSTHOG_ENABLED}` (default: false)
  - Capture filter: `PostHogCaptureFilter.kt` (request/response tracking)
  - Frontend client: `posthog-js:1.352.0` in web app (optional)

**Email (Noted but Not Fully Integrated):**
- Resend - Email service API (configured but implementation not visible in core)
  - Config var: `RESEND_API_KEY=re_xxxxxxxxxxxx` (in `.env.example`)
  - SDK: Not yet added to dependencies; integration pending

## Data Storage

**Databases:**
- PostgreSQL 14+ (Primary)
  - JDBC connection: `${POSTGRES_DB_JDBC}`
  - Driver: `org.postgresql:postgresql`
  - ORM: Hibernate 6.x via Spring Data JPA
  - Schema management: Raw SQL files in `db/schema/` (Flyway disabled)
  - Features: JSONB columns, vector support (`hibernate-vector`), RLS (Row-Level Security)
  - Auditing: `SecurityAuditorAware.kt` tracks created/modified by via Spring Data auditing

- H2 Database (Testing only)
  - In-memory PostgreSQL-compatible mode
  - Used in unit tests with profile `test` via `application-test.yml`
  - `ddl-auto: create-drop` for test isolation

**File Storage:**
- Supabase Storage - Primary development storage
  - SDK: `io.github.jan-tennert.supabase:storage-kt:3.1.4`
  - Bucket: `${STORAGE_SUPABASE_BUCKET}` (default: `riven-storage`)
  - Dev profile: Configured in `application-dev.yml`
  - URL signing: Custom signed URL generation for presigned uploads/downloads

- AWS S3-Compatible Storage - Production storage
  - SDK: `aws.sdk.kotlin:s3:1.3.112`
  - Providers: AWS S3, MinIO, Cloudflare R2, DigitalOcean Spaces
  - Config: `storage.s3.*` in `application.yml`
  - Bucket: `${STORAGE_S3_BUCKET}` (default: `riven-storage`)
  - Region: `${STORAGE_S3_REGION}` (default: `us-east-1`)
  - Endpoint URL: `${STORAGE_S3_ENDPOINT_URL}` (for MinIO/R2/Spaces)
  - Controller: `StorageController.kt` exposes `/api/v1/storage/*` endpoints

- Local Filesystem - Fallback/development
  - Base path: `${STORAGE_LOCAL_BASE_PATH}` (default: `./storage`)
  - Profile: `application.yml` default (overridden by dev/prod)

**Caching:**
- Caffeine - In-memory cache for rate limiting token buckets
  - Library: `com.github.ben-manes.caffeine:caffeine:3.2.0`
  - Config: Rate limit cache max size `10000`, expire after `10` minutes
  - Integration: `RateLimitFilter.kt` uses `Bucket4j` with Caffeine backend

## Authentication & Identity

**Auth Provider:**
- Supabase (Primary)
  - User creation: Supabase Auth API
  - JWT tokens: Issued by Supabase, decoded via Spring Security `JwtAuthenticationToken`
  - Issuer: `${JWT_AUTH_URL}` (e.g., `https://project.supabase.co/auth/v1`)
  - Signing key: `${JWT_SECRET_KEY}` (configured in SecurityConfig)
  - Refresh tokens: Handled by frontend via Supabase SDK (`@supabase/supabase-js`)
  - OAuth flow: Callback at `http://localhost:3001/auth/callback` → `/api/auth/token/callback` in client app

**Workspace Access Control:**
- Pattern: `@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")` on service methods
- Bean: `workspaceSecurity` in `OrganisationSecurity.kt`
- Verification: Role-based access via `UserWorkspaceRoleEntity` (workspace memberships)
- JWT claims extraction: `AuthTokenService.kt` retrieves userId from JWT `sub` claim

## Monitoring & Observability

**Error Tracking:**
- Not detected (no Sentry/Rollbar/DataDog integration)
- Strategy: Exceptions propagate to `ExceptionHandler.kt` in `configuration/` → HTTP responses

**Logs:**
- Approach: SLF4J facade with kotlin-logging wrapper
  - Logger: Injected via `LoggerConfig.kt` (prototype-scoped `KLogger` bean)
  - Configuration: `riven.include-stack-trace: true/false` for stack trace inclusion in responses
  - No centralized log aggregation detected (CloudWatch/ELK/Datadog not configured)

**Performance & Metrics:**
- Actuator: `spring-boot-starter-actuator` enabled (default endpoints available)
- Custom metrics: Not detected in codebase

## CI/CD & Deployment

**Hosting:**
- Docker - Multi-stage builds
  - Backend build: `gradle:8.7.0-jdk21` → `eclipse-temurin:21-jre`
  - Frontend build: Multi-stage Next.js builds (web, client apps)
  - Compose profiles: `web` (marketing only), `platform` (backend + client), `all` (all services)
  - Port mapping: `WEB_PORT=3000`, `CLIENT_PORT=3001`, `SERVER_PORT=8081`

**CI Pipeline:**
- Not detected in repository; likely GitHub Actions or similar (not visible in codebase)
- Build commands: `./gradlew build`, `npm run build` (via turbo)

**Deployment Configuration:**
- `docker-compose.yml` defines three services: `web`, `core`, `client`
- Service dependencies: `client` depends on `core` (for API)
- Environment variables: Passed via `.env` file at runtime
- Restart policy: `unless-stopped`

## Environment Configuration

**Required env vars (production checklist):**

**Core backend:**
- `POSTGRES_DB_JDBC` - Database JDBC string
- `JWT_SECRET_KEY` - JWT signing secret (must match frontend expectations)
- `JWT_AUTH_URL` - Supabase auth issuer URI
- `SUPABASE_URL` - Supabase project URL
- `SUPABASE_KEY` - Supabase anonymous key
- `ORIGIN_API_URL` - Frontend URL (used in CORS `allowed-origins`)
- `TEMPORAL_SERVER_ADDRESS` - Temporal server address + port
- `SERVER_PORT` - Application port (default: 8081)

**Storage selection:**
- If using Supabase (dev): `STORAGE_SUPABASE_BUCKET`
- If using S3-compatible (prod): `STORAGE_S3_BUCKET`, `STORAGE_S3_REGION`, `STORAGE_S3_ACCESS_KEY_ID`, `STORAGE_S3_SECRET_ACCESS_KEY`, `STORAGE_S3_ENDPOINT_URL`
- Signed URLs: `STORAGE_SIGNED_URL_SECRET` (required for URL generation)

**Integrations:**
- Nango: `NANGO_SECRET_KEY`, `NANGO_BASE_URL` (optional if not using third-party syncing)

**Enrichment (optional):**
- `ENRICHMENT_PROVIDER` - `openai` or `ollama` (default: `openai`)
- If OpenAI: `OPENAI_API_KEY`, `OPENAI_BASE_URL`, `OPENAI_EMBEDDING_MODEL`
- If Ollama: `OLLAMA_BASE_URL`, `OLLAMA_EMBEDDING_MODEL`

**Analytics (optional):**
- `POSTHOG_ENABLED` - Set to `true` to enable
- `POSTHOG_API_KEY`, `POSTHOG_HOST`

**Rate limiting (optional):**
- `RATE_LIMIT_ENABLED` (default: true)
- `RATE_LIMIT_AUTHENTICATED_RPM`, `RATE_LIMIT_ANONYMOUS_RPM`
- `RATE_LIMIT_TRUSTED_PROXY_CIDRS` (for reverse proxies)

**Frontend:**
- `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY` - Supabase
- `NEXT_PUBLIC_API_URL` - Backend API base
- `NEXT_PUBLIC_HOSTED_URL` - Frontend URL
- `NEXT_PUBLIC_AUTH_PROVIDER` - `supabase`

**Secrets location:**
- Development: `.env` file at monorepo root (uses `dotenv-cli`)
- Production: Environment variables injected at container startup (docker-compose or orchestration platform)
- Never commit: `.env`, `.env.local`, sensitive credentials

## Webhooks & Callbacks

**Incoming:**
- Nango webhook receiver: `POST /api/v1/integrations/nango/webhook`
  - Controller: `NangoWebhookController.kt`
  - Security: HMAC-SHA256 validation via `NangoWebhookHmacFilter.kt`
  - Payload: Integration sync events, connection status updates
  - Processing: Delegates to `IntegrationService` for state updates

**Outgoing:**
- None detected
- Pattern for future webhooks: Would use ShedLock (`shedlock-spring`) for reliable delivery

**WebSocket Endpoints:**
- STOMP endpoint: `ws://localhost:8081/ws` (via Spring WebSocket support)
  - Protocol: STOMP (Simple Text Oriented Messaging Protocol)
  - Authentication: JWT token via `StompSubProtocolHandler` (configured in SecurityConfig)
  - Use case: Real-time messaging, collaboration (Yjs multiplayer editing)

- Raw binary endpoint: Also at `/ws` but for direct binary frames
  - Use case: React Flow canvas updates, collaborative document editing
  - Message size limit: `65536` bytes
  - Send buffer size: `524288` bytes
  - Timeout: `15000` ms (15 seconds)

**Server Heartbeat:**
- WebSocket server heartbeat: `10000` ms (10 seconds)
- Client heartbeat: `10000` ms (expected from client)
- Purpose: Keep-alive for long-lived WebSocket connections

---

*Integration audit: 2026-04-12*
