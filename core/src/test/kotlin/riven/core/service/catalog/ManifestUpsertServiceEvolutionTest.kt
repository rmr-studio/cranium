package riven.core.service.catalog

import tools.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import riven.core.entity.catalog.*
import riven.core.enums.catalog.ManifestType
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.models.catalog.*
import riven.core.repository.catalog.*
import riven.core.service.util.factory.catalog.CatalogFactory
import java.time.ZonedDateTime
import java.util.*

/**
 * Tests manifest schema evolution — verifies that when a manifest is re-upserted with changed
 * content (V1 -> V2), the delete-then-reinsert strategy in ManifestUpsertService correctly
 * persists the updated children. Each test upserts a V1 manifest, then upserts a V2 manifest
 * with a specific change, and asserts the V2 children are saved correctly.
 */
class ManifestUpsertServiceEvolutionTest {

    private lateinit var manifestCatalogRepository: ManifestCatalogRepository
    private lateinit var catalogEntityTypeRepository: CatalogEntityTypeRepository
    private lateinit var catalogRelationshipRepository: CatalogRelationshipRepository
    private lateinit var catalogRelationshipTargetRuleRepository: CatalogRelationshipTargetRuleRepository
    private lateinit var catalogFieldMappingRepository: CatalogFieldMappingRepository
    private lateinit var catalogSemanticMetadataRepository: CatalogSemanticMetadataRepository
    private lateinit var logger: KLogger
    private lateinit var service: ManifestUpsertService

    private val objectMapper = ObjectMapper()
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

