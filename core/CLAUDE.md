## Project Overview

Multi-tenant workspace-scoped backend API for a configurable data platform with block-based content, user-defined entity schemas, and DAG-based workflow automation. Built with Spring Boot 3.5.3, Kotlin 2.1.21, Java 21, PostgreSQL (JSONB), Supabase (auth + storage), and Temporal (workflow execution).

**Domains:**

- **Block** — Content system: versioned block types with JSON schemas, block trees, layouts, and environment state.
- **Entity** — User-defined data model: entity types with dynamic schemas, typed attributes, relationships, and query engine.
- **Workflow** — DAG-based automation: definitions, versioned graphs, node execution via Temporal, queue-based dispatch.
- **Workspace** — Multi-tenancy: workspace CRUD, membership, role-based access, invitations.
- **User** — Auth integration: Supabase JWT, profile management.
- **Activity** — Audit trail: operation logging across all domains.

**External integrations:** Supabase (JWT auth, file storage), Temporal Server (workflow orchestration), PostgreSQL (RLS-enabled).

## Architecture Rules

- **Pattern:** Layered architecture — Controller → Service → Repository, with separate Entity (JPA) and Model (domain) layers.
- **Package structure:** `riven.core.{layer}.{domain}` — e.g. `controller.entity`, `service.entity`, `repository.entity`, `entity.entity`, `models.entity`. Follow this convention for all new code.
- **Single Gradle module** — no module boundaries. All domain separation is via packages.
- **Dependency direction:** Controllers depend on services. Services depend on repositories and other services. Repositories depend only on entities. Models are standalone. Cross-domain communication is through direct service injection.
- **Security enforcement:** Workspace access control via `@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")` and `@PostAuthorize` on **service methods**, not controllers. Controllers delegate to services and return `ResponseEntity`.
- **API versioning:** All routes prefixed with `/api/v1/`. Tagged with `@Tag(name = "domain")` for OpenAPI grouping.
- **Soft delete:** All major entities implement `SoftDeletable` (`deleted` flag + `deletedAt` timestamp). Never hard-delete unless explicitly required.
- **Entity base class rules — user vs system entities:**
  - **User-facing entities** (workspace-scoped, user-created data): Extend `AuditableEntity` and implement `SoftDeletable`. These are entities like blocks, entity types, workflows, etc. that users create and manage.
  - **System-managed entities** (catalog entries, manifest data, integration definitions): Do NOT extend `AuditableEntity` or implement `SoftDeletable`. These are seeded/managed by the system, not by users. They use their own lifecycle tracking (e.g. `stale` flags) rather than soft-delete.

## Coding Standards

- **Constructor injection only.** All Spring beans use constructor injection. KLogger is injected as a prototype-scoped bean via `LoggerConfig` — inject it as a constructor parameter, not a companion object.
- **Data classes** for JPA entities, domain models, DTOs, and request/response objects. User-facing entities extend `AuditableEntity` and implement `SoftDeletable` (see entity base class rules in Architecture Rules). System-managed entities do not.
- **Entity-to-model mapping** via `toModel()` methods defined on the JPA entity class. Do not use MapStruct or separate mapper classes.
- **Repository lookups:** Use `ServiceUtil.findOrThrow { repository.findById(id) }` for single-entity fetches. This throws `NotFoundException` on miss.
- **Preconditions:** Use `require()` and `requireNotNull()` for argument validation in services. These produce `IllegalArgumentException`, caught by the global `@ControllerAdvice`.
- **Error handling:** Throw domain-specific exceptions (`NotFoundException`, `ConflictException`, `SchemaValidationException`, `UniqueConstraintViolationException`, `WorkflowValidationException`). These are mapped to HTTP status codes by `ExceptionHandler` in `riven.core.exceptions`. Do not catch-and-swallow — let exceptions propagate to the advice.
- **Swagger annotations:** Add `@Operation`, `@ApiResponses` to all controller endpoints. Use `@Tag(name = "domain")` at class level.
- **UUID everywhere:** All primary keys are `UUID` with `@GeneratedValue(strategy = GenerationType.UUID)`. Use `UUID` for path variables and request fields.
- **JSONB columns:** Use Hypersistence `@Type(JsonBinaryType::class)` with `columnDefinition = "jsonb"` for dynamic payload columns.
- **Enums over string literals:** Always use enums for fixed sets of values (types, statuses, categories). Never use raw strings in `when` branches, model fields, or API contracts when the set of valid values is known and finite. Use `@JsonProperty` on enum values for JSON serialization. This ensures compile-time exhaustiveness checks, IDE discoverability, and catches invalid values at deserialization rather than deep in business logic.
- **Naming:** Services are `{Domain}Service`, repositories are `{Domain}Repository`, controllers are `{Domain}Controller`, entities are `{Domain}Entity`. Enums live in `enums.{domain}`.
- **Configuration properties:** Always access application configuration via `@ConfigurationProperties` data classes in `configuration/properties/` (or the relevant `configuration/{domain}/` package). Do not use `@Value` annotations to inject individual properties — use a typed configuration bean instead. This ensures type safety, IDE discoverability, and centralised defaults. Existing properties classes follow the pattern: `@ConfigurationProperties(prefix = "riven.{domain}")` on a data class with default values.

