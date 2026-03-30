package riven.core.service.integration.sync

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionTemplate
import riven.core.entity.integration.IntegrationConnectionEntity
import riven.core.entity.integration.IntegrationSyncStateEntity
import riven.core.enums.catalog.ManifestType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.integration.ConnectionStatus
import riven.core.enums.integration.SourceType
import riven.core.enums.integration.SyncStatus
import riven.core.models.common.validation.Schema
import riven.core.models.entity.configuration.ColumnConfiguration
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.models.integration.NangoRecord
import riven.core.models.integration.NangoRecordAction
import riven.core.models.integration.NangoRecordMetadata
import riven.core.models.integration.NangoRecordsPage
import riven.core.models.integration.mapping.FieldCoverage
import riven.core.models.integration.mapping.MappingResult
import riven.core.models.integration.mapping.MappingError
import riven.core.models.integration.sync.IntegrationSyncWorkflowInput
import riven.core.models.integration.sync.SyncProcessingResult
import riven.core.repository.catalog.CatalogEntityTypeRepository
import riven.core.repository.catalog.CatalogFieldMappingRepository
import riven.core.repository.catalog.ManifestCatalogRepository
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.RelationshipDefinitionRepository
import riven.core.repository.integration.IntegrationConnectionRepository
import riven.core.repository.integration.IntegrationDefinitionRepository
import riven.core.repository.integration.IntegrationSyncStateRepository
import riven.core.service.entity.EntityAttributeService
import riven.core.service.integration.IntegrationHealthService
import riven.core.service.integration.NangoClientWrapper
import riven.core.service.integration.mapping.SchemaMappingService
import riven.core.service.util.factory.catalog.CatalogFactory
import riven.core.service.util.factory.entity.EntityFactory
import riven.core.service.util.factory.integration.IntegrationFactory
import java.util.*

/**
 * Unit tests for [IntegrationSyncActivitiesImpl].
 *
 * Covers all SYNC-01 through SYNC-07 behaviors:
 * - SYNC-01: Paginated fetch with heartbeating
 * - SYNC-02: Batch dedup via IN-clause query
 * - SYNC-03: ADDED record idempotency
 * - SYNC-04: UPDATED record semantics
 * - SYNC-05: DELETED record soft-delete
 * - SYNC-06: Per-record error isolation
 * - SYNC-07: Two-pass upsert then relationship resolution
 *
 * Note: No @WithUserPersona — activity methods are called by Temporal, not Spring AOP.
 * Activity methods have no @PreAuthorize annotations.
 */
@SpringBootTest(
    classes = [
        IntegrationSyncActivitiesImplTest.TestConfig::class,
    ]
)
class IntegrationSyncActivitiesImplTest {

    @Configuration
    class TestConfig {
        @Bean
        fun integrationSyncActivities(
            connectionRepository: IntegrationConnectionRepository,
            syncStateRepository: IntegrationSyncStateRepository,
            nangoClientWrapper: NangoClientWrapper,
            schemaMappingService: SchemaMappingService,
            entityRepository: EntityRepository,
            entityAttributeService: EntityAttributeService,
            entityRelationshipRepository: EntityRelationshipRepository,
            relationshipDefinitionRepository: RelationshipDefinitionRepository,
            definitionRepository: IntegrationDefinitionRepository,
            manifestCatalogRepository: ManifestCatalogRepository,
            catalogFieldMappingRepository: CatalogFieldMappingRepository,
            catalogEntityTypeRepository: CatalogEntityTypeRepository,
            entityTypeRepository: EntityTypeRepository,
            integrationHealthService: IntegrationHealthService,
            entityProjectionService: riven.core.service.ingestion.EntityProjectionService,
            transactionTemplate: TransactionTemplate,
            logger: KLogger,
        ): IntegrationSyncActivitiesImpl {
            return object : IntegrationSyncActivitiesImpl(
                connectionRepository = connectionRepository,
                syncStateRepository = syncStateRepository,
                nangoClientWrapper = nangoClientWrapper,
                schemaMappingService = schemaMappingService,
                entityRepository = entityRepository,
                entityAttributeService = entityAttributeService,
                entityRelationshipRepository = entityRelationshipRepository,
                relationshipDefinitionRepository = relationshipDefinitionRepository,
                definitionRepository = definitionRepository,
                manifestCatalogRepository = manifestCatalogRepository,
                catalogFieldMappingRepository = catalogFieldMappingRepository,
                catalogEntityTypeRepository = catalogEntityTypeRepository,
                entityTypeRepository = entityTypeRepository,
                integrationHealthService = integrationHealthService,
                entityProjectionService = entityProjectionService,
                transactionTemplate = transactionTemplate,
                logger = logger,
            ) {
                // Override heartbeat to be a no-op in tests (Temporal static context not available)
                override fun heartbeat(cursor: String?) { /* no-op in tests */ }
            }
        }
    }

