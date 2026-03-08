# Codebase Concerns

**Analysis Date:** 2026-03-08

## Tech Debt

**Blocks module complexity:**
- Issue: The blocks feature module has grown into the largest and most complex area of the codebase. Context providers alone total 4,588 lines across 10 files, with `layout-change-provider.tsx` at 948 lines and `block-environment-provider.tsx` at 733 lines. Heavy use of `useEffect` (14 instances in context providers alone) creates complex lifecycle interactions.
- Files: `components/feature-modules/blocks/context/layout-change-provider.tsx`, `components/feature-modules/blocks/context/block-environment-provider.tsx`, `components/feature-modules/blocks/context/block-edit-provider.tsx` (727 lines), `components/feature-modules/blocks/context/grid-container-provider.tsx` (447 lines)
- Impact: Difficult to reason about state flow, high risk of regressions, hard for new contributors to understand.
- Fix approach: Break large providers into smaller, focused hooks. Extract pure logic from providers into utility functions. Consider replacing some context with Zustand stores for complex state.

**Pervasive `any` type usage outside generated code:**
- Issue: At least 50 instances of `any` in hand-written code (excluding `lib/types/`). Concentrated in blocks module (`block.list.tsx`, `block-structure-renderer.tsx`, `block-edit-provider.tsx`, `list-sorting.util.ts`), entity tables (`entity-table-utils.tsx`, `entity-data-table.tsx`), and data-table cells (`editable-cell.tsx`, `cell-editor-widget.tsx`). Many are `as any` casts to bypass type checking.
- Files: `components/feature-modules/blocks/components/primitive/block.list.tsx`, `components/feature-modules/blocks/components/render/block-structure-renderer.tsx`, `components/feature-modules/blocks/context/block-edit-provider.tsx`, `components/feature-modules/blocks/util/list/list-sorting.util.ts`, `components/feature-modules/entity/components/tables/entity-table-utils.tsx`, `components/ui/data-table/components/cells/editable-cell.tsx`
- Impact: Type safety is defeated, runtime errors go undetected until production. Especially dangerous in `entity-table-utils.tsx` where `formatEntityAttributeValue(value: any, schema: any)` processes dynamic data.
- Fix approach: Create proper generic types for the block component system. Define typed interfaces for form widget props. Use discriminated unions for entity attribute values instead of `any`.

**Rich editor is a standalone subsystem with demo/mock code in production:**
- Issue: The rich editor under `components/ui/rich-editor/` contains 6,450 lines of demo/template content (`templates.ts` at 3,899 lines, `demo-content.ts` at 2,551 lines) shipped in the production bundle. The editor itself (`editor.tsx`) is 1,288 lines. History/undo is intentionally disabled (marked `TEMPORARY` in `editor-reducer.ts` line 33). Image upload uses a mock that returns random Unsplash URLs with a simulated 5% failure rate.
- Files: `components/ui/rich-editor/templates.ts`, `components/ui/rich-editor/demo-content.ts`, `components/ui/rich-editor/lib/reducer/editor-reducer.ts`, `components/ui/rich-editor/utils/image-upload.ts`
- Impact: Bundle size bloat from demo content. Undo/redo does not work. Image uploads are non-functional in production.
- Fix approach: Move templates/demo-content to dev-only imports or lazy-load them. Re-enable the history system in `editor-reducer.ts` (original code is preserved in comments). Replace mock `uploadImage()` with real storage API integration.

**Entirely commented-out modal files:**
- Issue: Two modal components are 100% commented out: `entity-selector-modal.tsx` (260 lines) and `type-picker-modal.tsx` (234 lines). These are dead code files that still exist in the codebase.
- Files: `components/feature-modules/blocks/components/modals/entity-selector-modal.tsx`, `components/feature-modules/blocks/components/modals/type-picker-modal.tsx`
- Impact: Confusing for developers, clutters the codebase, imports may reference stale types.
- Fix approach: Delete these files entirely. If needed later, retrieve from git history.

