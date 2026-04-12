# Testing Patterns

**Analysis Date:** 2026-04-12

## Test Framework

**Runner:**
- JUnit 5
- Config: No explicit `junit.properties` — uses Spring Boot defaults

**Assertion Library:**
- `org.junit.jupiter.api.Assertions.*` for assertions
- `kotlin.test` for inline assertions: `assertEquals()`, `assertNotNull()`, `assertTrue()`

**Mocking:**
- Mockito via `mockito-kotlin` (prefer `whenever`/`verify` over `Mockito.when`/`Mockito.verify`)
- Spring's `@MockitoBean` for Spring-managed dependencies

**Run Commands:**
```bash
./gradlew test              # Run all tests
./gradlew test --tests "*ServiceTest*"  # Run service tests only
```

## Test File Organization

**Location:**
- Mirror main source structure: `src/test/kotlin/riven/core/{layer}/{domain}/{Name}Test.kt`
- Factories in: `src/test/kotlin/riven/core/service/util/factory/`
- Test utilities in: `src/test/kotlin/riven/core/service/util/`

**Naming:**
- Test classes: `{Service}Test.kt` — e.g. `EntityServiceTest.kt`, `RateLimitFilterTest.kt`
- Test methods: describe the scenario — `fun testDisabledFlagPassesRequestThrough()` or `fun 'disabled flag passes request through without bucket interaction'()`
- Nested test classes for grouping related scenarios — use `@Nested inner class GroupName { ... }`

**Structure:**
```
src/test/kotlin/riven/core/
├── configuration/
│   ├── util/
│   │   └── CaseInsensitiveTypeIdResolverTest.kt
│   └── websocket/
│       └── WebSocketSecurityInterceptorTest.kt
├── filter/
│   ├── analytics/
│   │   └── PostHogCaptureFilterTest.kt
│   ├── integration/
│   │   └── NangoWebhookHmacFilterTest.kt
│   └── ratelimit/
│       ├── RateLimitFilterTest.kt
│       └── RateLimitFilterIntegrationTest.kt
├── service/
│   ├── entity/
│   │   └── EntityServiceTest.kt
│   ├── util/
│   │   ├── WithUserPersona.kt
│   │   ├── SecurityTestConfig.kt
│   │   └── factory/
│   │       ├── EntityFactory.kt
│   │       ├── WorkspaceFactory.kt
│   │       └── block/BlockFactory.kt
│   └── CoreApplicationTests.kt
```

## Test Structure

**Suite Organization:**
```kotlin
@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        EntityServiceTest.TestConfig::class,
        EntityService::class,
    ]
)
@RecordApplicationEvents
@WithUserPersona(
    userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
    email = "test@test.com",
    displayName = "Test User",
    roles = [
        WorkspaceRole(
            workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
            role = WorkspaceRoles.OWNER
        )
    ]
)
class EntityServiceTest : BaseServiceTest() {

    @Configuration
    class TestConfig

    @MockitoBean private lateinit var entityTypeClassificationService: EntityTypeClassificationService
    @MockitoBean private lateinit var entityRepository: EntityRepository

    @Autowired
    private lateinit var service: EntityService

    @BeforeEach
    fun setup() {
        // Test setup
    }

    @Nested
    inner class CreateEntityTests {
        @Test
        fun `creates entity with valid attributes`() {
            // Test implementation
        }
    }
}
```

**Patterns:**
- Use `@SpringBootTest` with targeted `classes = [...]` to load only the service under test + security config
- Mock all external dependencies with `@MockitoBean`
- Use `@WithUserPersona` annotation for JWT security context (mandatory for any test accessing JWT-authenticated code)
- Apply `@WithUserPersona` at **class level** for test-wide persona, **method level** to override for specific tests
- **CRITICAL:** JUnit 5 `@Nested` inner classes do **not** inherit the outer class's `@WithUserPersona` — reapply on nested class if tests call `authTokenService.getUserId()`

## Mocking

