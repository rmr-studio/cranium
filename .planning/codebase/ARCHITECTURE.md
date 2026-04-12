# Architecture

**Analysis Date:** 2026-04-12

## Pattern Overview

**Overall:** Layered monorepo with domain-driven organization. Riven is a multi-workspace, multi-tenant platform with three primary applications (backend API, dashboard frontend, marketing site) and shared libraries. Backend uses layered Spring Boot architecture (Controller → Service → Repository → Entity) with domain-based vertical slicing.

**Key Characteristics:**
- Backend: Layered Spring Boot 3.5.3 + Kotlin, single Gradle module with domain-scoped packages
- Frontend: Next.js 15 (App Router) with hybrid SSR/client approach, feature-module-based organization
- Shared: Monorepo with pnpm workspace, Turborepo orchestration
- Multi-tenancy: Workspace-scoped all data; security enforced via `@PreAuthorize` annotations
- Async workflows: Temporal server integration for DAG-based automation

## Layers

**Backend — Controller Layer:**
- Purpose: HTTP request handling, request/response transformation, Swagger documentation
- Location: `core/src/main/kotlin/riven/core/controller/{domain}/`
- Contains: `@RestController` classes, endpoint mapping, API versioning (`/api/v1/`)
- Depends on: Service layer only
- Used by: HTTP clients (frontend, external APIs)
- Pattern: Thin delegation to services; controllers do not contain business logic. All `@PreAuthorize` annotations are on service methods, not controllers. Return `ResponseEntity<T>` with appropriate HTTP status.

**Backend — Service Layer:**
- Purpose: Business logic, orchestration, cross-domain coordination, workspace security checks
- Location: `core/src/main/kotlin/riven/core/service/{domain}/`
- Contains: Service classes, method-level `@PreAuthorize`, transaction boundaries, domain workflows
- Depends on: Repositories, other services, external clients (Supabase, Temporal)
- Used by: Controllers, other services, async tasks
- Pattern: One service per domain responsibility (e.g., `EntityTypeService`, `EntityService`, `EntityRelationshipService` — not a monolithic service). Constructor injection. `@Transactional` on multi-write methods. Methods named after actions: `create()`, `update()`, `delete()`, `resolve()`.

**Backend — Repository Layer:**
- Purpose: Data access abstraction, query execution, JPA/Hibernate delegation
- Location: `core/src/main/kotlin/riven/core/repository/{domain}/`
- Contains: Spring Data JPA interfaces extending `JpaRepository` or `CrudRepository`, custom `@Query` methods
- Depends on: JPA entities only
- Used by: Services
- Pattern: JPQL `@Query` preferred; native SQL only for truly complex operations. Soft-deleted rows are auto-filtered via `@SQLRestriction` on entity base classes. Do not manually add `AND deleted = false` to queries.

**Backend — Entity Layer (JPA):**
- Purpose: ORM mapping, database representation, Hibernate schema
- Location: `core/src/main/kotlin/riven/core/entity/{domain}/`
- Contains: Data classes with `@Entity`, `@Table`, validation annotations, `toModel()` conversion methods
- Depends on: Nothing; entities are leaf nodes
- Used by: Repositories
- Pattern: User-facing entities extend `AuditableEntity` and implement `SoftDeletable`. System-managed entities (catalog, definitions) do not. Include `toModel()` method for entity → domain model conversion. UUIDs as primary keys, auto-generated. Foreign keys to other aggregates are `UUID` fields, not object references (avoid nested object graphs).

**Backend — Model Layer (Domain):**
- Purpose: Domain-driven data structures, request/response DTOs, enums
- Location: `core/src/main/kotlin/riven/core/models/{domain}/` and `core/src/main/kotlin/riven/core/models/request/` and `core/src/main/kotlin/riven/core/models/response/`
- Contains: Data classes for domain concepts, request/response objects, enum definitions
- Depends on: Nothing; standalone
- Used by: Controllers (request/response mapping), services, external serialization
- Pattern: Data classes. Enums over string literals. Use `@JsonProperty` for custom JSON serialization. Request/response objects are separate from domain models.

