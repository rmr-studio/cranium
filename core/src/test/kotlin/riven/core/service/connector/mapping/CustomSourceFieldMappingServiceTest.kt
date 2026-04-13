package riven.core.service.connector.mapping

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.connector.CustomSourceFieldMappingEntity
import riven.core.entity.connector.CustomSourceTableMappingEntity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.entity.entity.RelationshipDefinitionEntity
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.connector.SslMode
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.integration.SourceType
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.exceptions.connector.MappingValidationException
import riven.core.models.connector.CredentialPayload
import riven.core.models.connector.request.SaveCustomSourceFieldMappingRequest
import riven.core.models.connector.request.SaveCustomSourceMappingRequest
import riven.core.models.ingestion.adapter.ColumnSchema
import riven.core.models.ingestion.adapter.SchemaIntrospectionResult
import riven.core.models.ingestion.adapter.TableSchema
import riven.core.repository.connector.CustomSourceConnectionRepository
import riven.core.repository.connector.CustomSourceFieldMappingRepository
import riven.core.repository.connector.CustomSourceTableMappingRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.RelationshipDefinitionRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.connector.CredentialEncryptionService
import riven.core.service.connector.postgres.ForeignKeyMetadata
import riven.core.service.connector.postgres.IntrospectionResult
import riven.core.service.connector.postgres.PostgresAdapter
import riven.core.service.ingestion.adapter.PostgresCallContext
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import riven.core.service.util.factory.CustomSourceFieldMappingEntityFactory
import riven.core.service.util.factory.CustomSourceTableMappingEntityFactory
import riven.core.service.util.factory.customsource.DataConnectorConnectionEntityFactory
import java.time.ZonedDateTime
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for [CustomSourceFieldMappingService] (Phase 3 plan 03-03).
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
        CustomSourceFieldMappingServiceTest.TestConfig::class,
        CustomSourceFieldMappingService::class,
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
class CustomSourceFieldMappingServiceTest {

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
    @MockitoBean private lateinit var connectionRepository: CustomSourceConnectionRepository
    @MockitoBean private lateinit var tableMappingRepository: CustomSourceTableMappingRepository
    @MockitoBean private lateinit var fieldMappingRepository: CustomSourceFieldMappingRepository
    @MockitoBean private lateinit var entityTypeRepository: EntityTypeRepository
    @MockitoBean private lateinit var relationshipDefinitionRepository: RelationshipDefinitionRepository
    @MockitoBean private lateinit var activityService: ActivityService
    @MockitoBean private lateinit var authTokenService: AuthTokenService
    @MockitoBean private lateinit var logger: KLogger

