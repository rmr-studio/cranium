# Codebase Structure

**Analysis Date:** 2026-03-08

## Directory Layout

```
apps/client/
├── app/                              # Next.js App Router (pages + layouts)
│   ├── api/auth/token/callback/      # OAuth callback route handler
│   ├── auth/                         # Auth pages (login, register)
│   ├── dashboard/                    # Authenticated app shell
│   │   ├── settings/                 # User settings page
│   │   ├── templates/                # Templates page
│   │   └── workspace/               # Workspace routes
│   │       ├── new/                  # Create workspace
│   │       └── [workspaceId]/        # Workspace-scoped routes
│   │           ├── edit/             # Edit workspace
│   │           ├── entity/           # Entity management
│   │           │   ├── environment/  # Entity environment page
│   │           │   └── [key]/        # Entity type data + settings
│   │           ├── members/          # Workspace members
│   │           ├── subscriptions/    # Subscription management
│   │           └── usage/            # Usage stats
│   ├── globals.css                   # Tailwind 4 theme config + CSS variables
│   ├── layout.tsx                    # Root layout (providers, fonts)
│   └── page.tsx                      # Root redirect
├── components/
│   ├── feature-modules/              # Domain-specific feature code
│   │   ├── authentication/           # Auth UI components + interfaces
│   │   ├── blocks/                   # Block/grid editor system
│   │   ├── entity/                   # Entity type + instance management
│   │   ├── onboarding/              # User onboarding flow
│   │   ├── user/                     # User profile components
│   │   ├── waitlist/                 # Waitlist form
│   │   ├── workflow/                 # Visual workflow builder (React Flow)
│   │   └── workspace/               # Workspace management
│   ├── provider/                     # React context providers
│   ├── ui/                           # Shared UI components (shadcn + custom)
│   │   ├── background/              # Background patterns
│   │   ├── data-table/              # Data table with cells
│   │   ├── forms/                   # Form widgets (country, currency, date-picker, custom)
│   │   ├── icon/                    # Icon system
│   │   ├── nav/                     # Navbar
│   │   ├── rich-editor/             # Rich text editor (handlers, hooks, lib, store, toolbar)
│   │   └── sidebar/                 # App sidebar (icon-rail, sub-panel, panels)
│   └── util/                         # Wrapper components (query, store)
├── hooks/                            # Shared hooks
├── lib/
│   ├── api/                          # API factory functions
│   ├── auth/                         # Auth provider interface + adapters
│   │   └── adapters/supabase/       # Supabase auth implementation
│   ├── interfaces/                   # Shared TypeScript interfaces
│   ├── types/                        # Generated + domain types
│   │   ├── apis/                    # Generated API classes
│   │   ├── models/                  # Generated model types
│   │   ├── block/                   # Block domain barrel
│   │   ├── common/                  # Common types barrel
│   │   ├── entity/                  # Entity domain barrel
│   │   ├── user/                    # User domain barrel
│   │   ├── workflow/                # Workflow domain barrel
│   │   ├── workspace/              # Workspace domain barrel
│   │   ├── runtime.ts              # Generated fetch runtime
│   │   └── index.ts                # Root barrel (re-exports runtime, apis, models)
│   └── util/                         # Shared utilities
│       ├── country/                 # Country data
│       ├── error/                   # Error normalization
│       ├── form/                    # Form schema utilities
│       ├── service/                 # Service validation helpers
│       └── supabase/               # Supabase client creation
├── public/                           # Static assets
├── scripts/                          # Build scripts (type generation)
├── test/                             # Test files (minimal)
├── CLAUDE.md                         # AI coding instructions
├── package.json                      # Dependencies and scripts
├── tsconfig.json                     # TypeScript config (extends monorepo base)
├── eslint.config.mjs                 # ESLint config
├── jest.config.ts                    # Jest config
├── next.config.ts                    # Next.js config
├── postcss.config.mjs               # PostCSS config
├── Dockerfile                        # Production container
└── openapitools.json                 # OpenAPI generator config
```

## Directory Purposes

**`app/`:**
- Purpose: Next.js App Router — defines URL routes and layout hierarchy
- Contains: Page components (thin wrappers), layout components, one API route, `globals.css`
- Key files: `layout.tsx` (root provider tree), `dashboard/layout.tsx` (auth guard + app shell), `api/auth/token/callback/route.ts` (OAuth)

**`components/feature-modules/`:**
- Purpose: Self-contained feature domains with all their logic co-located
- Contains: Components, hooks, services, stores, context providers, utilities per domain
- Key files: Each module follows the pattern `{module}/components/`, `{module}/hooks/`, `{module}/service/`, `{module}/stores/` or `store/`, `{module}/context/`

