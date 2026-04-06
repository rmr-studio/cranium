# Riven Core

Backend API server.

## Tech stack

- Kotlin 2.1 on Java 21
- Spring Boot 3.5
- PostgreSQL + Flyway migrations
- Temporal (workflow orchestration)
- Supabase JWT (OAuth2 Resource Server)
- S3-compatible storage (Cloudflare, AWS, MinIO)
- Springdoc OpenAPI (Swagger)
- Resilience4j circuit breaker
- OpenPDF
- ShedLock (distributed locking)

## Development

```sh
./gradlew bootRun
```

Runs on [http://localhost:8081](http://localhost:8081). Needs PostgreSQL, a Supabase project, and a Temporal server.

### API docs

OpenAPI spec at `/docs/v3/api-docs` when the server is running. Swagger UI at `/docs/swagger-ui.html`.

### Environment variables

Configure via `core/.env` or Spring Boot `application.properties`:

| Variable | Description |
|----------|-------------|
| `POSTGRES_DB_JDBC` | PostgreSQL JDBC connection string |
| `JWT_AUTH_URL` | Supabase Auth URL |
| `JWT_SECRET_KEY` | JWT signing secret |
| `SUPABASE_URL` | Supabase project URL |
| `SUPABASE_KEY` | Supabase service key |
| `ORIGIN_API_URL` | Allowed CORS origin |
| `TEMPORAL_SERVER_ADDRESS` | Temporal server address (default: `localhost:7233`) |

## Project structure

```
src/main/kotlin/riven/core/
  configuration/    # Spring beans, security, properties
  controller/       # REST endpoints (by domain)
  service/          # Business logic (by domain)
  repository/       # JPA repositories (by domain)
  entity/           # JPA entities (by domain)
  models/           # Domain models and DTOs
  enums/            # Shared enumerations
  lifecycle/        # Application lifecycle hooks
  exceptions/       # Custom exception types
  util/             # Utilities

db/
  schema/           # Flyway SQL migrations (versioned)

src/main/resources/
  manifests/        # System templates and catalog definitions
```

## Testing

```sh
./gradlew test
```

JUnit 5, Mockito, Testcontainers.
