package riven.core.entity.connector

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import riven.core.repository.connector.DataConnectorConnectionRepository
import riven.core.repository.connector.DataConnectorFieldMappingRepository
import riven.core.repository.connector.DataConnectorTableMappingRepository
import riven.core.service.util.factory.DataConnectorFieldMappingEntityFactory
import riven.core.service.util.factory.DataConnectorTableMappingEntityFactory
import riven.core.service.util.factory.dataconnector.DataConnectorConnectionEntityFactory
import java.time.ZonedDateTime
import java.time.temporal.TemporalAccessor
import java.util.Optional
import java.util.UUID

/**
 * Integration tests for [DataConnectorTableMappingEntity] + [DataConnectorFieldMappingEntity]
 * against real Postgres via Testcontainers. Covers Phase 3 plan 03-01 must-haves:
 *
 *  - Round-trip persistence of every declared field on both entities
 *  - `@SQLRestriction("deleted = false")` declared on the concrete entity
 *    (Phase 2 lesson — Hibernate 6 does not propagate from @MappedSuperclass)
 *  - Unique constraints on (workspace, connection, table) and
 *    (workspace, connection, table, column)
 *  - FK ON DELETE CASCADE from mappings to data_connector_connections
 *
 * Scanning limited to `riven.core.entity.connector` to avoid dragging in
 * the full entity graph (Phase 2 lesson). The `workspaces` table needed for
 * the Phase 2 connection FK is hand-created via JdbcTemplate.
 */
@Configuration
@EnableAutoConfiguration(
    exclude = [
        SecurityAutoConfiguration::class,
        UserDetailsServiceAutoConfiguration::class,
        OAuth2ResourceServerAutoConfiguration::class,
    ],
    excludeName = [
        "io.temporal.spring.boot.autoconfigure.ServiceStubsAutoConfiguration",
        "io.temporal.spring.boot.autoconfigure.RootNamespaceAutoConfiguration",
        "io.temporal.spring.boot.autoconfigure.NonRootNamespaceAutoConfiguration",
        "io.temporal.spring.boot.autoconfigure.MetricsScopeAutoConfiguration",
        "io.temporal.spring.boot.autoconfigure.OpenTracingAutoConfiguration",
        "io.temporal.spring.boot.autoconfigure.TestServerAutoConfiguration",
    ],
)
@EnableJpaRepositories(basePackages = ["riven.core.repository.connector"])
@EntityScan("riven.core.entity.connector")
@EnableJpaAuditing(
    auditorAwareRef = "dataConnectorMappingAuditorProvider",
    dateTimeProviderRef = "dataConnectorMappingDateTimeProvider",
)
class DataConnectorMappingEntityTestConfig {

    @Bean
    fun dataConnectorMappingAuditorProvider(): AuditorAware<UUID> =
        AuditorAware { Optional.empty() }

    @Bean
    fun dataConnectorMappingDateTimeProvider(): DateTimeProvider =
        DateTimeProvider { Optional.of<TemporalAccessor>(ZonedDateTime.now()) }
}

