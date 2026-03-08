# Technology Stack

**Analysis Date:** 2026-03-08

## Languages

**Primary:**
- TypeScript ^5 - All application code (strict mode via `@riven/tsconfig/nextjs.json`)

**Secondary:**
- CSS - Tailwind 4 with CSS-based configuration in `app/globals.css`
- Bash - Type generation script `scripts/generate-types.sh`

## Runtime

**Environment:**
- Node.js (version not pinned; no `.nvmrc` or `.node-version` present)
- Next.js 15.3.4 (App Router, React Server Components)

**Package Manager:**
- pnpm 10.28.2 (declared in monorepo root `package.json` via `packageManager` field)
- Lockfile: `pnpm-lock.yaml` at monorepo root

## Monorepo Structure

**Orchestrator:** Turborepo ^2.5.4 (`turbo.json` at monorepo root)

**Workspace Layout:**
- `apps/client` - This Next.js frontend app (`@riven/client`)
- `apps/web` - Secondary web app (likely marketing/landing)
- `packages/ui` - Shared UI components (`@riven/ui`)
- `packages/hooks` - Shared React hooks (`@riven/hooks`)
- `packages/utils` - Shared utilities (`@riven/utils` - provides `cn()` via `clsx` + `tailwind-merge`)
- `packages/tsconfig` - Shared TypeScript configuration (`@riven/tsconfig`)

**Environment Loading:**
- `dotenv-cli` loads `.env` from monorepo root (`../../.env` relative to `apps/client`)
- Dev command: `dotenv -e ../../.env -- next dev --port 3001`

## Frameworks

**Core:**
- Next.js 15.3.4 - App Router, standalone output mode (`next.config.ts`)
- React 19 - UI rendering
- React DOM 19 - DOM bindings

**State Management:**
- Zustand ^5.0.8 - Client-side stores (5 feature-scoped stores)
- TanStack React Query ^5.81.2 - Server state, caching, mutations
- React Hook Form ^7.58.1 - Form state management

**UI Component System:**
- shadcn/ui (new-york style) - Base components in `components/ui/`
- Radix UI primitives - Dialog, Select, Checkbox, Avatar, Collapsible, etc.
- `@riven/ui` shared package - Button, Input, Card, Badge, Label, etc.

**Testing:**
- Jest ^29.7.0 - Test runner
- ts-jest ^29.3.4 - TypeScript transform
- React Testing Library ^16.1.0 - Component testing utilities
- jest-environment-jsdom ^29.7.0 - Browser environment simulation
- Config: `jest.config.ts`, setup: `jest.setup.ts`

**Build/Dev:**
- Turborepo ^2.5.4 - Monorepo task orchestration
- PostCSS with `@tailwindcss/postcss` plugin (`postcss.config.mjs`)
- ESLint ^9 with flat config (`eslint.config.mjs`) - extends `next/core-web-vitals`, `next/typescript`, `prettier`
- Prettier ^3.8.1 with `prettier-plugin-tailwindcss`

## Key Dependencies

**Critical:**
- `@supabase/supabase-js` ^2.50.0 - Authentication client
- `@supabase/ssr` ^0.6.1 - Server-side Supabase client (cookie-based sessions)
- `@tanstack/react-query` ^5.81.2 - All data fetching goes through this
- `zod` ^3.25.67 - Schema validation for forms and data
- `@hookform/resolvers` ^5.1.1 - Bridges zod schemas to react-hook-form

**Visual/Interactive:**
- `@xyflow/react` ^12.10.0 - Visual workflow builder (React Flow)
- `gridstack` ^12.3.3 - Dashboard grid layouts for blocks feature
- `@dnd-kit/core` ^6.3.1 + `@dnd-kit/sortable` ^10.0.0 - Drag-and-drop interactions
- `framer-motion` ^12.23.24 / `motion` ^12.23.12 - Animations
- `recharts` ^2.15.4 - Charts (used via shadcn chart component)
- `react-resizable-panels` ^3.0.6 - Resizable panel layouts

