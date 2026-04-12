# Technology Stack

**Analysis Date:** 2026-04-12

## Languages

**Primary:**
- Kotlin 2.1.21 - Backend (Spring Boot) services, domain logic, controllers, repositories
- TypeScript 5 - Frontend applications (Next.js 15)
- JavaScript/JSX/TSX - React component code

**Secondary:**
- SQL - PostgreSQL database schema and queries (Flyway disabled, raw SQL in `db/schema/`)
- YAML - Configuration files for Spring profiles and docker-compose

## Runtime

**Environment:**
- JVM: Java 21 via `eclipse-temurin:21-jre` for production, Gradle 8.7.0 with JDK 21 for build
- Node.js: Implied via npm/pnpm package managers (version not pinned in root, likely 18+)

**Package Manager:**
- Backend: Gradle 8.7.0 (Kotlin DSL with `build.gradle.kts`)
- Frontend: pnpm 10.28.2 (monorepo package manager, configured in `package.json` at root)
- Lockfile: `pnpm-lock.yaml` present but deprecated in favor of npm; `gradle` wrapper used for deterministic builds

## Frameworks

**Core Backend:**
- Spring Boot 3.5.3 - Web framework, dependency injection, configuration
- Spring Data JPA 3.5.3 - ORM, entity management
- Spring Security 6.5.0 - Authentication/authorization with OAuth2 JWT
- Spring WebFlux 3.5.3 - Reactive web support
- Spring WebSocket 3.5.3 - Real-time bidirectional communication (STOMP + raw binary)
- Hibernate 6.x - JPA provider with vector support (`hibernate-vector` 6.6.18)

**Core Frontend:**
- Next.js 15.3.4 (web), 15.1.1 (client) - React framework, App Router, SSR/SSG
- React 19.0.0 (web), 19.2.3 (client) - UI library
- Tailwind CSS 4 - Utility-first CSS framework with custom CSS property config

**Workflow Execution:**
- Temporal 1.32.1 (Kotlin SDK), 1.24.1 (SDK), 1.31.0 (Spring Boot starter) - Distributed workflow orchestration
- Workers auto-discovery from `riven.core.service.workflow` package

**Testing:**
- JUnit 5 - Backend test framework
- Mockito 5.20.0 + mockito-kotlin 3.2.0 - Mocking library
- TestContainers 2.0.3 (PostgreSQL module) - Containerized integration testing
- Jest 29.7.0 - Frontend unit testing
- Vitest 4.1.1 - Frontend test runner (web app)
- React Testing Library 16+ - Component testing

**Build/Dev Tools:**
- Turbo 2.5.4 - Monorepo build orchestration
- Wrangler 4.78.0 - Cloudflare Workers CLI
- Gradle Wrapper 8.7.0 - JVM build tool
- OpenAPI Generator CLI 2.28.0 - Type generation from OpenAPI spec (typescript-fetch)

## Key Dependencies

**Critical Backend:**
- `io.temporal:temporal-kotlin:1.32.1` - Workflow engine client and serialization
- `org.springframework.boot:spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server` - JWT auth with Supabase
- `io.hypersistence:hypersistence-utils-hibernate-63:3.9.2` - JSONB support for dynamic schemas
- `org.hibernate.orm:hibernate-vector:6.6.18.Final` - Vector embeddings for search/enrichment
- `org.postgresql:postgresql` - PostgreSQL driver (runtime)
- `io.github.jan-tennert.supabase:auth-kt:3.1.4` + `storage-kt:3.1.4` - Supabase SDKs for auth and file storage
- `aws.sdk.kotlin:s3:1.3.112` - AWS S3-compatible storage (MinIO, R2, Spaces support)

**Storage & File Handling:**
- `org.apache.tika:tika-core:3.2.0` - File type detection and content validation
- `io.github.borewit:svg-sanitizer:0.3.1` - SVG sanitization for safety
- `com.github.librepdf:openpdf:1.3.30` - PDF generation

**Distributed Locking:**
- `net.javacrumbs.shedlock:shedlock-spring:7.5.0` - Distributed task scheduling lock
- `net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.5.0` - JDBC backend for locking

**Resilience:**
- `io.github.resilience4j:resilience4j-spring-boot3:2.3.0` - Circuit breaker, retry, timeout patterns
- `com.bucket4j:bucket4j-core:8.10.1` - Token bucket rate limiting
- `com.github.ben-manes.caffeine:caffeine:3.2.0` - High-performance caching library

