# Coding Conventions

**Analysis Date:** 2026-03-08

## Naming Patterns

**Files:**
- Use kebab-case for all new files: `entity-type.service.ts`, `use-entities.ts`, `auth-context.tsx`
- Existing PascalCase files exist in `components/ui/` (`AvatarUploader.tsx`, `AuthenticateButton.tsx`) and `components/provider/` (`ThemeContext.tsx`) — do not rename, but do not create new PascalCase files
- Stores: `{name}.store.ts` (e.g., `workspace.store.ts`, `entity.store.ts`, `configuration.store.ts`)
- Services: `{domain}.service.ts` or `{domain}-{subdomain}.service.ts` (e.g., `entity.service.ts`, `entity-type.service.ts`)
- Query hooks: `use-{entity}.ts` or `use-{entity}s.ts` (e.g., `use-entities.ts`, `use-entity-types.ts`, `use-workspace.tsx`)
- Mutation hooks: `use-{action}-{entity}-mutation.ts` (e.g., `use-save-entity-mutation.ts`, `use-delete-type-mutation.ts`)
- Form hooks: `use-{name}-form.ts` (e.g., `use-new-type-form.ts`, `use-relationship-form.ts`)
- API factories: `{domain}-api.ts` (e.g., `entity-api.ts`, `workspace-api.ts`)

**Functions:**
- Use camelCase: `getEntityTypes`, `validateSession`, `normalizeApiError`
- Query hooks: `use{Entity}` or `use{Entity}s` — no `get`/`fetch`/`Query` suffix (e.g., `useEntities`, `useEntityTypes`, `useWorkspace`)
- Mutation hooks: `useSave{Entity}Mutation`, `useDelete{Entity}Mutation`, `usePublish{Entity}TypeMutation`
- Form hooks: `useNew{Entity}Form`, `use{Name}Form`
- Service methods: static methods with verb prefix: `getEntityTypes`, `saveEntityTypeConfiguration`, `deleteEntityType`
- API factories: `create{Domain}Api` (e.g., `createEntityApi`, `createWorkspaceApi`)
- Utility functions: camelCase, descriptive verbs: `toKeyCase`, `fromKeyCase`, `toTitleCase`, `hexToRgb`, `isPayloadEqual`

**Variables:**
- Use camelCase for all variables: `workspaceId`, `entityType`, `submissionToastRef`
- Boolean flags: `is` or `has` prefix: `isDraftMode`, `isLoading`, `isLoadingAuth`, `isValid`
- Refs: `{name}Ref` suffix: `submissionToastRef`

**Types/Interfaces:**
- PascalCase for types and interfaces: `EntityType`, `WorkspaceStore`, `AuthContextType`
- Zustand stores: separate `{Name}State` and `{Name}Actions` interfaces, combined as `{Name}Store` type
- Props: inline `{ prop: Type }` for simple components; named `interface {Name}Props` for complex ones
- Shared prop types in `lib/interfaces/interface.ts`: `FCWC<T>`, `Propless`, `ChildNodeProps`, `ClassNameProps`, `FormFieldProps<T>`, `DialogControl`
- Query result types: `AuthenticatedQueryResult<T>` and `AuthenticatedMultiQueryResult<T>` from `lib/interfaces/interface.ts`
- Zod inferred types: `type {Name} = z.infer<typeof {schema}>` (e.g., `type NewEntityTypeFormValues = z.infer<typeof baseEntityTypeFormSchema>`)

**Constants:**
- Use camelCase for object constants: `attributeTypes`, `workspaceInitState`
- Use SCREAMING_SNAKE_CASE only for true constants that are string literals used as error codes: `"NO_SESSION"`, `"INVALID_ID"`, `"API_ERROR"`

## Code Style

