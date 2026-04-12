package riven.core.service.integration.sync

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionTemplate
import riven.core.models.ingestion.ProjectionResult
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
import riven.core.service.ingestion.EntityProjectionService
import riven.core.service.integration.IntegrationHealthService
import riven.core.service.integration.NangoClientWrapper
import riven.core.service.integration.mapping.SchemaMappingService
import java.util.*

/**
 * Tests ONLY the projection-related behavior of IntegrationSyncActivitiesImpl.executeProjections.
 *
 * The base sync flow (fetch, upsert, relationships, health) is covered by IntegrationSyncActivitiesImplTest.
 * This test class focuses on the Pass 3 projection delegation:
 * - Correct argument forwarding to EntityProjectionService
 * - Empty input short-circuit
 * - Error propagation from projection service
 */
@SpringBootTest(
    classes = [
        IntegrationSyncActivitiesImplProjectionTest.TestConfig::class,
    ]
)
class IntegrationSyncActivitiesImplProjectionTest {

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
            entityProjectionService: EntityProjectionService,
            noteEmbeddingService: riven.core.service.note.NoteEmbeddingService,
            objectMapper: com.fasterxml.jackson.databind.ObjectMapper,
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

    // ------ Mocked Dependencies ------

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
    @MockitoBean private lateinit var entityProjectionService: EntityProjectionService
    @MockitoBean private lateinit var noteEmbeddingService: riven.core.service.note.NoteEmbeddingService
    @MockitoBean private lateinit var objectMapper: com.fasterxml.jackson.databind.ObjectMapper
    @MockitoBean private lateinit var resourceLoader: org.springframework.core.io.ResourceLoader
    @MockitoBean private lateinit var transactionTemplate: TransactionTemplate
    @MockitoBean private lateinit var logger: KLogger

    @Autowired
    private lateinit var activitiesImpl: IntegrationSyncActivitiesImpl

    // ------ Shared Test Data ------

    private val connectionId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val workspaceId: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val entityTypeId: UUID = UUID.fromString("44444444-4444-4444-4444-444444444444")

    @BeforeEach
    fun setup() {
        reset(entityProjectionService)
    }

    @Test
    fun `delegates to EntityProjectionService with correct args`() {
        val syncedEntityIds = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

        whenever(entityProjectionService.processProjections(
            syncedEntityIds = syncedEntityIds,
            workspaceId = workspaceId,
            sourceEntityTypeId = entityTypeId,
        )).thenReturn(ProjectionResult(created = 2, updated = 1, skipped = 0, errors = 0))

        activitiesImpl.executeProjections(connectionId, workspaceId, entityTypeId, syncedEntityIds)

        verify(entityProjectionService).processProjections(
            syncedEntityIds = eq(syncedEntityIds),
            workspaceId = eq(workspaceId),
            sourceEntityTypeId = eq(entityTypeId),
        )
    }

    @Test
    fun `empty syncedEntityIds - no-op, no service call`() {
        activitiesImpl.executeProjections(connectionId, workspaceId, entityTypeId, emptyList())

        verify(entityProjectionService, never()).processProjections(any(), any(), any())
    }

    @Test
    fun `projection service error - exception propagated`() {
        val syncedEntityIds = listOf(UUID.randomUUID())

        whenever(entityProjectionService.processProjections(
            syncedEntityIds = syncedEntityIds,
            workspaceId = workspaceId,
            sourceEntityTypeId = entityTypeId,
        )).thenThrow(RuntimeException("Projection pipeline failure"))

        assertThrows<RuntimeException> {
            activitiesImpl.executeProjections(connectionId, workspaceId, entityTypeId, syncedEntityIds)
        }
    }
}
