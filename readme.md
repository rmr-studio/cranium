<p align="center">
  <img src="https://cdn.riven.software/images/og-image.jpg" alt="Riven Dashboard" width="100%" />
</p>
<h1 align="center">Riven</h1>

<p align="center">
  Riven is an all-in-one operational intelligence platform, designed to help B2C SaaS and DTC Ecommerce Businesses manage and gain complete oversight of their customer lifecycle in order to gain immediate cross domain actionable insights from marketing and acquisition groups, product usage, support load all the way through to churn, and the underlying reasons behind it. Riven connects all of a businesses external SaaS tools and data sources to surface the patterns and insights that no singular tool could pick up alone, combined inside a unified, collaborative flexible entiy ecosystem and workspace. 

</p>

<p align="center">
  <a href="https://getriven.io"><strong>Website</strong></a> ·
  <a href="#getting-started"><strong>Getting Started</strong></a> ·
  <a href="#tech-stack"><strong>Tech Stack</strong></a> ·
  <a href="#architecture"><strong>Architecture</strong></a>
</p>

<br />

<p align="center">
  <img src="https://cdn.riven.software/images/dashboard-preview.png" alt="Riven Dashboard" width="100%" />
</p>


## What is Riven?

Riven pulls data from your CRM, project management, support, billing, and other SaaS tools into a single workspace. You define your own entity types, schemas and relationships, then query across all of it with AI.

Most platforms force you into their data model. Riven lets you define your own entity types, attributes and relationships so the schema fits your business.

The knowledge base has context across every connected source, so you can ask a question that touches your CRM and your support desk without switching tabs. Workflows are built visually as DAGs and run on Temporal.

## Activity

![Alt](https://repobeats.axiom.co/api/embed/d19c02d268f41f632d0d4e81648544b93ffd1ae8.svg "Repobeats analytics image")

## Tech stack

[Next.js](https://nextjs.org/) · [React 19](https://react.dev/) · [Tailwind CSS 4](https://tailwindcss.com/) · [shadcn/ui](https://ui.shadcn.com/) · [Framer Motion](https://www.framer.com/motion/) · [Zustand](https://zustand-demo.pmnd.rs/) · [TanStack Query](https://tanstack.com/query) · [Spring Boot 3](https://spring.io/projects/spring-boot) · [Kotlin](https://kotlinlang.org/) · [PostgreSQL](https://www.postgresql.org/) · [Flyway](https://flywaydb.org/) · [Temporal](https://temporal.io/) · [Supabase](https://supabase.com/) · [Docker](https://www.docker.com/) · [Turborepo](https://turbo.build/) · [pnpm](https://pnpm.io/)

## Architecture

```
riven/
├── apps/
│   ├── web/          # Marketing site (Next.js 16)
│   └── client/       # Dashboard app (Next.js 15)
├── core/             # Backend API (Spring Boot + Kotlin)
│   ├── src/          # Application source
│   └── db/           # Database schemas & migrations
├── packages/
│   ├── ui/           # Shared component library
│   ├── hooks/        # Shared React hooks
│   ├── utils/        # Shared utilities
│   └── tsconfig/     # Shared TypeScript config
└── docs/             # System design & architecture docs
```

### Backend domains

| Domain | What it does |
|--------|-------------|
| Entities | User-defined data models with dynamic schemas and relationships |
| Integrations | SaaS connectors and sync pipelines |
| Workflows | DAG orchestration on Temporal |
| Identity Resolution | Deduplication and matching across sources |
| Knowledge | AI reasoning, retrieval and enrichment |
| Catalog | System templates and core model definitions |
| Storage | Files on S3-compatible providers |
| Notifications | Alerts triggered by system events |

## Getting started

### Prerequisites

- [Node.js 22+](https://nodejs.org/)
- [pnpm 10+](https://pnpm.io/)
- [Java 21+](https://adoptium.net/) (backend)
- [Docker](https://www.docker.com/) (optional)
- A [Supabase](https://supabase.com/) project
- A [PostgreSQL](https://www.postgresql.org/) instance
- A [Temporal](https://temporal.io/) server

### 1. Clone the repository

```sh
git clone https://github.com/rmr-studio/riven.git
cd riven
```

### 2. Install dependencies

```sh
pnpm install
```

### 3. Set up environment variables

```sh
cp .env.example .env
```

Fill in the required values:

| Variable | Description |
|----------|-------------|
| `NEXT_PUBLIC_SUPABASE_URL` | Supabase project URL |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | Supabase anonymous key |
| `POSTGRES_DB_JDBC` | PostgreSQL JDBC connection string |
| `JWT_AUTH_URL` | Supabase Auth URL |
| `JWT_SECRET_KEY` | JWT signing secret |
| `SUPABASE_URL` | Supabase project URL (server-side) |
| `SUPABASE_KEY` | Supabase service key |
| `TEMPORAL_SERVER_ADDRESS` | Temporal server address |
| `NEXT_PUBLIC_API_URL` | Backend API base URL |

See `.env.example` for the full list.

### 4. Run locally

Start whichever services you need:

| Working on | Commands |
|------------|----------|
| Landing page | `pnpm --filter @riven/web dev` |
| Dashboard | `pnpm --filter @riven/client dev` |
| Backend API | `cd core && ./gradlew bootRun` |
| Full stack | All of the above |

Default ports: Web `:3000` · Client `:3001` · API `:8081`

### Docker deployment

Three Docker Compose profiles:

```sh
# Landing page only
docker compose --profile web up --build

# Dashboard + backend API
docker compose --profile platform up --build

# Everything
docker compose --profile all up --build
```

Override ports in `.env`:

```env
WEB_PORT=3000
CLIENT_PORT=3001
SERVER_PORT=8081
```

See [`apps/README.md`](apps/README.md) for full deployment docs, env vars, build args and teardown.