**Frontend — Page Layer:**
- Purpose: Route handlers and thin wrappers around feature components
- Location: `apps/client/app/{feature}/page.tsx` (App Router)
- Contains: Page component definitions, segment parameters
- Depends on: Feature components
- Used by: Next.js router
- Pattern: Pages are typically server components that import a single client feature component. Some pages are entirely client-side (`"use client"` at page level). No consistent boundary.

**Frontend — Feature Module Layer:**
- Purpose: Feature-specific state, hooks, services, and UI components
- Location: `apps/client/components/feature-modules/{feature}/`
- Contains: Subdirectories: `components/`, `hooks/query/`, `hooks/mutation/`, `hooks/form/`, `service/`, `store/` or `stores/`, `context/`
- Depends on: Shared UI, shared hooks, libraries, other features via API
- Used by: Pages, other feature modules
- Pattern: Encapsulation by feature. Context providers for feature-scoped state (Zustand stores with React Context). Query/mutation hooks co-located. Services wrap API calls and error normalization. No barrel exports at feature-module level.

**Frontend — Shared Layer:**
- Purpose: Reusable components, hooks, utilities, type definitions
- Location: `apps/client/components/ui/`, `apps/client/hooks/`, `apps/client/lib/`
- Contains: shadcn/ui components, TanStack Query setup, utility functions, auth providers, API factory functions
- Depends on: External libraries (React, TanStack Query, Zustand, Supabase)
- Used by: Feature modules, pages
- Pattern: `cn()` for conditional styling. All utility functions in `lib/util/` (never inline in components). Type definitions in `lib/types/` (generated from OpenAPI spec or hand-written domain barrels). API factories in `lib/api/`.

**Shared Libraries:**
- Location: `packages/{hooks,ui,utils,tsconfig}/`
- Purpose: Cross-app reusable code
- Contains: `@riven/hooks` (custom React hooks), `@riven/ui` (reusable UI components), `@riven/utils` (shared utilities), `@riven/tsconfig` (TypeScript config)
- Used by: All frontend apps
- Pattern: Versioned as workspace dependencies (`workspace:*`).

## Data Flow

**Create Entity Instance:**

1. User submits form in dashboard (`apps/client/components/feature-modules/entity/`)
2. `useSaveEntityMutation` hook calls `EntityApi.postEntity(request)` with JWT access token
3. API client routes to `POST /api/v1/entity/workspace/{workspaceId}/type/{entityTypeId}`
4. `EntityController.saveEntity()` delegates to `EntityService.saveEntity()`
5. `EntityService` calls `EntityTypeService.getEntityType()` to fetch schema, validates payload against schema via `SchemaService`
6. On save, inserts to `entities` table via `EntityRepository.save(entity)`
7. Activity logged via `ActivityService.logActivity()` with operation type and entity ID
8. Response mapped to `SaveEntityResponse` DTO
9. Frontend mutation hook invalidates `['entities', workspaceId, typeId]` cache, shows toast
10. Component re-renders with fresh data from cache

**Workflow Execution:**

1. User publishes workflow definition in dashboard
2. Frontend calls `WorkflowDefinitionApi.publishWorkflowDefinition()`
3. Backend `WorkflowDefinitionController.publishWorkflowDefinition()` → `WorkflowDefinitionService`
4. Service saves definition to database, emits event via `WorkflowEventService`
5. Event triggers async task that sends workflow graph to Temporal server via `TemporalWorkflowClient`
6. Temporal executes activities (defined in `WorkflowActivityImpl`) based on DAG structure
7. Activity results are stored; state machine updates workflow execution status
8. WebSocket message sent to dashboard via `WebSocketService.notifyWorkflowProgress()`
9. Dashboard listener receives message on raw binary WebSocket, updates React Flow visualization

**State Management Flow:**

