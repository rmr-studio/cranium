# Phase 4: Entity Error Resilience & E2E Tests Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-dcranium-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add granular React error boundaries around entity components to prevent cascading UI failures, and establish Playwright E2E tests for critical entity data entry and relationship flows.

**Architecture:** Two independent workstreams: (1) a reusable `EntityErrorBoundary` component placed at key composition points (data table, draft row, relationship picker, configuration form) so runtime errors are isolated and recoverable, and (2) Playwright E2E tests with API mocking that exercise the full entity CRUD + relationship lifecycle. Error boundaries are purely additive — no behavior change for happy path. E2E tests use Playwright's route interception to mock backend responses.

**Tech Stack:** React 19 Error Boundaries, Playwright, Next.js App Router, PostHog (error capture from Phase 3)

**Prerequisites:**
- Phase 1 (foundation refactors) — centralized query keys
- Phase 2 (testing core) — unit test baseline
- Phase 3 (validation & observability) — PostHog events module for error capture

---

## File Structure

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `components/feature-modules/entity/components/ui/entity-error-boundary.tsx` | Reusable error boundary for entity components |
| Create | `components/feature-modules/entity/components/ui/entity-error-boundary.test.tsx` | Tests for error boundary render and recovery |
| Modify | `components/feature-modules/entity/components/tables/entity-data-table.tsx` | Wrap table sections in error boundaries |
| Modify | `components/feature-modules/entity/components/dashboard/entity-dashboard.tsx` | Wrap dashboard content in error boundary |
| Modify | `components/feature-modules/entity/components/dashboard/entity-type-dashboard.tsx` | Wrap dashboard content in error boundary |
| Create | `playwright.config.ts` | Playwright configuration |
| Create | `e2e/entity-crud.spec.ts` | E2E test: create, edit, delete entity |
| Create | `e2e/entity-relationships.spec.ts` | E2E test: relationship CRUD + bidirectional verification |
| Create | `e2e/fixtures/entity-api-mocks.ts` | Shared API mock factories for Playwright |
| Create | `e2e/fixtures/entity-type-responses.ts` | Mock EntityType API responses |

---

## Chunk 1: Granular Error Boundaries

### Task 1: Create reusable EntityErrorBoundary component

**Files:**
- Create: `components/feature-modules/entity/components/ui/entity-error-boundary.tsx`
- Test: `components/feature-modules/entity/components/ui/entity-error-boundary.test.tsx`

**Context:** React error boundaries catch JavaScript errors in their child component tree, log them, and display a fallback UI. Currently, the entity dashboard has zero error boundaries — a single error in any child component (malformed entity payload, unexpected schema type, relationship picker crash) takes down the entire page.

We create a reusable boundary component that:
- Shows a contextual fallback UI (not a generic "Something went wrong")
- Provides a "Retry" button that resets the error state
- Captures the error to PostHog (using the module from Phase 3)
- Supports custom fallback content via props

**Important:** React error boundaries must be class components (or use the `react-error-boundary` library). Check if `react-error-boundary` is installed. If not, use a class component.

- [ ] **Step 1: Check if react-error-boundary is installed**

Run: `grep "react-error-boundary" package.json`
If not installed, we'll build a class component. If installed, use the library.

- [ ] **Step 2: Write the error boundary component**

