package riven.core.service.connector.mapping

import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.connector.DataConnectorFieldMappingEntity
import riven.core.entity.connector.DataConnectorConnectionEntity
import riven.core.enums.connector.SslMode
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.models.connector.CredentialPayload
import riven.core.models.connector.response.DriftStatus
import riven.core.models.ingestion.adapter.ColumnSchema
import riven.core.models.ingestion.adapter.SchemaIntrospectionResult
import riven.core.models.ingestion.adapter.TableSchema
import riven.core.repository.connector.DataConnectorConnectionRepository
import riven.core.repository.connector.DataConnectorFieldMappingRepository
import riven.core.repository.connector.DataConnectorTableMappingRepository
import riven.core.service.auth.AuthTokenService
import riven.core.service.connector.CredentialEncryptionService
import riven.core.service.connector.postgres.ForeignKeyMetadata
import riven.core.service.connector.postgres.IntrospectionResult
import riven.core.service.connector.postgres.PostgresAdapter
import riven.core.service.connector.postgres.SchemaHasher
import riven.core.service.ingestion.adapter.PostgresCallContext
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import riven.core.service.util.factory.DataConnectorFieldMappingEntityFactory
import riven.core.service.util.factory.DataConnectorTableMappingEntityFactory
import riven.core.service.util.factory.dataconnector.DataConnectorConnectionEntityFactory
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for [DataConnectorSchemaInferenceService] (Phase 3 plan 03-03).
 *
 * Covers the 7 named assertions from plan 03-00 — drift detection, FK
 * metadata propagation, stale-marking of dropped columns, cursor-index
 * warning surfacing, and workspace scoping via @PreAuthorize.
 */
@SpringBootTest(
    classes = [
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        DataConnectorSchemaInferenceServiceTest.TestConfig::class,
        DataConnectorSchemaInferenceService::class,
    ],
)
@TestPropertySource(properties = ["riven.connector.enabled=true"])
@WithUserPersona(
    userId = "11111111-1111-1111-1111-111111111111",
    email = "test@test.com",
    displayName = "Test User",
    roles = [
        WorkspaceRole(
            workspaceId = "22222222-2222-2222-2222-222222222222",
            role = WorkspaceRoles.ADMIN,
        ),
    ],
)
class DataConnectorSchemaInferenceServiceTest {

    @Configuration
    class TestConfig {
        @Bean fun objectMapper(): ObjectMapper = jacksonObjectMapper()
    }

    private val workspaceId: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val otherWorkspaceId: UUID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
    private val connectionId: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")

    @MockitoBean private lateinit var postgresAdapter: PostgresAdapter
    @MockitoBean private lateinit var encryptionService: CredentialEncryptionService
    @MockitoBean private lateinit var connectionRepository: DataConnectorConnectionRepository
    @MockitoBean private lateinit var tableMappingRepository: DataConnectorTableMappingRepository
    @MockitoBean private lateinit var fieldMappingRepository: DataConnectorFieldMappingRepository
    @MockitoBean private lateinit var cursorIndexProbe: CursorIndexProbe
    @MockitoBean private lateinit var logger: KLogger
    @MockitoBean private lateinit var authTokenService: AuthTokenService

    @Autowired private lateinit var service: DataConnectorSchemaInferenceService
    @Autowired private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setup() {
        reset(
            postgresAdapter,
            encryptionService,
            connectionRepository,
            tableMappingRepository,
            fieldMappingRepository,
            cursorIndexProbe,
        )

        // Default: the connection exists + decrypts + any stored fields saves return themselves.
        val entity: DataConnectorConnectionEntity = DataConnectorConnectionEntityFactory.create(
            workspaceId = workspaceId,
        )
        whenever(connectionRepository.findByIdAndWorkspaceId(connectionId, workspaceId))
            .thenReturn(Optional.of(entity))
        whenever(connectionRepository.findByIdAndWorkspaceId(connectionId, otherWorkspaceId))
            .thenReturn(Optional.of(entity))

        val credentialJson = objectMapper.writeValueAsString(
            CredentialPayload(
                host = "db.example.com",
                port = 5432,
                database = "analytics",
                user = "readonly",
                password = "hunter2",
                sslMode = SslMode.REQUIRE,
            ),
        )
        whenever(encryptionService.decrypt(any())).thenReturn(credentialJson)

        whenever(fieldMappingRepository.save(any())).thenAnswer { it.arguments[0] as DataConnectorFieldMappingEntity }
        whenever(tableMappingRepository.save(any())).thenAnswer { it.arguments[0] }

        // Default: cursor column considered indexed (no warning) unless overridden.
        whenever(cursorIndexProbe.isIndexed(any(), any(), any(), any(), any())).thenReturn(true)
    }