## Service and Function Design

### Service structure

- **One responsibility per service.** Split by sub-domain concern, not by CRUD. e.g. `EntityTypeService`, `EntityTypeRelationshipService`, `EntityTypeAttributeService` — not a single `EntityTypeService` doing everything.
- **Organise methods within a service** using section comment blocks to group by concern. Use `// ------ Section Name ------` style consistently. Preferred groupings: Public read operations, Public mutations, Private helpers, Batch operations.
- **Do not mix public API methods with internal helpers** without visual separation. A reader scanning the file should immediately understand which methods are entry points vs. internal.

### Function length and extraction

- **Target ~40 lines per function.** If a function exceeds ~50 lines, extract named steps into private methods. A function with inline `// PHASE 1:`, `// PHASE 2:` comments is a sign it should be broken into private methods named after those phases.
- **Name extracted methods after what they do, not when they run.** Do: `resolveIdMappings()`, `cascadeDeleteChildren()`. Don't: `phase1()`, `step2()`.
- **Multi-step mutation flows** (create + relate + log activity) should read as a sequence of high-level calls in the public method, with each step's implementation in a private method. The public method should tell the story; the private methods should do the work.

### Auth and userId retrieval

- **Retrieve `userId` as a `val` at the top of the function:** `val userId = authTokenService.getUserId()`. Do not wrap the entire function body in `authTokenService.getUserId().let { userId -> ... }` — it adds unnecessary nesting.

### Scope functions

- **Use `.let` for nullable chaining and single transforms.** Do not use `.let` to wrap entire function bodies as a substitute for variable assignment.
- **Use `.also` for side effects** (logging, activity tracking) that don't transform the value.
- **Use `.run` sparingly.** Avoid `repository.save(entity).run { return SomeResponse(...) }` — just assign and return. Nested scope functions (`.let { ... .run { ... .also { } } }`) are hard to follow; flatten into sequential `val` assignments.
- **Prefer plain `val` assignment + return** over scope function chains when readability is equal. Three `val` statements are easier to debug than a `.let { }.also { }.run { }` chain.

### Return values and error communication

- **Use exceptions for errors, not response-object flags.** Do not return response objects with `.error` or `.success` fields to communicate failures to the controller. Throw domain exceptions and let `@ControllerAdvice` handle HTTP status mapping. Reserve response-object-level "error states" only for domain-meaningful outcomes that are not failures (e.g. `conflict = true` in `SaveEnvironmentResponse` for version conflicts that the client can resolve).
- **Impact-check pattern:** For destructive operations that need user confirmation (e.g. `deleteEntityType`), the two-pass pattern is: first call returns impact analysis, second call with `impactConfirmed = true` executes. This is an established pattern — follow it for any new destructive operations that affect related entities.

### KDoc expectations

- **Always add KDoc on public service methods** — at minimum a one-line description. Include `@param` and `@return` only when the names aren't self-explanatory.
- **Add KDoc on private methods only when the logic is non-obvious** (recursive tree traversal, batch processing with edge cases, complex validation rules).
- **Do not add KDoc to controllers** — the Swagger `@Operation` annotation serves that purpose.

