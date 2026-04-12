# Coding Conventions

**Analysis Date:** 2026-04-12

## Naming Patterns

**Files:**
- Services: `{Domain}Service.kt` — e.g. `ActivityService.kt`, `AuthTokenService.kt`, `BlockService.kt`
- Controllers: `{Domain}Controller.kt` — e.g. `BlockEnvironmentController.kt`, `EntityController.kt`
- Repositories: `{Domain}Repository.kt`
- Entities: `{Domain}Entity.kt` — e.g. `BlockEntity.kt`, `ActivityLogEntity.kt`
- Enums: Located in `enums.{domain}` package — e.g. `riven.core.enums.activity`

**Functions:**
- camelCase throughout — `logActivity()`, `getUserId()`, `createBlock()`
- Private helpers follow same convention — `validatePayload()`, `calculateMetadata()`
- Extracted multi-step methods named after their purpose, not their order — `resolveIdMappings()`, `cascadeDeleteChildren()` (not `phase1()`, `step2()`)

**Variables:**
- camelCase for all: `userId`, `workspaceId`, `blockId`, `parentId`
- val over var where immutable
- Nullable types use `?` suffix — `userId: UUID? = null`, `parentId: UUID? = null`
- No Optional<T> in service code — use Kotlin nullable types exclusively

**Types:**
- Sealed classes and data classes for domain models
- Enums for fixed value sets with `@JsonProperty` for JSON serialization
- Constructor parameters match field names when possible for brevity

## Code Style

**Formatting:**
- Kotlin conventions with no explicit formatter tool detected in build
- 4-space indentation (IntelliJ default)
- Max line length ~120 characters (observed in code)
- Imports grouped: Java/Kotlin stdlib, Spring, third-party, then riven.core

**Linting:**
- No ktlint or explicit linting tool in build.gradle.kts
- Code follows idiomatic Kotlin patterns (data classes, scope functions, extension functions)

## Import Organization

**Order:**
1. Java standard library (`java.util.*`, `java.time.*`)
2. Kotlin and Spring Core (`org.springframework.*`)
3. Third-party libraries (`com.fasterxml.*`, `io.github.*`, etc.)
4. Project-specific imports (`riven.core.*`)

**Path Aliases:**
- No import aliases used; all imports are fully qualified
- Package structure: `riven.core.{layer}.{domain}`

**Example from `BlockService.kt`:**
```kotlin
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.block.BlockChildEntity
import riven.core.entity.block.BlockEntity
import riven.core.enums.core.ApplicationEntityType
import riven.core.exceptions.SchemaValidationException
import riven.core.models.block.Block
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import java.util.*
```

## Error Handling

**Patterns:**
- Throw domain-specific exceptions from services; let `@ControllerAdvice` handle mapping to HTTP status codes
- Never catch-and-swallow exceptions in services — let them propagate
- Use `require()` and `requireNotNull()` for argument validation in services

**Exception Hierarchy:**
- `NotFoundException` — 404 NOT_FOUND
- `ConflictException` — 409 CONFLICT
- `InvalidRelationshipException` — 400 BAD_REQUEST
- `SchemaValidationException` — 400 BAD_REQUEST
- `UniqueConstraintViolationException` — Caught by ExceptionHandler
- `WorkflowValidationException` — 400 BAD_REQUEST
- `WorkflowExecutionException` — 500 INTERNAL_SERVER_ERROR
- `AccessDeniedException` — 403 FORBIDDEN
- `IllegalArgumentException` — 400 BAD_REQUEST (from `require()` statements)

**Example from `BlockService.kt` (lines 42-50):**
```kotlin
require(!type.deleted) { "BlockType '${type.deleted}' is deleted" }

val errs = schemaService.validate(type.schema, payload.data, type.strictness)
if (type.strictness == ValidationScope.STRICT && errs.isNotEmpty()) {
    throw SchemaValidationException(errs)
}
```

## Logging

**Framework:** `io.github.oshai.kotlinlogging.KLogger` injected as constructor parameter (not `companion object`)

**Patterns:**
- Info level for business operations: `logger.info { "Activity logged: $activity by User: $userId" }`
- Warn level for potential issues: `logger.warn { "No JWT found in the security context" }`
- Error level for failures: `logger.error { it }` (in ExceptionHandler)
- Use string templates (`$variable`) in lazy log blocks

**Example from `ActivityService.kt` (lines 43-45):**
```kotlin
logger.info {
    "Activity logged: $activity by User: $userId"
}
```

## Comments

**When to Comment:**
- KDoc on all public service methods with at least a one-line description
- KDoc on private methods only when logic is non-obvious (recursive traversal, complex batch operations)
- Do NOT add KDoc to controllers — Swagger `@Operation` annotation serves that purpose
- Inline comments for non-obvious business logic or validation rules

**KDoc/TSDoc:**
Standard Kotlin KDoc format with `@param` and `@return` only when names aren't self-explanatory.

**Example from `AuthTokenService.kt` (lines 13-16):**
```kotlin
/**
 * Retrieves the JWT from the security context.
 */
private fun getJwt(): Jwt {
```

**Example from `BlockChildrenService.kt` (lines 8-14):**
```kotlin
/**
 * Manages *owned* parent→child block edges with slots & order.
 *
 * - Enforces: same-workspace, allowed types (via parent.type.nesting),
 *   max children constraint, contiguous orderIndex per slot.
 * - Uses BlockChildEntity as the single source of truth for hierarchy.
 * - Constraint: child_id is globally unique - a child can only belong to ONE parent.
 * - Optionally mirrors parentId on child BlockEntity for denormalization/query performance.
 */
```

