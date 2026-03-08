# Architecture

**Analysis Date:** 2026-03-08

## Pattern Overview

**Overall:** Feature-module monolith with layered service architecture inside a Next.js 15 App Router shell.

**Key Characteristics:**
- Next.js App Router pages are thin wrappers that delegate to feature-module components
- Each feature module is self-contained with its own components, hooks, services, stores, and context providers
- Data flows through a strict layer chain: Component → Query/Mutation Hook → Service → API Factory → Generated OpenAPI Client
- Auth is provider-agnostic via an adapter pattern; route protection is client-side only (no middleware)
- State is split between TanStack Query (server state) and Zustand (client state), with React Context for scoped store provision

## Layers

**App Router Pages (Routing Shell):**
- Purpose: URL-to-component mapping and layout nesting
- Location: `app/`
- Contains: Thin page components, layouts, one API route
- Depends on: Feature module components
- Used by: Next.js router

**Feature Modules (Domain Logic):**
- Purpose: Encapsulate all logic for a business domain
- Location: `components/feature-modules/{domain}/`
- Contains: Components, hooks (query/mutation/form), services, stores, context providers, utilities
- Depends on: Shared UI (`components/ui/`), generated types (`lib/types/`), API factories (`lib/api/`), auth (`lib/auth/`), shared utils (`lib/util/`)
- Used by: App Router pages

**Shared UI (Design System):**
- Purpose: Reusable UI primitives and layout components
- Location: `components/ui/`
- Contains: shadcn/ui components, data-table, sidebar, navbar, rich-editor, form widgets, icon system
- Depends on: `lib/util/utils.ts` (for `cn()`), Tailwind CSS
- Used by: Feature module components

**API Client Layer:**
- Purpose: Create authenticated API client instances per domain
- Location: `lib/api/`
- Contains: Factory functions (`create{Domain}Api(session)`) that instantiate generated API classes with auth tokens
- Depends on: Generated types (`lib/types/`), auth types (`lib/auth/`)
- Used by: Service classes in feature modules

**Generated Types Layer:**
- Purpose: TypeScript types, API classes, and runtime from OpenAPI spec
- Location: `lib/types/` (generated: `apis/`, `models/`, `runtime.ts`; hand-written: `entity/`, `workspace/`, `block/`, `workflow/`, `user/`, `common/`)
- Contains: Generated fetch-based API classes, model types, domain barrel re-exports with custom types/guards
- Depends on: Nothing (generated code)
- Used by: API factories, services, components, hooks

**Auth Layer:**
- Purpose: Provider-agnostic authentication with adapter pattern
- Location: `lib/auth/` (interface + factory), `lib/auth/adapters/supabase/` (implementation), `components/provider/auth-context.tsx` (React context)
- Contains: `AuthProvider` interface, `SupabaseAuthAdapter`, `AuthError` types, `createAuthProvider()` factory
- Depends on: Supabase client (`lib/util/supabase/`)
- Used by: `AuthProvider` context, `AuthGuard`, all query/mutation hooks (via `useAuth()`)

**Utilities Layer:**
- Purpose: Shared helpers, error handling, form utilities, validation
- Location: `lib/util/`
- Contains: `cn()`, `uuid()`, `get()`/`set()` path helpers, error normalization, form schema builders, service validation helpers
- Depends on: clsx, tailwind-merge, dayjs, zod, validator
- Used by: All layers

## Data Flow

**Read (Query) Flow:**

1. Component calls query hook (e.g., `useEntities(workspaceId, typeId)`)
2. Hook calls `useAuth()` to get session, gates query with `enabled: !!session && !!params`
3. Hook's `queryFn` calls static service method (e.g., `EntityService.getEntitiesForType(session, workspaceId, typeId)`)
4. Service validates session and params, then calls `createEntityApi(session)` to get authenticated API instance
5. Service calls generated API method (e.g., `api.getEntityByTypeIdForWorkspace(...)`)
6. Generated client makes fetch request with bearer token to Spring Boot backend
7. Response flows back through the chain; TanStack Query caches the result

**Write (Mutation) Flow:**

1. Component calls mutation hook (e.g., `useSaveEntityMutation(workspaceId, entityTypeId)`)
2. Hook shows `toast.loading()` on mutate
3. Hook calls service method with payload
4. Service creates API instance and calls generated API method
5. On success: hook updates TanStack Query cache via `queryClient.setQueryData()` (optimistic-style) or `queryClient.invalidateQueries()`, shows `toast.success()`
6. On error: hook shows `toast.error()` with message

**Auth Flow:**

1. `AuthProvider` in root layout creates auth provider singleton via `createAuthProvider()`
2. Provider subscribes to `onAuthStateChange` and stores session in React state
3. `AuthGuard` wraps dashboard layout; if no session after loading, redirects to `/auth/login`
4. OAuth callback handled by server-side route at `app/api/auth/token/callback/route.ts`
5. All API calls receive session from `useAuth()` and pass it to service methods

**State Management:**
- **Server state:** TanStack Query manages all API data. Query keys follow `['domain', ...params]` pattern (e.g., `['entities', workspaceId, typeId]`)
- **Client state:** Zustand stores manage UI state (selected workspace, draft forms, editor state, workflow canvas)
- **Form state:** react-hook-form instances are created in context providers and passed to Zustand stores for coordination
- **Store provision:** Zustand stores are provided via React Context (factory pattern: `createXxxStore()` → `useRef` → `Context.Provider`), except `editor-store.ts` which is a global singleton

## Key Abstractions