```tsx
// components/feature-modules/entity/components/ui/entity-error-boundary.tsx
'use client';

import { captureEntityEvent } from '@/lib/observability/entity-events';
import { AlertTriangle, RefreshCw } from 'lucide-react';
import { Component, ErrorInfo, ReactNode } from 'react';

interface EntityErrorBoundaryProps {
  children: ReactNode;
  /** Context label shown in the fallback UI (e.g., "data table", "relationship picker") */
  context: string;
  /** Optional custom fallback — if not provided, uses default error card */
  fallback?: ReactNode;
  /** Optional callback when error is caught */
  onError?: (error: Error, errorInfo: ErrorInfo) => void;
}

interface EntityErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

export class EntityErrorBoundary extends Component<
  EntityErrorBoundaryProps,
  EntityErrorBoundaryState
> {
  constructor(props: EntityErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): EntityErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    // Capture to PostHog for observability
    captureEntityEvent('entity_save_failed', {
      workspaceId: 'unknown',
      entityTypeId: 'unknown',
      error: `[ErrorBoundary:${this.props.context}] ${error.message}`,
      hasConflict: false,
    });

    // Call optional error handler
    this.props.onError?.(error, errorInfo);

    // Log for development debugging
    console.error(`[EntityErrorBoundary:${this.props.context}]`, error, errorInfo);
  }

  handleRetry = (): void => {
    this.setState({ hasError: false, error: null });
  };

  render(): ReactNode {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div className="flex flex-col items-center justify-center gap-3 rounded-lg border border-destructive/20 bg-destructive/5 p-6">
          <AlertTriangle className="size-8 text-destructive" />
          <div className="text-center">
            <p className="font-medium text-foreground">
              Something went wrong in the {this.props.context}
            </p>
            <p className="mt-1 text-sm text-muted-foreground">
              {this.state.error?.message || 'An unexpected error occurred'}
            </p>
          </div>
          <button
            onClick={this.handleRetry}
            className="inline-flex items-center gap-2 rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:bg-primary/90"
          >
            <RefreshCw className="size-4" />
            Retry
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}
```

- [ ] **Step 3: Write tests for error boundary**

```tsx
// components/feature-modules/entity/components/ui/entity-error-boundary.test.tsx
import { render, screen, fireEvent } from '@testing-library/react';
import { EntityErrorBoundary } from './entity-error-boundary';

// Component that throws on render
function ThrowingComponent({ shouldThrow }: { shouldThrow: boolean }) {
  if (shouldThrow) {
    throw new Error('Test error from child component');
  }
  return <div>Child content</div>;
}

// Suppress console.error for expected errors in tests
beforeEach(() => {
  jest.spyOn(console, 'error').mockImplementation(() => {});
});
afterEach(() => {
  jest.restoreAllMocks();
});

// Mock PostHog events
jest.mock('@/lib/observability/entity-events', () => ({
  captureEntityEvent: jest.fn(),
}));

describe('EntityErrorBoundary', () => {
  it('renders children when no error', () => {
    render(
      <EntityErrorBoundary context="test">
        <div>Normal content</div>
      </EntityErrorBoundary>,
    );

    expect(screen.getByText('Normal content')).toBeInTheDocument();
  });

  it('shows fallback UI when child throws', () => {
    render(
      <EntityErrorBoundary context="data table">
        <ThrowingComponent shouldThrow={true} />
      </EntityErrorBoundary>,
    );

    expect(screen.getByText(/Something went wrong in the data table/)).toBeInTheDocument();
    expect(screen.getByText(/Test error from child component/)).toBeInTheDocument();
  });

  it('shows context in error message', () => {
    render(
      <EntityErrorBoundary context="relationship picker">
        <ThrowingComponent shouldThrow={true} />
      </EntityErrorBoundary>,
    );

    expect(screen.getByText(/relationship picker/)).toBeInTheDocument();
  });

  it('shows retry button that resets error state', () => {
    // We can't easily test the retry resets state because the ThrowingComponent
    // will throw again. Instead, verify the retry button exists.
    render(
      <EntityErrorBoundary context="test">
        <ThrowingComponent shouldThrow={true} />
      </EntityErrorBoundary>,
    );

    expect(screen.getByRole('button', { name: /Retry/ })).toBeInTheDocument();
  });

  it('renders custom fallback when provided', () => {
    render(
      <EntityErrorBoundary
        context="test"
        fallback={<div>Custom error UI</div>}
      >
        <ThrowingComponent shouldThrow={true} />
      </EntityErrorBoundary>,
    );

    expect(screen.getByText('Custom error UI')).toBeInTheDocument();
  });

  it('calls onError callback when error is caught', () => {
    const onError = jest.fn();

    render(
      <EntityErrorBoundary context="test" onError={onError}>
        <ThrowingComponent shouldThrow={true} />
      </EntityErrorBoundary>,
    );

    expect(onError).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'Test error from child component' }),
      expect.objectContaining({ componentStack: expect.any(String) }),
    );
  });

  it('captures error to PostHog', () => {
    const { captureEntityEvent } = require('@/lib/observability/entity-events');

    render(
      <EntityErrorBoundary context="data table">
        <ThrowingComponent shouldThrow={true} />
      </EntityErrorBoundary>,
    );

    expect(captureEntityEvent).toHaveBeenCalledWith(
      'entity_save_failed',
      expect.objectContaining({
        error: expect.stringContaining('Test error from child component'),
      }),
    );
  });
});
```