1. Feature module creates Zustand store via `createXxxStore()` factory in `stores/xxx.store.ts`
2. Provider wrapper (`context/xxx-provider.tsx`) instantiates store once via `useRef`, provides via React Context
3. Components use `useXxxStore(selector)` hook to subscribe to slices
4. Store mutations trigger `queryClient.invalidateQueries()` to refresh API cache
5. TanStack Query refetches data, updates React state, component re-renders

**Multi-tenancy:**

1. All API endpoints include `workspaceId` path parameter
2. `@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")` annotation on service method checks JWT claims
3. If user lacks workspace role, `AccessDeniedException` thrown → `@ControllerAdvice` returns 403
4. Queries filtered by `workspace_id` at repository level (for user-facing data)
5. System entities (catalog, definitions) are workspace-agnostic (not filtered)

## Key Abstractions

**Entity Types and Instances:**
- Purpose: User-defined data model with dynamic schemas
- Examples: `core/src/main/kotlin/riven/core/entity/entity/EntityTypeEntity.kt`, `core/src/main/kotlin/riven/core/entity/entity/EntityEntity.kt`, `apps/client/components/feature-modules/entity/`
- Pattern: Type defines schema (name, attributes, relationships). Instance stores data conforming to that schema. Schema validation via JSON Schema (`SchemaService`). Relationships are uni-directional references.

**Blocks:**
- Purpose: Versioned content system with JSON-based structure
- Examples: `core/src/main/kotlin/riven/core/entity/block/BlockTypeEntity.kt`, `core/src/main/kotlin/riven/core/service/block/BlockService.kt`
- Pattern: Block types define structure (immutable, copy-on-write versioning). Blocks are instances with JSONB `data` column. Block environments hold execution state.

**Workflows:**
- Purpose: DAG-based automation with Temporal integration
- Examples: `core/src/main/kotlin/riven/core/entity/workflow/WorkflowDefinitionEntity.kt`, `core/src/main/kotlin/riven/core/service/workflow/`
- Pattern: Definition stores graph structure. Executions are temporal workflows + database state. Activities are Spring components.

**Integrations:**
- Purpose: External SaaS connectors with sync pipelines
- Examples: `core/src/main/kotlin/riven/core/entity/integration/IntegrationEntity.kt`, `core/src/main/kotlin/riven/core/service/integration/`
- Pattern: Definition holds configuration. Source layer (readonly, visible) stores raw data from connector. Projection layer aggregates multi-source data.

**Zustand Stores:**
- Purpose: Feature-scoped state management with React Context
- Examples: `apps/client/components/feature-modules/workspace/context/workspace-provider.tsx`, `apps/client/components/feature-modules/entity/context/entity-provider.tsx`
- Pattern: Factory function returns store API. Provider wraps tree, provides via React Context. Hooks consume context with selectors.

## Entry Points

**Backend API Server:**
- Location: `core/src/main/kotlin/riven/core/CoreApplication.kt`
- Triggers: Spring Boot startup
- Responsibilities: Bind port 8081, initialize Spring context, auto-wire beans, scan configuration properties
- Listens on: `http://localhost:8081`
- Exports: REST endpoints at `/api/v1/{domain}`, WebSocket at `/ws`, OpenAPI spec at `/docs/v3/api-docs`

**Frontend Dashboard:**
- Location: `apps/client/app/layout.tsx`, `apps/client/app/dashboard/layout.tsx`
- Triggers: Next.js routing to `/dashboard`
- Responsibilities: Initialize providers (Auth, Query, Store, Theme, WebSocket), render sidebar, navbar, main content
- Listens on: `http://localhost:3001`
- Exports: User-facing UI for workspace management, entity editing, workflow creation, integrations

**Frontend Marketing Site:**
- Location: `apps/web/app/layout.tsx`
- Triggers: Next.js routing to `/`
- Responsibilities: Render landing page, public content
- Listens on: `http://localhost:3000`

