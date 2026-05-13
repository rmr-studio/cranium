package cranium.core.service.connector.mapping

import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
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
import cranium.core.configuration.auth.WorkspaceSecurity
import cranium.core.entity.connector.DataConnectorFieldMappingEntity
import cranium.core.entity.connector.DataConnectorTableMappingEntity
import cranium.core.entity.entity.EntityTypeEntity
import cranium.core.entity.entity.RelationshipDefinitionEntity
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.connector.SslMode
import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticGroup
import cranium.core.enums.integration.SourceType
import cranium.core.enums.workspace.WorkspaceRoles
import cranium.core.models.connector.CredentialPayload
import cranium.core.models.connector.request.SaveDataConnectorFieldMappingRequest
import cranium.core.models.connector.request.SaveDataConnectorMappingRequest
import cranium.core.models.ingestion.adapter.ColumnSchema
import cranium.core.models.ingestion.adapter.SchemaIntrospectionResult
import cranium.core.models.ingestion.adapter.TableSchema
import cranium.core.repository.connector.DataConnectorConnectionRepository
import cranium.core.repository.connector.DataConnectorFieldMappingRepository
import cranium.core.repository.connector.DataConnectorTableMappingRepository
import cranium.core.repository.entity.EntityTypeRepository
import cranium.core.repository.entity.RelationshipDefinitionRepository
import cranium.core.service.activity.ActivityService
import cranium.core.service.auth.AuthTokenService
import cranium.core.service.connector.CredentialEncryptionService
import cranium.core.service.connector.postgres.ForeignKeyMetadata
import cranium.core.service.connector.postgres.IntrospectionResult
import cranium.core.service.connector.postgres.PostgresAdapter
import cranium.core.service.ingestion.adapter.PostgresCallContext
import cranium.core.service.util.SecurityTestConfig
import cranium.core.service.util.WithUserPersona
import cranium.core.service.util.WorkspaceRole
import cranium.core.service.util.factory.DataConnectorFieldMappingEntityFactory
import cranium.core.service.util.factory.DataConnectorTableMappingEntityFactory
import cranium.core.service.util.factory.dataconnector.DataConnectorConnectionEntityFactory
import cranium.core.service.util.factory.entity.EntityFactory
import java.time.ZonedDateTime
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for [DataConnectorFieldMappingService] (Phase 3 plan 03-03).
 * Covers the 10 named assertions from 03-00: EntityType creation with
 * sourceType=CONNECTOR+readonly, attribute definitions per mapped column,
 * FK relationship materialisation + pending metadata, composite FK skip,
 * published transition, cursor-index warning, re-save drift handling, and
 * @PreAuthorize scoping.
 */
@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        DataConnectorFieldMappingServiceTest.TestConfig::class,
        DataConnectorFieldMappingService::class,
    ],
)
@TestPropertySource(properties = ["cranium.connector.enabled=true"])
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
class DataConnectorFieldMappingServiceTest {

    @Configuration
    class TestConfig {
        @Bean fun objectMapper(): ObjectMapper = jacksonObjectMapper()
    }

    private val userId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val workspaceId: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val otherWorkspaceId: UUID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
    private val connectionId: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")

    @MockitoBean private lateinit var postgresAdapter: PostgresAdapter
    @MockitoBean private lateinit var encryptionService: CredentialEncryptionService
    @MockitoBean private lateinit var cursorIndexProbe: CursorIndexProbe
    @MockitoBean private lateinit var connectionRepository: DataConnectorConnectionRepository
    @MockitoBean private lateinit var tableMappingRepository: DataConnectorTableMappingRepository
    @MockitoBean private lateinit var fieldMappingRepository: DataConnectorFieldMappingRepository
    @MockitoBean private lateinit var entityTypeRepository: EntityTypeRepository
    @MockitoBean private lateinit var relationshipDefinitionRepository: RelationshipDefinitionRepository
    @MockitoBean private lateinit var activityService: ActivityService
    @MockitoBean private lateinit var authTokenService: AuthTokenService
    @MockitoBean private lateinit var logger: KLogger

