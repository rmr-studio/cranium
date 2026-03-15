# CLAUDE.md — Riven Client

## Project Overview

Riven is a unified business tooling SaaS platform that aims to connect all tools that a business uses together through integrations and flexible data modelling to provide contextual knowledge and cross domain intelligence. This is the Next.js 15 frontend (App Router, React 19, TypeScript strict) backed by a Spring Boot API at `localhost:8081`. Auth aims to be agnostic, through implementable interfaces. But is currently using supabase as its primary source. The API contract is defined by an OpenAPI spec served by the backend, with types generated via `openapi-generator-cli` (typescript-fetch) into `lib/types/`. UI is built with shadcn/ui (new-york style) + Tailwind 4 + Framer Motion.

**Route groups:**

- `/` — Auth-aware redirect (authenticated → last workspace or `/dashboard/workspace`, unauthenticated → `/auth/login`)
- `/auth/login`, `/auth/register` — Supabase auth flows
- `/dashboard` — Authenticated app shell (sidebar, navbar, onboarding wrapper), protected by `AuthGuard`
- `/dashboard/workspace/[workspaceId]/entity/**` — Entity type management, data tables, schema config
- `/dashboard/workspace/[workspaceId]/workflow/**` — Visual workflow builder (React Flow)
- `/dashboard/settings`, `/dashboard/templates` — User settings, templates

**API contract:** OpenAPI spec fetched at generation time from `http://localhost:8081/docs/v3/api-docs`. Generated output in `lib/types/`. Regenerate with `npm run types`.

## Architecture Rules

**App Router conventions:** Flat route structure under `app/`. Dynamic segments use `[workspaceId]`, `[key]`, `[workflowId]`. No route groups `(groupName)`, no parallel routes, no intercepting routes. Pages are thin wrappers that render a single feature component.

**Server vs Client components:** The root layout (`app/layout.tsx`) is a server component. All providers (`AuthProvider`, `QueryClientWrapper`, `StoreProviderWrapper`, `ThemeProvider`) are client components. Pages are mixed — some have `"use client"` at page level, others are server components that delegate to a client feature component. The pattern is ad-hoc — there is no consistent boundary strategy. **Standardise on:** pages as server components that import a single client feature component.

**Layout hierarchy:** Root layout → providers (Theme → Auth → QueryClient → Store) → `<main>`. Dashboard layout adds `AuthGuard`, `OnboardWrapper`, `SidebarProvider`, `DashboardSidebar`, `AppNavbar`. Auth layout is a simple passthrough.

**Feature organization:** Hybrid. Feature code lives in `components/feature-modules/{feature}/` with subdirectories: `components/`, `hooks/query/`, `hooks/mutation/`, `hooks/form/`, `service/`, `store/` (or `stores/`), `context/`. Shared UI lives in `components/ui/`. Shared hooks in `hooks/`. Shared utils in `lib/util/`.

**Import conventions:** Always use the `@/*` path alias — never use relative imports. This applies to all imports: intra-feature, cross-feature, shared, library, and test files. Direct imports — no barrel exports at feature-module level. Domain type barrels exist at `lib/types/{domain}/index.ts`. Import generated types from domain barrels (`@/lib/types/entity`), not from `@/lib/types/models/` directly.

## Data Fetching and State

### TanStack Query

- Query hooks live co-located with features: `components/feature-modules/{feature}/hooks/query/`.
- Naming: `use{Entity}` or `use{Entity}s` (e.g., `useWorkspace`, `useEntities`, `useEntityTypes`, `useProfile`). No `get`/`fetch`/`Query` suffix.
- Query keys: string arrays — `['workspace', workspaceId]`, `['entities', workspaceId, typeId]`, `['userProfile', userId]`.
- **No default QueryClient options.** Each hook sets its own `staleTime` (typically `5 * 60 * 1000`), `gcTime`, `retry`, `enabled`. Standardise: consider adding defaults to `QueryClientWrapper`.
- Mutations live in `hooks/mutation/`. Pattern: `useSave{Entity}Mutation`, `useDelete{Entity}Mutation`, `usePublish{Entity}TypeMutation`. Use `toast.loading()` on mutate, `toast.success()`/`toast.error()` on settle, `toast.dismiss()` to clear. Cache update via `queryClient.invalidateQueries` or `queryClient.setQueryData` for optimistic-style updates.
- Auth-gated queries use `enabled: !!session && !!requiredParam && !loading`. Return type wraps `UseQueryResult` with `isLoadingAuth` flag via `AuthenticatedQueryResult<T>` from `lib/interfaces/interface.ts`.

### OpenAPI Type Generation

