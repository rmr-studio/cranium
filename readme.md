<p align="center">
  <img src="apps/web/public/Logo.svg" width="80" alt="Riven Logo" />
</p>

<h1 align="center">Riven</h1>

<p align="center">
  AI-powered data platform enabling cross-domain intelligence
</p>

<p align="center">
  Unify and integrate your SaaS tools into one centralised platform, uncovering patterns, relationships and insights between data otherwise hidden away in siloed databases and dashboards.
</p>

<p align="center">
  <a href="https://getriven.io"><strong>Website</strong></a> ·
  <a href="#getting-started"><strong>Getting Started</strong></a> ·
  <a href="#tech-stack"><strong>Tech Stack</strong></a> ·
  <a href="#architecture"><strong>Architecture</strong></a>
</p>

<br />

<p align="center">
  <img src="https://cdn.riven.software/images/og-image.png" alt="Riven Dashboard" width="100%" />
</p>

## About

Riven is a unified data platform that connects your SaaS tools — CRM, project management, support, billing — into a single, intelligent workspace. Instead of switching between dashboards, Riven pulls everything together and uses AI to surface cross-domain patterns, relationships and insights that would otherwise stay buried.

**One Unified Data Ecosystem** — Data models and relationships that adapt to how your business works, not the other way around. Define your own entity types, schemas and relationships.

**Cross-Domain Intelligence** — AI-powered knowledge base that reasons across your entire business, not just one corner of it. Ask questions that span multiple data sources and get answers with full context.

**Workflow Automation** — Visual DAG-based workflow builder powered by Temporal for reliable, observable orchestration across all your connected tools.

## Tech Stack

- [Next.js](https://nextjs.org/) – Frontend framework
- [React 19](https://react.dev/) – UI library
- [Tailwind CSS 4](https://tailwindcss.com/) – Styling
- [shadcn/ui](https://ui.shadcn.com/) – Component library
- [Framer Motion](https://www.framer.com/motion/) – Animations
- [Zustand](https://zustand-demo.pmnd.rs/) – State management
- [TanStack Query](https://tanstack.com/query) – Server state
- [Spring Boot 3](https://spring.io/projects/spring-boot) – Backend framework
- [Kotlin](https://kotlinlang.org/) – Backend language
- [PostgreSQL](https://www.postgresql.org/) – Database
- [Flyway](https://flywaydb.org/) – Database migrations
- [Temporal](https://temporal.io/) – Workflow orchestration
- [Supabase](https://supabase.com/) – Authentication & storage
- [Docker](https://www.docker.com/) – Containerisation
- [Turborepo](https://turbo.build/) – Monorepo build system
- [pnpm](https://pnpm.io/) – Package manager

## Architecture

```
riven/
├── apps/
│   ├── web/          # Marketing & landing page (Next.js 16)
│   └── client/       # Dashboard application (Next.js 15)
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

### Backend Domains

| Domain | Description |
|--------|-------------|
| **Entities** | User-defined data models with dynamic schemas, attributes and relationships |
| **Integrations** | SaaS connectors with sync pipelines for pulling external data |
| **Workflows** | DAG-based orchestration engine built on Temporal |
| **Identity Resolution** | Deduplication and entity matching across data sources |
| **Knowledge** | AI reasoning engine with retrieval and enrichment |
| **Catalog** | System templates, manifests and core model definitions |
| **Storage** | File management with S3-compatible providers |
| **Notifications** | Event-driven alerting system |

## Getting Started

### Prerequisites

- [Node.js 22+](https://nodejs.org/)
- [pnpm 10+](https://pnpm.io/)
- [Java 21+](https://adoptium.net/) (for the backend)
- [Docker](https://www.docker.com/) (optional, for containerised deployment)
- A [Supabase](https://supabase.com/) project
- A [PostgreSQL](https://www.postgresql.org/) instance
- A [Temporal](https://temporal.io/) server (for workflow orchestration)

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

See `.env.example` for the full list of configuration options.

### 4. Run locally

You only need to start the services you're working on:

| Working on | Commands |
|------------|----------|
| Landing page | `pnpm --filter @riven/web dev` |
| Dashboard | `pnpm --filter @riven/client dev` |
| Backend API | `cd core && ./gradlew bootRun` |
| Full stack | All of the above |

Default ports: **Web** `:3000` · **Client** `:3001` · **API** `:8081`

### Docker Deployment

Three Docker Compose profiles are available:

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

See [`apps/README.md`](apps/README.md) for full deployment documentation including environment variables, build args and teardown instructions.