    // ------ Mocked Dependencies ------

    @MockitoBean
    private lateinit var connectionRepository: IntegrationConnectionRepository

    @MockitoBean
    private lateinit var syncStateRepository: IntegrationSyncStateRepository

    @MockitoBean
    private lateinit var nangoClientWrapper: NangoClientWrapper

    @MockitoBean
    private lateinit var schemaMappingService: SchemaMappingService

    @MockitoBean
    private lateinit var entityRepository: EntityRepository

    @MockitoBean
    private lateinit var entityAttributeService: EntityAttributeService

    @MockitoBean
    private lateinit var entityRelationshipRepository: EntityRelationshipRepository

    @MockitoBean
    private lateinit var relationshipDefinitionRepository: RelationshipDefinitionRepository

    @MockitoBean
    private lateinit var definitionRepository: IntegrationDefinitionRepository

    @MockitoBean
    private lateinit var manifestCatalogRepository: ManifestCatalogRepository

    @MockitoBean
    private lateinit var catalogFieldMappingRepository: CatalogFieldMappingRepository

    @MockitoBean
    private lateinit var catalogEntityTypeRepository: CatalogEntityTypeRepository

    @MockitoBean
    private lateinit var entityTypeRepository: EntityTypeRepository

    @MockitoBean
    private lateinit var integrationHealthService: IntegrationHealthService

    @MockitoBean
    private lateinit var entityProjectionService: riven.core.service.ingestion.EntityProjectionService

    @MockitoBean
    private lateinit var transactionTemplate: TransactionTemplate

    @MockitoBean
    private lateinit var logger: KLogger

    @Autowired
    private lateinit var activitiesImpl: IntegrationSyncActivitiesImpl

    // ------ Shared Test Data ------

    private val connectionId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val workspaceId: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val integrationId: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val entityTypeId: UUID = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val attributeUuid: UUID = UUID.fromString("55555555-5555-5555-5555-555555555555")
    private val entityId: UUID = UUID.fromString("66666666-6666-6666-6666-666666666666")
    private val manifestId: UUID = UUID.fromString("77777777-7777-7777-7777-777777777777")

    private val model = "hubspot-contact"
    private val externalId = "ext-001"
    private val nangoConnectionId = "nango-conn-001"
    private val providerConfigKey = "hubspot"

    private val workflowInput = IntegrationSyncWorkflowInput(
        connectionId = connectionId,
        workspaceId = workspaceId,
        integrationId = integrationId,
        nangoConnectionId = nangoConnectionId,
        providerConfigKey = providerConfigKey,
        model = model,
        modifiedAfter = null,
    )

    // ------ Setup Helpers ------

    private fun buildEntityType(
        id: UUID = entityTypeId,
        key: String = model,
        attributeId: UUID = attributeUuid,
    ) = EntityFactory.createEntityType(
        id = id,
        key = key,
        workspaceId = workspaceId,
        sourceType = SourceType.INTEGRATION,
        sourceIntegrationId = integrationId,
        identifierKey = attributeId,
        schema = Schema(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = mapOf(
                attributeId to Schema(key = SchemaType.TEXT, type = DataType.STRING)
            )
        ),
        columnConfiguration = ColumnConfiguration(order = listOf(attributeId)),
    )

    private fun buildDefinition() = IntegrationFactory.createIntegrationDefinition(
        id = integrationId,
        slug = "hubspot",
    )

