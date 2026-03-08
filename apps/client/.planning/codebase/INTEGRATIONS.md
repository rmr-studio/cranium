# External Integrations

**Analysis Date:** 2026-03-08

## APIs & External Services

**Backend API (Spring Boot):**
- Purpose: Primary data backend for all domain operations (workspaces, entities, workflows, blocks, users, knowledge, storage)
- Base URL: `NEXT_PUBLIC_API_URL` env var (typically `http://localhost:8081/api`)
- Contract: OpenAPI spec served at `http://localhost:8081/docs/v3/api-docs`
- Client: Generated TypeScript-fetch clients in `lib/types/apis/`
- Auth: Bearer token via Supabase session `access_token`
- Factory functions in `lib/api/`:
  - `lib/api/workspace-api.ts` - `createWorkspaceApi(session)`
  - `lib/api/entity-api.ts` - `createEntityApi(session)`
  - `lib/api/workflow-api.ts` - `createWorkflowApi(session)`
  - `lib/api/block-api.ts` - `createBlockApi(session)`
  - `lib/api/user-api.ts` - `createUserApi(session)`
  - `lib/api/knowledge-api.ts` - `createKnowledgeApi(session)`

**Generated API Classes** (`lib/types/apis/`):
- `WorkspaceApi.ts` - Workspace CRUD, membership
- `EntityApi.ts` - Entity types, instances, attributes, relationships
- `WorkflowApi.ts` - Workflow definitions, execution
- `BlockApi.ts` - Block layouts, widgets, environments
- `UserApi.ts` - User profiles, preferences
- `KnowledgeApi.ts` - Knowledge base operations
- `StorageApi.ts` - File/asset storage operations

**Google Maps:**
- Purpose: Location-based features (address autocomplete, maps)
- SDK: `@googlemaps/js-api-loader` ^1.16.10, `@react-google-maps/api` ^2.20.7
- Autocomplete: `use-places-autocomplete` ^4.0.1
- Auth: `NEXT_PUBLIC_GOOGLE_MAPS_API_KEY` env var

## Authentication & Identity

**Auth Provider: Supabase (via adapter pattern)**

**Architecture:**
- Provider-agnostic interface: `lib/auth/auth-provider.interface.ts` (`AuthProvider` interface)
- Factory: `lib/auth/factory.ts` - creates adapter based on `NEXT_PUBLIC_AUTH_PROVIDER` env var
- Currently only `"supabase"` adapter implemented

**Supabase Adapter:**
- Implementation: `lib/auth/adapters/supabase/supabase-adapter.ts`
- Error mapping: `lib/auth/adapters/supabase/error-mapper.ts`
- Type mappers: `lib/auth/adapters/supabase/mappers.ts`

**Supabase Clients:**
- Browser client: `lib/util/supabase/client.ts` - `createClient()` using `@supabase/supabase-js`
- SSR client: `lib/util/supabase/client.ts` - `createSSRClient()` using `@supabase/ssr` with cookie-based sessions
- Env vars: `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY`
- Optional: `NEXT_PUBLIC_COOKIE_DOMAIN` for cross-domain cookie sharing

**Auth Flow:**
- Context provider: `components/provider/auth-context.tsx` exposes `useAuth()` hook
- Route protection: `AuthGuard` component wraps dashboard layout (no Next.js middleware)
- OAuth callback: `app/api/auth/token/callback/route.ts` - handles Supabase code exchange
- Token attachment: Each API factory creates `Configuration` with `accessToken: async () => session.access_token`

**Auth Types:**
- Custom types: `lib/auth/auth.types.ts` (Session, User, SignInCredentials, SignUpCredentials, OAuthProvider, etc.)
- Error types: `lib/auth/auth-error.ts` (AuthError with typed AuthErrorCode enum)
- Error messages: `lib/auth/error-messages.ts` (user-friendly error string mapping)

**Supported Auth Methods:**
- Email/password sign in and sign up
- OAuth (provider types defined in `OAuthProvider` type)
- OTP verification (email-based)
- OTP resend

