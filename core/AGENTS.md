# Repository Guidelines

## Project Structure & Module Organisation

This service is a single Spring Boot module: domain and application logic live in `src/main/kotlin/cranium`, with packages
mirroring feature boundaries (auth, storage, pdf, etc.). Shared config and resources sit in `src/main/resources`;
database bootstrap SQL lives in `src/main/resources/db/schema.sql` and feeds Postgres on first run. Place unit fixtures
under `src/test/kotlin/cranium`, mirroring the production package you cover; Gradle’s default `build/` output and wrapper
files should remain untouched by hand.

## Build, Test, and Development Commands

- `./gradlew clean build` compiles Kotlin, runs unit tests, and assembles the Spring Boot jar.
- `./gradlew test` runs the JUnit 5 suite in isolation; use it for quick validation before pushing.
- `./gradlew bootRun` launches the API with DevTools reloading; ensure a Postgres instance is reachable before invoking.

## Coding Style & Naming Conventions

We follow the standard JetBrains Kotlin style: 4-space indentation, trailing commas in multiline argument lists, and
expression-bodied functions when they improve readability. Classes and Spring components use PascalCase (
`InvoiceService`), functions and properties use camelCase, while constants remain in SCREAMING_SNAKE_CASE inside a
`companion object`. Keep package names lowercase and feature-oriented; prefer constructor injection and Kotlin nullable
semantics over optional wrappers.

## Testing Guidelines

Tests live beside their targets in `src/test/kotlin/cranium` and should be suffixed with `Test` (for example,
`InvoiceControllerTest`). Leverage Spring Boot’s test slices and Mockito for mocking external integrations; use H2 to
exercise JPA boundaries without external dependencies. Strive for high coverage on service and controller layers, and
add regression tests for every reported defect before the fix lands.

## Commit & Pull Request Guidelines

Commit subjects follow the repo’s imperative, concise style (`Add invoice PDF renderer`, `Fix user claims mapping`).
Group related file changes into a single commit to keep history reviewable, and include body text when context is not
obvious. Pull requests should describe the change, link the relevant issue or ticket, call out breaking migrations or
new environment variables, and attach screenshots for UI-facing modifications.

## Security & Configuration Notes

Secrets must not be committed; rely on Spring Boot’s configuration properties and keep overrides in your local
`application-*.yml`. Verify that any new endpoints enforce Spring Security roles consistently, and prefer environment
variables rather than hard-coded credentials when integrating with Supabase or external storage.