**Database:**
- Location: `core/db/schema/`
- Triggers: Application startup (Flyway migrations)
- Responsibilities: DDL, RLS policies, triggers, constraints
- Connection: PostgreSQL at `POSTGRES_DB_JDBC` env var

**Temporal Server:**
- Location: Standalone external service
- Triggers: `TemporalWorkflowClient` bean initialization
- Responsibilities: Execute workflow definitions, schedule activities, maintain execution state
- Connection: gRPC at `TEMPORAL_SERVER_ADDRESS` env var

## Error Handling

**Strategy:** Layered exception hierarchy with `@ControllerAdvice` centralized mapping.

**Patterns:**

- **Domain exceptions** (thrown by services): `NotFoundException`, `ConflictException`, `SchemaValidationException`, `UniqueConstraintViolationException`, `WorkflowValidationException`, `AccessDeniedException`
  - Location: `core/src/main/kotlin/riven/core/exceptions/`
  - Caught by: `ExceptionHandler` in `riven.core.exceptions`
  - Mapped to: HTTP status (404, 409, 400, 403, etc.)

- **Precondition validation** (in service methods): `require()`, `requireNotNull()`
  - Produces: `IllegalArgumentException`
  - Caught by: Global advice (returns 400)

- **API client errors** (OpenAPI generated): `ResponseError` from generated `runtime.ts`
  - Normalized by: Service-level try-catch in query/mutation hooks
  - Displayed to: User via `toast.error()`

- **Auth errors** (Supabase): `AuthError` from `lib/auth/auth-error.ts`
  - Mapped by: `getAuthErrorMessage()` utility
  - Displayed to: User-friendly messages in auth flows

- **Validation errors** (form): `zod.ZodError` from react-hook-form
  - Handled by: Form submission, inline error display via `form.setError()`

## Cross-Cutting Concerns

**Logging:**
- Backend: `KLogger` injected via constructor using prototype bean from `LoggerConfig`. Usage: `logger.info("message", e)`, `logger.error("message", exception)`.
- Frontend: `console.log()`, no structured logging
- Location: Service methods for business operations, controllers for HTTP events

**Validation:**
- Backend: `SchemaService` validates JSON payloads against JSON Schema. Jakarta validation annotations on entities. `require()`/`requireNotNull()` in service methods.
- Frontend: Zod schemas with react-hook-form integration. Server-side validation via API errors.

**Authentication:**
- Backend: Supabase JWT verification via `AuthTokenService`. `@PreAuthorize` with JWT claims (roles, workspaceId).
- Frontend: Supabase session management via `AuthProvider`. `useAuth()` hook. Protected routes via `AuthGuard`.

**Authorization:**
- Backend: Workspace-scoped via `@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")`. Role-based access control via JWT claims.
- Frontend: Query/mutation enabled only when authenticated (`enabled: !!session`). No UI elements for unauthorized actions (role-based visibility in future).

**Activity Logging:**
- Backend: `ActivityService.logActivity()` called from service methods on create/update/delete. Records operation, userId, workspaceId, entityId, details.
- Stored in: `activity_log` table
- Used by: Audit trail, undo/recovery features (planned)

**Rate Limiting:**
- Backend: Implemented via Bucket4j in `RateLimitFilter` in `core/src/main/kotlin/riven/core/filter/ratelimit/`
- Per IP or per user (configurable via properties)

**WebSocket Communication:**
- Backend: STOMP for app notifications on `/api/v1/websocket`. Raw binary for Yjs collaboration (planned).
- Frontend: `WebSocketProvider` manages connection. Handlers in feature modules subscribe to topics.

**Transaction Management:**
- Backend: Service methods marked with `@Transactional` for multi-write operations. Repository operations are individually transactional by default.
- Rollback on: Exception, validation failure

**Caching:**
- Frontend: TanStack Query with configurable `staleTime`, `gcTime`, `retry` per hook. Manual invalidation on mutations.
- Backend: No frontend-side cache invalidation coordination; each query hook decides invalidation strategy independently.