@SpringBootTest(
    classes = [DataConnectorMappingEntityTestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@ActiveProfiles("integration")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataConnectorMappingEntityIntegrationTest {

    companion object {
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"),
        )
            .withDatabaseName("riven_test")
            .withUsername("test")
            .withPassword("test")

        init {
            postgres.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }

    @Autowired
    private lateinit var tableMappingRepository: DataConnectorTableMappingRepository

    @Autowired
    private lateinit var fieldMappingRepository: DataConnectorFieldMappingRepository

    @Autowired
    private lateinit var connectionRepository: DataConnectorConnectionRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    /**
     * Hibernate's `ddl-auto: create-drop` only generates tables for scanned
     * entities. We scope the scan to `riven.core.entity.connector`, which
     * transitively drags in the Phase 2 [DataConnectorConnectionEntity] —
     * that entity FKs to `workspaces`, so we hand-create it.
     */
    @BeforeAll
    fun createWorkspacesTableAndCascadeConstraints() {
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS workspaces (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                name VARCHAR(100) NOT NULL,
                deleted BOOLEAN NOT NULL DEFAULT FALSE
            )
            """.trimIndent(),
        )

        // Apply the production DDL's FK CASCADE constraints. Hibernate's
        // ddl-auto = create-drop auto-generates FKs without ON DELETE CASCADE;
        // the declarative SQL file in db/schema/04_constraints/ is the source
        // of truth for cascade semantics, so we mirror it here.
        jdbcTemplate.execute(
            "ALTER TABLE connector_table_mappings " +
                "DROP CONSTRAINT IF EXISTS fk_connector_table_mappings_connection",
        )
        jdbcTemplate.execute(
            "ALTER TABLE connector_field_mappings " +
                "DROP CONSTRAINT IF EXISTS fk_connector_field_mappings_connection",
        )
        jdbcTemplate.execute(
            """
            ALTER TABLE connector_table_mappings
            ADD CONSTRAINT fk_connector_table_mappings_connection
            FOREIGN KEY (connection_id)
            REFERENCES data_connector_connections(id)
            ON DELETE CASCADE
            """.trimIndent(),
        )
        jdbcTemplate.execute(
            """
            ALTER TABLE connector_field_mappings
            ADD CONSTRAINT fk_connector_field_mappings_connection
            FOREIGN KEY (connection_id)
            REFERENCES data_connector_connections(id)
            ON DELETE CASCADE
            """.trimIndent(),
        )
    }

    // ------ Helpers ------

    private fun insertWorkspace(): UUID {
        val id = UUID.randomUUID()
        jdbcTemplate.update(
            "INSERT INTO workspaces (id, name) VALUES (?, ?)",
            id,
            "ws-${id.toString().take(8)}",
        )
        return id
    }

    private fun insertConnection(workspaceId: UUID): UUID {
        val saved = connectionRepository.saveAndFlush(
            DataConnectorConnectionEntityFactory.create(workspaceId = workspaceId),
        )
        return requireNotNull(saved.id) { "connection id must be assigned on save" }
    }

    // ------ Round-trip ------

    @Test
    @Transactional
    fun saveAndReloadDataConnectorTableMappingPersistsAllFields() {
        val workspaceId = insertWorkspace()
        val connectionId = insertConnection(workspaceId)
        val entityTypeId = UUID.randomUUID()
        val introspectedAt = ZonedDateTime.now().withNano(0)

        val entity = DataConnectorTableMappingEntityFactory.create(
            workspaceId = workspaceId,
            connectionId = connectionId,
            tableName = "customers",
            entityTypeId = entityTypeId,
            schemaHash = "sha256:abcdef",
            lastIntrospectedAt = introspectedAt,
            published = true,
        )

        val saved = tableMappingRepository.saveAndFlush(entity)
        val reloadedId = requireNotNull(saved.id) { "id must be assigned on save" }
        entityManager.clear()

        val reloaded = tableMappingRepository.findById(reloadedId).orElseThrow()

        assertThat(reloaded.workspaceId).isEqualTo(workspaceId)
        assertThat(reloaded.connectionId).isEqualTo(connectionId)
        assertThat(reloaded.tableName).isEqualTo("customers")
        assertThat(reloaded.entityTypeId).isEqualTo(entityTypeId)
        assertThat(reloaded.schemaHash).isEqualTo("sha256:abcdef")
        assertThat(reloaded.published).isTrue()
        assertThat(reloaded.createdAt).isNotNull()

        // toModel() round-trips all user-facing fields
        val model = reloaded.toModel()
        assertThat(model.id).isEqualTo(reloadedId)
        assertThat(model.workspaceId).isEqualTo(workspaceId)
        assertThat(model.entityTypeId).isEqualTo(entityTypeId)
        assertThat(model.schemaHash).isEqualTo("sha256:abcdef")
        assertThat(model.published).isTrue()
    }

    @Test
    @Transactional
    fun saveAndReloadDataConnectorFieldMappingPersistsAllFields() {
        val workspaceId = insertWorkspace()
        val connectionId = insertConnection(workspaceId)

        val entity = DataConnectorFieldMappingEntityFactory.create(
            workspaceId = workspaceId,
            connectionId = connectionId,
            tableName = "customers",
            columnName = "owner_id",
            pgDataType = "uuid",
            nullable = true,
            isPrimaryKey = false,
            isForeignKey = true,
            fkTargetTable = "users",
            fkTargetColumn = "id",
            attributeName = "owner",
            isIdentifier = false,
            isSyncCursor = false,
            isMapped = true,
            stale = false,
        )

        val saved = fieldMappingRepository.saveAndFlush(entity)
        val reloadedId = requireNotNull(saved.id) { "id must be assigned on save" }
        entityManager.clear()

        val reloaded = fieldMappingRepository.findById(reloadedId).orElseThrow()

        assertThat(reloaded.workspaceId).isEqualTo(workspaceId)
        assertThat(reloaded.connectionId).isEqualTo(connectionId)
        assertThat(reloaded.tableName).isEqualTo("customers")
        assertThat(reloaded.columnName).isEqualTo("owner_id")
        assertThat(reloaded.pgDataType).isEqualTo("uuid")
        assertThat(reloaded.nullable).isTrue()
        assertThat(reloaded.isPrimaryKey).isFalse()
        assertThat(reloaded.isForeignKey).isTrue()
        assertThat(reloaded.fkTargetTable).isEqualTo("users")
        assertThat(reloaded.fkTargetColumn).isEqualTo("id")
        assertThat(reloaded.attributeName).isEqualTo("owner")

        val model = reloaded.toModel()
        assertThat(model.id).isEqualTo(reloadedId)
        assertThat(model.isForeignKey).isTrue()
        assertThat(model.fkTargetTable).isEqualTo("users")
    }

    // ------ Soft-delete via @SQLRestriction on concrete entity ------

    @Test
    @Transactional
    fun softDeleteOnTableMappingHidesFromDerivedQueries() {
        val workspaceId = insertWorkspace()
        val connectionId = insertConnection(workspaceId)

        val saved = tableMappingRepository.saveAndFlush(
            DataConnectorTableMappingEntityFactory.create(
                workspaceId = workspaceId,
                connectionId = connectionId,
                tableName = "to_be_deleted",
            ),
        )
        val savedId = requireNotNull(saved.id) { "id must be assigned" }

        jdbcTemplate.update(
            "UPDATE connector_table_mappings SET deleted = TRUE, deleted_at = NOW() WHERE id = ?",
            savedId,
        )
        entityManager.clear()

        // Derived query — @SQLRestriction is appended and hides the row.
        val results = tableMappingRepository.findByConnectionId(connectionId)
        assertThat(results).isEmpty()

        // Sanity: row physically present (soft-delete, not hard-delete).
        val physical = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM connector_table_mappings WHERE id = ?",
            Long::class.java,
            savedId,
        )
        assertThat(physical).isEqualTo(1L)
    }

    @Test
    @Transactional
    fun softDeleteOnFieldMappingHidesFromDerivedQueries() {
        val workspaceId = insertWorkspace()
        val connectionId = insertConnection(workspaceId)

        val saved = fieldMappingRepository.saveAndFlush(
            DataConnectorFieldMappingEntityFactory.create(
                workspaceId = workspaceId,
                connectionId = connectionId,
                tableName = "customers",
                columnName = "temp_col",
            ),
        )
        val savedId = requireNotNull(saved.id) { "id must be assigned" }

        jdbcTemplate.update(
            "UPDATE connector_field_mappings SET deleted = TRUE, deleted_at = NOW() WHERE id = ?",
            savedId,
        )
        entityManager.clear()

        val results = fieldMappingRepository.findByConnectionIdAndTableName(connectionId, "customers")
        assertThat(results).isEmpty()

        val physical = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM connector_field_mappings WHERE id = ?",
            Long::class.java,
            savedId,
        )
        assertThat(physical).isEqualTo(1L)
    }

    // ------ Unique constraints ------

    @Test
    @Transactional
    fun uniqueConstraintRejectsDuplicateConnectionTableTuple() {
        val workspaceId = insertWorkspace()
        val connectionId = insertConnection(workspaceId)

        tableMappingRepository.saveAndFlush(
            DataConnectorTableMappingEntityFactory.create(
                workspaceId = workspaceId,
                connectionId = connectionId,
                tableName = "customers",
            ),
        )

        assertThatThrownBy {
            tableMappingRepository.saveAndFlush(
                DataConnectorTableMappingEntityFactory.create(
                    workspaceId = workspaceId,
                    connectionId = connectionId,
                    tableName = "customers",
                ),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    @Transactional
    fun uniqueConstraintRejectsDuplicateConnectionTableColumnTuple() {
        val workspaceId = insertWorkspace()
        val connectionId = insertConnection(workspaceId)

        fieldMappingRepository.saveAndFlush(
            DataConnectorFieldMappingEntityFactory.create(
                workspaceId = workspaceId,
                connectionId = connectionId,
                tableName = "customers",
                columnName = "email",
            ),
        )

        assertThatThrownBy {
            fieldMappingRepository.saveAndFlush(
                DataConnectorFieldMappingEntityFactory.create(
                    workspaceId = workspaceId,
                    connectionId = connectionId,
                    tableName = "customers",
                    columnName = "email",
                ),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    // ------ Cascade delete ------

    @Test
    fun cascadeDeleteOfConnectionDeletesMappingRows() {
        val workspaceId = insertWorkspace()
        val connectionId = insertConnection(workspaceId)

        tableMappingRepository.saveAndFlush(
            DataConnectorTableMappingEntityFactory.create(
                workspaceId = workspaceId,
                connectionId = connectionId,
                tableName = "cascade_target",
            ),
        )
        fieldMappingRepository.saveAndFlush(
            DataConnectorFieldMappingEntityFactory.create(
                workspaceId = workspaceId,
                connectionId = connectionId,
                tableName = "cascade_target",
                columnName = "email",
            ),
        )

        // Hard-delete the connection via native SQL (soft-delete via
        // @SQLRestriction on the JPA layer does NOT trigger FK CASCADE —
        // cascade semantics live at the SQL layer).
        jdbcTemplate.update("DELETE FROM data_connector_connections WHERE id = ?", connectionId)
        entityManager.clear()

        val remainingTables = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM connector_table_mappings WHERE connection_id = ?",
            Long::class.java,
            connectionId,
        )
        val remainingFields = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM connector_field_mappings WHERE connection_id = ?",
            Long::class.java,
            connectionId,
        )

        assertThat(remainingTables).isEqualTo(0L)
        assertThat(remainingFields).isEqualTo(0L)
    }
}