- **Tool:** `@openapitools/openapi-generator-cli` v7.19 with `typescript-fetch` generator.
- **Spec source:** Fetched live from `http://localhost:8081/docs/v3/api-docs` (backend must be running).
- **Generated output:** `lib/types/` — `runtime.ts` (fetch-based BaseAPI, Configuration), `apis/` (API classes), `models/` (type definitions).
- **Custom domain types:** Hand-written barrels in `lib/types/{entity,workspace,block,workflow,user,common}/` that re-export from generated models and add custom types, guards, request/response types.
- **Regenerate:** `npm run types` (runs `scripts/generate-types.sh`).
- Generated types are used directly. Domain barrels re-export them for cleaner imports.
- Generated API classes are instantiated via factory functions in `lib/api/` (e.g., `createWorkspaceApi(session)`).

- **Ignored generated files:** The generator also produces Angular-style files (`api.base.service.ts`, `configuration.ts`, `encoder.ts`, `param.ts`, `query.params.ts`) that import `@angular/common/http`. These are unused — the active code uses `runtime.ts` (fetch-based). They regenerate on every `npm run types` run and cannot be suppressed. Ignore them.

### Zustand

All new stores **must** follow the factory + context + provider pattern. Do not use global singletons (`create()` at module level).

**Required pattern (3 files):**

1. **Store file** (`stores/{name}.store.ts`):
   - Separate `State` and `Actions` interfaces, combined as `type XxxStore = XxxState & XxxActions`.
   - Export a `createXxxStore()` factory function that returns `StoreApi<XxxStore>`.
   - Use `createStore` (from `zustand`) with `subscribeWithSelector` middleware.
   - Do not export selector hooks from this file — those belong in the provider.

2. **Provider file** (`context/{name}-provider.tsx`):
   - `'use client'` directive.
   - Create a `React.createContext<XxxStoreApi | undefined>(undefined)`.
   - Provider component creates the store once via `useRef` + factory, passes it through context.
   - Export a `useXxxStore<T>(selector: (store: XxxStore) => T): T` hook that calls `useStore(context, selector)` with a guard that throws if context is missing.
   - Export convenience selector hooks here (e.g., `useXxxNodes`, `useXxxActions`) that call `useXxxStore` internally.

3. **Consumers** import hooks from the provider, never from the store file directly.

**Reference implementations:** `workspace-provider.tsx`, `entity-provider.tsx`, `workflow-canvas-provider.tsx`.

**Existing stores:**
  - `workspace.store.ts` — selected workspace ID. Simple `createStore`, no middleware. Persists to localStorage manually.
  - `entity.store.ts` — entity draft mode (new entity creation). `createStore` + `subscribeWithSelector`. Holds react-hook-form instance.
  - `configuration.store.ts` — entity type config editing. `createStore` + `subscribeWithSelector`. localStorage draft persistence with 7-day staleness.
  - `workflow-canvas.store.ts` — React Flow nodes/edges/selection. `createStore` + `subscribeWithSelector`.
  - `editor-store.ts` — Rich text editor state. Legacy global singleton — do not follow this pattern for new stores.

**Do not:** use `create()` for global singletons, export `useShallow` selector hooks from store files, or access stores without going through the provider's context hook.

## Component Conventions

**shadcn usage:** Standard new-york style from `components/ui/`. Mostly unmodified. Button has a custom `xs` size variant. Sonner toaster is themed via CSS variables. Some custom components live alongside shadcn: `attribute-type-dropdown.tsx`, `AuthenticateButton.tsx`, `AvatarUploader.tsx`, `breadcrumb-group.tsx`, `country-select.tsx`, `filter-list.tsx`, `phone-input.tsx`, `status.tsx`, `stepper.tsx`, `truncated-tooltip.tsx`. Sub-directories for complex UI: `data-table/`, `forms/`, `icon/`, `nav/`, `sidebar/`, `rich-editor/`, `background/`.

**Component file structure:** Single file per component. No co-located tests or stories. No index barrel exports at component level.

**Props pattern:** Inline object types in function signatures for simple components. Named interfaces/types for complex ones. Props destructured in function signature. Shared prop interfaces in `lib/interfaces/interface.ts` (`FCWC<T>`, `Propless`, `ChildNodeProps`, `ClassNameProps`, `FormFieldProps<T>`, `DialogControl`).

**Styling:** Tailwind only. `cn()` utility (`clsx` + `tailwind-merge`) for conditional classes. No CSS modules. Minimal inline styles (only for CSS variable injection). One custom global CSS utility class: `.h-screen-without-header`.

**Naming:** Inconsistent. Most files are kebab-case (`auth-context.tsx`, `workspace.store.ts`). Some are PascalCase (`ThemeContext.tsx`, `AuthenticateButton.tsx`, `AvatarUploader.tsx`). **Standardise on kebab-case for all new files.**

