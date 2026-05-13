# @cranium/client

The main Cranium dashboard. This is where users manage entities, build workflows, connect integrations and interact with the knowledge base.

## Tech stack

- Next.js 15 (App Router, React 19, TypeScript strict)
- Tailwind CSS 4 + shadcn/ui (new-york) + Framer Motion
- Zustand (factory + context pattern) + TanStack Query 5
- react-hook-form + Zod
- Supabase auth (via adapter pattern)
- OpenAPI-generated TypeScript client
- BlockNote (rich text editor)
- React Flow, Recharts, dnd-kit

## Development

```sh
# From the monorepo root
pnpm --filter @cranium/client dev
```

Runs on [http://localhost:3001](http://localhost:3001). Needs the backend API running on `:8081`.

### Environment variables

Create `apps/client/.env.local`:

```env
NEXT_PUBLIC_SUPABASE_URL=https://your-project.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=your-anon-key
NEXT_PUBLIC_API_URL=http://localhost:8081/api
NEXT_PUBLIC_HOSTED_URL=http://localhost:3001
NEXT_PUBLIC_AUTH_PROVIDER=supabase
```

### Type generation

Regenerate API types from the backend OpenAPI spec (backend must be running):

```sh
npm run types
```

## Project structure

```
app/
  auth/             # Login, signup, OAuth callback
  dashboard/        # Main application routes
  api/              # API routes
components/
  feature-modules/  # Domain-specific features
    entity/         # Entity management
    workflow/       # Workflow builder (React Flow)
    integration/    # SaaS connectors
    knowledge/      # AI knowledge base
    ...
  ui/               # shadcn + custom components
  provider/         # Context providers
hooks/              # Shared React hooks
lib/
  api/              # API client factories
  auth/             # Auth adapter layer
  types/            # Generated + custom TypeScript types
  util/             # Shared utilities
```