    @Test
    fun getSchemaReturnsTablesWithComputedSchemaHash() {
        val columns = listOf(
            ColumnSchema("id", "uuid", false),
            ColumnSchema("email", "text", false),
        )
        stubIntrospection(listOf(TableSchema("customers", columns)))
        whenever(tableMappingRepository.findByConnectionId(connectionId)).thenReturn(emptyList())
        whenever(fieldMappingRepository.findByConnectionId(connectionId)).thenReturn(emptyList())

        val response = service.getSchema(workspaceId, connectionId)

        assertEquals(1, response.tables.size)
        val table = response.tables.first()
        assertEquals("customers", table.tableName)
        assertEquals(SchemaHasher.compute("customers", columns), table.schemaHash)
        assertEquals(DriftStatus.NEW, table.driftStatus)
    }

    @Test
    fun getSchemaSurfacesDriftWhenStoredHashDiffers() {
        val columns = listOf(ColumnSchema("id", "uuid", false))
        stubIntrospection(listOf(TableSchema("customers", columns)))
        val storedTable = DataConnectorTableMappingEntityFactory.create(
            workspaceId = workspaceId,
            connectionId = connectionId,
            tableName = "customers",
            schemaHash = "stale-hash-xxx",
        )
        val storedField = DataConnectorFieldMappingEntityFactory.create(
            workspaceId = workspaceId,
            connectionId = connectionId,
            tableName = "customers",
            columnName = "id",
            pgDataType = "uuid",
            isPrimaryKey = true,
        )
        whenever(tableMappingRepository.findByConnectionId(connectionId)).thenReturn(listOf(storedTable))
        whenever(fieldMappingRepository.findByConnectionId(connectionId)).thenReturn(listOf(storedField))

        val response = service.getSchema(workspaceId, connectionId)

        assertEquals(DriftStatus.DRIFTED, response.tables.first().driftStatus)
    }

    @Test
    fun getSchemaSurfacesAddedColumnsAsUnmappedDriftEntries() {
        val live = listOf(
            ColumnSchema("id", "uuid", false),
            ColumnSchema("email", "text", false),
            ColumnSchema("new_col", "text", true),
        )
        stubIntrospection(listOf(TableSchema("customers", live)))
        val storedTable = DataConnectorTableMappingEntityFactory.create(
            workspaceId = workspaceId,
            connectionId = connectionId,
            tableName = "customers",
            schemaHash = SchemaHasher.compute(
                "customers",
                listOf(ColumnSchema("id", "uuid", false), ColumnSchema("email", "text", false)),
            ),
        )
        val storedFields = listOf(
            DataConnectorFieldMappingEntityFactory.create(
                workspaceId = workspaceId, connectionId = connectionId,
                tableName = "customers", columnName = "id", pgDataType = "uuid", isPrimaryKey = true,
            ),
            DataConnectorFieldMappingEntityFactory.create(
                workspaceId = workspaceId, connectionId = connectionId,
                tableName = "customers", columnName = "email", pgDataType = "text",
            ),
        )
        whenever(tableMappingRepository.findByConnectionId(connectionId)).thenReturn(listOf(storedTable))
        whenever(fieldMappingRepository.findByConnectionId(connectionId)).thenReturn(storedFields)

        val response = service.getSchema(workspaceId, connectionId)
        val table = response.tables.first()

        assertEquals(DriftStatus.DRIFTED, table.driftStatus)
        val newCol = table.columns.first { it.columnName == "new_col" }
        assertNull(newCol.existingMapping)
    }