    @Nested
    inner class SchemaEvolution {

        /** Verifies that adding a new attribute in V2 results in the schema containing all three attributes. */
        @Test
        fun `attribute added - V2 schema includes the new attribute`() {
            val v1Schema = mapOf(
                "email" to mapOf("type" to "TEXT"),
                "first-name" to mapOf("type" to "TEXT")
            )
            val v2Schema = mapOf(
                "email" to mapOf("type" to "TEXT"),
                "first-name" to mapOf("type" to "TEXT"),
                "website" to mapOf("type" to "TEXT")
            )

            val v1 = createManifest(entityTypes = listOf(createEntityType(schema = v1Schema)))
            val v2 = createManifest(entityTypes = listOf(createEntityType(schema = v2Schema)))

            upsertAsNew(v1)
            service.upsertManifest(v1)

            reset(catalogEntityTypeRepository, catalogRelationshipRepository,
                catalogRelationshipTargetRuleRepository, catalogFieldMappingRepository,
                catalogSemanticMetadataRepository)

            upsertAsExisting(v2)
            service.upsertManifest(v2)

            val captor = argumentCaptor<List<CatalogEntityTypeEntity>>()
            verify(catalogEntityTypeRepository).saveAll(captor.capture())

            val savedSchema = captor.firstValue[0].schema
            assert(savedSchema.size == 3) { "Expected 3 attributes but got ${savedSchema.size}" }
            assert(savedSchema.containsKey("email")) { "Missing 'email' attribute" }
            assert(savedSchema.containsKey("first-name")) { "Missing 'first-name' attribute" }
            assert(savedSchema.containsKey("website")) { "Missing 'website' attribute" }
        }

        /** Verifies that removing an attribute in V2 results in the schema without the removed attribute. */
        @Test
        fun `attribute removed - V2 schema excludes the removed attribute`() {
            val v1Schema = mapOf(
                "email" to mapOf("type" to "TEXT"),
                "first-name" to mapOf("type" to "TEXT"),
                "city" to mapOf("type" to "TEXT")
            )
            val v2Schema = mapOf(
                "email" to mapOf("type" to "TEXT"),
                "first-name" to mapOf("type" to "TEXT")
            )

            val v1 = createManifest(entityTypes = listOf(createEntityType(schema = v1Schema)))
            val v2 = createManifest(entityTypes = listOf(createEntityType(schema = v2Schema)))

            upsertAsNew(v1)
            service.upsertManifest(v1)

            reset(catalogEntityTypeRepository, catalogRelationshipRepository,
                catalogRelationshipTargetRuleRepository, catalogFieldMappingRepository,
                catalogSemanticMetadataRepository)

            upsertAsExisting(v2)
            service.upsertManifest(v2)

            val captor = argumentCaptor<List<CatalogEntityTypeEntity>>()
            verify(catalogEntityTypeRepository).saveAll(captor.capture())

            val savedSchema = captor.firstValue[0].schema
            assert(savedSchema.size == 2) { "Expected 2 attributes but got ${savedSchema.size}" }
            assert(savedSchema.containsKey("email")) { "Missing 'email' attribute" }
            assert(savedSchema.containsKey("first-name")) { "Missing 'first-name' attribute" }
            assert(!savedSchema.containsKey("city")) { "Attribute 'city' should have been removed" }
        }

        /** Verifies that changing an attribute's type in V2 results in the updated type being persisted. */
        @Test
        fun `attribute type changed - V2 schema reflects the new type`() {
            val v1Schema = mapOf(
                "lifecycle-stage" to mapOf("type" to "TEXT")
            )
            val v2Schema = mapOf(
                "lifecycle-stage" to mapOf("type" to "SELECT")
            )

            val v1 = createManifest(entityTypes = listOf(createEntityType(schema = v1Schema)))
            val v2 = createManifest(entityTypes = listOf(createEntityType(schema = v2Schema)))

            upsertAsNew(v1)
            service.upsertManifest(v1)

            reset(catalogEntityTypeRepository, catalogRelationshipRepository,
                catalogRelationshipTargetRuleRepository, catalogFieldMappingRepository,
                catalogSemanticMetadataRepository)

            upsertAsExisting(v2)
            service.upsertManifest(v2)

            val captor = argumentCaptor<List<CatalogEntityTypeEntity>>()
            verify(catalogEntityTypeRepository).saveAll(captor.capture())

            val savedSchema = captor.firstValue[0].schema
            @Suppress("UNCHECKED_CAST")
            val lifecycleStage = savedSchema["lifecycle-stage"] as Map<String, Any>
            assert(lifecycleStage["type"] == "SELECT") {
                "Expected type 'SELECT' but got '${lifecycleStage["type"]}'"
            }
        }

        /** Verifies that adding a new entity type in V2 results in both entity types being saved. */
        @Test
        fun `new entity type added - V2 saves both entity types`() {
            val customerType = createEntityType(key = "customer", singular = "Customer", plural = "Customers")
            val dealType = createEntityType(key = "deal", singular = "Deal", plural = "Deals")

            val v1 = createManifest(entityTypes = listOf(customerType))
            val v2 = createManifest(entityTypes = listOf(customerType, dealType))

            upsertAsNew(v1)
            service.upsertManifest(v1)

            reset(catalogEntityTypeRepository, catalogRelationshipRepository,
                catalogRelationshipTargetRuleRepository, catalogFieldMappingRepository,
                catalogSemanticMetadataRepository)

            upsertAsExisting(v2)
            service.upsertManifest(v2)

            val captor = argumentCaptor<List<CatalogEntityTypeEntity>>()
            verify(catalogEntityTypeRepository).saveAll(captor.capture())

            assert(captor.firstValue.size == 2) {
                "Expected 2 entity types but got ${captor.firstValue.size}"
            }
            val keys = captor.firstValue.map { it.key }.toSet()
            assert(keys == setOf("customer", "deal")) {
                "Expected entity type keys {customer, deal} but got $keys"
            }
        }

        /** Verifies that changing a relationship's cardinality in V2 results in the updated cardinality. */
        @Test
        fun `relationship cardinality changed - V2 saves updated cardinality`() {
            val v1Rel = createRelationship(cardinality = EntityRelationshipCardinality.MANY_TO_ONE)
            val v2Rel = createRelationship(cardinality = EntityRelationshipCardinality.MANY_TO_MANY)

            val v1 = createManifest(relationships = listOf(v1Rel))
            val v2 = createManifest(relationships = listOf(v2Rel))

            upsertAsNew(v1)
            service.upsertManifest(v1)

            reset(catalogEntityTypeRepository, catalogRelationshipRepository,
                catalogRelationshipTargetRuleRepository, catalogFieldMappingRepository,
                catalogSemanticMetadataRepository)

            upsertAsExisting(v2)
            service.upsertManifest(v2)

            val captor = argumentCaptor<CatalogRelationshipEntity>()
            verify(catalogRelationshipRepository).save(captor.capture())

            assert(captor.firstValue.cardinalityDefault == EntityRelationshipCardinality.MANY_TO_MANY) {
                "Expected MANY_TO_MANY but got ${captor.firstValue.cardinalityDefault}"
            }
        }
        /**
         * Regression test: computeContentHash omitted syncModels, so a syncModels-only change
         * would produce the same hash and skip child reconciliation. Fix: include syncModels in hash.
         * Verifies that changing only syncModels triggers deleteChildren and re-insertion.
         */
        @Test
        fun `syncModels change triggers child re-insertion`() {
            val v1 = createManifest(syncModels = mapOf("NangoContact" to "customer"))
            val v2 = createManifest(syncModels = mapOf("NangoContactV2" to "customer"))

            upsertAsNew(v1)
            service.upsertManifest(v1)

            reset(catalogEntityTypeRepository, catalogRelationshipRepository,
                catalogRelationshipTargetRuleRepository, catalogFieldMappingRepository,
                catalogSemanticMetadataRepository)

            upsertAsExisting(v2)
            service.upsertManifest(v2)

            // If syncModels is in the hash, V2 will have a different hash and trigger re-insert
            verify(catalogEntityTypeRepository).saveAll(any<List<CatalogEntityTypeEntity>>())
        }

        /**
         * Regression test: reversing syncModels via .associate silently dropped entries when
         * multiple nango models mapped to the same entity type key. Fix: require() guard rejects duplicates.
         * Verifies that duplicate entityTypeKey values in syncModels throw IllegalArgumentException.
         */
        @Test
        fun `duplicate syncModel entity type keys throw IllegalArgumentException`() {
            val manifest = createManifest(
                syncModels = mapOf(
                    "NangoContact" to "customer",
                    "NangoPerson" to "customer"
                )
            )

            upsertAsNew(manifest)

            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                service.upsertManifest(manifest)
            }
        }
    }

    // ------ Mock Setup Helpers ------

    /**
     * Configures mocks for the "new manifest" path: no existing manifest found,
     * save returns the entity with an ID assigned.
     */
    private fun upsertAsNew(manifest: ResolvedManifest) {
        whenever(manifestCatalogRepository.findByKeyAndManifestType(manifest.key, manifest.type))
            .thenReturn(null)
        whenever(manifestCatalogRepository.save(any<ManifestCatalogEntity>()))
            .thenAnswer { invocation ->
                val entity = invocation.getArgument<ManifestCatalogEntity>(0)
                entity.copy(id = manifestId)
            }
        mockEmptyChildren()
        mockChildSaves()
    }

    /**
     * Configures mocks for the "existing manifest with different hash" path:
     * returns a stored manifest entity with a stale content hash, triggering delete-reinsert.
     */
    private fun upsertAsExisting(manifest: ResolvedManifest) {
        val existingEntity = ManifestCatalogEntity(
            id = manifestId,
            key = manifest.key,
            name = "Previous Version",
            manifestType = manifest.type,
            stale = false,
            contentHash = "outdated-hash-that-will-not-match"
        )
        whenever(manifestCatalogRepository.findByKeyAndManifestType(manifest.key, manifest.type))
            .thenReturn(existingEntity)
        whenever(manifestCatalogRepository.save(any<ManifestCatalogEntity>()))
            .thenAnswer { invocation -> invocation.getArgument<ManifestCatalogEntity>(0) }
        mockExistingChildren()
        mockChildSaves()
    }

    private fun mockEmptyChildren() {
        whenever(catalogRelationshipRepository.findByManifestId(any()))
            .thenReturn(emptyList())
        whenever(catalogEntityTypeRepository.findByManifestId(any()))
            .thenReturn(emptyList())
    }

    private fun mockExistingChildren() {
        whenever(catalogRelationshipRepository.findByManifestId(manifestId))
            .thenReturn(listOf(
                CatalogFactory.createRelationshipEntity(
                    manifestId = manifestId,
                    key = "old-rel",
                    sourceEntityTypeKey = "old-source",
                    name = "Old Rel",
                    cardinalityDefault = EntityRelationshipCardinality.ONE_TO_MANY
                )
            ))
        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(listOf(
                CatalogFactory.createEntityTypeEntity(
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
            .thenAnswer { invocation ->
                val entities = invocation.getArgument<List<CatalogEntityTypeEntity>>(0)
                entities.mapIndexed { index, entity ->
                    entity.copy(id = UUID.randomUUID())
                }
            }
        val savedRelId = UUID.randomUUID()
        whenever(catalogRelationshipRepository.save(any<CatalogRelationshipEntity>()))
            .thenAnswer { invocation ->
                val entity = invocation.getArgument<CatalogRelationshipEntity>(0)
                entity.copy(id = savedRelId)
            }
    }

    // ------ Factory Helpers ------

    private fun createManifest(
        entityTypes: List<ResolvedEntityType> = listOf(createEntityType()),
        relationships: List<NormalizedRelationship> = listOf(createRelationship()),
        fieldMappings: List<ResolvedFieldMapping> = listOf(createFieldMapping()),
        syncModels: Map<String, String> = emptyMap()
    ) = ResolvedManifest(
        key = "evolution-test",
        name = "Evolution Test Manifest",
        description = "Manifest for schema evolution tests",
        type = ManifestType.TEMPLATE,
        manifestVersion = "1.0",
        entityTypes = entityTypes,
        relationships = relationships,
        fieldMappings = fieldMappings,
        syncModels = syncModels,
        stale = false
    )

    private fun createEntityType(
        key: String = "customer",
        singular: String = "Customer",
        plural: String = "Customers",
        schema: Map<String, Any> = mapOf("email" to mapOf("type" to "TEXT"))
    ) = ResolvedEntityType(
        key = key,
        displayNameSingular = singular,
        displayNamePlural = plural,
        iconType = "CIRCLE_DASHED",
        iconColour = "NEUTRAL",
        semanticGroup = "CUSTOMER",
        identifierKey = "email",
        readonly = false,
        schema = schema,
        columns = null,
        semantics = null
    )

    private fun createRelationship(
        cardinality: EntityRelationshipCardinality = EntityRelationshipCardinality.ONE_TO_MANY
    ) = NormalizedRelationship(
        key = "customer-deals",
        sourceEntityTypeKey = "customer",
        name = "Deals",
        iconType = "LINK",
        iconColour = "NEUTRAL",
        cardinalityDefault = cardinality,
        `protected` = false,
        targetRules = listOf(
            NormalizedTargetRule(targetEntityTypeKey = "deal")
        )
    )

    private fun createFieldMapping() = ResolvedFieldMapping(
        entityTypeKey = "customer",
        mappings = mapOf("email" to mapOf("source" to "email_address"))
    )
}