- [ ] **Step 4: Run tests**

Run: `npm test -- --testPathPattern="entity-error-boundary" --verbose`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add components/feature-modules/entity/components/ui/entity-error-boundary.tsx components/feature-modules/entity/components/ui/entity-error-boundary.test.tsx
git commit -m "feat(entity): add reusable EntityErrorBoundary component

Class-based error boundary with contextual fallback UI, retry button,
PostHog error capture, and optional custom fallback. Prevents cascading
UI failures in entity components."
```

---

### Task 2: Add error boundaries to entity dashboard components

**Files:**
- Modify: `components/feature-modules/entity/components/dashboard/entity-dashboard.tsx`
- Modify: `components/feature-modules/entity/components/dashboard/entity-type-dashboard.tsx`
- Modify: `components/feature-modules/entity/components/tables/entity-data-table.tsx`

**Context:** Place `EntityErrorBoundary` at key composition points:

1. **`entity-dashboard.tsx`**: Wrap the `<EntityDataTable>` section so a table crash doesn't kill the breadcrumbs/header
2. **`entity-type-dashboard.tsx`**: Wrap the `<EntityTypeOverview>` section
3. **`entity-data-table.tsx`**: Wrap the draft row rendering and individual cell edit components if feasible

Placement strategy: boundaries go around the largest independent UI sections. Don't wrap every cell — that's too granular and creates visual noise.

- [ ] **Step 1: Add error boundary to entity-dashboard.tsx**

In `entity-dashboard.tsx`, wrap the section content:

```tsx
import { EntityErrorBoundary } from '../ui/entity-error-boundary';

// In the return JSX, wrap the section content:
<section className="flex w-full min-w-0">
  <EntityErrorBoundary context="entity data table">
    <TooltipProvider>
      <EntityTypeConfigurationProvider workspaceId={workspaceId} entityType={entityType}>
        <EntityDraftProvider workspaceId={workspaceId} entityType={entityType}>
          <EntityDataTable
            entityType={entityType}
            entities={entities || []}
            loadingEntities={isPendingEntities || isLoadingAuth}
            workspaceId={workspaceId}
          />
        </EntityDraftProvider>
      </EntityTypeConfigurationProvider>
    </TooltipProvider>
  </EntityErrorBoundary>
</section>
```

- [ ] **Step 2: Add error boundary to entity-type-dashboard.tsx**

In `entity-type-dashboard.tsx`, wrap the section content:

```tsx
import { EntityErrorBoundary } from '../ui/entity-error-boundary';

// In the return JSX:
<section>
  <EntityErrorBoundary context="entity type settings">
    <EntityTypeConfigurationProvider workspaceId={workspaceId} entityType={entityType}>
      <EntityTypeOverview workspaceId={workspaceId} entityType={entityType} />
    </EntityTypeConfigurationProvider>
  </EntityErrorBoundary>
</section>
```

- [ ] **Step 3: Verify build**

Run: `npm run build`
Expected: No errors

- [ ] **Step 4: Commit**

```bash
git add components/feature-modules/entity/components/dashboard/entity-dashboard.tsx components/feature-modules/entity/components/dashboard/entity-type-dashboard.tsx
git commit -m "feat(entity): add error boundaries to entity dashboards

