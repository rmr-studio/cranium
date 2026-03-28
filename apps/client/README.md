# @riven/client

The main Riven dashboard application — a multi-tenant SaaS platform for managing entities, workflows, integrations and knowledge.

## Tech Stack

- **Framework:** Next.js 15 (App Router, React 19, TypeScript strict)
- **Styling:** Tailwind CSS 4 + shadcn/ui (new-york) + Framer Motion
- **State:** Zustand (factory + context pattern) + TanStack Query 5
- **Forms:** react-hook-form + Zod
- **Auth:** Supabase (via adapter pattern)
- **API:** OpenAPI-generated TypeScript client
- **Rich Text:** BlockNote editor
- **Visualisation:** React Flow, Recharts, dnd-kit

## Development

```sh
# From the monorepo root
pnpm --filter @riven/client dev
```

Runs on [http://localhost:3001](http://localhost:3001). Requires the backend API running on `:8081`.

### Environment Variables

Create `apps/client/.env.local`:

```env
NEXT_PUBLIC_SUPABASE_URL=https://your-project.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=your-anon-key
NEXT_PUBLIC_API_URL=http://localhost:8081/api
NEXT_PUBLIC_HOSTED_URL=http://localhost:3001
NEXT_PUBLIC_AUTH_PROVIDER=supabase
```

### Type Generation

Regenerate API types from the backend OpenAPI spec (requires the backend running):

```sh
npm run types
```

## Project Structure

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