    @Autowired private lateinit var service: DataConnectorFieldMappingService
    @Autowired private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setup() {
        reset(
            postgresAdapter, encryptionService, cursorIndexProbe, connectionRepository,
            tableMappingRepository, fieldMappingRepository, entityTypeRepository,
            relationshipDefinitionRepository, activityService, authTokenService,
        )
        whenever(authTokenService.getUserId()).thenReturn(userId)

        val entity = DataConnectorConnectionEntityFactory.create(workspaceId = workspaceId)
        whenever(connectionRepository.findByIdAndWorkspaceId(connectionId, workspaceId))
            .thenReturn(Optional.of(entity))
        whenever(connectionRepository.findByIdAndWorkspaceId(connectionId, otherWorkspaceId))
            .thenReturn(Optional.of(entity))

        val credentialJson = objectMapper.writeValueAsString(
            CredentialPayload(
                host = "db.example.com", port = 5432, database = "analytics",
                user = "readonly", password = "hunter2", sslMode = SslMode.REQUIRE,
            ),
        )
        whenever(encryptionService.decrypt(any())).thenReturn(credentialJson)

        // Save stubs: return argument with an ID injected (simulating JPA save).
        whenever(fieldMappingRepository.save(any())).thenAnswer { inv ->
            val arg = inv.arguments[0] as DataConnectorFieldMappingEntity
            if (arg.id == null) injectId(arg, UUID.randomUUID()) else arg
        }
        whenever(tableMappingRepository.save(any())).thenAnswer { inv ->
            val arg = inv.arguments[0] as DataConnectorTableMappingEntity
            if (arg.id == null) injectIdTable(arg, UUID.randomUUID()) else arg
        }
        whenever(entityTypeRepository.save(any())).thenAnswer { inv ->
            val arg = inv.arguments[0] as EntityTypeEntity
            if (arg.id == null) arg.copy(id = UUID.randomUUID()) else arg
        }
        whenever(relationshipDefinitionRepository.save(any())).thenAnswer { inv ->
            val arg = inv.arguments[0] as RelationshipDefinitionEntity
            if (arg.id == null) arg.copy(id = UUID.randomUUID()) else arg
        }
        whenever(cursorIndexProbe.isIndexed(any(), any(), any(), any(), any())).thenReturn(true)
    }

    @Test
    fun saveCreatesEntityTypeWithSourceTypeConnectorAndReadonlyTrue() {
        stubIntrospection(customersTable())
        whenever(fieldMappingRepository.findByConnectionIdAndTableName(connectionId, "customers")).thenReturn(emptyList())
        whenever(tableMappingRepository.findByConnectionIdAndTableName(connectionId, "customers")).thenReturn(null)

        service.saveMapping(workspaceId, connectionId, "customers", baseRequest())

        val captor = argumentCaptor<EntityTypeEntity>()
        verify(entityTypeRepository).save(captor.capture())
        val saved = captor.firstValue
        assertEquals(SourceType.CONNECTOR, saved.sourceType)
        assertTrue(saved.readonly)
    }

    @Test
    fun saveCreatesAttributeDefinitionsForEachMappedColumn() {
        stubIntrospection(customersTable())
        whenever(fieldMappingRepository.findByConnectionIdAndTableName(connectionId, "customers")).thenReturn(emptyList())
        whenever(tableMappingRepository.findByConnectionIdAndTableName(connectionId, "customers")).thenReturn(null)

        service.saveMapping(
            workspaceId, connectionId, "customers",
            baseRequest().copy(
                columns = listOf(
                    col("id", SchemaType.ID, isMapped = true, attributeName = "id", isIdentifier = true),
                    col("email", SchemaType.EMAIL, isMapped = true, attributeName = "email"),
                    col("internal_flag", SchemaType.TEXT, isMapped = false),
                ),
            ),
        )

        val captor = argumentCaptor<EntityTypeEntity>()
        verify(entityTypeRepository).save(captor.capture())
        val saved = captor.firstValue
        // Only mapped columns become attributes.
        assertEquals(2, saved.schema.properties?.size)
    }

    @Test
    fun saveCreatesRelationshipDefinitionWhenBothFkEndsArePublished() {
        stubIntrospection(
            table = TableSchema(
                "orders",
                listOf(
                    ColumnSchema("id", "uuid", false),
                    ColumnSchema("customer_id", "uuid", false),
                ),
            ),
            fks = listOf(
                ForeignKeyMetadata("orders", "customer_id", "customers", "id", isComposite = false),
            ),
        )
        whenever(fieldMappingRepository.findByConnectionIdAndTableName(connectionId, "orders")).thenReturn(emptyList())
        whenever(tableMappingRepository.findByConnectionIdAndTableName(connectionId, "orders")).thenReturn(null)
        // Target "customers" is already published.
        val publishedTarget = DataConnectorTableMappingEntityFactory.create(
            workspaceId = workspaceId, connectionId = connectionId, tableName = "customers", published = true,
            entityTypeId = UUID.randomUUID(),
        )
        whenever(tableMappingRepository.findByConnectionIdAndTableName(connectionId, "customers"))
            .thenReturn(publishedTarget)

        val response = service.saveMapping(
            workspaceId, connectionId, "orders",
            baseRequest().copy(
                columns = listOf(
                    col("id", SchemaType.ID, isMapped = true, attributeName = "id", isIdentifier = true),
                    col("customer_id", SchemaType.TEXT, isMapped = true, attributeName = "customerId"),
                ),
            ),
        )

        verify(relationshipDefinitionRepository).save(any())
        assertEquals(1, response.relationshipsCreated.size)
        assertTrue(response.pendingRelationships.isEmpty())
    }