**Framework:** Mockito via `mockito-kotlin`

**Patterns:**
```kotlin
private lateinit var filter: RateLimitFilter
private lateinit var bucketCache: Cache<String, Bucket>
private lateinit var exceededCounter: Counter

@BeforeEach
fun setup() {
    properties = RateLimitConfigurationProperties(
        enabled = true,
        authenticatedRpm = 5,
        anonymousRpm = 3,
        cacheMaxSize = 1000,
        cacheExpireMinutes = 10
    )
    bucketCache = Caffeine.newBuilder()
        .maximumSize(properties.cacheMaxSize)
        .expireAfterAccess(properties.cacheExpireMinutes, TimeUnit.MINUTES)
        .build()
    exceededCounter = mock()
    filterErrorCounter = mock()
    kLogger = mock()
    filterChain = mock()

    filter = RateLimitFilter(
        properties = properties,
        bucketCache = bucketCache,
        objectMapper = objectMapper,
        exceededCounter = exceededCounter,
        filterErrorCounter = filterErrorCounter,
        kLogger = kLogger
    )
}
```

**Verification:**
```kotlin
verify(filterChain).doFilter(request, response)
verify(exceededCounter).increment()
verify(exceededCounter, never()).increment()
```

**Stubbing:**
```kotlin
whenever(throwingCache.get(any(), any())).thenThrow(RuntimeException("cache exploded"))
whenever(filterChain.doFilter(any(), any())).thenThrow(downstreamException)
```

**What to Mock:**
- External service dependencies (repositories, services from other domains)
- Third-party integrations (HTTP clients, databases)
- Cross-cutting concerns (logging, metrics)

**What NOT to Mock:**
- Core business logic under test
- Value objects and domain models
- Utility functions
- Test helper classes

## Fixtures and Factories

**Test Data:**
Use factory classes in `src/test/kotlin/riven/core/service/util/factory/` — extend these for new domains.

**Example from `BlockFactory.kt` (lines 31-40):**
```kotlin
object BlockFactory {

    fun createComponent(): BlockComponentNode = BlockComponentNode(
        id = "component_1",
        type = ComponentType.CONTACT_CARD,
        props = mapOf(
            "title" to "Contact Card",
            "showEmail" to true
        )
    )

    fun createType(
        orgId: UUID,
        key: String = "contact_card",
        version: Int = 1,
        /*...*/
    ): BlockTypeEntity = BlockTypeEntity(
        id = UUID.randomUUID(),
        key = key,
        displayName = "Contact",
        /*...*/
    )
}
```

**Location:**
- `src/test/kotlin/riven/core/service/util/factory/`
- Organized by domain: `factory/block/`, `factory/entity/`, `factory/workflow/`

**Mandatory Rule:**
NEVER construct JPA entities inline in tests — always use or create a factory method. If a factory doesn't exist for the entity, add one. This applies to all entity construction in test files, including mock return values and argument captors.

## Coverage

**Requirements:** Not explicitly enforced in test config

**View Coverage:**
```bash
./gradlew test --info 2>&1 | grep -i coverage
```

## Test Types

**Unit Tests:**
- Scope: Single service or component
- Approach: Mock all external dependencies
- Config: `@SpringBootTest` with minimal class list
- Example: `EntityServiceTest`, `RateLimitFilterTest`

**Integration Tests:**
- Scope: Service + Repository + Database
- Approach: Use real H2 database (PostgreSQL-compatible mode)
- Config: `@SpringBootTest` with `@ActiveProfiles("integration")` + Testcontainers
- Profile: `application-integration.yml` (if exists) or `application-test.yml`

**Filter/Interceptor Tests:**
- Scope: Single filter or interceptor
- Approach: Mock request/response objects, test filter logic in isolation
- Example: `RateLimitFilterTest`, `PostHogCaptureFilterTest`

**E2E Tests:**
- Not implemented — no dedicated E2E test suite detected

## Test Database Configuration