### Workspace verification

- **Use `@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")` on the service method** as the primary workspace access check. Do not duplicate this with inline `if (entity.workspaceId != workspaceId) throw AccessDeniedException(...)` checks in the same method — the annotation handles it. Inline checks are only needed when fetching an entity by non-workspace-scoped ID (e.g. `findById(id)` where the query doesn't filter by workspace).

### Activity logging

- **Log activity for all create, update, and delete mutations** using `activityService.logActivity(...)`. Include `activity`, `operation`, `userId`, `workspaceId`, `entityType`, `entityId`, and a `details` map with relevant context.
- **Do not skip activity logging** in new service methods that perform mutations — it's the audit trail.

## Testing Rules

- **Frameworks:** JUnit 5, Mockito via `mockito-kotlin` (prefer `whenever`/`verify` over `Mockito.when`/`Mockito.verify`).
- **Unit tests:** `@SpringBootTest` with targeted `classes = [...]` to load only the service under test + its security config. Mock all dependencies with `@MockitoBean`.
- **Security in tests:** Use the custom `@WithUserPersona` annotation (in `service.util`) to set up JWT security context with workspace roles.
- **Test data:** Use factory classes in `src/test/kotlin/riven/core/service/util/factory/` — extend these for new domains.
- **Integration tests:** Use `@ActiveProfiles("integration")` with Testcontainers PostgreSQL. Base classes provide shared container and `@DynamicPropertySource` wiring. Exclude security and Temporal auto-configuration.
- **Unit test profile:** `application-test.yml` uses H2 in PostgreSQL-compat mode with `ddl-auto: create-drop`.
- **Run tests:** `./gradlew test`
- **Always test:** Service methods with `@PreAuthorize` (verify access control), validation logic, entity-to-model mappings for new entities, and any custom JPQL/native queries.

## Database and Persistence

- **ORM:** Spring Data JPA with Hibernate. `ddl-auto: none` in production — schema is managed externally.
- **Schema management:** Raw SQL files in `db/schema/` organized by numbered directories (`00_extensions/` through `09_grants/`). No Flyway or Liquibase. **Schema files are declarative, not incremental** — there are no migration scripts. Each SQL file represents the current desired state of its object. When a table definition changes (e.g. renaming or replacing a column), edit the original table file directly instead of creating a separate migration file. Migration tooling (Flyway/Liquibase) does not exist yet, so standalone migration scripts will not be executed and must not be created.
- **Entity conventions:** Data classes annotated with `@Entity`, `@Table(name = "snake_case")`. Extend `AuditableEntity` for audit columns. Implement `SoftDeletable` for soft-delete support. Use `@Column(name = "snake_case")`.
- **Soft-delete filtering is automatic:** All entities extending `AuditableSoftDeletableEntity` have `@SQLRestriction("deleted = false")`, which makes Hibernate auto-append `AND deleted = false` to all JPQL, derived, and Criteria queries. You do **not** need to add `AND deleted = false` to repository queries manually — soft-deleted rows are invisible by default. Only native SQL queries bypass this restriction.
- **Schema changes:** Add new SQL files to the appropriate `db/schema/` subdirectory. Never modify an existing table SQL file without understanding the execution order documented in `db/schema/README.md`.
- **Transactions:** Mark service methods with `@Transactional` when they perform multiple writes. Repository-level operations are individually transactional by default.
- **JSONB:** Use Hypersistence `JsonBinaryType` for all JSON/JSONB columns. Validate JSON payloads against schemas via `SchemaService` before persisting.
- **Versioning patterns:** Block types are immutable (copy-on-write, new version = new row). Entity types are mutable (updated in-place).

## Build and Run

- **Build:** `./gradlew build`
- **Run locally:** `./gradlew bootRun` — requires env vars: `POSTGRES_DB_JDBC`, `JWT_SECRET_KEY`, `JWT_AUTH_URL`, `SUPABASE_URL`, `SUPABASE_KEY`, `SERVER_PORT`, `ORIGIN_API_URL`, `TEMPORAL_SERVER_ADDRESS`
- **Run tests:** `./gradlew test`
- **Docker build:** `docker build -t riven-core .` (multi-stage, skips tests)

## Always Perform List

- Discuss any new dependency before adding it — this is a carefully scoped stack.
- Use constructor injection exclusively. (No field-level `@Autowired`.)
- Keep controllers thin — they delegate to services and return `ResponseEntity`. Business logic and access control belong in the service layer.
- Inject `KLogger` via constructor using the prototype-scoped bean from `LoggerConfig`. (No companion-object loggers.)
- Use the soft-delete pattern (`SoftDeletable`) by default. Only hard-delete when explicitly told otherwise.
- Treat existing SQL files in `db/schema/` as append-only context — understand the full execution order and downstream effects before modifying any file.
- Let `@ControllerAdvice` handle all exception-to-response mapping. Controllers should throw or propagate, never catch-and-wrap into custom error responses.
- Use Kotlin nullable types (`T?`) instead of `Optional<T>`. The only exception is JPA repository return types, where `Optional` is acceptable.
- Add new beans to existing config classes in `configuration/`. Only create a new `@Configuration` class when there's a clear, distinct reason.
- Prefer JPQL `@Query` for all queries. Reserve native SQL for cases where JPQL is genuinely insufficient (e.g. `RETURNING *` clauses).
- Apply `@PreAuthorize` on every service method that accesses workspace-scoped data.
- Split services by domain responsibility — match the granularity of the existing service structure. No god services.

## Workflow Preferences

### 1. Planning

- Plan before building on any task involving 3+ files or architectural decisions.
- Verify before marking done — run `./gradlew test`, check compilation, demonstrate the change works.
- When fixing a bug, do not refactor unrelated code in the same change.
- If something goes sideways, STOP and re-plan immediately — don't keep pushing
- Explain what you changed and why at each step.
- If a task reveals architectural drift or inconsistency, log it in `docs/architecture-suggestions.md` per the Documentation Protocol.
- When uncertain about a pattern, check existing implementations first. Match the established pattern even if you'd prefer a different approach — consistency beats preference.

### 2. Sub Agent Strategy

- Use subagents liberally to keep main context window clean
- Offload research, exploration and parallel analysis to subagents
- For complex problems, it is always better to throw more compute at it via subagents
- One task per subagent for focused execution

### 3. Self-Improvement

- If you encounter ANY correction from the user. Point out the mistake made, and suggest to the user if they would like to add this to the relevant CLAUDE.md that this mistake should never be repeated again. Write it as a result to prevent yourself from making the same mistake again.

### 4. Verification Before Marking as complete

- Never mark a task complete without proving it works
- Run ./gradlew test and confirm compilation with ./gradlew build before marking any task done. This is the final verification gate and is never optional.
- During iteration, you may run isolated tests (e.g. a single service class or domain) for faster feedback. However, isolated tests do not replace the full verification gate above — always run the full suite before marking done.

### 5. Bug Fix Testing

- Every bug reported by the user that results in a code fix **must** have an accompanying regression test at the appropriate level (unit, integration, or security) that confirms the fixed behaviour.
- The test should include a KDoc comment with: a brief description of the original bug, a concise overview of the fix, and what the test is verifying.
- The test must fail without the fix and pass with it — it should directly exercise the code path that was broken.

## Known Inconsistencies

Flag these when working nearby. Do not fix opportunistically — only address if explicitly asked or if the current task requires it:

1. **`@Valid` usage is inconsistent** — `BlockEnvironmentController` uses `@Valid` on `@RequestBody`, `EntityController` does not. Standardise on always using `@Valid` for request bodies that have validation annotations.
2. **Inline error handling in `EntityController.deleteEntity`** — checks `response.error` and returns status codes manually instead of using the exception hierarchy. Should throw domain exceptions and let `@ControllerAdvice` handle it.
3. **`ObjectMapperConfig` has duplicate calls** — `.setDateFormat(dateFormat)` and `.registerModules(JavaTimeModule())` are each called twice.
4. **Test style mixing** — some tests use `Mockito.when()` static calls, others use `mockito-kotlin`'s `whenever()`. Prefer `mockito-kotlin` DSL consistently.
5. **`WorkspaceServiceTest` is entirely commented out** — represents broken/drifted tests that need to be restored.
6. **`WorkflowGraphService` uses module-level `KotlinLogging.logger {}`** instead of the injected `KLogger` bean that all other services use. Should be refactored to constructor-injected `KLogger`.
7. **Long functions with inline phase comments** — `BlockEnvironmentService.executeOperations` (~190 lines), `EntityRelationshipService.saveRelationships` (~130 lines), and `EntityTypeService.saveEntityTypeDefinition` (~130 lines) use `// PHASE 1:` / `// 1.` inline comments instead of extracting into named private methods.
8. **`userId` retrieval style inconsistent** — some services use `val userId = authTokenService.getUserId()` (WorkflowDefinitionService), others wrap the entire body in `authTokenService.getUserId().let { userId -> ... }` (BlockEnvironmentService, EntityTypeService). Standardise on `val` assignment.
9. **Duplicate workspace verification** — `WorkflowDefinitionService` and `WorkflowGraphService` have both `@PreAuthorize` annotations AND inline `if (entity.workspaceId != workspaceId)` checks. The inline check is only needed when the repository query doesn't filter by workspace.

## Documentation Protocol

- Architecture documentation vault: `../docs/system-design/`

- This vault is the source of truth for system architecture, domain boundaries, patterns, and design decisions. You do
  NOT write architectural documentation directly. Your role is to maintain structural scaffolding and log changes for human
  review.

### Repo Documentation Files

- `docs/architecture-changelog.md` — Append-only log of architectural changes made during development tasks.
- `docs/architecture-suggestions.md` — Suggestions for vault updates that require human review and authoring.

## Documentation Rules

### 1. Scaffold Only — Do Not Author

When your work results in a new domain, sub-domain, feature, or architecturally significant component that does not yet
have a corresponding location in the vault:

- Create the appropriate folder and/or stub note following the existing vault structure.
- Stub notes should contain only:

```markdown
# [Name]

<!-- Pending review — created [date] during [brief task reference] -->
```

- Do not write domain overviews, pattern descriptions, flow documentation, or any substantive architectural content.

### 2. Architecture Changelog

After every task that changes the system's structure, append an entry to `docs/architecture-changelog.md` in the
following format:

```markdown
## [YYYY-MM-DD] — [Short Task Description]

**Domains affected:** [list]
**What changed:**

- [concise bullet describing each structural change]

**New cross-domain dependencies:** [yes/no — if yes, describe: Source Domain → Target Domain via what mechanism]
**New components introduced:
** [list any new services, controllers, repositories, or other Spring components that are architecturally significant, with a one-line description of purpose]
```

Do not log trivial changes (bug fixes, styling, copy changes, minor refactors within a single service). Log changes that
affect domain responsibilities, introduce new components, alter data flow, change API contracts, or modify
infrastructure.

### 3. Architecture Suggestions

When your work introduces any of the following, append a suggestion to `docs/architecture-suggestions.md`:

- A new dependency between domains that is not reflected in the existing dependency map.
- A deviation from or extension of a pattern documented in `System Design/System Patterns/`.
- A change that alters the responsibilities or boundaries of an existing domain.
- A significant frontend change that affects how a feature maps to backend domains.
- Anything where you believe the existing documentation no longer accurately represents the system.

Format:

```markdown
## [YYYY-MM-DD] — [Suggestion Title]

**Trigger:** [what you did that prompted this suggestion]
**Affected vault notes:** [which specific notes or sections may need updating]
**Suggested update:** [concise description of what should be reviewed or changed]
```

### 4. Do Not Modify Existing Vault Content

- Never edit, overwrite, or append to existing vault notes.
- Never restructure or reorganise vault folders.
- Never delete anything in the vault.
- If you believe existing documentation is outdated or incorrect, log it as a suggestion in
  `docs/architecture-suggestions.md`.

### 5. When in Doubt

If you are unsure whether a change warrants a changelog entry or suggestion, include it. False positives are low cost.
Missed architectural drift is high cost.
