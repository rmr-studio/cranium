package cranium.core.service.integration.sync

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionTemplate
import cranium.core.enums.catalog.ManifestType
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.core.DataType
import cranium.core.enums.integration.SourceType
import cranium.core.models.common.validation.Schema
import cranium.core.models.entity.configuration.ColumnConfiguration
import cranium.core.models.integration.NangoRecordsPage
import cranium.core.models.integration.sync.IntegrationSyncWorkflowInput
import cranium.core.repository.catalog.CatalogEntityTypeRepository
import cranium.core.repository.catalog.CatalogFieldMappingRepository
import cranium.core.repository.catalog.ManifestCatalogRepository
import cranium.core.repository.entity.EntityRelationshipRepository
import cranium.core.repository.entity.EntityRepository
import cranium.core.repository.entity.EntityTypeRepository
import cranium.core.repository.entity.RelationshipDefinitionRepository
import cranium.core.repository.integration.IntegrationConnectionRepository
import cranium.core.repository.integration.IntegrationDefinitionRepository
import cranium.core.repository.integration.IntegrationSyncStateRepository
import cranium.core.service.entity.EntityAttributeService
import cranium.core.service.integration.IntegrationHealthService
import cranium.core.service.integration.NangoClientWrapper
import cranium.core.service.integration.mapping.SchemaMappingService
import cranium.core.service.util.factory.catalog.CatalogFactory
import cranium.core.service.util.factory.entity.EntityFactory
import cranium.core.service.util.factory.integration.IntegrationFactory
import java.util.*

/**
 * Tests for Nango model name -> entity type key resolution in the sync pipeline.
 *
 * Bug: resolveModelContext() used input.model (Nango model name like "contacts") directly
 * as the entity type key, but entity type keys are prefixed (e.g. "hubspot-contact").
 * Three lookups were affected: catalogFieldMappingRepository, entityTypeRepository filter,
 * and catalogEntityTypeRepository.
 *
 * Fix: nangoModel field on CatalogFieldMappingEntity provides explicit Nango model -> entity
 * type key mapping. The sync pipeline resolves via findByManifestIdAndNangoModel() first,
 * then uses the entityTypeKey from the result for subsequent lookups.
 */
@SpringBootTest(
    classes = [
        IntegrationSyncActivitiesImplModelResolutionTest.TestConfig::class,
    ]
)
class IntegrationSyncActivitiesImplModelResolutionTest {

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
            entityProjectionService: cranium.core.service.ingestion.EntityProjectionService,
            noteEmbeddingService: cranium.core.service.note.NoteEmbeddingService,
            objectMapper: tools.jackson.databind.ObjectMapper,
            resourceLoader: org.springframework.core.io.ResourceLoader,
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
                noteEmbeddingService = noteEmbeddingService,
                objectMapper = objectMapper,
                resourceLoader = resourceLoader,
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
    @MockitoBean private lateinit var entityProjectionService: cranium.core.service.ingestion.EntityProjectionService
    @MockitoBean private lateinit var noteEmbeddingService: cranium.core.service.note.NoteEmbeddingService
    @MockitoBean private lateinit var objectMapper: tools.jackson.databind.ObjectMapper
    @MockitoBean private lateinit var resourceLoader: org.springframework.core.io.ResourceLoader
    @MockitoBean private lateinit var transactionTemplate: TransactionTemplate
    @MockitoBean private lateinit var logger: KLogger

