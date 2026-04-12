# Codebase Structure

**Analysis Date:** 2026-04-12

## Directory Layout

```
postgres-ingestion/
├── apps/                           # Frontend applications (Next.js)
│   ├── client/                     # Dashboard app (Next.js 15, React 19)
│   │   ├── app/                    # App Router pages
│   │   │   ├── api/                # Route handlers
│   │   │   ├── auth/               # Auth pages (login, signup, verify)
│   │   │   ├── dashboard/          # Protected dashboard routes
│   │   │   ├── layout.tsx          # Root layout with providers
│   │   │   └── page.tsx            # Home page (redirects based on auth)
│   │   ├── components/
│   │   │   ├── feature-modules/    # Feature-scoped code
│   │   │   │   ├── authentication/
│   │   │   │   ├── blocks/
│   │   │   │   ├── entity/
│   │   │   │   ├── integrations/
│   │   │   │   ├── knowledge/
│   │   │   │   ├── notes/
│   │   │   │   ├── onboarding/
│   │   │   │   ├── user/
│   │   │   │   ├── waitlist/
│   │   │   │   ├── workflow/
│   │   │   │   └── workspace/
│   │   │   ├── provider/           # Context providers
│   │   │   ├── ui/                 # Shared shadcn/ui components
│   │   │   └── util/               # Utility components (wrappers)
│   │   ├── hooks/                  # Shared custom hooks
│   │   │   ├── query/              # TanStack Query hooks (global)
│   │   │   └── websocket/          # WebSocket hooks
│   │   ├── lib/
│   │   │   ├── api/                # API client factories
│   │   │   ├── auth/               # Auth utilities and errors
│   │   │   ├── interfaces/         # Shared TypeScript types
│   │   │   ├── types/              # Generated OpenAPI types + domain barrels
│   │   │   ├── util/               # Utility functions (non-React)
│   │   │   └── websocket/          # WebSocket client utilities
│   │   ├── test/                   # Test files
│   │   ├── .claude/                # Claude agent configuration
│   │   ├── docs/                   # Design and feature docs
│   │   ├── public/                 # Static assets
│   │   ├── scripts/                # Utility scripts (e.g., generate-types.sh)
│   │   ├── package.json
│   │   └── tsconfig.json
│   ├── web/                        # Marketing site (Next.js 16)
│   │   ├── app/                    # Pages
│   │   ├── components/
│   │   ├── content/                # Markdown content
│   │   ├── docs/
│   │   ├── hooks/
│   │   ├── lib/
│   │   ├── providers/
│   │   ├── public/
│   │   ├── package.json
│   │   └── tsconfig.json
│   └── README.md
├── core/                           # Backend API (Spring Boot + Kotlin)
│   ├── src/main/kotlin/riven/core/
│   │   ├── CoreApplication.kt      # Spring Boot entry point
│   │   ├── configuration/          # Spring beans and configuration
│   │   │   ├── properties/         # @ConfigurationProperties beans
│   │   │   ├── auth/               # Security config
│   │   │   ├── integration/        # Integration setup (Supabase, Temporal)
│   │   │   ├── workflow/           # Workflow/Temporal config
│   │   │   ├── websocket/          # WebSocket config
│   │   │   ├── storage/            # S3 client config
│   │   │   ├── ratelimit/          # Rate limiting config
│   │   │   ├── analytics/          # PostHog config
│   │   │   ├── audit/              # Audit logging config
│   │   │   ├── enrichment/         # Enrichment provider config
│   │   │   └── openapi/            # Swagger/OpenAPI config
│   │   ├── controller/             # HTTP endpoints
│   │   │   ├── block/
│   │   │   ├── entity/
│   │   │   ├── integration/
│   │   │   ├── knowledge/
│   │   │   ├── notification/
│   │   │   ├── onboarding/
│   │   │   ├── storage/
│   │   │   ├── user/
│   │   │   ├── workflow/
│   │   │   ├── workspace/
│   │   │   └── ...
│   │   ├── service/                # Business logic
│   │   │   ├── activity/
│   │   │   ├── analytics/
│   │   │   ├── auth/
│   │   │   ├── block/              # Block CRUD, environment execution
│   │   │   ├── catalog/            # System template management
│   │   │   ├── enrichment/         # Data enrichment
│   │   │   ├── entity/             # Entity type and instance management
│   │   │   │   ├── query/          # Entity query engine
│   │   │   │   └── type/           # Entity type lifecycle
│   │   │   ├── identity/           # Identity resolution (deduplication)
│   │   │   ├── ingestion/          # Entity ingestion pipeline
│   │   │   ├── integration/        # Sync pipelines, connectors
│   │   │   ├── knowledge/          # AI reasoning and retrieval
│   │   │   ├── lifecycle/          # DTC/SaaS lifecycle models
│   │   │   ├── notification/
│   │   │   ├── onboarding/
│   │   │   ├── schema/             # JSON Schema validation
│   │   │   ├── storage/            # File storage (S3)
│   │   │   ├── user/
│   │   │   ├── websocket/          # WebSocket message handling
│   │   │   ├── workflow/           # Workflow definition and execution
│   │   │   └── workspace/
│   │   ├── repository/             # Data access (JPA)
│   │   │   ├── block/
│   │   │   ├── entity/
│   │   │   ├── integration/
│   │   │   ├── workflow/
│   │   │   └── ...
│   │   ├── entity/                 # JPA entities
│   │   │   ├── block/
│   │   │   ├── entity/
│   │   │   ├── integration/
│   │   │   ├── workflow/
│   │   │   └── ...
│   │   ├── models/                 # Domain data structures and DTOs
│   │   │   ├── request/            # Request objects
│   │   │   ├── response/           # Response objects
│   │   │   ├── entity/
│   │   │   ├── block/
│   │   │   ├── workflow/
│   │   │   └── ...
│   │   ├── enums/                  # Domain enums
│   │   │   ├── entity/
│   │   │   ├── integration/
│   │   │   ├── workflow/
│   │   │   └── ...
│   │   ├── exceptions/             # Custom exception classes
│   │   ├── filter/                 # HTTP filters (analytics, rate limit)
│   │   ├── validation/             # Custom validators
│   │   ├── util/                   # Utility classes
│   │   ├── projection/             # Data projection/aggregation logic
│   │   ├── lifecycle/              # Lifecycle model definitions
│   │   └── deserializer/           # Custom Jackson deserializers
│   ├── src/test/kotlin/            # Test files (mirrors src/main structure)
│   ├── db/
│   │   ├── schema/                 # SQL schema files (declarative DDL)
│   │   │   ├── 00_extensions/
│   │   │   ├── 01_tables/
│   │   │   ├── 02_indexes/
│   │   │   ├── 03_functions/
│   │   │   ├── 04_constraints/
│   │   │   ├── 05_rls/
│   │   │   ├── 08_triggers/
│   │   │   ├── 09_grants/
│   │   │   └── README.md
│   │   └── seed/                   # Seed data (SQL and Kotlin)
│   ├── gradle/                     # Gradle wrapper
│   ├── build.gradle.kts
│   ├── CLAUDE.md
│   └── README.md
├── packages/                       # Shared libraries (monorepo)
│   ├── ui/                         # @riven/ui — shared components
│   │   ├── src/
│   │   └── package.json
│   ├── hooks/                      # @riven/hooks — shared React hooks
│   │   ├── src/
│   │   └── package.json
│   ├── utils/                      # @riven/utils — shared utilities
│   │   ├── src/
│   │   └── package.json
│   ├── tsconfig/                   # @riven/tsconfig — shared TypeScript config
│   │   ├── base.json
│   │   ├── package.json
│   │   └── ...
│   └── README.md
├── docs/                           # System design and architecture vault
│   ├── system-design/              # Obsidian vault
│   │   ├── decisions/              # ADRs
│   │   ├── domains/                # Domain documentation
│   │   ├── feature-design/         # Feature specifications
│   │   ├── flows/                  # Data flow diagrams
│   │   ├── infrastructure/
│   │   ├── integrations/
│   │   └── ...
│   ├── frontend-design/
│   ├── templates/
│   └── .obsidian/
├── .planning/                      # Phase planning artifacts
│   ├── codebase/                   # Codebase analysis docs (ARCHITECTURE.md, STRUCTURE.md, etc.)
│   └── phases/
├── docker-compose.yml              # Local development services
├── .env.example                    # Environment template
├── package.json                    # Root package (pnpm workspace)
├── pnpm-workspace.yaml             # Workspace definition
├── pnpm-lock.yaml                  # Lock file (legacy; npm is canonical)
├── turbo.json                      # Turborepo configuration
├── readme.md                       # Project overview
├── TODOS.md                        # Task tracking
└── CLAUDE.md                       # Workspace-level instructions
```

