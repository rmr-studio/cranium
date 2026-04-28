package riven.core.service.connector.postgres

import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KLogger
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import riven.core.configuration.properties.ConnectorPoolProperties
import riven.core.entity.connector.DataConnectorConnectionEntity
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.connector.SslMode
import riven.core.enums.integration.SourceType
import riven.core.models.connector.CredentialPayload
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.repository.connector.DataConnectorConnectionRepository
import riven.core.service.connector.CredentialEncryptionService
import riven.core.service.connector.pool.WorkspaceConnectionPoolManager
import riven.core.service.ingestion.adapter.NangoCallContext
import riven.core.service.ingestion.adapter.PostgresCallContext
import riven.core.service.ingestion.adapter.SourceTypeAdapter
import riven.core.service.ingestion.adapter.exception.AdapterAuthException
import riven.core.service.ingestion.adapter.exception.AdapterUnavailableException
import riven.core.service.util.factory.dataconnector.DataConnectorConnectionEntityFactory
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for [PostgresAdapter] (Phase 3 PG-01..03, PG-05).
 *
 * Uses Testcontainers PostgreSQL for realistic JDBC behaviour: the adapter's
 * fetcher + introspector run against a live Postgres rather than elaborate
 * mocks of the JDBC cursor chain. Tests focus on:
 *   - Cursor vs PK-fallback SQL shape (observed via actual seeded rows).
 *   - Limit enforcement.
 *   - jsonb round-trip → Map.
 *   - SQLState translation into the adapter exception hierarchy.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresAdapterTest {

    companion object {
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"),
        )
            .withDatabaseName("riven_adapter_test")
            .withUsername("test")
            .withPassword("test")

        init {
            postgres.start()
        }
    }

    private val props = ConnectorPoolProperties()
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val logger: KLogger = mock()

    private lateinit var poolManager: WorkspaceConnectionPoolManager
    private lateinit var connectionRepository: DataConnectorConnectionRepository
    private lateinit var encryptionService: CredentialEncryptionService
    private lateinit var introspector: PostgresIntrospector
    private lateinit var fetcher: PostgresFetcher

    private lateinit var adapter: PostgresAdapter

    private val workspaceId: UUID = UUID.randomUUID()
    private val connectionId: UUID = UUID.randomUUID()

    private val credentials = CredentialPayload(
        host = postgres.host,
        port = postgres.firstMappedPort,
        database = postgres.databaseName,
        user = postgres.username,
        password = postgres.password,
        sslMode = SslMode.PREFER,
    )

    @BeforeAll
    fun seedDatabase() {
        val direct = buildDirectPool()
        try {
            direct.connection.use { c ->
                c.createStatement().use { s ->
                    s.execute("DROP TABLE IF EXISTS jsonb_demo")
                    s.execute("DROP TABLE IF EXISTS orders")
                    s.execute("DROP TABLE IF EXISTS customers")
                    s.execute(
                        """
                        CREATE TABLE customers (
                            id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                            email text NOT NULL,
                            updated_at timestamptz NOT NULL DEFAULT now()
                        )
                        """.trimIndent(),
                    )
                    s.execute(
                        """
                        CREATE TABLE orders (
                            id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                            customer_id uuid NOT NULL REFERENCES customers(id),
                            total numeric(10,2) NOT NULL,
                            updated_at timestamptz NOT NULL DEFAULT now()
                        )
                        """.trimIndent(),
                    )
                    s.execute(
                        """
                        CREATE TABLE jsonb_demo (
                            id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                            payload jsonb NOT NULL,
                            updated_at timestamptz NOT NULL DEFAULT now()
                        )
                        """.trimIndent(),
                    )
                    // Seed deterministic rows ordered by id for cursor-based fetches.
                    for (i in 1..20) {
                        s.execute(
                            "INSERT INTO customers(email) VALUES ('user$i@example.com')",
                        )
                    }
                    s.execute(
                        "INSERT INTO jsonb_demo(payload) VALUES ('{\"a\": 1, \"nested\": {\"b\": 2}}'::jsonb)",
                    )
                }
            }
        } finally {
            direct.close()
        }
    }

    private fun buildDirectPool(): HikariDataSource {
        val config = com.zaxxer.hikari.HikariConfig().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            maximumPoolSize = 2
            initializationFailTimeout = -1
        }
        return HikariDataSource(config)
    }

    @BeforeEach
    fun setupAdapter() {
        poolManager = WorkspaceConnectionPoolManager(props, logger)
        connectionRepository = mock()
        encryptionService = mock()
        introspector = PostgresIntrospector(logger)
        fetcher = PostgresFetcher(props, objectMapper, logger)

        val entity = DataConnectorConnectionEntityFactory.create(workspaceId = workspaceId)
        // Inject id via reflection (matches existing service-test pattern).
        val idField = DataConnectorConnectionEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(entity, connectionId)

        whenever(connectionRepository.findByIdAndWorkspaceId(eq(connectionId), eq(workspaceId)))
            .thenReturn(Optional.of(entity))
        whenever(encryptionService.decrypt(any()))
            .thenReturn(objectMapper.writeValueAsString(credentials))

        adapter = PostgresAdapter(
            poolManager = poolManager,
            connectionRepository = connectionRepository,
            encryptionService = encryptionService,
            introspector = introspector,
            fetcher = fetcher,
            objectMapper = objectMapper,
            logger = logger,
        )
    }

    @AfterEach
    fun teardown() {
        poolManager.evictAll()
    }

    private fun ctx(
        tableName: String? = null,
        cursorColumn: String? = null,
        primaryKeyColumn: String? = null,
        cursorIsTimestamp: Boolean = false,
    ) = PostgresCallContext(
        workspaceId = workspaceId,
        connectionId = connectionId,
        schema = "public",
        tableName = tableName,
        cursorColumn = cursorColumn,
        primaryKeyColumn = primaryKeyColumn,
        cursorColumnIsTimestamp = cursorIsTimestamp,
    )

    // ------ syncMode ------

    @Test
    fun syncModeReturnsPOLL() {
        assertThat(adapter.syncMode().name).isEqualTo("POLL")
    }

    // ------ fetchRecords ------

    @Test
    fun fetchRecordsUsesUpdatedAtCursorWhenColumnPresent() {
        val batch = adapter.fetchRecords(
            ctx(tableName = "customers", cursorColumn = "updated_at", primaryKeyColumn = "id", cursorIsTimestamp = true),
            cursor = null,
            limit = 5,
        )
        assertThat(batch.records).hasSize(5)
        // Rows returned ordered by updated_at ASC.
        assertThat(batch.nextCursor).isNotNull()
    }

    @Test
    fun fetchRecordsFallsBackToPkInsertsOnlyWhenNoCursor() {
        val batch = adapter.fetchRecords(
            ctx(tableName = "customers", cursorColumn = null, primaryKeyColumn = "id"),
            cursor = null,
            limit = 10,
        )
        assertThat(batch.records).hasSize(10)
        // externalId comes from the `id` PK column.
        batch.records.forEach { record ->
            assertThat(record.externalId).isNotBlank()
            assertThat(record.payload).containsKey("id")
        }
    }

    @Test
    fun fetchRecordsHonorsLimitStrictly() {
        val batch = adapter.fetchRecords(
            ctx(tableName = "customers", primaryKeyColumn = "id"),
            cursor = null,
            limit = 3,
        )
        assertThat(batch.records).hasSize(3)
        assertThat(batch.hasMore).isTrue()
    }

    @Test
    fun fetchRecordsRoundTripsJsonbAsObjectStructure() {
        val batch = adapter.fetchRecords(
            ctx(tableName = "jsonb_demo", primaryKeyColumn = "id"),
            cursor = null,
            limit = 10,
        )
        assertThat(batch.records).hasSize(1)
        val payloadCell = batch.records.first().payload["payload"]
        assertThat(payloadCell).isInstanceOf(EntityAttributePrimitivePayload::class.java)
        val primitive = payloadCell as EntityAttributePrimitivePayload
        assertThat(primitive.schemaType).isEqualTo(SchemaType.OBJECT)
        @Suppress("UNCHECKED_CAST")
        val parsed = primitive.value as Map<String, Any?>
        assertThat(parsed["a"]).isEqualTo(1)
        @Suppress("UNCHECKED_CAST")
        val nested = parsed["nested"] as Map<String, Any?>
        assertThat(nested["b"]).isEqualTo(2)
    }

    @Test
    fun fetchRecordsReturnsEmptyBatchWithHasMoreFalse() {
        // Query a table with no rows — create one on the fly.
        val direct = buildDirectPool()
        try {
            direct.connection.use { c ->
                c.createStatement().use { s ->
                    s.execute("CREATE TABLE IF NOT EXISTS empty_demo (id uuid PRIMARY KEY)")
                }
            }
        } finally {
            direct.close()
        }

        val batch = adapter.fetchRecords(
            ctx(tableName = "empty_demo", primaryKeyColumn = "id"),
            cursor = null,
            limit = 100,
        )
        assertThat(batch.records).isEmpty()
        assertThat(batch.nextCursor).isNull()
        assertThat(batch.hasMore).isFalse()
    }

    // ------ introspectSchema ------

    @Test
    fun introspectSchemaReturnsTablesWithFkMetadata() {
        val result = adapter.introspectWithFkMetadata(ctx())
        val tableNames = result.schema.tables.map { it.name }
        assertThat(tableNames).contains("customers", "orders")

        val fk = result.foreignKeys.firstOrNull { it.sourceTable == "orders" && it.targetTable == "customers" }
        assertThat(fk).isNotNull
        assertThat(fk!!.sourceColumn).isEqualTo("customer_id")
        assertThat(fk.targetColumn).isEqualTo("id")
        assertThat(fk.isComposite).isFalse()
    }

    // ------ exception translation ------

    @Test
    fun mapsPostgresAuthFailureToFatalAdapterAuthException() {
        // Replace the credentials decrypt response with bogus creds so pool
        // construction builds a DataSource but JDBC fails at first connect.
        val badCreds = credentials.copy(password = "this-password-is-wrong")
        whenever(encryptionService.decrypt(any()))
            .thenReturn(objectMapper.writeValueAsString(badCreds))
        // Use a distinct connection id so we get a fresh pool with bad creds.
        val badConnId = UUID.randomUUID()
        val badEntity = DataConnectorConnectionEntityFactory.create(workspaceId = workspaceId)
        val idField = DataConnectorConnectionEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(badEntity, badConnId)
        whenever(connectionRepository.findByIdAndWorkspaceId(eq(badConnId), eq(workspaceId)))
            .thenReturn(Optional.of(badEntity))

        val badCtx = PostgresCallContext(
            workspaceId = workspaceId,
            connectionId = badConnId,
            schema = "public",
            tableName = "customers",
            primaryKeyColumn = "id",
        )

        assertThatThrownBy { adapter.fetchRecords(badCtx, null, 5) }
            .isInstanceOf(AdapterAuthException::class.java)
    }

    @Test
    fun mapsPostgresTimeoutToTransientAdapterException() {
        // Force a short statement_timeout and run pg_sleep to trigger SQLState 57014.
        val direct = buildDirectPool()
        try {
            direct.connection.use { c ->
                c.autoCommit = false
                c.createStatement().use { s ->
                    s.execute("SET LOCAL statement_timeout = '100ms'")
                    try {
                        s.executeQuery("SELECT pg_sleep(2)")
                        throw AssertionError("Expected SQLException with SQLState 57014")
                    } catch (e: java.sql.SQLException) {
                        assertThat(e.sqlState).isEqualTo("57014")
                    }
                }
                c.rollback()
            }
        } finally {
            direct.close()
        }

        // Invoke the adapter's translator via a synthesized SQLException to
        // verify the mapping rule covers 57014.
        val mapMethod = PostgresAdapter::class.java.getDeclaredMethod(
            "mapJdbcException",
            java.sql.SQLException::class.java,
            Throwable::class.java,
        )
        mapMethod.isAccessible = true
        val fake = java.sql.SQLException("statement cancelled", "57014")
        val mapped = mapMethod.invoke(adapter, fake, fake)
        assertThat(mapped).isInstanceOf(AdapterUnavailableException::class.java)
    }

    // ------ registration ------

    @Test
    fun isAnnotatedAsSourceTypeAdapterForConnector() {
        val annotation = PostgresAdapter::class.java.getAnnotation(SourceTypeAdapter::class.java)
        assertThat(annotation).isNotNull
        assertThat(annotation.value).isEqualTo(SourceType.CONNECTOR)
    }

    @Test
    fun requireContextRejectsNonPostgresCallContext() {
        val nangoCtx = NangoCallContext(
            workspaceId = workspaceId,
            providerConfigKey = "k",
            connectionId = "c",
            model = "m",
        )
        assertThatThrownBy { adapter.fetchRecords(nangoCtx, null, 5) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