**Integration & API:**
- `com.networknt:json-schema-validator:1.0.83` - JSON schema validation for dynamic entity types
- `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6` - OpenAPI/Swagger documentation and UI

**Analytics:**
- `com.posthog:posthog-server:2.3.2` - Product analytics (optional, feature-flagged)

**Logging:**
- `org.slf4j:slf4j-api:2.0.16` - Logging facade
- `io.github.oshai:kotlin-logging-jvm:7.0.0` - Kotlin-friendly logging wrapper

**Data Mapping:**
- `com.fasterxml.jackson.module:jackson-module-kotlin` - JSON serialization with Kotlin support

**Database Migration:**
- `org.flywaydb:flyway-core` - Disabled (`flyway.enabled: false`); schema managed via raw SQL in `db/schema/`
- `org.flywaydb:flyway-database-postgresql` - PostgreSQL-specific Flyway support (present but unused)

**Critical Frontend:**
- `@supabase/supabase-js:2.50.0` (client), `@supabase/ssr:0.6.1` - Supabase auth and storage SDKs
- `@tanstack/react-query:5.81.2` (client) - Server state management, caching, synchronization
- `zustand:5.0.8` - Lightweight client-side state management for UI state and canvas state
- `react-hook-form:7.58.1` - Form state and validation
- `zod:3.25.67` - TypeScript-first schema validation
- `@hookform/resolvers:5.1.1` - Integration between react-hook-form and zod

**UI Components & Rendering:**
- `@radix-ui/*` - Headless, accessible UI primitives (dialog, dropdown, popover, tabs, etc.)
- `@blocknote/react:0.47.1` - Block-based rich text editor with shadcn theme
- `@xyflow/react:12.10.0` - React Flow for DAG visualization (workflows)
- `framer-motion:12.23.24` - Animation library
- `recharts:2.15.4` - Charting library for data visualization
- `lucide-react:0.522.0` - Icon library

**Frontend Communication:**
- `@stomp/stompjs:7.3.0` + `sockjs-client:1.6.1` - WebSocket communication (STOMP protocol)
- `@tanstack/react-table:8.21.3` - Headless table component library
- `@tanstack/react-virtual:3.13.13` - Virtual scrolling for large lists

**Workspace Monorepo Packages:**
- `@riven/ui:workspace:*` - Shared shadcn UI components
- `@riven/hooks:workspace:*` - Shared React hooks
- `@riven/utils:workspace:*` - Shared utility functions
- `@riven/tsconfig:workspace:*` - Shared TypeScript configuration

**Utilities & Helpers:**
- `date-fns:4.1.0` + `dayjs:1.11.19` - Date/time manipulation
- `uuid:11.1.0` - UUID generation
- `clsx:2.1.1` + `tailwind-merge:3.3.1` - Conditional className utility (`cn()`)
- `validator:13.15.15` - Input validation
- `jsonpath-plus:10.3.0` + `jsonpointer:5.0.1` - JSON path traversal
- `posthog-js:1.352.0` - Client-side analytics (web app only)

**TypeScript & Code Quality:**
- `typescript:5` - Language
- `eslint:9` - Linting (Next.js config extends)
- `prettier:3.8.1` - Code formatting with `prettier-plugin-tailwindcss` for Tailwind class ordering

## Configuration

**Environment:**
All critical configuration is environment variable-driven:

**Backend vars (required):**
- `POSTGRES_DB_JDBC` - Database connection string
- `JWT_SECRET_KEY` - JWT signing key
- `JWT_AUTH_URL` - Supabase JWT issuer URI (e.g., `https://project.supabase.co/auth/v1`)
- `SUPABASE_URL` - Supabase project URL
- `SUPABASE_KEY` - Supabase anonymous key
- `ORIGIN_API_URL` - Frontend URL for CORS
- `TEMPORAL_SERVER_ADDRESS` - Temporal server address (e.g., `localhost:7233`)
- `SERVER_PORT` - Port for Spring Boot app (default: 8081)

