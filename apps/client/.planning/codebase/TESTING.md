# Testing Patterns

**Analysis Date:** 2026-03-08

## Test Framework

**Runner:**
- Jest 29.7 with `next/jest` preset
- Config: `jest.config.ts`
- Environment: `jest-environment-jsdom`

**Assertion Library:**
- Jest built-in assertions (`expect`, `toBe`, `toBeDefined`, etc.)
- `@testing-library/jest-dom` for DOM assertions (`toBeInTheDocument`, `toHaveAttribute`)

**Component Testing:**
- `@testing-library/react` 16.1 (`render`, `screen`)
- `@testing-library/user-event` 14.5 (installed, not yet used in existing tests)

**Run Commands:**
```bash
npm test                # Run all tests (jest)
npm run test:watch      # Watch mode (jest --watch)
```

## Test File Organization

**Location:**
- Mixed pattern (not fully standardized):
  - Component tests: `__tests__/` directory adjacent to source files (e.g., `components/bespoke/__tests__/AddressCard.test.tsx`)
  - Type barrel tests: `test/types/` directory at project root (e.g., `test/types/barrel-verification.test.ts`)
- **Preferred pattern (per CLAUDE.md):** Co-located with source file as `{name}.test.ts` — use this for all new tests

**Naming:**
- Test files: `{ComponentName}.test.tsx` for components, `{name}.test.ts` for utilities/stores
- PascalCase test files exist for component tests (matching component name), but prefer kebab-case for new test files

**Current Test Inventory (5 files total):**
- `test/types/barrel-verification.test.ts` — Entity and block domain barrel export verification
- `test/types/workspace-user-barrel-verification.test.ts` — Workspace and user domain barrel export verification
- `components/feature-modules/blocks/components/bespoke/__tests__/AddressCard.test.tsx` — AddressCard component rendering
- `components/feature-modules/blocks/components/bespoke/__tests__/ContactCard.test.tsx` — ContactCard component rendering
- `components/feature-modules/blocks/components/bespoke/__tests__/FallbackBlock.test.tsx` — FallbackBlock component rendering

## Test Structure

**Suite Organization:**
```typescript
describe('ComponentOrModuleName', () => {
  it('describes expected behavior in plain english', () => {
    // Arrange - set up test data
    // Act - perform action
    // Assert - verify result
  });
});
```

**Component Test Pattern:**
```typescript
import { render, screen } from '@testing-library/react';
import { ComponentName } from '../ComponentName';

describe('ComponentName', () => {
  it('renders default state', () => {
    const Component = ComponentName.component; // For bespoke block components
    render(<Component />);
    expect(screen.getByText('Expected text')).toBeInTheDocument();
  });

  it('renders with provided props', () => {
    const Component = ComponentName.component;
    render(<Component prop="value" />);
    expect(screen.getByText('value')).toBeInTheDocument();
  });
});
```

**Type Barrel Verification Pattern:**
```typescript
import type { TypeA, TypeB } from '@/lib/types/domain';
import { enumValue, guardFunction } from '@/lib/types/domain';

describe('Type barrel exports', () => {
  it('domain barrel exports types correctly', () => {
    // Type-level checks - if this compiles, types work
    const checkType = (entity: TypeA) => entity.key;
    expect(typeof guardFunction).toBe('function');
  });

  it('enum values are accessible', () => {
    expect(EnumName.Value).toBe('EXPECTED_STRING');
  });
});
```

**Patterns:**
- No `beforeEach` / `afterEach` setup in existing tests — tests are self-contained
- No shared fixtures or factories yet
- Tests are short (5-15 lines per test case)

## Mocking

**Framework:** Jest built-in mocking (no additional mocking library)

**Static Asset Mocks:**
- CSS/SCSS: `identity-obj-proxy` (returns class names as-is)
- Images/files: `test/__mocks__/fileMock.ts` — exports `'test-file-stub'`
- Configured in `jest.config.ts` `moduleNameMapper`

**Path Alias Resolution:**
- `'^@/(.*)$': '<rootDir>/$1'` in `moduleNameMapper` resolves `@/` imports

**No API/Service Mocks Yet:**
- No mock implementations for services, API clients, or auth context
- No `jest.mock()` calls in existing test files
- When adding service tests, mock the API factory: `jest.mock('@/lib/api/entity-api')`

**What to Mock (guidance for new tests):**
- API factory functions (`createEntityApi`, `createWorkspaceApi`)
- `useAuth()` context hook (session, user, loading state)
- `next/navigation` hooks (`useRouter`, `useParams`)
- External services (Supabase client)
- `toast` from `sonner`

**What NOT to Mock:**
- Zustand stores (test actual state transitions)
- Zod schemas (test actual validation)
- Utility functions in `lib/util/` (test actual logic)
- Domain type guards (test actual type checking)

## Fixtures and Factories

