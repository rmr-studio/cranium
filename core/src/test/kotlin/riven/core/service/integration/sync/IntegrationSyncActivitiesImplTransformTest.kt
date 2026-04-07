package riven.core.service.integration.sync

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionTemplate
import riven.core.enums.catalog.ManifestType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.integration.CoercionType
import riven.core.enums.integration.SourceType
import riven.core.models.common.validation.Schema
import riven.core.models.entity.configuration.ColumnConfiguration
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.models.integration.NangoRecord
import riven.core.models.integration.NangoRecordAction
import riven.core.models.integration.NangoRecordMetadata
import riven.core.models.integration.NangoRecordsPage
import riven.core.models.integration.mapping.FieldCoverage
import riven.core.models.integration.mapping.FieldTransform
import riven.core.models.integration.mapping.MappingResult
import riven.core.models.integration.mapping.ResolvedFieldMapping
import riven.core.models.integration.sync.IntegrationSyncWorkflowInput
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
 * Regression tests for transform parsing in resolveFieldMappings().
 *
 * Bug: resolveFieldMappings() always set transform = FieldTransform.Direct,
 * ignoring manifest-defined transforms like type_coercion and json_path_extraction.
 * This caused date fields, number fields, and computed fields to be stored as raw
 * strings from Nango instead of being properly coerced.
 *
 * Fix: parseTransform() helper parses the transform block from raw JSONB mappings
 * and maps to the correct FieldTransform sealed class variant.
 *
 * These tests verify the fix by capturing the fieldMappings argument passed to
 * schemaMappingService.mapPayload() via argumentCaptor.
 */