Wraps entity data table and type settings sections in EntityErrorBoundary.
Runtime errors in these areas now show a recoverable fallback instead of
crashing the entire page."
```

---

## Chunk 2: Playwright E2E Test Infrastructure

### Task 3: Set up Playwright configuration

**Files:**
- Create: `playwright.config.ts`
- Create: `e2e/fixtures/entity-api-mocks.ts`
- Create: `e2e/fixtures/entity-type-responses.ts`

**Context:** Playwright is not yet installed. We need to:
1. Install Playwright
2. Create config targeting the Next.js dev server
3. Create shared API mock factories that intercept `localhost:8081/api` calls
4. Create realistic response fixtures

**Important:** The app runs on `localhost:3000` (Next.js dev) and calls `localhost:8081/api` (Spring Boot backend). For E2E tests, we use Playwright's `page.route()` to intercept API calls — no backend needed.

- [ ] **Step 1: Install Playwright**

Run: `npm install -D @playwright/test`
Then: `npx playwright install chromium` (only need one browser for now)

- [ ] **Step 2: Create Playwright config**

```typescript
// playwright.config.ts
import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:3000',
    reuseExistingServer: !process.env.CI,
    timeout: 120 * 1000,
  },
});
```

- [ ] **Step 3: Create API mock factories**

```typescript
// e2e/fixtures/entity-api-mocks.ts
import { Page } from '@playwright/test';

const API_BASE = 'http://localhost:8081/api';

/**
 * Sets up API route interception for entity-related endpoints.
 * Call this in test beforeEach or at the start of each test.
 */
export async function setupEntityApiMocks(page: Page, options: {
  entityTypes?: any[];
  entities?: Record<string, any[]>; // typeId -> entities
  workspaceId?: string;
}) {
  const { entityTypes = [], entities = {}, workspaceId = 'ws-test' } = options;

  // Mock: GET entity types for workspace
  await page.route(
    `${API_BASE}/workspace/${workspaceId}/entity/type`,
    (route) => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(entityTypes),
    }),
  );

  // Mock: GET single entity type by key
  for (const type of entityTypes) {
    await page.route(
      `${API_BASE}/workspace/${workspaceId}/entity/type/${type.key}`,
      (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(type),
      }),
    );
  }

  // Mock: GET entities by type ID
  for (const [typeId, typeEntities] of Object.entries(entities)) {
    await page.route(
      `${API_BASE}/workspace/${workspaceId}/entity/${typeId}`,
      (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(typeEntities),
      }),
    );
  }

  // Mock: POST save entity (returns the entity back)
  await page.route(
    `${API_BASE}/workspace/${workspaceId}/entity/*/save`,
    (route) => {
      const body = route.request().postDataJSON();
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          entity: {
            id: 'new-entity-id',
            workspaceId,
            typeId: 'type-1',
            payload: body?.payload ?? {},
            identifierKey: 'attr-name',
            icon: { type: 'User', colour: 'Neutral' },
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          },
        }),
      });
    },
  );

  // Mock: DELETE entities
  await page.route(
    `${API_BASE}/workspace/${workspaceId}/entity/delete`,
    (route) => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ deletedCount: 1 }),
    }),
  );
}

/**
 * Mocks the auth session (Supabase).
 * Call before navigating to authenticated pages.
 */
export async function setupAuthMock(page: Page) {
  // This depends on how auth is checked — may need to mock Supabase session storage
  // or intercept the Supabase auth endpoint.
  // Placeholder — implementation depends on auth flow specifics.
  await page.addInitScript(() => {
    // Mock the Supabase session in localStorage
    const mockSession = {
      access_token: 'mock-token',
      refresh_token: 'mock-refresh',
      expires_in: 3600,
      token_type: 'bearer',
      user: {
        id: 'user-test',
        email: 'test@example.com',
        role: 'authenticated',
      },
    };
    localStorage.setItem(
      'sb-localhost-auth-token',
      JSON.stringify({ currentSession: mockSession, expiresAt: Date.now() + 3600000 }),
    );
  });
}
```

- [ ] **Step 4: Create entity type response fixtures**

```typescript
// e2e/fixtures/entity-type-responses.ts

/**
 * Mock EntityType responses for E2E tests.
 * These match the actual API response shape.
 */
