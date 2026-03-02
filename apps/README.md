# Deployment Guide

This monorepo has three deployable units managed via Docker Compose profiles.

## Prerequisites

- Docker and Docker Compose v2+
- Copy `.env.example` to `.env` at the repo root and fill in values

```sh
cp .env.example .env
```

## Local Development

No Docker needed — run each service directly in its own terminal.

### Web (landing page)

```sh
pnpm --filter @riven/web dev
```

Runs on `http://localhost:3000`. Requires a `.env.local` in `apps/web/` with the relevant `NEXT_PUBLIC_*` vars.

### Client (dashboard)

```sh
pnpm --filter @riven/client dev
```

Runs on `http://localhost:3001` (or whichever port Next.js assigns). Requires the backend running and a `.env.local` in `apps/client/` with `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY`, `NEXT_PUBLIC_API_URL`, etc.

### Core (backend API)

```sh
cd core && ./gradlew bootRun
```

Runs on `http://localhost:8081`. Requires a running PostgreSQL instance and Supabase project. Configure via `core/.env` or Spring Boot `application.properties`.

### What to run

You only need to start the services you're working on:

| Working on | Start |
|------------|-------|
| Landing page only | web |
| Dashboard features | core + client |
| Full stack | web + core + client |

---

## Docker Deployment

### Profiles

| Profile | Services | Use case |
|---------|----------|----------|
| `web` | web (`:3000`) | Standalone landing page / waitlist |
| `platform` | core (`:8081`) + client (`:3001`) | Dashboard + backend API |
| `all` | web + core + client | Full stack |

## Commands

### Web only

```sh
docker compose --profile web up --build
```

Starts the landing page on port `3000` (configurable via `WEB_PORT`).

### Platform (client + backend)

```sh
docker compose --profile platform up --build
```

Starts the Spring Boot API on port `8081` and the dashboard on port `3001` (configurable via `SERVER_PORT` and `CLIENT_PORT`).

### Everything

```sh
docker compose --profile all up --build
```

Starts all three services on their respective ports.

## Port Configuration

Override default ports in `.env`:

```env
WEB_PORT=3000
CLIENT_PORT=3001
SERVER_PORT=8081
```

## Environment Variables

### Required for all profiles

| Variable | Description |
|----------|-------------|
| `NEXT_PUBLIC_SUPABASE_URL` | Supabase project URL |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | Supabase anonymous key |

### Backend (platform, all)

| Variable | Description |
|----------|-------------|
| `POSTGRES_DB_JDBC` | PostgreSQL JDBC connection string |
| `JWT_AUTH_URL` | Supabase Auth URL |
| `JWT_SECRET_KEY` | JWT signing secret |
| `SUPABASE_URL` | Supabase project URL (server-side) |
| `SUPABASE_KEY` | Supabase service key |
| `ORIGIN_API_URL` | Allowed CORS origin |
| `TEMPORAL_SERVER_ADDRESS` | Temporal server address |

### Client app (platform, all)

| Variable | Description |
|----------|-------------|
| `NEXT_PUBLIC_API_URL` | Backend API base URL |
| `NEXT_PUBLIC_HOSTED_URL` | Client app public URL (OAuth redirects) |
| `NEXT_PUBLIC_AUTH_PROVIDER` | Auth provider (`supabase`) |

### Web app (web, all)

| Variable | Description |
|----------|-------------|
| `NEXT_PUBLIC_SITE_URL` | Landing page public URL |

### Cross-app auth (all)

| Variable | Description |
|----------|-------------|
| `NEXT_PUBLIC_AUTH_ENABLED` | Enable shared auth on landing page |
| `NEXT_PUBLIC_COOKIE_DOMAIN` | Shared cookie domain |
| `NEXT_PUBLIC_CLIENT_URL` | Client URL for login redirects from web |

## Build Args

`NEXT_PUBLIC_*` variables are baked into the Next.js static bundle at build time via Docker build args. They are defined in `docker-compose.yml` under each service's `build.args` and forwarded from your `.env` file.

Changing any `NEXT_PUBLIC_*` value requires a rebuild (`--build` flag).

## Detached Mode

Add `-d` to run in the background:

```sh
docker compose --profile all up --build -d
```

View logs:

```sh
docker compose --profile all logs -f
```

## Teardown

```sh
docker compose --profile all down
```

Add `-v` to also remove volumes.