**Data/Utility:**
- `date-fns` ^4.1.0 - Date formatting
- `dayjs` ^1.11.19 - Date manipulation (dual date library)
- `uuid` ^11.1.0 - UUID generation
- `validator` ^13.15.15 - String validation
- `jsonpath-plus` ^10.3.0 - JSONPath queries for block data binding
- `jsonpointer` ^5.0.1 - JSON pointer resolution

**UI Primitives:**
- `lucide-react` ^0.522.0 - Icon library (primary)
- `react-icons` ^5.5.0 - Additional icons
- `cmdk` ^1.1.1 - Command palette
- `sonner` ^2.0.7 - Toast notifications
- `vaul` ^1.1.2 - Drawer component
- `input-otp` ^1.4.2 - OTP input for auth
- `react-day-picker` ^9.7.0 - Date picker
- `react-phone-number-input` ^3.4.12 - Phone number input
- `class-variance-authority` ^0.7.1 - Component variant management
- `clsx` ^2.1.1 + `tailwind-merge` ^3.3.1 - Conditional class composition

**Code Generation:**
- `@openapitools/openapi-generator-cli` ^2.28.0 (devDependency) - Generates TypeScript API clients from OpenAPI spec
- `openapi-typescript` ^7.8.0 - OpenAPI type generation (listed but generation script uses openapi-generator-cli)

**Infrastructure:**
- `next-themes` ^0.4.6 - Theme switching (light/dark/amber)
- `tw-animate-css` ^1.3.4 - Tailwind animation utilities

## Styling

**Framework:** Tailwind CSS ^4
- CSS-based config in `app/globals.css` (no `tailwind.config.ts`)
- PostCSS integration via `@tailwindcss/postcss`
- Color system: CSS custom properties using oklch color space
- Semantic tokens: `--background`, `--foreground`, `--primary`, `--muted`, `--destructive`, `--edit`, `--archive`, plus sidebar and chart variants
- Dark mode: class-based (`.dark` on `<html>`) via `next-themes`
- Custom variant: `@custom-variant dark (&:is(.dark *))` and `@custom-variant amber (&:is(.amber *))`

**Fonts (Google Fonts via `next/font`):**
- Geist (sans-serif, `--font-geist-sans`)
- Geist Mono (monospace, `--font-geist-mono`)
- Instrument Serif (serif, `--font-instrument-serif`)
- Space Mono (display, `--font-space-mono`)

## Configuration

**Environment:**
- `.env` file at monorepo root (`/home/jared/dev/worktrees/onboarding/.env`)
- `.env.example` present for reference
- Loaded via `dotenv-cli` in npm scripts
- Required vars: `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY`, `NEXT_PUBLIC_API_URL`, `NEXT_PUBLIC_HOSTED_URL`, `NEXT_PUBLIC_AUTH_PROVIDER`, `NEXT_PUBLIC_GOOGLE_MAPS_API_KEY`
- Optional: `NEXT_PUBLIC_COOKIE_DOMAIN`

**TypeScript:**
- `tsconfig.json` extends `@riven/tsconfig/nextjs.json`
- Path alias: `@/*` maps to project root
- Incremental compilation enabled

**Build:**
- `next.config.ts` - Standalone output, transpiles `@riven/ui`, `@riven/hooks`, `@riven/utils`
- `postcss.config.mjs` - Tailwind PostCSS plugin
- `eslint.config.mjs` - Flat config extending Next.js + Prettier rules

## Platform Requirements

**Development:**
- Node.js (no version pinned)
- pnpm 10.28.2
- Backend API running at `localhost:8081` (required for type generation)
- Supabase project (for authentication)

**Production:**
- Standalone Next.js build (`output: "standalone"` in `next.config.ts`)
- Target: `https://app.getriven.io` (per OpenGraph metadata)

## Run Commands

```bash
npm run dev              # Start dev server on port 3001
npm run build            # Production build
npm run start            # Start production server
npm run lint             # ESLint check
npm run type-check       # TypeScript type checking (tsc --noEmit)
npm run types            # Regenerate API types from OpenAPI spec
npm run test             # Run Jest tests
npm run test:watch       # Jest watch mode
npm run format           # Prettier format
npm run format:check     # Prettier check
```

---

*Stack analysis: 2026-03-08*