    @Test
    fun saveStoresFkMetadataOnlyWhenTargetTableUnpublished() {
        stubIntrospection(
            table = TableSchema(
                "orders",
                listOf(
                    ColumnSchema("id", "uuid", false),
                    ColumnSchema("customer_id", "uuid", false),
                ),
            ),
            fks = listOf(
                ForeignKeyMetadata("orders", "customer_id", "customers", "id", isComposite = false),
            ),
        )
        whenever(fieldMappingRepository.findByConnectionIdAndTableName(connectionId, "orders")).thenReturn(emptyList())
        whenever(tableMappingRepository.findByConnectionIdAndTableName(connectionId, "orders")).thenReturn(null)
        whenever(tableMappingRepository.findByConnectionIdAndTableName(connectionId, "customers")).thenReturn(null)

        val response = service.saveMapping(
            workspaceId, connectionId, "orders",
            baseRequest().copy(
                columns = listOf(
                    col("id", SchemaType.ID, isMapped = true, attributeName = "id", isIdentifier = true),
                    col("customer_id", SchemaType.TEXT, isMapped = true, attributeName = "customerId"),
                ),
            ),
        )

        verify(relationshipDefinitionRepository, never()).save(any())
        assertEquals(1, response.pendingRelationships.size)
        assertEquals("customers", response.pendingRelationships.first().targetTable)

        // FK metadata persists on the field mapping row itself.
        val captor = argumentCaptor<DataConnectorFieldMappingEntity>()
        verify(fieldMappingRepository, org.mockito.kotlin.atLeastOnce()).save(captor.capture())
        val customerIdRow = captor.allValues.first { it.columnName == "customer_id" }
        assertEquals("customers", customerIdRow.fkTargetTable)
        assertEquals("id", customerIdRow.fkTargetColumn)
        assertTrue(customerIdRow.isForeignKey)
    }

    @Test
    fun saveSkipsCompositeFkWithUnsupportedNote() {
        stubIntrospection(
            table = TableSchema(
                "orders",
                listOf(
                    ColumnSchema("id", "uuid", false),
                    ColumnSchema("customer_id", "uuid", false),
                ),
            ),
            fks = listOf(
                ForeignKeyMetadata("orders", "customer_id", "customers", "id", isComposite = true),
            ),
        )
        whenever(fieldMappingRepository.findByConnectionIdAndTableName(connectionId, "orders")).thenReturn(emptyList())
        whenever(tableMappingRepository.findByConnectionIdAndTableName(connectionId, "orders")).thenReturn(null)

        val response = service.saveMapping(
            workspaceId, connectionId, "orders",
            baseRequest().copy(
                columns = listOf(
                    col("id", SchemaType.ID, isMapped = true, attributeName = "id", isIdentifier = true),
                    col("customer_id", SchemaType.TEXT, isMapped = true, attributeName = "customerId"),
                ),
            ),
        )

        verify(relationshipDefinitionRepository, never()).save(any())
        assertEquals(1, response.compositeFkSkipped.size)
        assertTrue(response.compositeFkSkipped.first().contains("customer_id"))
    }

    @Test
    fun saveTransitionsTableMappingPublishedTrue() {
        stubIntrospection(customersTable())
        whenever(fieldMappingRepository.findByConnectionIdAndTableName(connectionId, "customers")).thenReturn(emptyList())
        whenever(tableMappingRepository.findByConnectionIdAndTableName(connectionId, "customers")).thenReturn(null)

        service.saveMapping(workspaceId, connectionId, "customers", baseRequest())

        val captor = argumentCaptor<DataConnectorTableMappingEntity>()
        verify(tableMappingRepository, org.mockito.kotlin.atLeastOnce()).save(captor.capture())
        val lastSave = captor.allValues.last()
        assertTrue(lastSave.published)
        assertNotNull(lastSave.entityTypeId)
    }