@SpringBootTest(
    classes = [
        IntegrationSyncActivitiesImplTransformTest.TestConfig::class,
    ]
)
class IntegrationSyncActivitiesImplTransformTest {

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
                override fun heartbeat(cursor: String?) { /* no-op in tests */ }
            }
        }
    }

    @Autowired
    private lateinit var activities: IntegrationSyncActivitiesImpl

    @MockitoBean private lateinit var connectionRepository: IntegrationConnectionRepository
    @MockitoBean private lateinit var syncStateRepository: IntegrationSyncStateRepository
    @MockitoBean private lateinit var nangoClientWrapper: NangoClientWrapper
    @MockitoBean private lateinit var schemaMappingService: SchemaMappingService
    @MockitoBean private lateinit var entityRepository: EntityRepository
    @MockitoBean private lateinit var entityAttributeService: EntityAttributeService
    @MockitoBean private lateinit var entityRelationshipRepository: EntityRelationshipRepository
    @MockitoBean private lateinit var relationshipDefinitionRepository: RelationshipDefinitionRepository
    @MockitoBean private lateinit var definitionRepository: IntegrationDefinitionRepository
    @MockitoBean private lateinit var manifestCatalogRepository: ManifestCatalogRepository
    @MockitoBean private lateinit var catalogFieldMappingRepository: CatalogFieldMappingRepository
    @MockitoBean private lateinit var catalogEntityTypeRepository: CatalogEntityTypeRepository
    @MockitoBean private lateinit var entityTypeRepository: EntityTypeRepository
    @MockitoBean private lateinit var integrationHealthService: IntegrationHealthService
    @MockitoBean private lateinit var entityProjectionService: riven.core.service.ingestion.EntityProjectionService
    @MockitoBean private lateinit var transactionTemplate: TransactionTemplate
    @MockitoBean private lateinit var logger: KLogger

    private val workspaceId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val integrationId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val manifestId = UUID.fromString("77777777-7777-7777-7777-777777777777")
    private val connectionId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val entityTypeId = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val emailAttrUuid = UUID.fromString("55555555-5555-5555-5555-555555555555")
    private val createdDateAttrUuid = UUID.fromString("66666666-6666-6666-6666-666666666666")

    private val model = "test-model"
    private val entityTypeKey = "test-entity"

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
        whenever(entityRepository.save(any())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as riven.core.entity.entity.EntityEntity
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }
    }

    /** Sets up full model context mocks with custom field mappings JSONB. */
    private fun setupMocksWithMappings(rawMappings: Map<String, Any>) {
        val input = buildInput()

        whenever(definitionRepository.findById(integrationId))
            .thenReturn(Optional.of(IntegrationFactory.createIntegrationDefinition(id = integrationId, slug = "test")))
        whenever(manifestCatalogRepository.findByKey("test"))
            .thenReturn(listOf(CatalogFactory.createManifestEntity(
                type = ManifestType.INTEGRATION, id = manifestId, key = "test", name = "Test",
            )))
        whenever(catalogFieldMappingRepository.findByManifestIdAndNangoModel(manifestId, model))
            .thenReturn(CatalogFactory.createFieldMappingEntity(
                manifestId = manifestId,
                entityTypeKey = entityTypeKey,
                nangoModel = model,
                mappings = rawMappings,
            ))

        val entityType = EntityFactory.createEntityType(
            id = entityTypeId,
            key = entityTypeKey,
            workspaceId = workspaceId,
            sourceType = SourceType.INTEGRATION,
            sourceIntegrationId = integrationId,
            identifierKey = emailAttrUuid,
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    emailAttrUuid to Schema(key = SchemaType.TEXT, type = DataType.STRING),
                    createdDateAttrUuid to Schema(key = SchemaType.DATETIME, type = DataType.STRING),
                )
            ),
            columnConfiguration = ColumnConfiguration(order = listOf(emailAttrUuid, createdDateAttrUuid)),
        )
        whenever(entityTypeRepository.findBySourceIntegrationIdAndWorkspaceId(integrationId, workspaceId))
            .thenReturn(listOf(entityType))
        whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityTypeKey))
            .thenReturn(CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId, key = entityTypeKey,
                displayNameSingular = "Test", displayNamePlural = "Tests",
                schema = linkedMapOf(
                    "email" to mapOf("key" to "TEXT", "label" to "Email", "type" to "string"),
                    "created-date" to mapOf("key" to "DATETIME", "label" to "Created Date", "type" to "string"),
                ),
            ))
        whenever(syncStateRepository.findByIntegrationConnectionIdAndEntityTypeId(any(), any()))
            .thenReturn(null)
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(any(), any()))
            .thenReturn(emptyList())

        val record = NangoRecord(
            nangoMetadata = NangoRecordMetadata(lastAction = NangoRecordAction.ADDED, cursor = "c1"),
            payload = mutableMapOf<String, Any?>("id" to "ext-001", "email" to "test@example.com", "createdate" to "2024-01-15T10:00:00Z"),
        )
        whenever(nangoClientWrapper.fetchRecords(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(NangoRecordsPage(records = listOf(record), nextCursor = null))
        whenever(entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(any(), any(), any()))
            .thenReturn(emptyList())
        whenever(schemaMappingService.mapPayload(any(), any(), any()))
            .thenReturn(MappingResult(
                attributes = mapOf(emailAttrUuid.toString() to EntityAttributePrimitivePayload(
                    value = "test@example.com", schemaType = SchemaType.TEXT,
                )),
                warnings = emptyList(),
                errors = emptyList(),
                fieldCoverage = FieldCoverage(mapped = 1, total = 1, ratio = 1.0),
            ))
    }

    private fun buildInput() = IntegrationSyncWorkflowInput(
        connectionId = connectionId,
        workspaceId = workspaceId,
        integrationId = integrationId,
        nangoConnectionId = "nango-conn-1",
        providerConfigKey = "test",
        model = model,
        modifiedAfter = null,
    )

    @Nested
    inner class TransformParsing {

        @Test
        fun `type_coercion transform is parsed as TypeCoercion with correct targetType`() {
            val mappings = mapOf(
                "email" to mapOf<String, Any>("source" to "email"),
                "created-date" to mapOf<String, Any>(
                    "source" to "createdate",
                    "transform" to mapOf("type" to "type_coercion", "targetType" to "datetime")
                ),
            )
            setupMocksWithMappings(mappings)

            activities.fetchAndProcessRecords(buildInput())

            val captor = argumentCaptor<Map<String, ResolvedFieldMapping>>()
            org.mockito.kotlin.verify(schemaMappingService).mapPayload(any(), captor.capture(), any())

            val fieldMappings = captor.firstValue
            val createdDateMapping = fieldMappings["created-date"]
            assertTrue(createdDateMapping != null) { "Expected created-date mapping" }
            assertTrue(createdDateMapping!!.transform is FieldTransform.TypeCoercion) {
                "Expected TypeCoercion but got ${createdDateMapping.transform}"
            }
            assertEquals(
                CoercionType.DATETIME,
                (createdDateMapping.transform as FieldTransform.TypeCoercion).targetType
            )
        }

        @Test
        fun `direct transform is parsed as Direct`() {
            val mappings = mapOf(
                "email" to mapOf<String, Any>(
                    "source" to "email",
                    "transform" to mapOf("type" to "direct")
                ),
            )
            setupMocksWithMappings(mappings)

            activities.fetchAndProcessRecords(buildInput())

            val captor = argumentCaptor<Map<String, ResolvedFieldMapping>>()
            org.mockito.kotlin.verify(schemaMappingService).mapPayload(any(), captor.capture(), any())

            val emailMapping = captor.firstValue["email"]
            assertTrue(emailMapping!!.transform is FieldTransform.Direct) {
                "Expected Direct but got ${emailMapping.transform}"
            }
        }

        @Test
        fun `missing transform defaults to Direct`() {
            val mappings = mapOf(
                "email" to mapOf<String, Any>("source" to "email"),
            )
            setupMocksWithMappings(mappings)

            activities.fetchAndProcessRecords(buildInput())

            val captor = argumentCaptor<Map<String, ResolvedFieldMapping>>()
            org.mockito.kotlin.verify(schemaMappingService).mapPayload(any(), captor.capture(), any())

            val emailMapping = captor.firstValue["email"]
            assertTrue(emailMapping!!.transform is FieldTransform.Direct) {
                "Expected Direct but got ${emailMapping.transform}"
            }
        }

        @Test
        fun `default_value transform is parsed with correct value`() {
            val mappings = mapOf(
                "email" to mapOf<String, Any>(
                    "source" to "_ignored",
                    "transform" to mapOf("type" to "default_value", "value" to "hubspot")
                ),
            )
            setupMocksWithMappings(mappings)

            activities.fetchAndProcessRecords(buildInput())

            val captor = argumentCaptor<Map<String, ResolvedFieldMapping>>()
            org.mockito.kotlin.verify(schemaMappingService).mapPayload(any(), captor.capture(), any())

            val emailMapping = captor.firstValue["email"]
            assertTrue(emailMapping!!.transform is FieldTransform.DefaultValue) {
                "Expected DefaultValue but got ${emailMapping.transform}"
            }
            assertEquals("hubspot", (emailMapping.transform as FieldTransform.DefaultValue).value)
        }

        @Test
        fun `json_path_extraction transform is parsed with correct path`() {
            val mappings = mapOf(
                "email" to mapOf<String, Any>(
                    "source" to "address",
                    "transform" to mapOf("type" to "json_path_extraction", "path" to "$.city")
                ),
            )
            setupMocksWithMappings(mappings)

            activities.fetchAndProcessRecords(buildInput())

            val captor = argumentCaptor<Map<String, ResolvedFieldMapping>>()
            org.mockito.kotlin.verify(schemaMappingService).mapPayload(any(), captor.capture(), any())

            val emailMapping = captor.firstValue["email"]
            assertTrue(emailMapping!!.transform is FieldTransform.JsonPathExtraction) {
                "Expected JsonPathExtraction but got ${emailMapping.transform}"
            }
            assertEquals("$.city", (emailMapping.transform as FieldTransform.JsonPathExtraction).path)
        }
    }
}
