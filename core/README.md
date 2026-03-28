# Riven Core

Backend API server for the Riven platform.

## Tech Stack

- **Language:** Kotlin 2.1 on Java 21
- **Framework:** Spring Boot 3.5
- **Database:** PostgreSQL + Flyway migrations
- **Orchestration:** Temporal (workflow engine)
- **Auth:** Supabase JWT (OAuth2 Resource Server)
- **Storage:** S3-compatible (Cloudflare, AWS, MinIO)
- **API Docs:** Springdoc OpenAPI (Swagger)
- **Resilience:** Resilience4j circuit breaker
- **PDF:** OpenPDF
- **Scheduling:** ShedLock (distributed locking)

## Development

```sh
./gradlew bootRun
```

Runs on [http://localhost:8081](http://localhost:8081). Requires a running PostgreSQL instance, Supabase project and Temporal server.

### API Documentation

OpenAPI spec is served at `/docs/v3/api-docs` when the server is running. Swagger UI is available at `/docs/swagger-ui.html`.

### Environment Variables

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

## Project Structure

```
src/main/kotlin/riven/core/
  configuration/    # Spring beans, security, properties
  controller/       # REST API endpoints (by domain)
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

Uses JUnit 5, Mockito and Testcontainers.