export const mockContactType = {
  id: 'type-contacts',
  key: 'contacts',
  version: 1,
  name: { singular: 'Contact', plural: 'Contacts' },
  icon: { type: 'User', colour: 'Neutral' },
  identifierKey: 'attr-name',
  _protected: false,
  columns: [
    { key: 'attr-name', type: 'ATTRIBUTE', visible: true, width: 200 },
    { key: 'attr-email', type: 'ATTRIBUTE', visible: true, width: 200 },
  ],
  columnConfiguration: {
    order: ['attr-name', 'attr-email'],
    overrides: {},
  },
  schema: {
    key: 'OBJECT',
    type: 'OBJECT',
    label: 'Contact Schema',
    icon: { type: 'Code', colour: 'Neutral' },
    required: false,
    unique: false,
    _protected: false,
    properties: {
      'attr-name': {
        key: 'TEXT',
        type: 'STRING',
        label: 'Name',
        icon: { type: 'ALargeSmall', colour: 'Neutral' },
        required: true,
        unique: false,
        _protected: false,
      },
      'attr-email': {
        key: 'EMAIL',
        type: 'STRING',
        format: 'EMAIL',
        label: 'Email',
        icon: { type: 'AtSign', colour: 'Neutral' },
        required: false,
        unique: false,
        _protected: false,
      },
    },
  },
  relationships: [],
  workspaceId: 'ws-test',
  entitiesCount: 0,
};

export const mockCompanyType = {
  id: 'type-companies',
  key: 'companies',
  version: 1,
  name: { singular: 'Company', plural: 'Companies' },
  icon: { type: 'Building', colour: 'Blue' },
  identifierKey: 'attr-company-name',
  _protected: false,
  columns: [
    { key: 'attr-company-name', type: 'ATTRIBUTE', visible: true, width: 250 },
  ],
  columnConfiguration: {
    order: ['attr-company-name'],
    overrides: {},
  },
  schema: {
    key: 'OBJECT',
    type: 'OBJECT',
    label: 'Company Schema',
    icon: { type: 'Code', colour: 'Neutral' },
    required: false,
    unique: false,
    _protected: false,
    properties: {
      'attr-company-name': {
        key: 'TEXT',
        type: 'STRING',
        label: 'Company Name',
        icon: { type: 'Building', colour: 'Blue' },
        required: true,
        unique: false,
        _protected: false,
      },
    },
  },
  relationships: [],
  workspaceId: 'ws-test',
  entitiesCount: 0,
};

export const mockContactEntity = {
  id: 'entity-contact-1',
  workspaceId: 'ws-test',
  typeId: 'type-contacts',
  identifierKey: 'attr-name',
  icon: { type: 'User', colour: 'Neutral' },
  payload: {
    'attr-name': {
      payload: {
        value: 'Jane Smith',
        schemaType: 'TEXT',
        type: 'ATTRIBUTE',
      },
    },
    'attr-email': {
      payload: {
        value: 'jane@example.com',
        schemaType: 'EMAIL',
        type: 'ATTRIBUTE',
      },
    },
  },
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};
```

- [ ] **Step 5: Commit**

```bash
git add playwright.config.ts e2e/
git commit -m "chore: set up Playwright E2E testing infrastructure

Playwright config targeting Next.js dev server with API route interception.
Shared mock factories for entity API endpoints and Supabase auth.
EntityType and Entity response fixtures."
```

---

### Task 4: Write E2E test for entity CRUD flow

**Files:**
- Create: `e2e/entity-crud.spec.ts`

**Context:** This test exercises the core entity data entry flow end-to-end:
1. Navigate to entity type dashboard
2. See entity data table with existing entities
3. Enter draft mode (click "Add" button)
4. Fill in fields
5. Submit draft
6. Verify new entity appears in the table

**Important:** The exact selectors (button text, input labels, data-testid attributes) depend on the actual component rendering. Read the entity data table and draft row components to identify the correct selectors.

This test requires mocked API responses. The auth mock must be set up before navigation.

- [ ] **Step 1: Write the entity CRUD E2E test**

```typescript
// e2e/entity-crud.spec.ts
import { test, expect } from '@playwright/test';
import { setupEntityApiMocks, setupAuthMock } from './fixtures/entity-api-mocks';
import { mockContactType, mockContactEntity } from './fixtures/entity-type-responses';