    private val workspaceId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val integrationId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val manifestId = UUID.fromString("77777777-7777-7777-7777-777777777777")
    private val connectionId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val entityTypeId = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val attributeUuid = UUID.fromString("55555555-5555-5555-5555-555555555555")

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
            val entity = invocation.arguments[0] as cranium.core.entity.entity.EntityEntity
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }
    }

    @Nested
    inner class NangoModelResolution {

        /**
         * Verifies that the sync pipeline correctly resolves Nango model names (e.g. "contacts")
         * to entity type keys (e.g. "hubspot-contact") via the nangoModel field on CatalogFieldMapping.
         *
         * This is the core regression test for the model resolution bug: Nango sends model="contacts"
         * but entity type keys are "hubspot-contact". The pipeline must use findByManifestIdAndNangoModel
         * to resolve the field mapping, then use the entityTypeKey from the result for all subsequent lookups.
         */
        @Test
        fun `resolves Nango model name to entity type key via nangoModel field`() {
            // Nango sends model="contacts", but entity type key is "hubspot-contact"
            val nangoModel = "contacts"
            val entityTypeKey = "hubspot-contact"

            val input = IntegrationSyncWorkflowInput(
                connectionId = connectionId,
                workspaceId = workspaceId,
                integrationId = integrationId,
                nangoConnectionId = "nango-conn-1",
                providerConfigKey = "hubspot",
                model = nangoModel,
                modifiedAfter = null,
            )

            // Definition + manifest lookups
            whenever(definitionRepository.findById(integrationId))
                .thenReturn(Optional.of(IntegrationFactory.createIntegrationDefinition(id = integrationId, slug = "hubspot")))
            whenever(manifestCatalogRepository.findByKey("hubspot"))
                .thenReturn(listOf(CatalogFactory.createManifestEntity(
                    type = ManifestType.INTEGRATION, id = manifestId, key = "hubspot", name = "HubSpot",
                )))

            // Field mapping lookup by Nango model name — returns entity with entityTypeKey
            whenever(catalogFieldMappingRepository.findByManifestIdAndNangoModel(manifestId, nangoModel))
                .thenReturn(CatalogFactory.createFieldMappingEntity(
                    manifestId = manifestId,
                    entityTypeKey = entityTypeKey,
                    nangoModel = nangoModel,
                    mappings = mapOf("email" to mapOf<String, Any>("source" to "email")),
                ))

            // Entity type filter uses resolved key "hubspot-contact", NOT "contacts"
            val entityType = EntityFactory.createEntityType(
                id = entityTypeId,
                key = entityTypeKey,
                workspaceId = workspaceId,
                sourceType = SourceType.INTEGRATION,
                sourceIntegrationId = integrationId,
                identifierKey = attributeUuid,
                schema = Schema(
                    key = SchemaType.OBJECT,
                    type = DataType.OBJECT,
                    properties = mapOf(
                        attributeUuid to Schema(key = SchemaType.TEXT, type = DataType.STRING)
                    )
                ),
                columnConfiguration = ColumnConfiguration(order = listOf(attributeUuid)),
                attributeKeyMapping = mapOf("email" to attributeUuid.toString()),
            )
            whenever(entityTypeRepository.findBySourceIntegrationIdAndWorkspaceId(integrationId, workspaceId))
                .thenReturn(listOf(entityType))

            // Catalog entity type lookup uses resolved key
            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityTypeKey))
                .thenReturn(CatalogFactory.createEntityTypeEntity(
                    manifestId = manifestId, key = entityTypeKey,
                    displayNameSingular = "Contact", displayNamePlural = "Contacts",
                    schema = linkedMapOf("email" to mapOf("key" to "TEXT", "label" to "Email", "type" to "string")),
                ))

            whenever(syncStateRepository.findByIntegrationConnectionIdAndEntityTypeId(any(), any()))
                .thenReturn(null)
            whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(any(), any()))
                .thenReturn(emptyList())
            whenever(nangoClientWrapper.fetchRecords(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(NangoRecordsPage(records = emptyList(), nextCursor = null))

            val result = activities.fetchAndProcessRecords(input)

            assertTrue(result.success) { "Expected success but got failure: ${result.lastErrorMessage}" }
        }

        /**
         * Verifies that the sync pipeline returns a failure result when the Nango model name
         * has no matching nangoModel in catalog field mappings.
         */
        @Test
        fun `returns failure when Nango model has no field mapping with matching nangoModel`() {
            val input = IntegrationSyncWorkflowInput(
                connectionId = connectionId,
                workspaceId = workspaceId,
                integrationId = integrationId,
                nangoConnectionId = "nango-conn-1",
                providerConfigKey = "hubspot",
                model = "unknown_model",
                modifiedAfter = null,
            )

            whenever(definitionRepository.findById(integrationId))
                .thenReturn(Optional.of(IntegrationFactory.createIntegrationDefinition(id = integrationId, slug = "hubspot")))
            whenever(manifestCatalogRepository.findByKey("hubspot"))
                .thenReturn(listOf(CatalogFactory.createManifestEntity(
                    type = ManifestType.INTEGRATION, id = manifestId, key = "hubspot", name = "HubSpot",
                )))

            // No field mapping found for unknown model
            whenever(catalogFieldMappingRepository.findByManifestIdAndNangoModel(manifestId, "unknown_model"))
                .thenReturn(null)

            val result = activities.fetchAndProcessRecords(input)

            assertFalse(result.success) { "Expected failure for unmapped model" }
        }
    }
}