**Inconsistent file naming conventions:**
- Issue: Mix of PascalCase and kebab-case across the codebase. At least 20+ PascalCase component files exist, concentrated in `authentication/components/` (all PascalCase: `Login.tsx`, `Register.tsx`, `ThirdPartyAuth.tsx`, etc.), `blocks/components/bespoke/` (all PascalCase: `AddressCard.tsx`, `TaskCard.tsx`, etc.), and `ui/rich-editor/` (PascalCase: `CoverImage.tsx`, `SelectionToolbar.tsx`, etc.). CLAUDE.md mandates kebab-case for all new files.
- Files: `components/feature-modules/authentication/components/Login.tsx`, `components/feature-modules/authentication/components/Register.tsx`, `components/feature-modules/blocks/components/bespoke/AddressCard.tsx`, `components/ui/AvatarUploader.tsx`, `components/ui/rich-editor/SelectionToolbar.tsx`
- Impact: Inconsistency reduces codebase predictability and violates stated conventions.
- Fix approach: Batch rename PascalCase files to kebab-case. Update all import paths. Do this in a dedicated cleanup PR to avoid mixing with feature work.

**Inconsistent store directory naming:**
- Issue: Entity and workflow features use `stores/` (plural) while workspace uses `store/` (singular).
- Files: `components/feature-modules/entity/stores/`, `components/feature-modules/workflow/stores/`, `components/feature-modules/workspace/store/`
- Impact: Minor inconsistency but adds friction when creating new stores.
- Fix approach: Standardize on `store/` (singular) to match the singular `service/`, `context/` convention used elsewhere.

**No default QueryClient options:**
- Issue: Every query hook independently configures `staleTime`, `gcTime`, and `retry`. Values vary significantly: `staleTime` ranges from 30 seconds (`use-entity-layout.ts`) to 30 minutes (`use-workflow-node-config.ts`). Most use 5 minutes. No centralized defaults exist.
- Files: `components/feature-modules/entity/hooks/query/use-entities.ts`, `components/feature-modules/blocks/hooks/use-entity-layout.ts`, `components/feature-modules/workflow/hooks/query/use-workflow-node-config.ts`, all query hooks across feature modules
- Impact: Inconsistent caching behavior. Easy to forget setting cache times on new hooks, resulting in excessive refetching.
- Fix approach: Set sensible defaults on the `QueryClient` in the provider (`staleTime: 5 * 60 * 1000`, `gcTime: 10 * 60 * 1000`, `retry: 1`). Override only when hooks have specific requirements.

**Incomplete TODO implementations in layout change provider:**
- Issue: Critical conflict resolution code in `layout-change-provider.tsx` has TODO placeholders for `use-theirs` and `keep-mine` conflict resolution flows. The `use-theirs` branch is completely empty. Multiple other TODOs reference unimplemented backend integration.
- Files: `components/feature-modules/blocks/context/layout-change-provider.tsx` (lines 572, 582, 610, 846, 856, 884)
- Impact: Conflict resolution between concurrent editors will silently fail or produce undefined behavior.
- Fix approach: Implement the conflict resolution strategies or remove the UI that exposes them until they are functional.

## Security Considerations

**XSS risk in rich editor HTML rendering:**
- Risk: `dangerouslySetInnerHTML` is used in `ExportFloatingButton.tsx` (line 131) to render serialized editor content. The `buildHTML()` function in `block-utils.ts` constructs HTML strings from user data including `child.href`, `child.content`, and `child.className`. While `escapeHTML()` exists for code blocks, regular content is inserted without escaping. Link `href` values are inserted directly into HTML attribute strings without sanitization.
- Files: `components/ui/rich-editor/ExportFloatingButton.tsx`, `components/ui/rich-editor/handlers/block/block-utils.ts`, `components/ui/rich-editor/block.tsx` (line 425 sets `element.innerHTML`)
- Current mitigation: Content is user-generated within the app (not external input), and `escapeHTML()` is applied to code blocks.
- Recommendations: Sanitize all HTML output through DOMPurify before `dangerouslySetInnerHTML` usage. Validate `href` values against an allowlist of protocols (http, https, mailto). Escape content values in `buildHTML()` consistently, not just for code blocks.