**Feature Module:**
- Purpose: Self-contained domain encapsulation
- Examples: `components/feature-modules/entity/`, `components/feature-modules/workspace/`, `components/feature-modules/workflow/`, `components/feature-modules/blocks/`, `components/feature-modules/user/`
- Pattern: Each module contains `components/`, `hooks/{query,mutation,form}/`, `service/`, `stores/` or `store/`, `context/`, `util/`

**Service Class:**
- Purpose: Wrap generated API calls with validation and error handling
- Examples: `components/feature-modules/entity/service/entity.service.ts`, `components/feature-modules/workspace/service/workspace.service.ts`, `components/feature-modules/workflow/service/workflow.service.ts`
- Pattern: Static methods that accept `session`, validate inputs with `validateSession()`/`validateUuid()`, create API instance via factory, call generated API method, handle/rethrow errors

**API Factory:**
- Purpose: Create authenticated instances of generated API classes
- Examples: `lib/api/entity-api.ts`, `lib/api/workspace-api.ts`, `lib/api/workflow-api.ts`, `lib/api/block-api.ts`, `lib/api/user-api.ts`, `lib/api/knowledge-api.ts`
- Pattern: `create{Domain}Api(session)` → `new Configuration({ basePath, accessToken })` → `new {Domain}Api(config)`

**Domain Type Barrel:**
- Purpose: Clean import paths for generated + custom types per domain
- Examples: `lib/types/entity/index.ts`, `lib/types/workspace/index.ts`, `lib/types/workflow/index.ts`
- Pattern: Re-exports from `models.ts` (generated re-exports), `custom.ts` (hand-written types), `guards.ts` (type guards), `requests.ts`, `responses.ts`

**Context Provider (Store Scoping):**
- Purpose: Provide scoped Zustand store instances to a component subtree
- Examples: `components/feature-modules/entity/context/entity-provider.tsx`, `components/feature-modules/entity/context/configuration-provider.tsx`, `components/feature-modules/workspace/provider/workspace-provider.tsx`, `components/feature-modules/workflow/context/workflow-canvas-provider.tsx`
- Pattern: `useRef(createXxxStore())` → `XxxContext.Provider value={storeRef.current}` → `useXxxStore(selector)` custom hook with context lookup

**AuthenticatedQueryResult:**
- Purpose: Extend TanStack Query result with auth loading state
- Examples: `lib/interfaces/interface.ts` defines `AuthenticatedQueryResult<T>` and `AuthenticatedMultiQueryResult<T>`
- Pattern: Query hooks return `{ ...useQuery(), isLoadingAuth: loading }` so components can distinguish auth loading from data loading

## Entry Points

**Root Page (`/`):**
- Location: `app/page.tsx`
- Triggers: Direct navigation to `/`
- Responsibilities: Renders `RootRedirect` which checks auth state and redirects to `/auth/login` or `/dashboard/workspace`

**Root Layout:**
- Location: `app/layout.tsx`
- Triggers: Every page render
- Responsibilities: Provider tree setup: `ThemeProvider` → `AuthProvider` → `QueryClientWrapper` → `StoreProviderWrapper` → `<main>`. Loads fonts (Geist, Geist Mono, Instrument Serif, Space Mono). Renders `<Toaster>`.

**Dashboard Layout:**
- Location: `app/dashboard/layout.tsx`
- Triggers: Any `/dashboard/**` route
- Responsibilities: Wraps children with `AuthGuard` → `OnboardWrapper` → `IconRailProvider` → sidebar/navbar shell (`IconRail`, `SubPanel`, `DashboardContent`, `Navbar`)

**Auth Layout:**
- Location: `app/auth/layout.tsx`
- Triggers: `/auth/**` routes
- Responsibilities: Passthrough (renders children only)

**OAuth Callback:**
- Location: `app/api/auth/token/callback/route.ts`
- Triggers: OAuth provider redirect after authentication
- Responsibilities: Exchanges auth code for session via Supabase, redirects to dashboard or error page

## Error Handling

**Strategy:** Per-mutation error handling with toast notifications. No global error boundary or global query error handler.

**Patterns:**
- Services use `validateSession()` and `validateUuid()` for input validation, throwing `ResponseError` on failure (`lib/util/service/service.util.ts`)
- Services catch `ResponseError` from generated API client for specific HTTP statuses (e.g., 400, 409) and return parsed response body instead of throwing
- `normalizeApiError()` (`lib/util/error/error.util.ts`) unwraps OpenAPI `ResponseError`, parses JSON body, rethrows as app-level `ResponseError` with `{ status, error, message }`
- `fromError()` (`lib/util/error/error.util.ts`) converts any unknown error to a normalized `ResponseError`
- Mutation hooks handle errors with `toast.error()` in `onError` callback
- Auth errors use `AuthError` class (`lib/auth/auth-error.ts`) with typed `AuthErrorCode` enum and `getAuthErrorMessage()` for user-facing messages (`lib/auth/error-messages.ts`)

## Cross-Cutting Concerns

**Logging:** No structured logging framework. `console.error` in OAuth callback route. Toast notifications serve as user-visible "logging".

**Validation:** Dual: Zod schemas for form validation (react-hook-form + `zodResolver`), `validateSession()`/`validateUuid()` for service-layer input validation. Entity instance forms use dynamically-built Zod schemas from entity type definitions (`lib/util/form/entity-instance-validation.util.ts`).

**Authentication:** Client-side only via `AuthGuard` component wrapping dashboard layout. No Next.js middleware. Session passed through React Context (`useAuth()`). Token attached to API calls via `Configuration({ accessToken })` in API factories.

---

*Architecture analysis: 2026-03-08*