**Profile:** `application-test.yml` — loads automatically when running tests

**Configuration:**
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH
    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    hibernate:
      ddl-auto: create-drop  # Auto-create schema from entities
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
        format_sql: true
    show-sql: false
```

**Key Points:**
- H2 in-memory database in PostgreSQL-compatibility mode
- Schema auto-generated from JPA entity annotations (`ddl-auto: create-drop`)
- Flyway disabled for tests — uses Hibernate schema generation instead
- Each test run gets a fresh schema (dropped after)

## Common Patterns

**Async Testing:**
```kotlin
@Test
fun `processes async event correctly`() {
    runBlocking {
        // Test coroutine-based code
    }
}
```

**Error Testing:**
```kotlin
@Test
fun `unknown type id produces InvalidTypeIdException`() {
    val json = """{"type":"NONEXISTENT","conditions":[]}"""
    val ex = assertThrows<InvalidTypeIdException> {
        mapper.readValue<QueryFilter>(json)
    }
    assert(ex.message?.contains("NONEXISTENT") == true) {
        "Error should mention the invalid type id, got: ${ex.message}"
    }
}
```

**JWT Testing with @WithUserPersona:**
```kotlin
@WithUserPersona(
    userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
    email = "test@test.com",
    displayName = "Test User",
    roles = [
        WorkspaceRole(
            workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
            role = WorkspaceRoles.OWNER
        )
    ]
)
@Test
fun `retrieves user id from JWT`() {
    val userId = authTokenService.getUserId()
    assertEquals(UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef0123456789"), userId)
}
```

**Nested Test Organization:**
```kotlin
@Nested
inner class AuthenticatedRequests {
    @Test
    fun `authenticated request under limit passes through`() {
        setSecurityContext()
        val request = createRequest()
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)

        verify(filterChain).doFilter(request, response)
        assertEquals("5", response.getHeader("X-RateLimit-Limit"))
    }

    @Test
    fun `authenticated request over limit returns 429`() {
        setSecurityContext()
        repeat(5) {
            filter.doFilter(createRequest(), MockHttpServletResponse(), filterChain)
        }

        val response = MockHttpServletResponse()
        filter.doFilter(createRequest(), response, filterChain)

        assertEquals(429, response.status)
        verify(exceededCounter).increment()
    }
}
```

**Regression Testing:**
When fixing a bug, include a KDoc comment with bug description, fix overview, and what the test verifies:

```kotlin
/**
 * Regression test: downstream filter chain exceptions must propagate normally.
 *
 * Previously the try/catch wrapped both the rate-limit logic AND filterChain.doFilter,
 * causing downstream handler exceptions to be swallowed and logged as rate-limit errors.
 * After the fix, only rate-limit logic is inside the try/catch.
 */
@Test
fun `downstream filter chain exception propagates and is not caught by rate limit error handler`() {
    setSecurityContext()
    val downstreamException = RuntimeException("downstream handler exploded")
    whenever(filterChain.doFilter(any(), any())).thenThrow(downstreamException)

    val request = createRequest()
    val response = MockHttpServletResponse()

    val thrown = org.junit.jupiter.api.assertThrows<RuntimeException> {
        filter.doFilter(request, response, filterChain)
    }

    assertEquals("downstream handler exploded", thrown.message)
    verify(filterErrorCounter, never()).increment()
}
```

## Service Test Base Class

**Base Class:** `BaseServiceTest` in `src/test/kotlin/riven/core/service/util/`

Provides:
- Common setup/teardown
- Shared test utilities
- Helper methods for security context management

## Known Test Gaps

**Commented Out Tests:**
- `CoreApplicationTests` is entirely commented out — context load test needs restoration

**Security Annotation Gotchas:**
- `@Nested` inner classes do NOT inherit outer class's `@WithUserPersona` — must reapply
- Without proper JWT setup, any call to `authTokenService.getUserId()` will throw `AccessDeniedException`

---

*Testing analysis: 2026-04-12*