**Formatting:**
- Prettier with `prettier-plugin-tailwindcss`
- Config: `.prettierrc` at project root
- Key settings:
  - `printWidth: 100`
  - `tabWidth: 2`
  - `semi: true` (always use semicolons)
  - `singleQuote: true` (single quotes for JS/TS strings)
  - `jsxSingleQuote: false` (double quotes in JSX)
  - `trailingComma: "all"` (trailing commas everywhere)
  - `bracketSpacing: true`
  - `arrowParens: "always"` (always wrap arrow params in parens)
  - `endOfLine: "lf"`
- Run: `npm run format` to auto-format, `npm run format:check` to verify

**Linting:**
- ESLint 9 flat config: `eslint.config.mjs`
- Extends: `next/core-web-vitals`, `next/typescript`, `prettier`
- Run: `npm run lint`
- Type checking: `npm run type-check` (runs `tsc --noEmit`)

## Import Organization

**Order:**
1. External packages (`react`, `next`, `@tanstack/react-query`, `zod`, `sonner`, `zustand`)
2. Internal aliases starting with `@/` — providers, libs, types
3. Relative imports (sibling/parent modules within the feature)

**Path Aliases:**
- `@/*` maps to project root (configured in `tsconfig.json`)
- Example: `@/components/provider/auth-context`, `@/lib/types/entity`, `@/lib/api/entity-api`

**Domain Type Imports:**
- Always import from domain barrels: `@/lib/types/entity`, `@/lib/types/workspace`, `@/lib/types/block`, `@/lib/types/user`, `@/lib/types/common`
- Never import directly from `@/lib/types/models/` or `@/lib/types/apis/`
- Domain barrels are at `lib/types/{domain}/index.ts` and re-export from `./models`, `./requests`, `./responses`, `./guards`, `./custom`

**No Barrel Exports at Feature Level:**
- Feature modules do not use `index.ts` barrel exports
- Import directly: `import { EntityTypeService } from '../../../service/entity-type.service'`

## Error Handling

**API Errors:**
- Services wrap API calls in `try/catch` and call `normalizeApiError(error)` from `lib/util/error/error.util.ts`
- `normalizeApiError` unwraps OpenAPI `ResponseError`, parses JSON body, rethrows as app-level `ResponseError` with `{ status, error, message, stackTrace }`
- Special case: 409 (conflict) responses are parsed directly as `EntityTypeImpactResponse` for impact confirmation flows

**Service Validation:**
- Use `validateSession(session)` and `validateUuid(id)` from `lib/util/service/service.util.ts` at the top of every service method
- These throw structured `ResponseError` with appropriate status codes (401, 400)

**Mutation Error Handling:**
- Pattern: `toast.loading()` in `onMutate`, `toast.dismiss()` + `toast.error()` in `onError`, `toast.dismiss()` + `toast.success()` in `onSuccess`
- Use `useRef` to track toast ID: `const submissionToastRef = useRef<string | number | undefined>(undefined)`
- Error message format: `Failed to {action}: ${error.message}`

**Custom Error Types:**
- App-level: `ResponseError` in `lib/util/error/error.util.ts` — `{ status, error, message, stackTrace?, details? }`
- Auth-level: `AuthError` in `lib/auth/auth-error.ts` — typed `AuthErrorCode` enum
- Generated: `ResponseError`, `FetchError`, `RequiredError` in `lib/types/runtime.ts`

**No Error Boundaries:**
- The codebase does not use React error boundaries
- No global error handler on QueryClient or API client

## Logging

**Framework:** `console` (browser console)

**Patterns:**
- `console.warn()` for non-critical issues in development (e.g., missing metadata in store)
- No structured logging library
- No production logging infrastructure

## Comments

**When to Comment:**
- JSDoc on service methods, utility functions, and API factory functions
- Inline comments for non-obvious logic (e.g., `// non-null assertion as enabled ensures workspaceId is defined`)
- Comments explaining "why" over "what" (e.g., `// Handle impact confirmation flow (409 returns impact details)`)
- Comments on type guard functions explaining their purpose