**Test Data:**
- Inline test data in each test case — no shared fixtures
- Example pattern from `ContactCard.test.tsx`:
```typescript
const testClient = {
  id: 'client-1',
  name: 'Jane Doe',
  contact: { email: 'jane@example.com' },
  company: { name: 'Acme Corp' },
  archived: false,
  type: 'VIP',
};
```

**Location:**
- No dedicated fixtures directory
- When test data becomes shared, create `test/fixtures/{domain}.fixtures.ts`

## Coverage

**Requirements:** None enforced. No coverage thresholds configured.

**View Coverage:**
```bash
npx jest --coverage
```

## Test Types

**Unit Tests:**
- Component rendering tests (React Testing Library)
- Type barrel export verification (compile-time + runtime checks)
- No store, service, or utility tests yet

**Integration Tests:**
- None

**E2E Tests:**
- None (no Playwright/Cypress configured)

## Testing Priority (from CLAUDE.md)

When adding new tests, follow this priority order:

1. **Zustand stores** — test state transitions, actions, selectors, persistence
   - Co-locate: `{store-name}.store.test.ts`
   - Test `create{Name}Store` factory, verify initial state, call actions, assert state changes

2. **Zod schemas** — test validation: valid input passes, invalid input fails with correct errors
   - Co-locate: `{schema-file}.test.ts`
   - Test each field constraint, edge cases, error messages

3. **Service functions** — test API call construction and error normalization
   - Location: `__tests__/services/{domain}.service.test.ts`
   - Mock API factory, verify correct method calls, test error handling

4. **Query/mutation hooks** — test cache key structure, enabled conditions, mutation side effects
   - Co-locate: `{hook-name}.test.ts`
   - Wrap in `QueryClientProvider`, mock service layer

## Testing Rules

- Every new Zustand store, zod schema, or service function must ship with tests
- Every bug fix must include a regression test
- Do not write component render tests unless the component is stable and reusable
- Do not generate snapshot tests
- Test files live co-located with source, not in a separate `__tests__` tree (exception: existing `__tests__` directories)
- Use `describe` blocks named after the function/store under test
- Keep test setup minimal — if a test needs 30+ lines of setup, the code under test needs refactoring

## Common Patterns

**Async Testing (expected pattern for services):**
```typescript
describe('EntityTypeService', () => {
  it('throws normalized error on API failure', async () => {
    // Mock API to reject
    jest.mocked(createEntityApi).mockReturnValue({
      getEntityTypesForWorkspace: jest.fn().mockRejectedValue(
        new ResponseError(new Response(JSON.stringify({ message: 'Not found' }), { status: 404 }))
      ),
    } as any);

    await expect(
      EntityTypeService.getEntityTypes(mockSession, 'workspace-uuid')
    ).rejects.toMatchObject({ status: 404 });
  });
});
```

**Store Testing (expected pattern):**
```typescript
describe('createWorkspaceStore', () => {
  it('sets selected workspace ID', () => {
    const store = createWorkspaceStore();
    store.getState().setSelectedWorkspace({ id: 'ws-1', name: 'Test' } as Workspace);
    expect(store.getState().selectedWorkspaceId).toBe('ws-1');
  });
});
```

**Schema Testing (expected pattern):**
```typescript
describe('baseEntityTypeFormSchema', () => {
  it('accepts valid input', () => {
    const result = baseEntityTypeFormSchema.safeParse({
      singularName: 'Client',
      pluralName: 'Clients',
      semanticGroup: 'UNCATEGORIZED',
      tags: [],
      icon: { type: 'Database', colour: 'NEUTRAL' },
    });
    expect(result.success).toBe(true);
  });

  it('rejects missing singular name', () => {
    const result = baseEntityTypeFormSchema.safeParse({
      singularName: '',
      pluralName: 'Clients',
      semanticGroup: 'UNCATEGORIZED',
      tags: [],
      icon: { type: 'Database', colour: 'NEUTRAL' },
    });
    expect(result.success).toBe(false);
  });
});
```

## Jest Configuration Details

**Config file:** `jest.config.ts`

```typescript
const customJestConfig: Config = {
  setupFilesAfterEnv: ['<rootDir>/jest.setup.ts'],
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/$1',
    '^.+\\.(css|scss|sass)$': 'identity-obj-proxy',
    '^.+\\.(png|jpg|jpeg|gif|webp|avif|svg)$': '<rootDir>/test/__mocks__/fileMock.ts',
  },
  testEnvironment: 'jest-environment-jsdom',
  testMatch: [
    '<rootDir>/**/__tests__/**/*.(test|spec).[jt]s?(x)',
    '<rootDir>/**/?(*.)+(spec|test).[jt]s?(x)',
  ],
};
```

**Setup file:** `jest.setup.ts` — imports `@testing-library/jest-dom` for DOM matchers

**TypeScript:** Uses `ts-jest` 29.3 for TypeScript compilation in tests

---

*Testing analysis: 2026-03-08*