    private fun buildNangoRecord(
        externalId: String = this.externalId,
        action: NangoRecordAction = NangoRecordAction.ADDED,
        extraPayload: Map<String, Any?> = mapOf("email" to "test@example.com"),
    ) = NangoRecord(
        nangoMetadata = NangoRecordMetadata(
            lastAction = action,
            cursor = "cursor-001",
        ),
        payload = (mutableMapOf("id" to externalId as Any?) + extraPayload).toMutableMap(),
    )

    private fun buildSuccessMappingResult(attrUuid: UUID = attributeUuid) = MappingResult(
        attributes = mapOf(attrUuid.toString() to EntityAttributePrimitivePayload(
            value = "test@example.com",
            schemaType = SchemaType.TEXT,
        )),
        warnings = emptyList(),
        errors = emptyList(),
        fieldCoverage = FieldCoverage(mapped = 1, total = 1, ratio = 1.0),
    )

    /** Sets up the full model context resolution chain. */
    private fun setupModelContextMocks(
        entityType: riven.core.entity.entity.EntityTypeEntity = buildEntityType(),
    ) {
        whenever(definitionRepository.findById(integrationId))
            .thenReturn(Optional.of(buildDefinition()))
        whenever(manifestCatalogRepository.findByKey("hubspot"))
            .thenReturn(listOf(CatalogFactory.createManifestEntity(
                type = ManifestType.INTEGRATION,
                id = manifestId,
                key = "hubspot",
                name = "HubSpot",
            )))
        whenever(catalogFieldMappingRepository.findByManifestIdAndEntityTypeKey(manifestId, model))
            .thenReturn(CatalogFactory.createFieldMappingEntity(
                manifestId = manifestId,
                entityTypeKey = model,
                mappings = mapOf(
                    "email" to mapOf<String, Any>("source" to "email", "transform" to mapOf("type" to "direct"))
                ),
            ))
        whenever(entityTypeRepository.findBySourceIntegrationIdAndWorkspaceId(integrationId, workspaceId))
            .thenReturn(listOf(entityType))
        whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, model))
            .thenReturn(CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = model,
                displayNameSingular = "Contact",
                displayNamePlural = "Contacts",
                schema = linkedMapOf("email" to mapOf("key" to "TEXT", "label" to "Email", "type" to "string")),
            ))
        whenever(syncStateRepository.findByIntegrationConnectionIdAndEntityTypeId(any(), any()))
            .thenReturn(null)
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(any(), any()))
            .thenReturn(emptyList())
    }

    @BeforeEach
    fun setUp() {
        reset(
            connectionRepository, syncStateRepository, nangoClientWrapper, schemaMappingService,
            entityRepository, entityAttributeService, entityRelationshipRepository,
            relationshipDefinitionRepository, definitionRepository, manifestCatalogRepository,
            catalogFieldMappingRepository, catalogEntityTypeRepository, entityTypeRepository,
            integrationHealthService, entityProjectionService, transactionTemplate,
        )

        whenever(transactionTemplate.execute<Any>(any())).thenAnswer { invocation ->
            val callback = invocation.arguments[0] as org.springframework.transaction.support.TransactionCallback<*>
            callback.doInTransaction(org.mockito.Mockito.mock(org.springframework.transaction.TransactionStatus::class.java))
        }

        // Default entity save: return entity with ID
        whenever(entityRepository.save(any())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as riven.core.entity.entity.EntityEntity
            if (entity.id == null) entity.copy(id = entityId) else entity
        }
        whenever(connectionRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(syncStateRepository.save(any())).thenAnswer { it.arguments[0] }
    }

    // ------ TransitionToSyncing Tests ------

    @Nested
    inner class TransitionToSyncingTests {

        @Test
        fun `connected transitions to syncing`() {
            val connection = IntegrationFactory.createIntegrationConnection(
                id = connectionId,
                workspaceId = workspaceId,
                status = ConnectionStatus.CONNECTED,
            )
            whenever(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection))

            activitiesImpl.transitionToSyncing(connectionId, workspaceId)

            val captor = argumentCaptor<IntegrationConnectionEntity>()
            verify(connectionRepository).save(captor.capture())
            assertEquals(ConnectionStatus.SYNCING, captor.firstValue.status)
        }

        @Test
        fun `already syncing skips silently`() {
            val connection = IntegrationFactory.createIntegrationConnection(
                id = connectionId,
                workspaceId = workspaceId,
                status = ConnectionStatus.SYNCING,
            )
            whenever(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection))

            assertDoesNotThrow { activitiesImpl.transitionToSyncing(connectionId, workspaceId) }

            verify(connectionRepository, never()).save(any())
        }

        @Test
        fun `disconnected state cannot transition — skips with warning`() {
            val connection = IntegrationFactory.createIntegrationConnection(
                id = connectionId,
                workspaceId = workspaceId,
                status = ConnectionStatus.DISCONNECTED,
            )
            whenever(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection))

            assertDoesNotThrow { activitiesImpl.transitionToSyncing(connectionId, workspaceId) }

            verify(connectionRepository, never()).save(any())
        }

        @Test
        fun `connection not found returns without error`() {
            whenever(connectionRepository.findById(connectionId)).thenReturn(Optional.empty())

            assertDoesNotThrow { activitiesImpl.transitionToSyncing(connectionId, workspaceId) }

            verify(connectionRepository, never()).save(any())
        }

        @Test
        fun `connection with mismatched workspaceId skips without saving`() {
            val otherWorkspaceId = UUID.randomUUID()
            val connection = IntegrationFactory.createIntegrationConnection(
                id = connectionId,
                workspaceId = otherWorkspaceId,
                status = ConnectionStatus.CONNECTED,
            )
            whenever(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection))

            assertDoesNotThrow { activitiesImpl.transitionToSyncing(connectionId, workspaceId) }

            verify(connectionRepository, never()).save(any())
        }
    }

    // ------ FetchAndProcessRecords Tests ------

    @Nested
    inner class FetchAndProcessRecordsTests {

        @BeforeEach
        fun setupFetchContext() {
            setupModelContextMocks()
        }

        @Test
        fun `added record creates new entity with integration source type`() {
            val record = buildNangoRecord(action = NangoRecordAction.ADDED)
            whenever(nangoClientWrapper.fetchRecords(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(NangoRecordsPage(records = listOf(record), nextCursor = null))
            whenever(entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(any(), any(), any()))
                .thenReturn(emptyList())
            whenever(schemaMappingService.mapPayload(any(), any(), any()))
                .thenReturn(buildSuccessMappingResult())

            val result = activitiesImpl.fetchAndProcessRecords(workflowInput)

            val captor = argumentCaptor<riven.core.entity.entity.EntityEntity>()
            verify(entityRepository).save(captor.capture())
            val saved = captor.firstValue
            assertEquals(SourceType.INTEGRATION, saved.sourceType)
            assertEquals(externalId, saved.sourceExternalId)
            assertEquals(integrationId, saved.sourceIntegrationId)
            assertEquals(1, result.recordsSynced)
            assertTrue(result.success)
        }

        @Test
        fun `added record with existing entity updates instead of creating`() {
            val existing = EntityFactory.createEntityEntity(
                id = entityId,
                workspaceId = workspaceId,
                typeId = entityTypeId,
                sourceType = SourceType.INTEGRATION,
                sourceIntegrationId = integrationId,
                sourceExternalId = externalId,
            )
            val record = buildNangoRecord(action = NangoRecordAction.ADDED)
            whenever(nangoClientWrapper.fetchRecords(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(NangoRecordsPage(records = listOf(record), nextCursor = null))
            whenever(entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(any(), any(), any()))
                .thenReturn(listOf(existing))
            whenever(schemaMappingService.mapPayload(any(), any(), any()))
                .thenReturn(buildSuccessMappingResult())

            val result = activitiesImpl.fetchAndProcessRecords(workflowInput)

            // Should update the existing entity (save called with existing entity)
            val captor = argumentCaptor<riven.core.entity.entity.EntityEntity>()
            verify(entityRepository).save(captor.capture())
            assertEquals(entityId, captor.firstValue.id) // Updates existing, doesn't create new
            assertEquals(1, result.recordsSynced)
        }

        @Test
        fun `updated record with existing entity replaces attributes`() {
            val existing = EntityFactory.createEntityEntity(
                id = entityId,
                workspaceId = workspaceId,
                typeId = entityTypeId,
                sourceType = SourceType.INTEGRATION,
                sourceExternalId = externalId,
            )
            val record = buildNangoRecord(action = NangoRecordAction.UPDATED)
            whenever(nangoClientWrapper.fetchRecords(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(NangoRecordsPage(records = listOf(record), nextCursor = null))
            whenever(entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(any(), any(), any()))
                .thenReturn(listOf(existing))
            whenever(schemaMappingService.mapPayload(any(), any(), any()))
                .thenReturn(buildSuccessMappingResult())

            val result = activitiesImpl.fetchAndProcessRecords(workflowInput)

            verify(entityAttributeService).saveAttributes(eq(entityId), eq(workspaceId), eq(entityTypeId), any())
            assertEquals(1, result.recordsSynced)
        }

        @Test
        fun `updated record without existing entity creates new entity`() {
            val record = buildNangoRecord(action = NangoRecordAction.UPDATED)
            whenever(nangoClientWrapper.fetchRecords(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(NangoRecordsPage(records = listOf(record), nextCursor = null))
            whenever(entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(any(), any(), any()))
                .thenReturn(emptyList())
            whenever(schemaMappingService.mapPayload(any(), any(), any()))
                .thenReturn(buildSuccessMappingResult())

            val result = activitiesImpl.fetchAndProcessRecords(workflowInput)

            val captor = argumentCaptor<riven.core.entity.entity.EntityEntity>()
            verify(entityRepository).save(captor.capture())
            assertEquals(externalId, captor.firstValue.sourceExternalId)
            assertEquals(1, result.recordsSynced)
        }

        @Test
        fun `deleted record soft-deletes existing entity`() {
            val existing = EntityFactory.createEntityEntity(
                id = entityId,
                workspaceId = workspaceId,
                typeId = entityTypeId,
                sourceType = SourceType.INTEGRATION,
                sourceExternalId = externalId,
            )
            val record = buildNangoRecord(action = NangoRecordAction.DELETED)
            whenever(nangoClientWrapper.fetchRecords(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(NangoRecordsPage(records = listOf(record), nextCursor = null))
            whenever(entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(any(), any(), any()))
                .thenReturn(listOf(existing))

            val result = activitiesImpl.fetchAndProcessRecords(workflowInput)

            val captor = argumentCaptor<riven.core.entity.entity.EntityEntity>()
            verify(entityRepository).save(captor.capture())
            assertTrue(captor.firstValue.deleted)
            assertNotNull(captor.firstValue.deletedAt)
            assertEquals(1, result.recordsSynced)
        }

        @Test
        fun `deleted record without existing entity is no-op`() {
            val record = buildNangoRecord(action = NangoRecordAction.DELETED)
            whenever(nangoClientWrapper.fetchRecords(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(NangoRecordsPage(records = listOf(record), nextCursor = null))
            whenever(entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(any(), any(), any()))
                .thenReturn(emptyList())

            val result = activitiesImpl.fetchAndProcessRecords(workflowInput)

            verify(entityRepository, never()).save(any())
            assertEquals(1, result.recordsSynced) // DELETED no-op still counts as processed
        }

        @Test
        fun `mapping errors skip record and count as failed`() {
            val record = buildNangoRecord(action = NangoRecordAction.ADDED)
            val failedMapping = MappingResult(
                attributes = emptyMap(),
                warnings = emptyList(),
                errors = listOf(MappingError("email", "email", "key_mapping", "No UUID mapping found")),
                fieldCoverage = FieldCoverage(mapped = 0, total = 1, ratio = 0.0),
            )
            whenever(nangoClientWrapper.fetchRecords(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(NangoRecordsPage(records = listOf(record), nextCursor = null))
            whenever(entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(any(), any(), any()))
                .thenReturn(emptyList())
            whenever(schemaMappingService.mapPayload(any(), any(), any()))
                .thenReturn(failedMapping)

            val result = activitiesImpl.fetchAndProcessRecords(workflowInput)

            verify(entityRepository, never()).save(any())
            assertEquals(1, result.recordsFailed)
            assertEquals(0, result.recordsSynced)
        }

        @Test
        fun `mapping warnings proceed with partial data`() {
            val record = buildNangoRecord(action = NangoRecordAction.ADDED)
            val warningMapping = buildSuccessMappingResult().copy(
                warnings = listOf(riven.core.models.integration.mapping.MappingWarning("phone", "phone", "Source field not found"))
            )
            whenever(nangoClientWrapper.fetchRecords(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(NangoRecordsPage(records = listOf(record), nextCursor = null))
            whenever(entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(any(), any(), any()))
                .thenReturn(emptyList())
            whenever(schemaMappingService.mapPayload(any(), any(), any()))
                .thenReturn(warningMapping)

            val result = activitiesImpl.fetchAndProcessRecords(workflowInput)

            verify(entityRepository).save(any())
            assertEquals(1, result.recordsSynced)
            assertEquals(0, result.recordsFailed)
        }

        @Test
        fun `per-record exception is caught and batch continues`() {
            val badRecord = buildNangoRecord(externalId = "bad-001", action = NangoRecordAction.ADDED)
            val goodRecord = buildNangoRecord(externalId = "good-001", action = NangoRecordAction.ADDED)
            whenever(nangoClientWrapper.fetchRecords(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(NangoRecordsPage(records = listOf(badRecord, goodRecord), nextCursor = null))
            whenever(entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(any(), any(), any()))
                .thenReturn(emptyList())

            // First call throws, second succeeds
            whenever(schemaMappingService.mapPayload(any(), any(), any()))
                .thenThrow(RuntimeException("unexpected error"))
                .thenReturn(buildSuccessMappingResult())

            val result = activitiesImpl.fetchAndProcessRecords(workflowInput)

            assertEquals(1, result.recordsFailed)
            assertEquals(1, result.recordsSynced)
            assertNotNull(result.lastErrorMessage)
        }

        @Test
        fun `pagination fetches all pages until nextCursor is null`() {
            val record1 = buildNangoRecord(externalId = "ext-page1")
            val record2 = buildNangoRecord(externalId = "ext-page2")

            whenever(nangoClientWrapper.fetchRecords(any(), any(), any(), org.mockito.kotlin.isNull(), anyOrNull(), anyOrNull()))
                .thenReturn(NangoRecordsPage(records = listOf(record1), nextCursor = "cursor-1"))
            whenever(nangoClientWrapper.fetchRecords(any(), any(), any(), eq("cursor-1"), anyOrNull(), anyOrNull()))
                .thenReturn(NangoRecordsPage(records = listOf(record2), nextCursor = null))
            whenever(entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(any(), any(), any()))
                .thenReturn(emptyList())
            whenever(schemaMappingService.mapPayload(any(), any(), any()))
                .thenReturn(buildSuccessMappingResult())

            val result = activitiesImpl.fetchAndProcessRecords(workflowInput)

            verify(nangoClientWrapper, times(2)).fetchRecords(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull())
            assertEquals(2, result.recordsSynced)
        }

        @Test
        fun `pass 2 resolves relationships for synced entities`() {
            val relDefinitionId = UUID.randomUUID()
            val targetEntityId = UUID.randomUUID()
            val targetExternalId = "target-ext-001"

            val definition = EntityFactory.createRelationshipDefinitionEntity(
                id = relDefinitionId,
                workspaceId = workspaceId,
                sourceEntityTypeId = entityTypeId,
                name = "contacts",
            )

            // Record payload contains relationship field
            val record = NangoRecord(
                nangoMetadata = NangoRecordMetadata(lastAction = NangoRecordAction.ADDED, cursor = "c1"),
                payload = mutableMapOf<String, Any?>(
                    "id" to externalId,
                    "email" to "test@example.com",
                    "contacts" to listOf(targetExternalId),
                ),
            )

            val targetEntity = EntityFactory.createEntityEntity(
                id = targetEntityId,
                workspaceId = workspaceId,
                typeId = UUID.randomUUID(),
                sourceExternalId = targetExternalId,
                sourceIntegrationId = integrationId,
                sourceType = SourceType.INTEGRATION,
            )

            whenever(nangoClientWrapper.fetchRecords(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(NangoRecordsPage(records = listOf(record), nextCursor = null))
            whenever(entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(
                workspaceId, integrationId, listOf(externalId)
            )).thenReturn(emptyList())
            whenever(schemaMappingService.mapPayload(any(), any(), any()))
                .thenReturn(buildSuccessMappingResult())
            whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, entityTypeId))
                .thenReturn(listOf(definition))

            // Pass 2 target lookup
            whenever(entityRepository.findById(entityId))
                .thenReturn(Optional.of(EntityFactory.createEntityEntity(id = entityId, typeId = entityTypeId)))
            whenever(entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(
                workspaceId, integrationId, listOf(targetExternalId)
            )).thenReturn(listOf(targetEntity))
            whenever(entityRelationshipRepository.findBySourceIdAndTargetIdAndDefinitionId(any(), any(), any()))
                .thenReturn(emptyList())
            whenever(entityRelationshipRepository.save(any())).thenAnswer { it.arguments[0] }

            activitiesImpl.fetchAndProcessRecords(workflowInput)

            verify(entityRelationshipRepository).save(any())
        }

        @Test
        fun `pass 2 skips missing target entities silently`() {
            val definition = EntityFactory.createRelationshipDefinitionEntity(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                sourceEntityTypeId = entityTypeId,
                name = "contacts",
            )

            val record = NangoRecord(
                nangoMetadata = NangoRecordMetadata(lastAction = NangoRecordAction.ADDED, cursor = "c1"),
                payload = mutableMapOf<String, Any?>(
                    "id" to externalId,
                    "email" to "test@example.com",
                    "contacts" to listOf("missing-target-001"),
                ),
            )

            whenever(nangoClientWrapper.fetchRecords(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(NangoRecordsPage(records = listOf(record), nextCursor = null))
            whenever(entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(
                workspaceId, integrationId, listOf(externalId)
            )).thenReturn(emptyList())
            whenever(schemaMappingService.mapPayload(any(), any(), any()))
                .thenReturn(buildSuccessMappingResult())
            whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, entityTypeId))
                .thenReturn(listOf(definition))
            whenever(entityRepository.findById(entityId))
                .thenReturn(Optional.of(EntityFactory.createEntityEntity(id = entityId, typeId = entityTypeId)))
            // Pass 2: no target entities found
            whenever(entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(
                workspaceId, integrationId, listOf("missing-target-001")
            )).thenReturn(emptyList())

            assertDoesNotThrow { activitiesImpl.fetchAndProcessRecords(workflowInput) }

            verify(entityRelationshipRepository, never()).save(any())
        }
    }

    // ------ FinalizeSyncState Tests ------

    @Nested
    inner class FinalizeSyncStateTests {

        @Test
        fun `success creates sync state with SUCCESS status and saves cursor`() {
            whenever(syncStateRepository.findByIntegrationConnectionIdAndEntityTypeId(connectionId, entityTypeId))
                .thenReturn(null)

            val result = SyncProcessingResult(
                entityTypeId = entityTypeId,
                cursor = "cursor-final",
                recordsSynced = 10,
                recordsFailed = 0,
                success = true,
            )

            activitiesImpl.finalizeSyncState(connectionId, entityTypeId, result)

            val captor = argumentCaptor<IntegrationSyncStateEntity>()
            verify(syncStateRepository).save(captor.capture())
            val saved = captor.firstValue
            assertEquals(SyncStatus.SUCCESS, saved.status)
            assertEquals("cursor-final", saved.lastCursor)
            assertEquals(10, saved.lastRecordsSynced)
            assertEquals(0, saved.lastRecordsFailed)
        }

        @Test
        fun `success resets consecutive failure count to zero`() {
            val existing = IntegrationFactory.createIntegrationSyncState(
                integrationConnectionId = connectionId,
                entityTypeId = entityTypeId,
                consecutiveFailureCount = 3,
                status = SyncStatus.FAILED,
            )
            whenever(syncStateRepository.findByIntegrationConnectionIdAndEntityTypeId(connectionId, entityTypeId))
                .thenReturn(existing)

            val result = SyncProcessingResult(
                entityTypeId = entityTypeId,
                cursor = "cursor-recovery",
                recordsSynced = 5,
                recordsFailed = 0,
                success = true,
            )

            activitiesImpl.finalizeSyncState(connectionId, entityTypeId, result)

            val captor = argumentCaptor<IntegrationSyncStateEntity>()
            verify(syncStateRepository).save(captor.capture())
            assertEquals(0, captor.firstValue.consecutiveFailureCount)
        }

        @Test
        fun `failure does not save cursor and increments failure count`() {
            val existing = IntegrationFactory.createIntegrationSyncState(
                integrationConnectionId = connectionId,
                entityTypeId = entityTypeId,
                lastCursor = "cursor-previous",
                consecutiveFailureCount = 1,
                status = SyncStatus.SUCCESS,
            )
            whenever(syncStateRepository.findByIntegrationConnectionIdAndEntityTypeId(connectionId, entityTypeId))
                .thenReturn(existing)

            val result = SyncProcessingResult(
                entityTypeId = entityTypeId,
                cursor = "cursor-new",
                recordsSynced = 0,
                recordsFailed = 5,
                lastErrorMessage = "Something went wrong",
                success = false,
            )

            activitiesImpl.finalizeSyncState(connectionId, entityTypeId, result)

            val captor = argumentCaptor<IntegrationSyncStateEntity>()
            verify(syncStateRepository).save(captor.capture())
            val saved = captor.firstValue
            assertEquals(SyncStatus.FAILED, saved.status)
            assertEquals("cursor-previous", saved.lastCursor) // Cursor NOT updated on failure
            assertEquals(2, saved.consecutiveFailureCount)
            assertEquals("Something went wrong", saved.lastErrorMessage)
        }

        @Test
        fun `partial failure counts as SUCCESS with lastRecordsFailed greater than zero`() {
            whenever(syncStateRepository.findByIntegrationConnectionIdAndEntityTypeId(connectionId, entityTypeId))
                .thenReturn(null)

            val result = SyncProcessingResult(
                entityTypeId = entityTypeId,
                cursor = "cursor-partial",
                recordsSynced = 8,
                recordsFailed = 2,
                lastErrorMessage = "2 records failed",
                success = true, // Success is true — some records processed
            )

            activitiesImpl.finalizeSyncState(connectionId, entityTypeId, result)

            val captor = argumentCaptor<IntegrationSyncStateEntity>()
            verify(syncStateRepository).save(captor.capture())
            val saved = captor.firstValue
            assertEquals(SyncStatus.SUCCESS, saved.status)
            assertEquals(8, saved.lastRecordsSynced)
            assertEquals(2, saved.lastRecordsFailed)
            assertEquals("cursor-partial", saved.lastCursor)
        }

        @Test
        fun `lazy creates sync state entity on first sync`() {
            whenever(syncStateRepository.findByIntegrationConnectionIdAndEntityTypeId(connectionId, entityTypeId))
                .thenReturn(null)

            val result = SyncProcessingResult(
                entityTypeId = entityTypeId,
                cursor = null,
                recordsSynced = 3,
                recordsFailed = 0,
                success = true,
            )

            activitiesImpl.finalizeSyncState(connectionId, entityTypeId, result)

            val captor = argumentCaptor<IntegrationSyncStateEntity>()
            verify(syncStateRepository).save(captor.capture())
            val saved = captor.firstValue
            assertEquals(connectionId, saved.integrationConnectionId)
            assertEquals(entityTypeId, saved.entityTypeId)
            assertNull(saved.id) // New entity has no ID before persistence
        }
    }

    // ------ EvaluateHealth Tests ------

    // ------ ExecuteProjections Tests ------

    @Nested
    inner class ExecuteProjectionsTests {

        /**
         * Verifies the no-op stub does not throw when called with valid arguments.
         * The implementation simply logs — this confirms the activity is safe to invoke.
         */
        @Test
        fun `executeProjections no-op does not throw`() {
            val syncedEntityIds = listOf(UUID.randomUUID(), UUID.randomUUID())

            assertDoesNotThrow {
                activitiesImpl.executeProjections(connectionId, workspaceId, entityTypeId, syncedEntityIds)
            }
        }

        @Test
        fun `executeProjections no-op handles empty entity list`() {
            assertDoesNotThrow {
                activitiesImpl.executeProjections(connectionId, workspaceId, entityTypeId, emptyList())
            }
        }
    }

    // ------ EvaluateHealth Tests ------

    @Nested
    inner class EvaluateHealthTests {

        @Test
        fun `evaluateHealth delegates to integrationHealthService evaluateConnectionHealth`() {
            activitiesImpl.evaluateHealth(connectionId)

            verify(integrationHealthService).evaluateConnectionHealth(connectionId)
        }
    }
}