## Function Design

**Size:** Target ~40 lines per function. If exceeding ~50 lines, extract named steps into private methods.

**Parameters:**
- Constructor injection of all dependencies — no field-level `@Autowired`
- Use default parameters for optional values
- Destructure request objects where helpful

**Example from `BlockService.kt` (line 41):**
```kotlin
val (type, payload, name, parentId, index) = request
```

**Return Values:**
- Use exceptions for errors, never response-object flags
- Reserve response-object fields only for domain-meaningful outcomes that aren't failures (e.g. `SaveEnvironmentResponse.conflict` for version conflicts client can resolve)

**Multi-step mutations:**
Public method reads as high-level sequence, private methods implement each step.

**Example from `BlockService.kt` (lines 69-113):**
```kotlin
return BlockEntity(/*...*/).run {
    blockRepository.save(this)
}.also {
    requireNotNull(it.id) { "Block '${it.id}' not found" }
    activityService.log(/*...*/)
    parentId?.let { parentId ->
        getBlock(parentId).also { parent ->
            // parent validation and addChild
        }
    }
}
```

## Module Design

**Exports:**
- Services exported as `@Service` beans
- Controllers exported as `@RestController` beans
- Repositories exported via Spring Data JPA
- Factories (test utilities) not Spring-managed — instantiated directly

**Barrel Files:**
- Not used in core services
- Import specific classes directly

## Service and Function Organization

**Service Structure:**
- One responsibility per service — split by sub-domain, not CRUD
- Organize methods within service using section comment blocks: `// ------ Section Name ------`
- Group methods: Public read operations → Public mutations → Private helpers → Batch operations

**Example section organization from `BlockChildrenService.kt`:**
```kotlin
/* =========================
 * Public read operations
 * ========================= */
fun listChildren(parentId: UUID): List<BlockChildEntity> = /*...*/

/* =========================
 * Helpers / Validation
 * ========================= */
private fun load(id: UUID): BlockEntity = /*...*/

/* =========================
 * Mutations
 * ========================= */
@Transactional
fun addChild(/*...*/): Unit = /*...*/
```

## Scope Functions

**Usage patterns:**
- `.let` for nullable chaining and single transforms: `jwt.claims["sub"].let { if (it == null) ... }`
- `.also` for side effects that don't transform: `repository.save(this).also { activityService.log(...) }`
- `.run` sparingly — prefer plain `val` assignment + return for clarity
- Avoid nested scope chains — flatten into sequential `val` statements when possible

**Examples from `ActivityService.kt`:**
```kotlin
// Using .run and .also for side effects
ActivityLogEntity(/*...*/).run {
    repository.save(this)
    logger.info { "Activity logged: $activity" }
    return this.toModel()
}

// Using .also for activity logging
blockRepository.save(this).also {
    activityService.log(/*...*/)
}
```

## Auth and userId Retrieval

**Pattern:**
Retrieve `userId` as a val at the top of any function that needs it:

```kotlin
val userId = authTokenService.getUserId()
```

Do NOT wrap entire function bodies in `.let { userId -> ... }` — it adds unnecessary nesting.

**Example from `ActivityService.kt` (lines 20-28):**
```kotlin
fun logActivity(
    activity: Activity,
    operation: OperationType,
    userId: UUID,  // Passed as parameter, used directly
    workspaceId: UUID,
    /*...*/
): ActivityLog {
```

## Configuration

**Pattern:**
Use `@ConfigurationProperties` data classes in `configuration/properties/` with `@ConfigurationProperties(prefix = "riven.{domain}")`.

Do NOT use `@Value` for individual properties — use typed configuration beans instead.

**Examples:**
- `ApplicationConfigurationProperties`
- `RateLimitConfigurationProperties`
- `EnrichmentConfigurationProperties`

## Security & Access Control

**Pattern:**
Use `@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")` on service methods as the primary workspace access check.

Do NOT duplicate with inline `if (entity.workspaceId != workspaceId)` checks in the same method — the annotation handles it.

**Example from `BlockService.kt` (line 38):**
```kotlin
@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
@Transactional
fun createBlock(workspaceId: UUID, request: CreateBlockRequest): BlockEntity {
```

## Transactions

**Pattern:**
Mark service methods with `@Transactional` when they perform multiple writes.

**Example from `BlockService.kt` (line 39):**
```kotlin
@Transactional
fun createBlock(workspaceId: UUID, request: CreateBlockRequest): BlockEntity {
```

## Activity Logging

**Pattern:**
Log activity for all create, update, and delete mutations using `activityService.log()`.

**Example from `BlockService.kt` (lines 79-88):**
```kotlin
activityService.log(
    activity = riven.core.enums.activity.Activity.BLOCK,
    operation = OperationType.CREATE,
    userId = authTokenService.getUserId(),
    workspaceId = workspaceId,
    entityType = ApplicationEntityType.BLOCK,
    entityId = it.id,
    "blockId" to it.id.toString(),
    "typeKey" to type.key
)
```

## UUID Handling

**Pattern:**
- All primary keys are `UUID` with `@GeneratedValue(strategy = GenerationType.UUID)`
- Never manually generate UUIDs for JPA-managed entities — set `id = null` and let JPA generate
- Use `requireNotNull(id) { "descriptive message" }` instead of `id!!` for non-null assertions

**Example:**
```kotlin
val entity = BlockEntity(
    id = null,  // Let JPA generate
    workspaceId = workspaceId,
    /*...*/
)
```

---

*Convention analysis: 2026-04-12*