## Form Handling

- **Library:** react-hook-form v7 + zod v3 + `@hookform/resolvers`.
- **Validation schemas:** Co-located with form hooks (`hooks/form/type/use-new-type-form.ts`). Shared schemas in `lib/util/form/` (`form.util.ts`, `schema.util.ts`, `common/icon.form.ts`).
- **Pattern:** Custom hook returns `{ form, handleSubmit, ...extras }`. Schema defined with `z.object()`, resolved via `zodResolver()`.
- **Errors:** Displayed inline via react-hook-form's `form.setError()`. Also manual validation in store submit handlers.
- **Submit flow:** Form hook calls mutation hook. Mutations handle toasts.

## Styling Rules

- **Always use Tailwind's built-in utility classes** — never use arbitrary values (e.g., `text-[11px]`, `w-[200px]`) when a standard class exists (`text-xs`, `w-52`). Arbitrary values are a last resort for truly custom one-off values with no Tailwind equivalent.
- Tailwind 4 with CSS-based config in `app/globals.css` (no `tailwind.config.ts`).
- Color tokens via CSS custom properties using oklch. Semantic tokens: `--background`, `--foreground`, `--card`, `--primary`, `--muted`, `--destructive`, `--edit`, `--archive`, plus sidebar and chart variants.
- Dark mode: class-based toggling via `next-themes`. `.dark` class on `<html>`. Custom variant: `@custom-variant dark (&:is(.dark *))`.
- Responsive: mobile-first with `sm:`, `md:`, `lg:` breakpoints.
- Animation: Framer Motion (`framer-motion` / `motion`) + Tailwind transitions + `tw-animate-css`.
- Font: Montserrat (Google Fonts, loaded via `next/font`). Weights: 200, 400, 700.

## Auth and Middleware

- **Auth provider:** Supabase via adapter pattern. `lib/auth/` defines provider-agnostic interface (`AuthProvider`). Factory creates `SupabaseAuthAdapter`. Context in `components/provider/auth-context.tsx` exposes `useAuth()` → `{ session, user, loading, signIn, signUp, signOut, signInWithOAuth, verifyOtp, resendOtp }`.
- **Token attachment:** Generated API classes receive `accessToken: async () => session.access_token` via `Configuration`. Each service call creates a new API instance with the current session.
- **OAuth callback:** `app/api/auth/token/callback/route.ts` handles Supabase code exchange.
- **Route protection:** No Next.js middleware — intentional. Dashboard routes are protected by `AuthGuard` (`components/feature-modules/authentication/components/auth-guard.tsx`) which wraps the dashboard layout and redirects unauthenticated users to `/auth/login`. The root page (`/`) also redirects based on auth state. Auth-gated queries use `enabled: !!session` to avoid fetching when unauthenticated. Do not add `middleware.ts` for auth redirects.
- **On error:** Auth errors use `AuthError` class with typed `AuthErrorCode` enum. `getAuthErrorMessage()` maps codes to user-friendly strings for UI display.

## Error Handling

- **API errors:** Services catch errors and call `normalizeApiError()` which unwraps OpenAPI `ResponseError`, parses the JSON body, and rethrows as a `ResponseError` with `{ status, error, message }`.
- **Toast notifications:** `sonner` — `toast.loading()`, `toast.success()`, `toast.error()` in mutation hooks. `richColors` enabled on root `<Toaster>`.
- **No global error handler** on QueryClient or API client. Each mutation handles its own errors.
- **No error boundaries** observed in the codebase.
- **Custom error types:** `ResponseError` in `lib/util/error/error.util.ts` (app-level), `AuthError` in `lib/auth/auth-error.ts` (auth-level), `ResponseError`/`FetchError`/`RequiredError` in `lib/types/runtime.ts` (generated).

## Build and Run

- **Install:** `npm install`
- **Dev server:** `npm run dev` → `next dev`
- **Build:** `npm run build` → `next build`
- **Lint:** `npm run lint` → `next lint` (extends `next/core-web-vitals`, `next/typescript`, `prettier`)
- **Format:** `npm run format` → `prettier --write .` (with `prettier-plugin-tailwindcss`)
- **Format check:** `npm run format:check`
- **Type generation:** `npm run types` → `scripts/generate-types.sh` (requires backend running on `localhost:8081`)
- **Test:** `npm test` → `jest`

**Env vars required:**