**JSDoc/TSDoc:**
- Use JSDoc `/** ... */` on exported utility functions and service methods
- Include `@param` and `@returns` for non-trivial functions
- Include `@throws` when a function can throw
- Example pattern from `lib/api/entity-api.ts`:
  ```typescript
  /**
   * Creates an EntityApi instance configured with session-based authentication.
   * @param session - Supabase session with access_token
   * @returns Configured EntityApi instance
   * @throws Error if session is invalid or API URL not configured
   */
  ```

## Function Design

**Size:** Keep functions focused on a single responsibility. Service methods follow a consistent 5-10 line pattern: validate, create API, try/catch, return/throw.

**Parameters:**
- Session is always the first parameter for service methods: `(session: Session | null, workspaceId: string, ...)`
- Optional parameters use `?` suffix or default values
- Complex input uses request objects (e.g., `CreateEntityTypeRequest`, `UpdateEntityTypeConfigurationRequest`)

**Return Values:**
- Service methods return `Promise<T>` where `T` is the domain type
- Query hooks return `AuthenticatedQueryResult<T>` (extends `UseQueryResult` with `isLoadingAuth`)
- Mutation hooks return the `useMutation` result directly
- Form hooks return `{ form, handleSubmit, ...extras }`

## Module Design

**Exports:**
- Named exports for all functions, types, and components — no default exports except page components
- Page components use `export default` (Next.js requirement)
- Service classes use static methods (no instantiation needed): `EntityTypeService.getEntityTypes(...)`

**Barrel Files:**
- Domain type barrels at `lib/types/{domain}/index.ts` — use these
- No barrel files at feature-module level
- No barrel files at component level

## Component Patterns

**Page Components:**
- Thin wrappers that render a single feature component
- Pattern: `const PageName = () => { return <FeatureComponent />; }; export default PageName;`
- Example: `app/dashboard/workspace/[workspaceId]/entity/[key]/page.tsx`

**Feature Components:**
- Live in `components/feature-modules/{feature}/components/`
- Marked `'use client'` when they need client-side hooks
- Use query/mutation hooks for data access — never call services directly

**Provider Components:**
- Live in `components/provider/`
- Always `'use client'`
- Expose data via context hook (e.g., `useAuth()` from `auth-context.tsx`)

## Zustand Store Pattern

**Store Structure:**
```typescript
// Separate State and Actions interfaces
interface {Name}State { ... }
interface {Name}Actions { ... }
export type {Name}Store = {Name}State & {Name}Actions;

// Factory function
export const create{Name}Store = (...args) => {
  return createStore<{Name}Store>()(
    subscribeWithSelector((set, get) => ({
      // state + actions
    }))
  );
};

// Export store API type
export type {Name}StoreApi = ReturnType<typeof create{Name}Store>;
```

**Access Pattern:**
- Use selectors: `useWorkspaceStore((s) => s.selectedWorkspaceId)`
- Never subscribe to entire store
- Use `useShallow` for array/object selectors to prevent unnecessary re-renders

## Service Class Pattern

**Structure:**
```typescript
export class {Domain}Service {
  static async {method}(
    session: Session | null,
    workspaceId: string,
    ...args
  ): Promise<ReturnType> {
    validateSession(session);
    validateUuid(workspaceId);
    const api = create{Domain}Api(session!);

    try {
      return await api.{apiMethod}({ ...params });
    } catch (error) {
      throw await normalizeApiError(error);
    }
  }
}
```

## API Factory Pattern

**Structure:**
```typescript
export function create{Domain}Api(session: Session): {Domain}Api {
  const basePath = process.env.NEXT_PUBLIC_API_URL;
  if (!basePath) throw new Error("NEXT_PUBLIC_API_URL is not configured");

  const config = new Configuration({
    basePath,
    accessToken: async () => session.access_token,
  });

  return new {Domain}Api(config);
}
```
- Location: `lib/api/{domain}-api.ts`
- One factory per API domain

---

*Convention analysis: 2026-03-08*
