package riven.core.service.catalog

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import riven.core.entity.catalog.*
import riven.core.enums.catalog.ManifestType
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.models.catalog.*
import riven.core.repository.catalog.*
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.util.*

class ManifestUpsertServiceTest {

    private lateinit var manifestCatalogRepository: ManifestCatalogRepository
    private lateinit var catalogEntityTypeRepository: CatalogEntityTypeRepository
    private lateinit var catalogRelationshipRepository: CatalogRelationshipRepository
    private lateinit var catalogRelationshipTargetRuleRepository: CatalogRelationshipTargetRuleRepository
    private lateinit var catalogFieldMappingRepository: CatalogFieldMappingRepository
    private lateinit var catalogSemanticMetadataRepository: CatalogSemanticMetadataRepository
    private lateinit var logger: KLogger
    private lateinit var service: ManifestUpsertService

    private val objectMapper = ObjectMapper().registerModule(JavaTimeModule())
    private val manifestId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        manifestCatalogRepository = mock()
        catalogEntityTypeRepository = mock()
        catalogRelationshipRepository = mock()
        catalogRelationshipTargetRuleRepository = mock()
        catalogFieldMappingRepository = mock()
        catalogSemanticMetadataRepository = mock()
        logger = mock()

        service = ManifestUpsertService(
            manifestCatalogRepository,
            catalogEntityTypeRepository,
            catalogRelationshipRepository,
            catalogRelationshipTargetRuleRepository,
            catalogFieldMappingRepository,
            catalogSemanticMetadataRepository,
            objectMapper,
            logger
        )
    }

    // ------ New Manifest Creation ------

    @Test
    fun `upsertManifest creates new catalog entry and children`() {
        val resolved = createResolvedManifest()

        whenever(manifestCatalogRepository.findByKeyAndManifestType("test-key", ManifestType.TEMPLATE))
            .thenReturn(null)
        whenever(manifestCatalogRepository.save(any<ManifestCatalogEntity>()))
            .thenAnswer { invocation ->
                val entity = invocation.getArgument<ManifestCatalogEntity>(0)
                entity.copy(id = manifestId)
            }
        mockEmptyChildren()
        mockChildSaves()

        service.upsertManifest(resolved)

        verify(manifestCatalogRepository).save(argThat<ManifestCatalogEntity> {
            key == "test-key" && name == "Test Manifest" && !stale && contentHash != null
        })
        verify(catalogEntityTypeRepository).saveAll(argThat<List<CatalogEntityTypeEntity>> { size == 1 })
        verify(catalogRelationshipRepository).save(argThat<CatalogRelationshipEntity> {
            key == "customer-deals"
        })
        verify(catalogRelationshipTargetRuleRepository).saveAll(argThat<List<CatalogRelationshipTargetRuleEntity>> { size == 1 })
        verify(catalogFieldMappingRepository).saveAll(argThat<List<CatalogFieldMappingEntity>> { size == 1 })
    }

    // ------ Existing Manifest Update ------

    @Test
    fun `upsertManifest updates existing catalog entry and reinserts children`() {
        val resolved = createResolvedManifest()
        val existingEntity = ManifestCatalogEntity(
            id = manifestId,
            key = "test-key",
            name = "Old Name",
            manifestType = ManifestType.TEMPLATE,
            stale = true,
            contentHash = "old-hash"
        )

        whenever(manifestCatalogRepository.findByKeyAndManifestType("test-key", ManifestType.TEMPLATE))
            .thenReturn(existingEntity)
        whenever(manifestCatalogRepository.save(any<ManifestCatalogEntity>()))
            .thenAnswer { invocation -> invocation.getArgument<ManifestCatalogEntity>(0) }
        mockExistingChildren()
        mockChildSaves()

        service.upsertManifest(resolved)

        // Verify catalog entry updated (not created new)
        verify(manifestCatalogRepository).save(argThat<ManifestCatalogEntity> {
            id == manifestId && name == "Test Manifest" && !stale
        })
        // Verify children were deleted before re-insert
        verify(catalogRelationshipTargetRuleRepository).deleteByCatalogRelationshipIdIn(any())
        verify(catalogRelationshipRepository).deleteByManifestId(manifestId)
        verify(catalogSemanticMetadataRepository).deleteByCatalogEntityTypeIdIn(any())
        verify(catalogEntityTypeRepository).deleteByManifestId(manifestId)
        verify(catalogFieldMappingRepository).deleteByManifestId(manifestId)
        // Verify fresh children inserted
        verify(catalogEntityTypeRepository).saveAll(any<List<CatalogEntityTypeEntity>>())
    }

    // ------ Content Hash Match (Skip Children) ------

    @Test
    fun `upsertManifest skips child reconciliation when content hash matches`() {
        val resolved = createResolvedManifest()
        val contentHash = computeExpectedHash(resolved)

        val existingEntity = ManifestCatalogEntity(
            id = manifestId,
            key = "test-key",
            name = "Test Manifest",
            manifestType = ManifestType.TEMPLATE,
            stale = false,
            contentHash = contentHash
        )

        whenever(manifestCatalogRepository.findByKeyAndManifestType("test-key", ManifestType.TEMPLATE))
            .thenReturn(existingEntity)
        whenever(manifestCatalogRepository.save(any<ManifestCatalogEntity>()))
            .thenAnswer { invocation -> invocation.getArgument<ManifestCatalogEntity>(0) }

        service.upsertManifest(resolved)

        // Only catalog row should be saved (timestamp update)
        verify(manifestCatalogRepository).save(any())
        // No child operations
        verify(catalogEntityTypeRepository, never()).saveAll(any<List<CatalogEntityTypeEntity>>())
        verify(catalogRelationshipRepository, never()).save(any<CatalogRelationshipEntity>())
        verify(catalogFieldMappingRepository, never()).saveAll(any<List<CatalogFieldMappingEntity>>())
        verify(catalogEntityTypeRepository, never()).deleteByManifestId(any())
    }

    // ------ Stale Manifest ------

    @Test
    fun `upsertManifest with stale manifest only upserts catalog row`() {
        val resolved = createResolvedManifest(stale = true)

        whenever(manifestCatalogRepository.findByKeyAndManifestType("test-key", ManifestType.TEMPLATE))
            .thenReturn(null)
        whenever(manifestCatalogRepository.save(any<ManifestCatalogEntity>()))
            .thenAnswer { invocation ->
                val entity = invocation.getArgument<ManifestCatalogEntity>(0)
                entity.copy(id = manifestId)
            }

        service.upsertManifest(resolved)

        // Catalog row saved with stale=true and no content hash
        verify(manifestCatalogRepository).save(argThat<ManifestCatalogEntity> { stale && contentHash == null })
        // No child operations
        verify(catalogEntityTypeRepository, never()).saveAll(any<List<CatalogEntityTypeEntity>>())
        verify(catalogRelationshipRepository, never()).save(any<CatalogRelationshipEntity>())
        verify(catalogFieldMappingRepository, never()).saveAll(any<List<CatalogFieldMappingEntity>>())
        verify(catalogEntityTypeRepository, never()).deleteByManifestId(any())
    }

    // ------ Delete Order ------

    @Test
    fun `upsertManifest deletes children in correct order`() {
        val resolved = createResolvedManifest()
        val existingEntity = ManifestCatalogEntity(
            id = manifestId,
            key = "test-key",
            name = "Old Name",
            manifestType = ManifestType.TEMPLATE,
            stale = true,
            contentHash = "old-hash"
        )

        whenever(manifestCatalogRepository.findByKeyAndManifestType("test-key", ManifestType.TEMPLATE))
            .thenReturn(existingEntity)
        whenever(manifestCatalogRepository.save(any<ManifestCatalogEntity>()))
            .thenAnswer { invocation -> invocation.getArgument<ManifestCatalogEntity>(0) }
        mockExistingChildren()
        mockChildSaves()

        service.upsertManifest(resolved)

        // Verify delete order: target rules before relationships, semantic metadata before entity types
        val inOrder = inOrder(
            catalogRelationshipTargetRuleRepository,
            catalogRelationshipRepository,
            catalogSemanticMetadataRepository,
            catalogEntityTypeRepository,
            catalogFieldMappingRepository
        )
        inOrder.verify(catalogRelationshipTargetRuleRepository).deleteByCatalogRelationshipIdIn(any())
        inOrder.verify(catalogRelationshipRepository).deleteByManifestId(manifestId)
        inOrder.verify(catalogSemanticMetadataRepository).deleteByCatalogEntityTypeIdIn(any())
        inOrder.verify(catalogEntityTypeRepository).deleteByManifestId(manifestId)
        inOrder.verify(catalogFieldMappingRepository).deleteByManifestId(manifestId)
    }

    // ------ Semantic Metadata ------

    @Test
    fun `upsertManifest persists semantic metadata for entity types with semantics`() {
        val resolved = createResolvedManifest(
            entityTypes = listOf(
                createResolvedEntityType(
                    semantics = ResolvedSemantics(
                        definition = "A customer record",
                        tags = listOf("crm", "primary")
                    )
                )
            )
        )

        whenever(manifestCatalogRepository.findByKeyAndManifestType("test-key", ManifestType.TEMPLATE))
            .thenReturn(null)
        whenever(manifestCatalogRepository.save(any<ManifestCatalogEntity>()))
            .thenAnswer { invocation ->
                val entity = invocation.getArgument<ManifestCatalogEntity>(0)
                entity.copy(id = manifestId)
            }
        mockEmptyChildren()

        val savedEntityTypeId = UUID.randomUUID()
        whenever(catalogEntityTypeRepository.saveAll(any<List<CatalogEntityTypeEntity>>()))
            .thenReturn(listOf(
                CatalogEntityTypeEntity(
                    id = savedEntityTypeId,
                    manifestId = manifestId,
                    key = "customer",
                    displayNameSingular = "Customer",
                    displayNamePlural = "Customers",
                    schema = emptyMap()
                )
            ))
        val savedRelId = UUID.randomUUID()
        whenever(catalogRelationshipRepository.save(any<CatalogRelationshipEntity>()))
            .thenAnswer { invocation ->
                val entity = invocation.getArgument<CatalogRelationshipEntity>(0)
                entity.copy(id = savedRelId)
            }

        service.upsertManifest(resolved)

        verify(catalogSemanticMetadataRepository).saveAll(argThat<List<CatalogSemanticMetadataEntity>> {
            size == 1 &&
                this[0].catalogEntityTypeId == savedEntityTypeId &&
                this[0].targetType == SemanticMetadataTargetType.ENTITY_TYPE &&
                this[0].definition == "A customer record" &&
                this[0].tags == listOf("crm", "primary")
        })
    }

    // ------ Helpers ------

    private fun createResolvedManifest(
        stale: Boolean = false,
        entityTypes: List<ResolvedEntityType> = listOf(createResolvedEntityType()),
        relationships: List<NormalizedRelationship> = listOf(createNormalizedRelationship()),
        fieldMappings: List<ResolvedFieldMapping> = listOf(createResolvedFieldMapping())
    ) = ResolvedManifest(
        key = "test-key",
        name = "Test Manifest",
        description = "A test manifest",
        type = ManifestType.TEMPLATE,
        manifestVersion = "1.0",
        entityTypes = entityTypes,
        relationships = relationships,
        fieldMappings = fieldMappings,
        stale = stale
    )

    private fun createResolvedEntityType(
        semantics: ResolvedSemantics? = null
    ) = ResolvedEntityType(
        key = "customer",
        displayNameSingular = "Customer",
        displayNamePlural = "Customers",
        iconType = "CIRCLE_DASHED",
        iconColour = "NEUTRAL",
        semanticGroup = "CUSTOMER",
        identifierKey = "email",
        readonly = false,
        schema = mapOf("name" to mapOf("key" to "TEXT", "type" to "string")),
        columns = null,
        semantics = semantics
    )

    private fun createNormalizedRelationship() = NormalizedRelationship(
        key = "customer-deals",
        sourceEntityTypeKey = "customer",
        name = "Deals",
        iconType = "LINK",
        iconColour = "NEUTRAL",
        allowPolymorphic = false,
        cardinalityDefault = EntityRelationshipCardinality.ONE_TO_MANY,
        `protected` = false,
        targetRules = listOf(
            NormalizedTargetRule(targetEntityTypeKey = "deal")
        )
    )

    private fun createResolvedFieldMapping() = ResolvedFieldMapping(
        entityTypeKey = "customer",
        mappings = mapOf("email" to mapOf("source" to "email_address"))
    )

    private fun computeExpectedHash(resolved: ResolvedManifest): String {
        val content = objectMapper.writeValueAsString(
            mapOf(
                "name" to resolved.name,
                "description" to resolved.description,
                "manifestVersion" to resolved.manifestVersion,
                "entityTypes" to resolved.entityTypes,
                "relationships" to resolved.relationships,
                "fieldMappings" to resolved.fieldMappings
            )
        )
        return MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun mockEmptyChildren() {
        whenever(catalogRelationshipRepository.findByManifestId(any()))
            .thenReturn(emptyList())
        whenever(catalogEntityTypeRepository.findByManifestId(any()))
            .thenReturn(emptyList())
    }

    private fun mockExistingChildren() {
        val existingRelId = UUID.randomUUID()
        val existingEntityTypeId = UUID.randomUUID()
        whenever(catalogRelationshipRepository.findByManifestId(manifestId))
            .thenReturn(listOf(
                CatalogRelationshipEntity(
                    id = existingRelId,
                    manifestId = manifestId,
                    key = "old-rel",
                    sourceEntityTypeKey = "old-source",
                    name = "Old Rel",
                    cardinalityDefault = EntityRelationshipCardinality.ONE_TO_MANY
                )
            ))
        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(listOf(
                CatalogEntityTypeEntity(
                    id = existingEntityTypeId,
                    manifestId = manifestId,
                    key = "old-entity",
                    displayNameSingular = "Old",
                    displayNamePlural = "Olds",
                    schema = emptyMap()
                )
            ))
    }

    private fun mockChildSaves() {
        val savedEntityTypeId = UUID.randomUUID()
        whenever(catalogEntityTypeRepository.saveAll(any<List<CatalogEntityTypeEntity>>()))
            .thenReturn(listOf(
                CatalogEntityTypeEntity(
                    id = savedEntityTypeId,
                    manifestId = manifestId,
                    key = "customer",
                    displayNameSingular = "Customer",
                    displayNamePlural = "Customers",
                    schema = emptyMap()
                )
            ))
        val savedRelId = UUID.randomUUID()
        whenever(catalogRelationshipRepository.save(any<CatalogRelationshipEntity>()))
            .thenAnswer { invocation ->
                val entity = invocation.getArgument<CatalogRelationshipEntity>(0)
                entity.copy(id = savedRelId)
            }
    }
}