test.describe('Entity CRUD', () => {
  test.beforeEach(async ({ page }) => {
    await setupAuthMock(page);
  });

  test('displays entities in the data table', async ({ page }) => {
    await setupEntityApiMocks(page, {
      entityTypes: [mockContactType],
      entities: { 'type-contacts': [mockContactEntity] },
      workspaceId: 'ws-test',
    });

    // Also mock workspace endpoint
    await page.route('**/api/workspace/ws-test', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ id: 'ws-test', name: 'Test Workspace' }),
      }),
    );

    await page.goto('/dashboard/workspace/ws-test/entity/contacts');

    // Wait for the entity table to load
    await expect(page.getByText('Jane Smith')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText('jane@example.com')).toBeVisible();
  });

  test('creates a new entity via draft row', async ({ page }) => {
    await setupEntityApiMocks(page, {
      entityTypes: [mockContactType],
      entities: { 'type-contacts': [] }, // Start empty
      workspaceId: 'ws-test',
    });

    await page.route('**/api/workspace/ws-test', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ id: 'ws-test', name: 'Test Workspace' }),
      }),
    );

    await page.goto('/dashboard/workspace/ws-test/entity/contacts');

    // Wait for page to load
    await page.waitForLoadState('networkidle');

    // Click the "Add" or "New" button to enter draft mode
    // NOTE: Exact selector depends on the actual button text in entity-data-table.tsx
    // Check the actual component to find the correct button
    const addButton = page.getByRole('button', { name: /add|new|create/i });
    if (await addButton.isVisible()) {
      await addButton.click();

      // The draft row should appear — fill in the Name field
      // NOTE: Exact input selectors depend on the draft row rendering
      // This is a skeleton — update selectors based on actual component inspection
      await page.waitForTimeout(500); // Wait for draft row animation

      // Verify draft mode is active (UI shows a new row or form)
      // Fill, submit, and verify — implementation depends on actual selectors
    }
  });
});
```

**Note to implementer:** This test is a skeleton. The exact selectors depend on:
1. The "Add entity" button text/role in `entity-data-table.tsx`
2. The draft row input rendering in `entity-draft-row.tsx`
3. The submit button in the draft row
4. How the auth mock interacts with the Supabase client

Before running this test:
1. Read `entity-data-table.tsx` to find the add button selector
2. Read `entity-draft-row.tsx` to find input selectors
3. Read the auth context to understand how session is checked
4. Update selectors accordingly
5. Verify the workspace/profile API routes are also mocked

- [ ] **Step 2: Run the E2E test**

Run: `npx playwright test e2e/entity-crud.spec.ts --headed`
Expected: The first test (display entities) should work with proper mocking. The second test (create entity) needs selector refinement.

- [ ] **Step 3: Iterate on selectors until tests pass**

This is expected to require 2-3 iterations of:
1. Run test with `--headed` to see what renders
2. Use Playwright's inspector (`npx playwright test --debug`) to find correct selectors
3. Update test selectors
4. Re-run

- [ ] **Step 4: Commit**

```bash
git add e2e/entity-crud.spec.ts
git commit -m "test(e2e): add entity CRUD E2E test

Tests entity display in data table and entity creation via draft row.
Uses Playwright route interception to mock backend API responses."
```

---

### Task 5: Write E2E test for relationship flows

**Files:**
- Create: `e2e/entity-relationships.spec.ts`

**Context:** This tests the relationship lifecycle:
1. Create a relationship definition on an entity type
2. Create an entity with a relationship value
3. Verify the relationship link appears in the data table
4. Verify the reverse relationship (bidirectional) appears on the target entity

This is the most complex E2E test because it involves:
- Navigating to entity type settings
- Opening the relationship form modal
- Filling in relationship definition (name, cardinality, target rules)
- Saving the definition
- Navigating back to the data table
- Creating an entity with a relationship value
- Navigating to the target entity to verify the reverse link

**Important:** This test skeleton needs the same selector refinement as Task 4. The relationship form modal (`relationship-form.tsx`), relationship picker (`entity-relationship-picker.tsx`), and entity type settings UI all need their selectors identified.

- [ ] **Step 1: Write the relationship E2E test skeleton**

```typescript
// e2e/entity-relationships.spec.ts
import { test, expect } from '@playwright/test';
import { setupEntityApiMocks, setupAuthMock } from './fixtures/entity-api-mocks';
import {
  mockContactType,
  mockCompanyType,
  mockContactEntity,
} from './fixtures/entity-type-responses';