- `NEXT_PUBLIC_SUPABASE_URL` — Supabase project URL
- `NEXT_PUBLIC_SUPABASE_ANON_KEY` — Supabase anonymous key
- `NEXT_PUBLIC_API_URL` — Backend API base URL (e.g., `http://localhost:8081/api`)
- `NEXT_PUBLIC_HOSTED_URL` — Frontend URL for OAuth redirects
- `NEXT_PUBLIC_GOOGLE_MAPS_API_KEY` — Google Maps API key (for location features)
- `NEXT_PUBLIC_AUTH_PROVIDER` — Auth provider type (currently only `"supabase"`)

## Testing

Testing is **minimal**. Jest + React Testing Library are configured but only 2 test files exist: barrel export verification tests (`test/types/barrel-verification.test.ts`, `test/types/workspace-user-barrel-verification.test.ts`). No component tests, no hook tests, no E2E tests. Do not generate aspirational testing rules — add tests as features stabilise.

## Do-Not-Do List

- Do not use `fetch` directly in components — use the generated API client via service classes wrapped in TanStack Query hooks.
- Do not create new Zustand stores without discussing — evaluate if existing stores can be extended.
- Do not install new UI component libraries — use shadcn components and extend with variants if needed.
- Do not put API URLs or keys directly in components — use `NEXT_PUBLIC_*` env vars via `process.env`.
- Do not skip loading and error states when using query hooks — always handle `isLoading`, `isError`, and `isLoadingAuth`.
- Do not modify generated files in `lib/types/apis/`, `lib/types/models/`, or `lib/types/runtime.ts` manually — regenerate from spec with `npm run types`.
- Do not use `any` — if generated types are incomplete, extend them in domain type files (`lib/types/{domain}/custom.ts`).
- Do not create global CSS — use Tailwind utilities. The only exception is `globals.css` for theme variables.
- Do not subscribe to entire Zustand stores — use selectors to prevent unnecessary re-renders.
- Do not import from `@/lib/types/models/` directly — import from domain barrels (`@/lib/types/entity`, `@/lib/types/workspace`, etc.).
- Do not create API instances outside of `lib/api/` factory functions — always use `create{Domain}Api(session)`.
- Do not add PascalCase component files — use kebab-case for all new files.
- Do not bypass the service layer — components should use query/mutation hooks, not call services directly.

## Consistency Issues (Observed)

1. **Stale `pnpm-lock.yaml`:** npm is the canonical package manager. `pnpm-lock.yaml` should be deleted.
2. **No default QueryClient options:** Each hook independently sets `staleTime`/`gcTime`/`retry`. Consider adding defaults.
3. **Inconsistent AuthenticatedQueryResult usage:** Some hooks (`useEntities`, `useEntityTypes`) return `AuthenticatedQueryResult<T>`, others (`useProfile`) manually spread `isLoadingAuth`. Standardise on `AuthenticatedQueryResult<T>`.
4. **File naming:** Mix of PascalCase and kebab-case in `components/ui/`.
5. **Store directory naming:** Some features use `store/` (singular), others use `stores/` (plural).
6. **Minimal Tests** More effort should be made to incorporate testing into the development workflow

## Testing Strategy

Testing is being built progressively. Follow this priority order when adding tests:

### What to test (in priority order)

1. **Zustand stores** — test state transitions, actions, selectors, and persistence logic.
   Test file location: co-located as `{store-name}.store.test.ts`
2. **Zod schemas** — test validation: valid input passes, invalid input fails with correct errors.
   Test file location: co-located with the schema file as `{schema}.test.ts`
3. **Service functions** — test API call construction and error normalization.
   Test file location: `__tests__/services/{domain}.service.test.ts`
4. **Query/mutation hooks** — test cache key structure, enabled conditions, and mutation side effects.
   Test file location: co-located as `{hook-name}.test.ts`

### Testing rules

- Every new Zustand store, zod schema, or service function must ship with tests.
- Every bug fix must include a regression test that would have caught the bug.
- Do not write component render tests unless the component is stable and reusable.
- Do not generate snapshot tests — they catch nothing useful in this codebase.
- Test files live co-located with the source file, not in a separate **tests** tree.
- Use `describe` blocks named after the function/store under test.
- Keep test setup minimal — if a test needs 30 lines of setup, the code under test probably needs refactoring. If this is the case. Showcase the code to me and explain the current problem about its complexity.

```

## Workflow Preferences

- Plan before building on any task involving 3+ files or new feature areas.
- Verify before marking done — run `npm run lint`, `npm run build`, confirm no type errors.
- When fixing a bug, do not refactor unrelated code in the same change.
- When adding a new API integration: regenerate types first (`npm run types`), then create the service, then the query/mutation hook, then the UI. Never go UI-first.
- Match existing patterns. Check how similar features are implemented before creating new ones.
- When uncertain, check the entity feature module — it is the most complete reference implementation.
```