    @Autowired private lateinit var service: CustomSourceFieldMappingService
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
            val arg = inv.arguments[0] as CustomSourceFieldMappingEntity
            if (arg.id == null) injectId(arg, UUID.randomUUID()) else arg
        }
        whenever(tableMappingRepository.save(any())).thenAnswer { inv ->
            val arg = inv.arguments[0] as CustomSourceTableMappingEntity
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
                    col("id", SchemaType.ID, isMapped = true, attributeName = "id"),
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
        val publishedTarget = CustomSourceTableMappingEntityFactory.create(
            workspaceId = workspaceId, connectionId = connectionId, tableName = "customers", published = true,
            entityTypeId = UUID.randomUUID(),
        )
        whenever(tableMappingRepository.findByConnectionIdAndTableName(connectionId, "customers"))
            .thenReturn(publishedTarget)

        val response = service.saveMapping(
            workspaceId, connectionId, "orders",
            baseRequest().copy(
                columns = listOf(
                    col("id", SchemaType.ID, isMapped = true, attributeName = "id"),
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
                    col("id", SchemaType.ID, isMapped = true, attributeName = "id"),
                    col("customer_id", SchemaType.TEXT, isMapped = true, attributeName = "customerId"),
                ),
            ),
        )

        verify(relationshipDefinitionRepository, never()).save(any())
        assertEquals(1, response.pendingRelationships.size)
        assertEquals("customers", response.pendingRelationships.first().targetTable)

        // FK metadata persists on the field mapping row itself.
        val captor = argumentCaptor<CustomSourceFieldMappingEntity>()
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
                    col("id", SchemaType.ID, isMapped = true, attributeName = "id"),
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

        val captor = argumentCaptor<CustomSourceTableMappingEntity>()
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
                    col("id", SchemaType.ID, isMapped = true, attributeName = "id"),
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
        val existingTable = CustomSourceTableMappingEntityFactory.create(
            workspaceId = workspaceId, connectionId = connectionId, tableName = "customers",
            entityTypeId = existingEntityTypeId, published = true,
        )
        whenever(tableMappingRepository.findByConnectionIdAndTableName(connectionId, "customers"))
            .thenReturn(existingTable)
        whenever(fieldMappingRepository.findByConnectionIdAndTableName(connectionId, "customers")).thenReturn(
            listOf(
                CustomSourceFieldMappingEntityFactory.create(
                    workspaceId = workspaceId, connectionId = connectionId, tableName = "customers",
                    columnName = "id", pgDataType = "uuid", isPrimaryKey = true, attributeName = "id",
                ),
                CustomSourceFieldMappingEntityFactory.create(
                    workspaceId = workspaceId, connectionId = connectionId, tableName = "customers",
                    columnName = "email", pgDataType = "text", attributeName = "email",
                ),
            ),
        )

        val existingEntityType = EntityTypeEntity(
            id = existingEntityTypeId,
            key = "connector_existing",
            displayNameSingular = "customers",
            displayNamePlural = "customers",
            sourceType = SourceType.CONNECTOR,
            readonly = true,
            identifierKey = UUID.randomUUID(),
            workspaceId = workspaceId,
            schema = riven.core.models.common.validation.Schema(
                type = riven.core.enums.core.DataType.OBJECT,
                key = SchemaType.OBJECT,
                properties = emptyMap(),
            ),
        )
        whenever(entityTypeRepository.findById(existingEntityTypeId)).thenReturn(Optional.of(existingEntityType))

        service.saveMapping(
            workspaceId, connectionId, "customers",
            baseRequest().copy(
                columns = listOf(
                    col("id", SchemaType.ID, isMapped = true, attributeName = "id"),
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
        val existingTable = CustomSourceTableMappingEntityFactory.create(
            workspaceId = workspaceId, connectionId = connectionId, tableName = "customers", published = true,
        )
        whenever(tableMappingRepository.findByConnectionIdAndTableName(connectionId, "customers"))
            .thenReturn(existingTable)
        val droppedField = CustomSourceFieldMappingEntityFactory.create(
            workspaceId = workspaceId, connectionId = connectionId, tableName = "customers",
            columnName = "deleted_col", pgDataType = "text", attributeName = "deletedCol",
        )
        whenever(fieldMappingRepository.findByConnectionIdAndTableName(connectionId, "customers"))
            .thenReturn(listOf(droppedField))

        service.saveMapping(
            workspaceId, connectionId, "customers",
            baseRequest().copy(
                columns = listOf(col("id", SchemaType.ID, isMapped = true, attributeName = "id")),
            ),
        )

        verify(fieldMappingRepository).save(argThat<CustomSourceFieldMappingEntity> {
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

    private fun baseRequest() = SaveCustomSourceMappingRequest(
        lifecycleDomain = LifecycleDomain.UNCATEGORIZED,
        semanticGroup = SemanticGroup.UNCATEGORIZED,
        columns = listOf(
            col("id", SchemaType.ID, isMapped = true, attributeName = "id"),
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
    ) = SaveCustomSourceFieldMappingRequest(
        columnName = columnName,
        attributeName = attributeName,
        schemaType = schemaType,
        isIdentifier = isIdentifier,
        isSyncCursor = isSyncCursor,
        isMapped = isMapped,
    )

    private fun injectId(entity: CustomSourceFieldMappingEntity, id: UUID): CustomSourceFieldMappingEntity =
        CustomSourceFieldMappingEntity(
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

    private fun injectIdTable(entity: CustomSourceTableMappingEntity, id: UUID): CustomSourceTableMappingEntity =
        CustomSourceTableMappingEntity(
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