## Directory Purposes

**apps/**
- Purpose: Standalone Next.js applications with their own entry points, routing, and build outputs
- Contains: Pages, components, hooks, services specific to each app
- Committed: Yes

**core/**
- Purpose: Spring Boot backend API server with domain-driven organization
- Contains: Controllers, services, repositories, entities, models, configuration, database schema
- Committed: Yes

**packages/**
- Purpose: Shared libraries consumed by both frontend apps via pnpm workspace
- Contains: Reusable UI components, hooks, utilities, TypeScript config
- Committed: Yes

**docs/**
- Purpose: Architecture vault (Obsidian format) and design documentation
- Contains: System design decisions, domain overviews, feature specifications, integration docs
- Committed: Yes

**.planning/**
- Purpose: Phase planning and codebase analysis artifacts
- Contains: Task breakdowns, execution plans, codebase maps (ARCHITECTURE.md, STRUCTURE.md, etc.)
- Generated: During planning phase
- Committed: Yes

**core/db/schema/**
- Purpose: Database schema management (declarative, not incremental)
- Contains: SQL files organized by execution phase (extensions → tables → indexes → functions → constraints → RLS → triggers → grants)
- Pattern: Files are declarative — when a table definition changes, edit the original file directly; do not create migration scripts. Flyway will execute all files on startup.
- Committed: Yes

## Key File Locations

**Backend Entry Points:**
- `core/src/main/kotlin/riven/core/CoreApplication.kt` — Spring Boot application class
- `core/build.gradle.kts` — Gradle build configuration, dependencies
- `core/src/main/resources/application.yml` — Spring Boot properties

**Frontend Entry Points:**
- `apps/client/app/layout.tsx` — Root layout with providers (Auth, Query, Store, Theme)
- `apps/client/app/dashboard/layout.tsx` — Protected dashboard layout
- `apps/client/app/page.tsx` — Home page (redirects based on auth state)
- `apps/web/app/layout.tsx` — Marketing site root layout

**Configuration:**
- `core/src/main/kotlin/riven/core/configuration/properties/` — `@ConfigurationProperties` beans (auth, workspace, integration, etc.)
- `.env.example` — Environment variables template (root level)
- `docker-compose.yml` — Local development services (PostgreSQL, Temporal, etc.)

**Core Logic:**
- `core/src/main/kotlin/riven/core/service/{domain}/` — Business logic for each domain
- `core/src/main/kotlin/riven/core/repository/{domain}/` — Database queries for each domain
- `apps/client/components/feature-modules/{feature}/` — Feature-specific frontend code

**Testing:**
- `core/src/test/kotlin/riven/core/` — Backend unit/integration tests (mirrors source structure)
- `apps/client/test/` — Frontend test utilities and tests

**API Definitions:**
- `core/src/main/kotlin/riven/core/controller/{domain}/` — OpenAPI-annotated endpoints
- `apps/client/lib/types/` — Generated OpenAPI types from backend spec (do not edit manually)
- `apps/client/scripts/generate-types.sh` — Regenerate types from backend spec

**Database Schema:**
- `core/db/schema/01_tables/*.sql` — Main table definitions
- `core/db/schema/02_indexes/*.sql` — Index definitions
- `core/db/schema/05_rls/*.sql` — Row-level security policies
- `core/db/README.md` — Schema execution order and guidelines

## Naming Conventions

**Files:**
- Backend: `PascalCase.kt` (JPA entities, services, controllers, exceptions)
  - Examples: `EntityService.kt`, `EntityRepository.kt`, `EntityEntity.kt`, `NotFoundException.kt`
- Frontend: `kebab-case.tsx` (components, hooks, utilities)
  - Examples: `entity-form.tsx`, `use-entity-mutation.ts`, `entity.store.ts`
  - Exception: Some legacy PascalCase files in `components/ui/` (migrate to kebab-case on next touch)

**Directories:**
- Backend: lowercase with hyphens (though most are singular)
  - Examples: `controller/`, `service/`, `repository/`, `entity/`, `models/`
- Frontend: lowercase
  - Examples: `feature-modules/`, `components/`, `hooks/`, `lib/`, `stores/`

**Functions/Methods:**
- Backend (Kotlin): `camelCase` verb-first for actions, `camelCase` noun-based for queries
  - Examples: `saveEntity()`, `deleteEntityType()`, `getEntitiesByTypeId()`, `isWorkspaceAdmin()`
- Frontend (TypeScript): `camelCase` for functions; React hooks prefixed with `use`
  - Examples: `formatDate()`, `useEntities()`, `useSaveEntityMutation()`, `normalizeApiError()`

**Variables/Properties:**
- Backend: `camelCase`
- Frontend: `camelCase`

**Types/Interfaces:**
- Backend (Kotlin): `PascalCase` for data classes, enums, interfaces
  - Examples: `SaveEntityRequest`, `Entity`, `EntityStatus`
- Frontend (TypeScript): `PascalCase` for types, interfaces; lowercase for type aliases
  - Examples: `SaveEntityRequest`, `type EntityStore = ...`

**Constants:**
- Backend: `UPPER_SNAKE_CASE`
- Frontend: `UPPER_SNAKE_CASE` (rarely used; prefer enums or configs)

## Where to Add New Code

**New Feature in Existing Domain:**
- **Backend:**
  - Controller method: `core/src/main/kotlin/riven/core/controller/{domain}/{Domain}Controller.kt`
  - Service logic: `core/src/main/kotlin/riven/core/service/{domain}/{NewConcern}Service.kt`
  - Models: `core/src/main/kotlin/riven/core/models/request/{domain}/` and `response/{domain}/`
  - Tests: `core/src/test/kotlin/riven/core/service/{domain}/{NewConcern}ServiceTest.kt`
- **Frontend:**
  - Feature code: `apps/client/components/feature-modules/{feature}/` (add subdirs as needed)
  - Hooks: `apps/client/components/feature-modules/{feature}/hooks/`
  - Service: `apps/client/components/feature-modules/{feature}/service/`
  - Tests: Co-located with source file as `.test.ts` or `.test.tsx`

**New Domain (rare):**
- **Backend:**
  - Create directories: `controller/{new-domain}/`, `service/{new-domain}/`, `repository/{new-domain}/`, `entity/{new-domain}/`, `models/request/{new-domain}/`, `models/response/{new-domain}/`, `enums/{new-domain}/`
  - Main service: `service/{new-domain}/{NewDomain}Service.kt`
  - Main controller: `controller/{new-domain}/{NewDomain}Controller.kt`
  - Create entity base class if needed: `entity/{new-domain}/{NewDomain}Entity.kt`
  - Database schema: `core/db/schema/01_tables/_{new_domain}_*.sql`
- **Frontend:**
  - Create feature module: `components/feature-modules/{new-feature}/`
  - Internal structure: `components/`, `hooks/query/`, `hooks/mutation/`, `service/`, `store/`, `context/`

**New Shared Component/Hook:**
- **Shared UI Component:** `packages/ui/src/{ComponentName}.tsx`
- **Shared React Hook:** `packages/hooks/src/use{HookName}.ts`
- **Shared Utility:** `packages/utils/src/{utilityName}.ts`
- Re-export in workspace packages' `package.json` and from feature modules via `@riven/{ui,hooks,utils}` import aliases

**New Utility Function:**
- **Backend:** `core/src/main/kotlin/riven/core/util/{UtilName}.kt` or within service as `private fun`
- **Frontend:** `apps/client/lib/util/` (create domain-specific file if needed, e.g., `lib/util/entity-util.ts`)
  - Never inline utility functions in component files

## Special Directories

**core/db/schema/**
- Purpose: Declarative database schema (execute once at startup)
- Generated: No (hand-written SQL)
- Committed: Yes
- Execution order: 00 → 01 → 02 → 03 → 04 → 05 → 08 → 09 (via Flyway)
- Pattern: Edit existing files directly; do not create migration-style incremental scripts. Files declare desired state, not changes.

**apps/client/lib/types/**
- Purpose: OpenAPI-generated types and domain type barrels
- Generated: Yes (from `npm run types` script)
- Committed: Yes (generated files are checked in for offline builds)
- Manual edits: Only in domain barrel files (`lib/types/{domain}/index.ts`, `lib/types/{domain}/custom.ts`)
- Never edit: `apis/`, `models/`, `runtime.ts` — these are overwritten on regeneration

**apps/client/.turbo/**
- Purpose: Turborepo cache
- Generated: Yes (during build)
- Committed: No

**core/.turbo/**
- Purpose: Turborepo cache (backend)
- Generated: Yes
- Committed: No

**core/src/test/kotlin/**
- Purpose: Backend test files
- Contains: JUnit 5 tests, test factories, fixtures
- Pattern: Mirrors `src/main/kotlin/` structure. Test class names: `{SourceClass}Test.kt`.

**apps/client/test/**
- Purpose: Frontend test configuration and shared test utilities
- Contains: Jest config, test setup, mock factories
- Test files co-located: `{source}.test.ts` or `{source}.test.tsx` in same directory as source

**docs/system-design/**
- Purpose: Architecture vault (Obsidian format)
- Committed: Yes
- Generated: No (human-written)
- Structure: `domains/`, `decisions/`, `feature-design/`, `flows/`, `infrastructure/`

**.planning/codebase/**
- Purpose: Codebase analysis documents (written by mapper agent)
- Committed: Yes
- Generated: Yes (by `/gsd:map-codebase`)
- Files: ARCHITECTURE.md, STRUCTURE.md, CONVENTIONS.md, TESTING.md, STACK.md, INTEGRATIONS.md, CONCERNS.md

## Import Paths

**Backend (Kotlin):**
- Package structure: `riven.core.{layer}.{domain}`
- Examples: `riven.core.controller.entity`, `riven.core.service.workflow`, `riven.core.entity.integration`
- No path aliases; all imports are fully qualified

**Frontend (TypeScript):**
- All imports use `@/*` path alias (configured in `tsconfig.json`)
- Never use relative imports
- Examples:
  - `@/components/ui/button` (shared UI)
  - `@/components/feature-modules/entity/hooks/query/use-entities` (feature-scoped)
  - `@/lib/types/entity` (domain type barrel)
  - `@/lib/util/utils` (shared utilities)
  - `@/lib/api/create-entity-api` (API factory)
- Generated types: Import from domain barrels (`@/lib/types/entity`), not from `@/lib/types/models/`

## Monorepo Layout

**Turborepo configuration:** `turbo.json` at root
- Orchestrates `pnpm` scripts across `apps/` and `packages/`
- Shared build cache and task dependencies

**pnpm workspace:** `pnpm-workspace.yaml`
- Includes: `apps/*`, `packages/*`, `apps/web/.react-email`
- Lockfile: `pnpm-lock.yaml` (legacy; npm is canonical per `package.json` `packageManager` field)

**Package manager:** Root `package.json` specifies `pnpm@10.28.2` as canonical
- Install: `pnpm install` at root
- Scripts: `pnpm dev` (all dev servers), `pnpm build` (all builds), `pnpm lint`, `pnpm type-check`

**Workspace dependencies:** Referenced as `workspace:*` in package.json
- Example: `@riven/ui`, `@riven/hooks`, `@riven/utils`, `@riven/tsconfig`
- Resolved locally; no npm publish (private workspace packages)