    @Test
    fun getSchemaMarksDroppedColumnsStaleInMappingTable() {
        val live = listOf(ColumnSchema("id", "uuid", false))
        stubIntrospection(listOf(TableSchema("customers", live)))
        val storedTable = DataConnectorTableMappingEntityFactory.create(
            workspaceId = workspaceId,
            connectionId = connectionId,
            tableName = "customers",
        )
        val droppedField = DataConnectorFieldMappingEntityFactory.create(
            workspaceId = workspaceId, connectionId = connectionId,
            tableName = "customers", columnName = "deleted_me", pgDataType = "text",
        )
        val keptField = DataConnectorFieldMappingEntityFactory.create(
            workspaceId = workspaceId, connectionId = connectionId,
            tableName = "customers", columnName = "id", pgDataType = "uuid", isPrimaryKey = true,
        )
        whenever(tableMappingRepository.findByConnectionId(connectionId)).thenReturn(listOf(storedTable))
        whenever(fieldMappingRepository.findByConnectionId(connectionId)).thenReturn(listOf(droppedField, keptField))

        service.getSchema(workspaceId, connectionId)

        verify(fieldMappingRepository).save(argThat<DataConnectorFieldMappingEntity> {
            columnName == "deleted_me" && stale
        })
    }

    @Test
    fun getSchemaSurfacesFkMetadataPerColumn() {
        val columns = listOf(
            ColumnSchema("id", "uuid", false),
            ColumnSchema("org_id", "uuid", false),
        )
        stubIntrospection(
            tables = listOf(TableSchema("customers", columns)),
            fks = listOf(
                ForeignKeyMetadata(
                    sourceTable = "customers",
                    sourceColumn = "org_id",
                    targetTable = "orgs",
                    targetColumn = "id",
                    isComposite = false,
                ),
            ),
        )
        whenever(tableMappingRepository.findByConnectionId(connectionId)).thenReturn(emptyList())
        whenever(fieldMappingRepository.findByConnectionId(connectionId)).thenReturn(emptyList())

        val response = service.getSchema(workspaceId, connectionId)

        val orgId = response.tables.first().columns.first { it.columnName == "org_id" }
        assertTrue(orgId.isForeignKey)
        assertNotNull(orgId.fkTarget)
        assertEquals("orgs", orgId.fkTarget!!.table)
        assertEquals("id", orgId.fkTarget!!.column)
    }

    @Test
    fun getSchemaIncludesCursorIndexWarningWhenChosenCursorColumnUnindexed() {
        val columns = listOf(
            ColumnSchema("id", "uuid", false),
            ColumnSchema("updated_at", "timestamp", false),
        )
        stubIntrospection(listOf(TableSchema("customers", columns)))
        whenever(tableMappingRepository.findByConnectionId(connectionId)).thenReturn(emptyList())
        whenever(fieldMappingRepository.findByConnectionId(connectionId)).thenReturn(emptyList())
        whenever(
            cursorIndexProbe.isIndexed(
                eq(connectionId), any(), any(), eq("customers"), eq("updated_at"),
            ),
        ).thenReturn(false)

        val response = service.getSchema(workspaceId, connectionId)

        val table = response.tables.first()
        assertEquals("updated_at", table.detectedCursorColumn)
        assertNotNull(table.cursorIndexWarning)
        assertEquals("updated_at", table.cursorIndexWarning!!.column)
        assertTrue(table.cursorIndexWarning!!.suggestedDdl.contains("CREATE INDEX"))
    }

    @Test
    fun getSchemaScopedToWorkspaceViaPreAuthorize() {
        stubIntrospection(emptyList())
        assertThrows<AccessDeniedException> {
            service.getSchema(otherWorkspaceId, connectionId)
        }
    }

    // ------ Helpers ------

    private fun stubIntrospection(
        tables: List<TableSchema>,
        fks: List<ForeignKeyMetadata> = emptyList(),
    ) {
        val result = IntrospectionResult(
            schema = SchemaIntrospectionResult(tables = tables),
            foreignKeys = fks,
        )
        whenever(
            postgresAdapter.introspectWithFkMetadata(
                argThat<PostgresCallContext> {
                    this.connectionId == this@DataConnectorSchemaInferenceServiceTest.connectionId
                },
            ),
        ).thenReturn(result)
    }

}