**No CSRF protection on auth callback:**
- Risk: The OAuth callback route (`app/api/auth/token/callback/route.ts`) exchanges a code for a token without explicit CSRF validation beyond what Supabase provides.
- Files: `app/api/auth/token/callback/route.ts`
- Current mitigation: Supabase PKCE flow handles state verification internally.
- Recommendations: Verify this is sufficient. Consider adding explicit state parameter validation.

## Performance Bottlenecks

**Block context provider cascade:**
- Problem: The blocks feature uses a deep nesting of 10+ context providers, each potentially triggering re-renders. Any state change in `layout-change-provider.tsx` (948 lines of state and callbacks) can cascade through child providers.
- Files: `components/feature-modules/blocks/context/` (all 10 provider files)
- Cause: Monolithic context values with many fields. Any field change re-renders all consumers.
- Improvement path: Split large context values into smaller, focused contexts. Use `useMemo` on context values (verify this is consistent). Consider migrating performance-critical state to Zustand with selectors for granular subscriptions.

**Large bundle from rich editor templates:**
- Problem: `templates.ts` (3,899 lines) and `demo-content.ts` (2,551 lines) are statically imported and included in the main bundle.
- Files: `components/ui/rich-editor/templates.ts`, `components/ui/rich-editor/demo-content.ts`
- Cause: Static imports at module level.
- Improvement path: Dynamic import templates only when the template picker is opened. Move demo content to a dev-only module or fixture file.

**Mock factory in production code:**
- Problem: `mock.factory.ts` (550 lines) is imported by `editor-panel.tsx` for production rendering, suggesting mock data structures are used in the actual UI.
- Files: `components/feature-modules/blocks/util/block/factory/mock.factory.ts`, `components/feature-modules/blocks/components/panel/editor-panel.tsx`
- Cause: Development scaffolding that was never replaced with real data sources.
- Improvement path: Replace mock factory usage with real data from API/store. If mock data is needed for empty states, create minimal placeholder data instead.

## Fragile Areas

**Blocks context provider chain:**
- Files: `components/feature-modules/blocks/context/layout-change-provider.tsx`, `components/feature-modules/blocks/context/block-environment-provider.tsx`, `components/feature-modules/blocks/context/block-edit-provider.tsx`, `components/feature-modules/blocks/context/grid-container-provider.tsx`
- Why fragile: 10 interdependent context providers with circular data flow patterns. `layout-change-provider.tsx` has duplicated conflict resolution code (TODO blocks at lines 572 vs 846, 582 vs 856). Many `console.warn` statements (50+ across all block context files) indicate frequent runtime edge cases.
- Safe modification: Changes to any block context provider require testing the entire block rendering, editing, and saving flow end-to-end. Do not modify one provider without understanding its consumers.
- Test coverage: 3 test files exist for bespoke block components (`AddressCard.test.tsx`, `ContactCard.test.tsx`, `FallbackBlock.test.tsx`). No tests for any context provider, hook, or utility.

**Entity data table with dynamic columns:**
- Files: `components/feature-modules/entity/components/tables/entity-table-utils.tsx` (666 lines), `components/feature-modules/entity/components/tables/entity-data-table.tsx`
- Why fragile: Heavy `any` usage (6 instances), dynamic column generation from schema types, inline edit functionality that casts to `any` in multiple places. The `formatEntityAttributeValue` function accepts `(value: any, schema: any)` and handles 10+ schema types.
- Safe modification: Test all schema types (text, number, date, location, dropdown, etc.) after any change. The editable cell flow passes through 4 `as any` casts.
- Test coverage: No tests.

**Rich editor reducer with disabled history:**
- Files: `components/ui/rich-editor/lib/reducer/editor-reducer.ts` (1,150 lines), `components/ui/rich-editor/lib/reducer/actions.ts` (703 lines)
- Why fragile: History/undo system is disabled with a note saying "TEMPORARY". The `addToHistory` function overwrites current state instead of pushing to history stack. Original code is preserved in comments but may have drifted from the current action types.
- Safe modification: Re-enabling history requires verifying the commented-out code still matches the current `EditorAction` types and state shape.
- Test coverage: No tests.