    @Test
    fun saveSurfacesCursorIndexWarningInResponse() {
        stubIntrospection(
            table = TableSchema(
                "customers",
                listOf(
                    ColumnSchema("id", "uuid", false),
                    ColumnSchema("updated_at", "timestamp", false),
                ),
            ),
        )
        whenever(fieldMappingRepository.findByConnectionIdAndTableName(connectionId, "customers")).thenReturn(emptyList())
        whenever(tableMappingRepository.findByConnectionIdAndTableName(connectionId, "customers")).thenReturn(null)
        whenever(cursorIndexProbe.isIndexed(eq(connectionId), any(), any(), eq("customers"), eq("updated_at"))).thenReturn(false)

        val response = service.saveMapping(
            workspaceId, connectionId, "customers",
            baseRequest().copy(
                columns = listOf(
                    col("id", SchemaType.ID, isMapped = true, attributeName = "id", isIdentifier = true),
                    col("updated_at", SchemaType.DATETIME, isMapped = true, isSyncCursor = true, attributeName = "updatedAt"),
                ),
            ),
        )

        assertNotNull(response.cursorIndexWarning)
        assertEquals("updated_at", response.cursorIndexWarning!!.column)
    }

    @Test
    fun reSavePropagatesAddedColumnsToEntityType() {
        stubIntrospection(
            table = TableSchema(
                "customers",
                listOf(
                    ColumnSchema("id", "uuid", false),
                    ColumnSchema("email", "text", false),
                    ColumnSchema("new_col", "text", true),
                ),
            ),
        )
        val existingEntityTypeId = UUID.randomUUID()
        val existingTable = DataConnectorTableMappingEntityFactory.create(
            workspaceId = workspaceId, connectionId = connectionId, tableName = "customers",
            entityTypeId = existingEntityTypeId, published = true,
        )
        whenever(tableMappingRepository.findByConnectionIdAndTableName(connectionId, "customers"))
            .thenReturn(existingTable)
        whenever(fieldMappingRepository.findByConnectionIdAndTableName(connectionId, "customers")).thenReturn(
            listOf(
                DataConnectorFieldMappingEntityFactory.create(
                    workspaceId = workspaceId, connectionId = connectionId, tableName = "customers",
                    columnName = "id", pgDataType = "uuid", isPrimaryKey = true, attributeName = "id",
                ),
                DataConnectorFieldMappingEntityFactory.create(
                    workspaceId = workspaceId, connectionId = connectionId, tableName = "customers",
                    columnName = "email", pgDataType = "text", attributeName = "email",
                ),
            ),
        )

        val existingEntityType = EntityFactory.createEntityType(
            id = existingEntityTypeId,
            key = "connector_existing",
            displayNameSingular = "customers",
            displayNamePlural = "customers",
            sourceType = SourceType.CONNECTOR,
            readonly = true,
            workspaceId = workspaceId,
            identifierKey = UUID.randomUUID(),
            schema = cranium.core.models.common.validation.Schema(
                type = cranium.core.enums.core.DataType.OBJECT,
                key = SchemaType.OBJECT,
                properties = emptyMap(),
            ),
        )
        whenever(entityTypeRepository.findById(existingEntityTypeId)).thenReturn(Optional.of(existingEntityType))

        service.saveMapping(
            workspaceId, connectionId, "customers",
            baseRequest().copy(
                columns = listOf(
                    col("id", SchemaType.ID, isMapped = true, attributeName = "id", isIdentifier = true),
                    col("email", SchemaType.EMAIL, isMapped = true, attributeName = "email"),
                    col("new_col", SchemaType.TEXT, isMapped = true, attributeName = "newCol"),
                ),
            ),
        )

        val captor = argumentCaptor<EntityTypeEntity>()
        verify(entityTypeRepository).save(captor.capture())
        assertEquals(3, captor.firstValue.schema.properties?.size)
    }