**`components/ui/`:**
- Purpose: Shared UI primitives — shadcn/ui components plus custom reusable components
- Contains: Button, Dialog, Form, Table, etc. (shadcn), plus custom: data-table, sidebar, rich-editor, icon, nav, forms
- Key files: All shadcn components are single-file (`button.tsx`, `dialog.tsx`, etc.)

**`components/provider/`:**
- Purpose: Global React context providers
- Contains: `auth-context.tsx` (auth state), `ThemeContext.tsx` (theme)

**`components/util/`:**
- Purpose: Utility wrapper components
- Contains: `query.wrapper.tsx` (TanStack QueryClientProvider), `store.wrapper.tsx` (Zustand store providers)

**`hooks/`:**
- Purpose: Shared hooks not tied to a specific feature
- Contains: `use-media-query.ts`, `use-mobile.ts`, `use-isomorphic-layout-effect.tsx`

**`lib/api/`:**
- Purpose: API client factory functions that create authenticated API instances
- Contains: One factory per domain: `entity-api.ts`, `workspace-api.ts`, `workflow-api.ts`, `block-api.ts`, `user-api.ts`, `knowledge-api.ts`

**`lib/auth/`:**
- Purpose: Provider-agnostic auth abstraction
- Contains: Interface (`auth-provider.interface.ts`), types (`auth.types.ts`), factory (`factory.ts`), error handling (`auth-error.ts`, `error-messages.ts`), Supabase adapter (`adapters/supabase/`)

**`lib/types/`:**
- Purpose: All TypeScript types — generated from OpenAPI spec plus hand-written domain barrels
- Contains: Generated (`apis/`, `models/`, `runtime.ts`), domain barrels (`entity/`, `workspace/`, `block/`, `workflow/`, `user/`, `common/`)
- Key pattern: Domain barrels re-export from `models.ts` + add `custom.ts`, `guards.ts`, `requests.ts`, `responses.ts`

**`lib/util/`:**
- Purpose: Shared utility functions
- Contains: `utils.ts` (cn, uuid, get/set, etc.), `error/error.util.ts` (error normalization), `form/` (schema builders), `service/service.util.ts` (validation), `supabase/` (client), `country/`

**`lib/interfaces/`:**
- Purpose: Shared TypeScript interfaces used across features
- Contains: `interface.ts` — `FCWC<T>`, `Propless`, `ChildNodeProps`, `ClassNameProps`, `FormFieldProps<T>`, `DialogControl`, `AuthenticatedQueryResult<T>`, `AuthenticatedMultiQueryResult<T>`

## Key File Locations

**Entry Points:**
- `app/layout.tsx`: Root layout — provider hierarchy (Theme → Auth → QueryClient → Store)
- `app/page.tsx`: Root page — auth-aware redirect
- `app/dashboard/layout.tsx`: Dashboard layout — AuthGuard + OnboardWrapper + sidebar/navbar shell
- `app/api/auth/token/callback/route.ts`: OAuth code exchange route

**Configuration:**
- `tsconfig.json`: TypeScript config (extends `@riven/tsconfig/nextjs.json`, path alias `@/*`)
- `eslint.config.mjs`: ESLint config
- `jest.config.ts`: Jest config
- `next.config.ts`: Next.js config
- `postcss.config.mjs`: PostCSS config
- `app/globals.css`: Tailwind 4 theme — CSS variables (oklch colors), custom variants, font config
- `openapitools.json`: OpenAPI generator settings
- `components.json`: shadcn/ui component config

**Core Logic:**
- `components/feature-modules/entity/`: Entity management (most complete reference implementation)
- `components/feature-modules/workflow/`: Workflow builder (React Flow)
- `components/feature-modules/blocks/`: Block/grid editor (GridStack)
- `components/feature-modules/workspace/`: Workspace CRUD
- `components/feature-modules/authentication/`: Auth UI forms
- `lib/auth/`: Auth provider abstraction

**Testing:**
- `test/types/barrel-verification.test.ts`: Barrel export tests
- `test/types/workspace-user-barrel-verification.test.ts`: Workspace/user barrel tests
- `jest.config.ts`: Jest config
- `jest.setup.ts`: Jest setup

## Naming Conventions