## Missing Critical Features

**No error boundaries:**
- Problem: Zero React error boundaries exist in the application. An unhandled error in any component crashes the entire app.
- Blocks: Graceful degradation for individual block rendering failures, partial page recovery.
- Files: No `error.tsx` files in `app/` routes, no `ErrorBoundary` components anywhere.

**No Next.js loading/error route files:**
- Problem: No `loading.tsx`, `error.tsx`, or `not-found.tsx` files exist in any route segment. Users see no loading states during route transitions and get full-page crashes on errors.
- Blocks: Route-level loading skeletons, graceful error recovery, custom 404 pages.

**Image upload not implemented:**
- Problem: The rich editor's image upload returns random Unsplash URLs from a mock array with simulated latency and a 5% random failure rate.
- Files: `components/ui/rich-editor/utils/image-upload.ts`
- Blocks: Any feature requiring image upload in the editor.

## Test Coverage Gaps

**Near-zero test coverage:**
- What's not tested: The entire application has only 5 test files: 3 bespoke block component tests (`AddressCard.test.tsx`, `ContactCard.test.tsx`, `FallbackBlock.test.tsx`) and 2 barrel export verification tests. No tests exist for: stores (5 stores), services (6 API factories), query/mutation hooks (15+ hooks), context providers (10+ providers), form validation schemas, utility functions, or any page/feature component.
- Files: All of `components/feature-modules/*/hooks/`, `components/feature-modules/*/store*/`, `components/feature-modules/*/service/`, `lib/util/`, `lib/api/`
- Risk: Any refactoring or bug fix has no safety net. Regressions are only caught manually.
- Priority: High. Start with Zustand stores and zod schemas per the testing strategy in CLAUDE.md.

**Block context providers are untested:**
- What's not tested: The 4,588 lines of block context provider code have zero tests. This includes layout saving, conflict resolution, block editing, grid management, and environment management.
- Files: `components/feature-modules/blocks/context/*.tsx`
- Risk: The most complex and fragile area of the codebase has no automated verification.
- Priority: High. Extract pure logic into testable utility functions first, then test those.

**Entity table utilities untested:**
- What's not tested: `entity-table-utils.tsx` (666 lines) handles dynamic column creation, value formatting for all schema types, and inline editing setup. No tests verify correct formatting for any schema type.
- Files: `components/feature-modules/entity/components/tables/entity-table-utils.tsx`
- Risk: Adding new schema types or modifying formatting could silently break existing type rendering.
- Priority: Medium. The `formatEntityAttributeValue` function is a pure function that is straightforward to test.

## Dependencies at Risk

**Stale lockfile (pnpm-lock.yaml at monorepo root):**
- Risk: A `pnpm-lock.yaml` exists at `/home/jared/dev/worktrees/onboarding/pnpm-lock.yaml` while npm is the canonical package manager. No `package-lock.json` was found at the client app level (likely managed at monorepo root or via workspace config).
- Impact: Contributors may use the wrong package manager, leading to dependency resolution inconsistencies.
- Migration plan: Delete `pnpm-lock.yaml` if npm is canonical. Ensure `package-lock.json` exists and is committed.

**GridStack integration via direct DOM manipulation:**
- Risk: `grid-container-provider.tsx` uses `GridStack.renderCB` and `GridStack.resizeToContentCB` with direct DOM manipulation and `(globalThis as any).CSS?.escape`. This bypasses React's rendering model.
- Impact: React state and DOM can get out of sync. Errors are caught with try/catch and logged as "non-critical" (25+ such catches).
- Files: `components/feature-modules/blocks/context/grid-container-provider.tsx`
- Migration plan: Evaluate React-native grid layout alternatives. If GridStack must stay, create a thin abstraction layer that synchronizes GridStack state with React state more robustly.

---

*Concerns audit: 2026-03-08*