    @Test
    fun reSaveMarksDroppedFieldsStaleAndKeepsExistingAttributes() {
        stubIntrospection(
            table = TableSchema(
                "customers",
                listOf(ColumnSchema("id", "uuid", false)),
            ),
        )
        val existingTable = DataConnectorTableMappingEntityFactory.create(
            workspaceId = workspaceId, connectionId = connectionId, tableName = "customers", published = true,
        )
        whenever(tableMappingRepository.findByConnectionIdAndTableName(connectionId, "customers"))
            .thenReturn(existingTable)
        val droppedField = DataConnectorFieldMappingEntityFactory.create(
            workspaceId = workspaceId, connectionId = connectionId, tableName = "customers",
            columnName = "deleted_col", pgDataType = "text", attributeName = "deletedCol",
        )
        whenever(fieldMappingRepository.findByConnectionIdAndTableName(connectionId, "customers"))
            .thenReturn(listOf(droppedField))

        service.saveMapping(
            workspaceId, connectionId, "customers",
            baseRequest().copy(
                columns = listOf(col("id", SchemaType.ID, isMapped = true, attributeName = "id", isIdentifier = true)),
            ),
        )

        verify(fieldMappingRepository).save(argThat<DataConnectorFieldMappingEntity> {
            columnName == "deleted_col" && stale
        })
    }

    @Test
    fun saveScopedToWorkspaceViaPreAuthorize() {
        stubIntrospection(customersTable())
        assertThrows<AccessDeniedException> {
            service.saveMapping(otherWorkspaceId, connectionId, "customers", baseRequest())
        }
    }

    // ------ Fixtures ------

    private fun customersTable(): TableSchema = TableSchema(
        name = "customers",
        columns = listOf(
            ColumnSchema("id", "uuid", false),
            ColumnSchema("email", "text", false),
            ColumnSchema("internal_flag", "text", true),
        ),
    )

    private fun stubIntrospection(
        table: TableSchema,
        fks: List<ForeignKeyMetadata> = emptyList(),
    ) {
        val result = IntrospectionResult(
            schema = SchemaIntrospectionResult(tables = listOf(table)),
            foreignKeys = fks,
        )
        whenever(postgresAdapter.introspectWithFkMetadata(any<PostgresCallContext>())).thenReturn(result)
    }

    private fun baseRequest() = SaveDataConnectorMappingRequest(
        lifecycleDomain = LifecycleDomain.UNCATEGORIZED,
        semanticGroup = SemanticGroup.UNCATEGORIZED,
        columns = listOf(
            col("id", SchemaType.ID, isMapped = true, attributeName = "id", isIdentifier = true),
            col("email", SchemaType.EMAIL, isMapped = true, attributeName = "email"),
            col("internal_flag", SchemaType.TEXT, isMapped = false),
        ),
    )

    private fun col(
        columnName: String,
        schemaType: SchemaType,
        isMapped: Boolean = false,
        isIdentifier: Boolean = false,
        isSyncCursor: Boolean = false,
        attributeName: String? = null,
    ) = SaveDataConnectorFieldMappingRequest(
        columnName = columnName,
        attributeName = attributeName,
        schemaType = schemaType,
        isIdentifier = isIdentifier,
        isSyncCursor = isSyncCursor,
        isMapped = isMapped,
    )

    private fun injectId(entity: DataConnectorFieldMappingEntity, id: UUID): DataConnectorFieldMappingEntity =
        DataConnectorFieldMappingEntity(
            id = id,
            workspaceId = entity.workspaceId,
            connectionId = entity.connectionId,
            tableName = entity.tableName,
            columnName = entity.columnName,
            pgDataType = entity.pgDataType,
            nullable = entity.nullable,
            isPrimaryKey = entity.isPrimaryKey,
            isForeignKey = entity.isForeignKey,
            fkTargetTable = entity.fkTargetTable,
            fkTargetColumn = entity.fkTargetColumn,
            attributeName = entity.attributeName,
            schemaType = entity.schemaType,
            isIdentifier = entity.isIdentifier,
            isSyncCursor = entity.isSyncCursor,
            isMapped = entity.isMapped,
            stale = entity.stale,
        )

    private fun injectIdTable(entity: DataConnectorTableMappingEntity, id: UUID): DataConnectorTableMappingEntity =
        DataConnectorTableMappingEntity(
            id = id,
            workspaceId = entity.workspaceId,
            connectionId = entity.connectionId,
            tableName = entity.tableName,
            lifecycleDomain = entity.lifecycleDomain,
            semanticGroup = entity.semanticGroup,
            entityTypeId = entity.entityTypeId,
            schemaHash = entity.schemaHash,
            lastIntrospectedAt = entity.lastIntrospectedAt,
            published = entity.published,
        )

    @Suppress("unused") private fun nowish(): ZonedDateTime = ZonedDateTime.now()
    @Suppress("unused") private fun anyOrNullStub(): Any = anyOrNull<Any>() ?: Unit
}