**Files:**
- kebab-case for most files: `entity.service.ts`, `auth-guard.tsx`, `workspace.store.ts`
- PascalCase in some older files: `ThemeContext.tsx`, `AuthenticateButton.tsx`, `AvatarUploader.tsx`, `Login.tsx`, `Register.tsx`, `OnboardForm.tsx`
- **Use kebab-case for all new files**

**Directories:**
- kebab-case: `feature-modules/`, `data-table/`, `rich-editor/`
- Singular for single-store modules: `store/` (workspace)
- Plural for multi-store modules: `stores/` (entity, workflow)

**Components:**
- PascalCase exports: `EntityDashboard`, `AuthGuard`, `WorkspaceCard`
- Function declarations or arrow functions (both patterns exist)

**Hooks:**
- `use{Entity}` for query hooks: `useEntities`, `useWorkspace`, `useProfile`
- `useSave{Entity}Mutation` / `useDelete{Entity}Mutation` for mutations
- `use{Feature}Form` for form hooks: `useNewTypeForm`, `useSchemaForm`

**Services:**
- `{domain}.service.ts` with static class methods: `EntityService.saveEntity()`, `WorkspaceService.getWorkspaces()`

**Stores:**
- `{domain}.store.ts`: `workspace.store.ts`, `entity.store.ts`, `workflow-canvas.store.ts`
- `configuration.store.ts` for entity type config

## Where to Add New Code

**New Feature Module:**
- Create directory: `components/feature-modules/{feature}/`
- Subdirectories: `components/`, `hooks/query/`, `hooks/mutation/`, `hooks/form/`, `service/`, `store/` (or `stores/`), `context/`
- Follow entity module as reference: `components/feature-modules/entity/`

**New API Domain:**
1. Add API factory: `lib/api/{domain}-api.ts` (follow `lib/api/entity-api.ts` pattern)
2. Add domain type barrel: `lib/types/{domain}/` with `index.ts`, `models.ts`, `custom.ts`, `requests.ts`, `responses.ts`
3. Add service: `components/feature-modules/{feature}/service/{domain}.service.ts`
4. Add query/mutation hooks: `components/feature-modules/{feature}/hooks/query/`, `hooks/mutation/`

**New Page Route:**
- Add `app/dashboard/workspace/[workspaceId]/{feature}/page.tsx`
- Page should be a thin wrapper that renders a single feature component
- Pattern: `import { FeatureComponent } from '@/components/feature-modules/{feature}/components/...'`

**New Shared UI Component:**
- shadcn component: `components/ui/{component-name}.tsx`
- Complex shared component: `components/ui/{component-name}/` directory
- Form widget: `components/ui/forms/{widget-name}/`

**New Shared Hook:**
- Not tied to a feature: `hooks/use-{name}.ts`
- Feature-specific: `components/feature-modules/{feature}/hooks/`

**New Zustand Store:**
- Store file: `components/feature-modules/{feature}/store/{name}.store.ts` or `stores/{name}.store.ts`
- Context provider: `components/feature-modules/{feature}/context/{name}-provider.tsx`
- Pattern: Separate `State` and `Actions` interfaces, `createXxxStore()` factory, context provider with `useRef`, selector hook `useXxxStore(selector)`

**New Service:**
- Location: `components/feature-modules/{feature}/service/{domain}.service.ts`
- Pattern: Static class with methods that accept `session`, validate inputs, call API factory, handle errors

**Shared Utilities:**
- General helpers: `lib/util/utils.ts`
- Error handling: `lib/util/error/error.util.ts`
- Form schemas: `lib/util/form/`
- Service validation: `lib/util/service/service.util.ts`

## Special Directories

**`lib/types/apis/`, `lib/types/models/`, `lib/types/runtime.ts`:**
- Purpose: Generated code from OpenAPI spec
- Generated: Yes (via `npm run types` / `scripts/generate-types.sh`)
- Committed: Yes
- Do not modify manually

**`lib/types/.openapi-generator/`:**
- Purpose: OpenAPI generator metadata
- Generated: Yes
- Committed: Yes

**`.next/`:**
- Purpose: Next.js build output
- Generated: Yes
- Committed: No (gitignored)

**`node_modules/`:**
- Purpose: npm dependencies
- Generated: Yes
- Committed: No (gitignored)

**`.planning/`:**
- Purpose: Planning and analysis documents
- Generated: No (manually created)
- Committed: Varies

**`public/static/`:**
- Purpose: Static assets served by Next.js
- Generated: No
- Committed: Yes

**`.turbo/`:**
- Purpose: Turborepo cache configuration
- Generated: Yes
- Committed: Yes (config only)

---

*Structure analysis: 2026-03-08*