test.describe('Entity Relationships', () => {
  test.beforeEach(async ({ page }) => {
    await setupAuthMock(page);
  });

  test('displays relationship links in entity data table', async ({ page }) => {
    // Create a contact type with a relationship to companies
    const contactTypeWithRelationship = {
      ...mockContactType,
      relationships: [
        {
          id: 'rel-company',
          workspaceId: 'ws-test',
          sourceEntityTypeId: 'type-contacts',
          name: 'Company',
          icon: { type: 'Building', colour: 'Blue' },
          _protected: false,
          cardinalityDefault: 'MANY_TO_ONE',
          targetRules: [
            {
              id: 'rule-1',
              relationshipDefinitionId: 'rel-company',
              targetEntityTypeId: 'type-companies',
              inverseName: 'Contacts',
              cardinalityDefault: 'MANY_TO_ONE',
            },
          ],
          isPolymorphic: false,
        },
      ],
    };

    const contactWithRelationship = {
      ...mockContactEntity,
      payload: {
        ...mockContactEntity.payload,
        'rel-company': {
          payload: {
            relations: [
              {
                id: 'link-1',
                workspaceId: 'ws-test',
                sourceEntityId: 'entity-contact-1',
                fieldId: 'rel-company',
                definitionId: 'rel-company',
                label: 'Acme Corp',
                key: 'companies',
                icon: { type: 'Building', colour: 'Blue' },
              },
            ],
            type: 'RELATIONSHIP',
          },
        },
      },
    };

    await setupEntityApiMocks(page, {
      entityTypes: [contactTypeWithRelationship, mockCompanyType],
      entities: {
        'type-contacts': [contactWithRelationship],
        'type-companies': [],
      },
      workspaceId: 'ws-test',
    });

    await page.route('**/api/workspace/ws-test', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ id: 'ws-test', name: 'Test Workspace' }),
      }),
    );

    await page.goto('/dashboard/workspace/ws-test/entity/contacts');

    // Wait for entities to load
    await expect(page.getByText('Jane Smith')).toBeVisible({ timeout: 10000 });

    // Verify the relationship badge is visible
    await expect(page.getByText('Acme Corp')).toBeVisible();
  });

  // Additional tests to implement:
  // - test('creates entity with relationship selection via picker')
  // - test('relationship picker shows correct target type entities')
  // - test('bidirectional relationship appears on target entity view')
  // - test('relationship cardinality limits are enforced in picker')
});
```

- [ ] **Step 2: Run tests**

Run: `npx playwright test e2e/entity-relationships.spec.ts --headed`
Expected: First test should pass with correct API mocking. Additional tests need implementation.

- [ ] **Step 3: Commit**

```bash
git add e2e/entity-relationships.spec.ts
git commit -m "test(e2e): add entity relationship E2E test

Tests relationship link display in entity data table with mocked API responses.
Skeleton for relationship picker, bidirectional verification, and cardinality tests."
```

---

## Verification

After completing all tasks:

- [ ] **Run unit tests**: `npm test -- --verbose` — all pass
- [ ] **Run lint**: `npm run lint` — no errors
- [ ] **Run build**: `npm run build` — no type errors
- [ ] **Run E2E tests**: `npx playwright test` — passing tests pass, skeletons are marked as TODO

---

## Success Criteria

1. `EntityErrorBoundary` component exists with retry, PostHog capture, and custom fallback support
2. Error boundaries wrap `EntityDataTable` section in `entity-dashboard.tsx` and `EntityTypeOverview` section in `entity-type-dashboard.tsx`
3. Playwright is installed and configured with API route interception
4. At least 1 passing E2E test for entity display in data table
5. At least 1 passing E2E test for relationship link display
6. E2E test skeletons exist for entity creation and bidirectional relationship verification
7. All existing tests continue to pass
8. `npm run lint` and `npm run build` succeed