**Storage vars (conditional):**
- `STORAGE_SIGNED_URL_SECRET` - Secret for signed URL generation
- `STORAGE_SUPABASE_BUCKET` - Supabase bucket name (dev)
- `STORAGE_S3_BUCKET`, `STORAGE_S3_REGION`, `STORAGE_S3_ACCESS_KEY_ID`, `STORAGE_S3_SECRET_ACCESS_KEY`, `STORAGE_S3_ENDPOINT_URL` - AWS S3/compatible (prod)

**Integration vars (conditional):**
- `NANGO_SECRET_KEY` - Nango API key for third-party integrations
- `NANGO_BASE_URL` - Nango API endpoint (default: `https://api.nango.dev`)

**Enrichment/AI vars (optional):**
- `ENRICHMENT_PROVIDER` - Provider: `openai` or `ollama` (default: `openai`)
- `ENRICHMENT_VECTOR_DIMENSIONS` - Vector size (default: 1536 for OpenAI)
- `OPENAI_API_KEY` - OpenAI API key
- `OPENAI_BASE_URL` - OpenAI API endpoint (default: `https://api.openai.com/v1`)
- `OPENAI_EMBEDDING_MODEL` - Model name (default: `text-embedding-3-small`)
- `OLLAMA_BASE_URL` - Ollama endpoint (default: `http://localhost:11434`)
- `OLLAMA_EMBEDDING_MODEL` - Model name (default: `nomic-embed-text`)

**Analytics vars (optional):**
- `POSTHOG_ENABLED` - Enable PostHog analytics (default: false)
- `POSTHOG_API_KEY` - PostHog project API key
- `POSTHOG_HOST` - PostHog API endpoint (default: `https://us.i.posthog.com`)

**Rate limiting vars (optional):**
- `RATE_LIMIT_ENABLED` - Enable rate limiting (default: true)
- `RATE_LIMIT_AUTHENTICATED_RPM` - Authenticated requests per minute (default: 200)
- `RATE_LIMIT_ANONYMOUS_RPM` - Anonymous requests per minute (default: 30)
- `RATE_LIMIT_TRUSTED_PROXY_CIDRS` - Comma-separated CIDR ranges to trust for rate limiting

**Frontend vars (required):**
- `NEXT_PUBLIC_SUPABASE_URL` - Supabase project URL
- `NEXT_PUBLIC_SUPABASE_ANON_KEY` - Supabase anonymous key
- `NEXT_PUBLIC_API_URL` - Backend API base URL (e.g., `http://localhost:8081/api`)
- `NEXT_PUBLIC_HOSTED_URL` - Frontend URL for OAuth redirects
- `NEXT_PUBLIC_AUTH_PROVIDER` - Auth provider (`supabase` only currently)

**Web app vars (optional):**
- `NEXT_PUBLIC_SITE_URL` - Marketing site URL
- `NEXT_PUBLIC_CLIENT_URL` - Client/dashboard app URL
- `NEXT_PUBLIC_CDN_URL` - CDN URL for static assets (Cloudflare)

**Build:**
- Root `build.gradle.kts`: Gradle plugins (Kotlin, Spring Boot, Spring DM, Flyway, JPA)
- `tsconfig.json`: Strict TypeScript config via workspace package
- `tailwind.config.ts`: Disabled; config moved to `globals.css` with CSS custom properties
- `.prettierrc`: Prettier formatting (defined in root, shared)
- `application.yml`: Spring configuration for JPA, Temporal, WebSocket, storage, rate limiting
- `application-dev.yml`: Development overrides (storage provider = supabase)
- `docker-compose.yml`: Defines `web` (marketing), `core` (backend), `client` (dashboard) profiles

## Platform Requirements

**Development:**
- Java 21 (via Gradle wrapper)
- Node.js 18+ (pnpm 10.28.2)
- PostgreSQL 14+ (local or Docker)
- Temporal Server (local or Docker) - default `localhost:7233`
- Docker + docker-compose for containerized dependencies

**Production:**
- Deployment: Docker (multi-stage: Gradle build → eclipse-temurin:21-jre)
- Database: PostgreSQL 14+ with JSONB support and RLS (Row-Level Security)
- Temporal Server: Cloud-hosted or self-managed cluster
- Supabase: Cloud or self-hosted for auth + storage
- Optional: AWS S3/MinIO/R2/Spaces for object storage
- Optional: Nango for third-party API integrations
- Optional: OpenAI/Ollama for vector embeddings
- Optional: PostHog for analytics

---

*Stack analysis: 2026-04-12*