## Data Storage

**Databases:**
- No direct database connection from frontend
- All data access through Spring Boot API backend
- Backend manages its own database (not directly accessed by client)

**File Storage:**
- `StorageApi` class in `lib/types/apis/StorageApi.ts` - file operations via backend API
- No direct cloud storage SDK (S3, GCS, etc.) in frontend

**Caching:**
- TanStack React Query in-memory cache (client-side only)
- Zustand stores with selective localStorage persistence:
  - Workspace store: persists `selectedWorkspaceId` to localStorage
  - Configuration store: persists entity type config drafts to localStorage (7-day staleness)
- No external cache service (Redis, etc.)

## Monitoring & Observability

**Error Tracking:**
- None detected - no Sentry, Datadog, LogRocket, or similar SDK

**Logs:**
- `console.error()` for error logging (e.g., `app/api/auth/token/callback/route.ts`)
- No structured logging framework
- No log aggregation service

**Analytics:**
- None detected - no Google Analytics, Mixpanel, Amplitude, or similar SDK

## CI/CD & Deployment

**Hosting:**
- Target domain: `https://app.getriven.io` (per OpenGraph metadata)
- Build output: `standalone` mode (suitable for Docker/containerized deployment)
- No deployment configuration files detected (no Dockerfile, no Vercel config, no Netlify config)

**CI Pipeline:**
- Not detected in this workspace (no `.github/workflows/`, no `.gitlab-ci.yml`)

## Environment Configuration

**Required env vars:**
- `NEXT_PUBLIC_SUPABASE_URL` - Supabase project URL
- `NEXT_PUBLIC_SUPABASE_ANON_KEY` - Supabase anonymous/public key
- `NEXT_PUBLIC_API_URL` - Backend API base URL (e.g., `http://localhost:8081/api`)
- `NEXT_PUBLIC_HOSTED_URL` - Frontend URL for OAuth redirects
- `NEXT_PUBLIC_AUTH_PROVIDER` - Auth adapter type (currently only `"supabase"`)
- `NEXT_PUBLIC_GOOGLE_MAPS_API_KEY` - Google Maps API key

**Optional env vars:**
- `NEXT_PUBLIC_COOKIE_DOMAIN` - Custom domain for auth cookies (cross-domain sharing)

**Secrets location:**
- `.env` file at monorepo root (`/home/jared/dev/worktrees/onboarding/.env`)
- `.env.example` present for reference
- All vars use `NEXT_PUBLIC_` prefix (exposed to browser)

## Webhooks & Callbacks

**Incoming:**
- `app/api/auth/token/callback/route.ts` - OAuth callback endpoint (`GET /api/auth/token/callback`)
  - Receives `code` query parameter from Supabase OAuth flow
  - Exchanges code for session via `supabase.auth.exchangeCodeForSession()`
  - Redirects to `/dashboard/workspace` on success or `/auth/auth-code-error` on failure
  - Validates `next` redirect parameter to prevent open redirects
  - Handles `x-forwarded-host` for production load balancer scenarios

**Outgoing:**
- None detected from frontend (backend may send webhooks)

## Type Generation Pipeline

**Process:**
1. Backend must be running at `localhost:8081`
2. Run `npm run types` (executes `scripts/generate-types.sh`)
3. Script cleans `lib/types/models/`, `lib/types/apis/`, `lib/types/docs/`, and generated root files
4. Runs `openapi-generator-cli generate` with `typescript-fetch` generator
5. Output: API classes in `lib/types/apis/`, model types in `lib/types/models/`, runtime in `lib/types/runtime.ts`
6. Hand-written domain barrels in `lib/types/{entity,workspace,block,workflow,user,common}/` re-export and extend generated types

**Generator Config:**
- Generator: `typescript-fetch`
- Options: `withSeparateModelsAndApi=true`, `modelPackage=models`, `apiPackage=api`, `supportsES6=true`, `stringEnums=true`

---

*Integration audit: 2026-03-08*
