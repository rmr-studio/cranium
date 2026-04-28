package riven.core.service.integration.materialization

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.entity.catalog.CatalogEntityTypeEntity
import riven.core.entity.catalog.CatalogRelationshipEntity
import riven.core.entity.catalog.CatalogRelationshipTargetRuleEntity
import riven.core.entity.catalog.ManifestCatalogEntity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.entity.entity.RelationshipDefinitionEntity
import riven.core.entity.entity.RelationshipTargetRuleEntity
import riven.core.enums.catalog.ManifestType
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.integration.SourceType
import riven.core.exceptions.NotFoundException
import riven.core.repository.catalog.CatalogEntityTypeRepository
import riven.core.repository.catalog.CatalogRelationshipRepository
import riven.core.repository.catalog.CatalogRelationshipTargetRuleRepository
import riven.core.repository.catalog.ManifestCatalogRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.RelationshipDefinitionRepository
import riven.core.repository.entity.RelationshipTargetRuleRepository
import riven.core.service.entity.EntityTypeSemanticMetadataService
import riven.core.service.entity.type.EntityTypeRelationshipService
import riven.core.service.entity.type.EntityTypeSequenceService
import java.util.*

/**
 * Unit tests for TemplateMaterializationService.
 *
 * Tests the catalog-to-workspace bridge including:
 * - Entity type materialization with UUID v3 deterministic key generation
 * - Relationship materialization with string-to-UUID key resolution
 * - Reconnection: restoring soft-deleted entity types
 * - Idempotency: skipping already-existing entity types
 * - Error handling for missing manifests
 */
@SpringBootTest(
    classes = [
        TemplateMaterializationServiceTest.TestConfig::class,
        TemplateMaterializationService::class
    ]
)
class TemplateMaterializationServiceTest {

    @Configuration
    class TestConfig

    private val workspaceId: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef9876543210")
    private val manifestId: UUID = UUID.fromString("a1b2c3d4-5e6f-7890-abcd-ef1234567890")
    private val integrationDefinitionId: UUID = UUID.fromString("b2c3d4e5-6f78-9012-abcd-ef2345678901")
    private val integrationSlug = "hubspot"

    @MockitoBean
    private lateinit var entityTypeRepository: EntityTypeRepository

    @MockitoBean
    private lateinit var relationshipDefinitionRepository: RelationshipDefinitionRepository

    @MockitoBean
    private lateinit var relationshipTargetRuleRepository: RelationshipTargetRuleRepository

    @MockitoBean
    private lateinit var catalogEntityTypeRepository: CatalogEntityTypeRepository

    @MockitoBean
    private lateinit var catalogRelationshipRepository: CatalogRelationshipRepository

    @MockitoBean
    private lateinit var catalogRelationshipTargetRuleRepository: CatalogRelationshipTargetRuleRepository

    @MockitoBean
    private lateinit var manifestCatalogRepository: ManifestCatalogRepository

    @MockitoBean
    private lateinit var projectionRuleRepository: riven.core.repository.integration.ProjectionRuleRepository

    @MockitoBean
    private lateinit var semanticMetadataService: EntityTypeSemanticMetadataService

    @MockitoBean
    private lateinit var relationshipService: EntityTypeRelationshipService

    @MockitoBean
    private lateinit var sequenceService: EntityTypeSequenceService

    @MockitoBean
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var logger: KLogger

    @Autowired
    private lateinit var service: TemplateMaterializationService

    private lateinit var manifestEntity: ManifestCatalogEntity
    private lateinit var catalogContactType: CatalogEntityTypeEntity
    private lateinit var catalogCompanyType: CatalogEntityTypeEntity

    @BeforeEach
    fun setup() {
        reset(
            entityTypeRepository, relationshipDefinitionRepository, relationshipTargetRuleRepository,
            catalogEntityTypeRepository, catalogRelationshipRepository, catalogRelationshipTargetRuleRepository,
            manifestCatalogRepository, semanticMetadataService, relationshipService, sequenceService
        )

        manifestEntity = ManifestCatalogEntity(
            id = manifestId,
            key = integrationSlug,
            name = "HubSpot",
            manifestType = ManifestType.INTEGRATION
        )

        catalogContactType = CatalogEntityTypeEntity(
            id = UUID.randomUUID(),
            manifestId = manifestId,
            key = "hubspot-contact",
            displayNameSingular = "HubSpot Contact",
            displayNamePlural = "HubSpot Contacts",
            iconType = IconType.USER,
            iconColour = IconColour.BLUE,
            semanticGroup = SemanticGroup.CUSTOMER,
            identifierKey = "email",
            readonly = true,
            schema = mapOf(
                "email" to mapOf(
                    "label" to "Email",
                    "key" to "EMAIL",
                    "type" to "string",
                    "format" to "email",
                    "required" to true,
                    "unique" to true,
                    "protected" to true
                ),
                "first-name" to mapOf(
                    "label" to "First Name",
                    "key" to "TEXT",
                    "type" to "string",
                    "required" to false,
                    "unique" to false,
                    "protected" to true
                )
            ),
            columns = listOf(
                mapOf("key" to "email", "type" to "ATTRIBUTE", "width" to 200),
                mapOf("key" to "first-name", "type" to "ATTRIBUTE", "width" to 150)
            )
        )

        catalogCompanyType = CatalogEntityTypeEntity(
            id = UUID.randomUUID(),
            manifestId = manifestId,
            key = "hubspot-company",
            displayNameSingular = "HubSpot Company",
            displayNamePlural = "HubSpot Companies",
            iconType = IconType.BUILDING,
            iconColour = IconColour.GREEN,
            semanticGroup = SemanticGroup.OPERATIONAL,
            identifierKey = "domain",
            readonly = true,
            schema = mapOf(
                "domain" to mapOf(
                    "label" to "Domain",
                    "key" to "URL",
                    "type" to "string",
                    "format" to "uri",
                    "required" to true,
                    "unique" to true,
                    "protected" to true
                ),
                "name" to mapOf(
                    "label" to "Company Name",
                    "key" to "TEXT",
                    "type" to "string",
                    "required" to true,
                    "unique" to false,
                    "protected" to true
                )
            )
        )

        // Default: manifest found
        whenever(manifestCatalogRepository.findByKeyAndManifestType(integrationSlug, ManifestType.INTEGRATION))
            .thenReturn(manifestEntity)

        // Default: no existing entity types
        whenever(entityTypeRepository.findByworkspaceIdAndKeyIn(eq(workspaceId), any()))
            .thenReturn(emptyList())

        // Default: no soft-deleted entity types
        whenever(entityTypeRepository.findSoftDeletedByWorkspaceIdAndKeyIn(eq(workspaceId), any()))
            .thenReturn(emptyList())

        // Default: no existing relationships
        whenever(relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeIdIn(eq(workspaceId), any()))
            .thenReturn(emptyList())

        // Default: createFallbackDefinition returns a stub entity
        whenever(relationshipService.createFallbackDefinition(any(), any())).thenReturn(
            RelationshipDefinitionEntity(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                sourceEntityTypeId = UUID.randomUUID(),
                name = "Connected Entities",
                cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY
            )
        )

        // Default: save returns the entity with an ID
        whenever(entityTypeRepository.save(any<EntityTypeEntity>())).thenAnswer { invocation ->
            val entity = invocation.getArgument<EntityTypeEntity>(0)
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }

        whenever(relationshipDefinitionRepository.save(any<RelationshipDefinitionEntity>())).thenAnswer { invocation ->
            val entity = invocation.getArgument<RelationshipDefinitionEntity>(0)
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }

        whenever(relationshipTargetRuleRepository.save(any<RelationshipTargetRuleEntity>())).thenAnswer { invocation ->
            val entity = invocation.getArgument<RelationshipTargetRuleEntity>(0)
            if (entity.id == null) entity.copy(id = UUID.randomUUID()) else entity
        }
    }

    // ========== Entity Type Materialization Tests ==========

    @Test
    fun `materializeIntegrationTemplates - creates entity types from catalog definitions`() {
        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(listOf(catalogContactType, catalogCompanyType))
        whenever(catalogRelationshipRepository.findByManifestId(manifestId))
            .thenReturn(emptyList())

        val result = service.materializeIntegrationTemplates(workspaceId, integrationSlug, integrationDefinitionId)

        assertEquals(2, result.entityTypesCreated)
        assertEquals(0, result.entityTypesRestored)
        assertEquals(integrationSlug, result.integrationSlug)

        verify(entityTypeRepository, times(2)).save(argThat { entity ->
            entity.sourceType == SourceType.INTEGRATION &&
                entity.readonly == true &&
                entity.`protected` == true &&
                entity.workspaceId == workspaceId
        })
    }

    @Test
    fun `materializeIntegrationTemplates - generates deterministic UUID v3 for attribute keys`() {
        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(listOf(catalogContactType))
        whenever(catalogRelationshipRepository.findByManifestId(manifestId))
            .thenReturn(emptyList())

        service.materializeIntegrationTemplates(workspaceId, integrationSlug, integrationDefinitionId)

        // UUID v3 is deterministic: same input = same UUID
        val expectedEmailUuid = UUID.nameUUIDFromBytes("$integrationSlug:hubspot-contact:email".toByteArray())
        val expectedFirstNameUuid = UUID.nameUUIDFromBytes("$integrationSlug:hubspot-contact:first-name".toByteArray())

        verify(entityTypeRepository).save(argThat { entity ->
            entity.key == "hubspot-contact" &&
                entity.schema.properties != null &&
                entity.schema.properties!!.containsKey(expectedEmailUuid) &&
                entity.schema.properties!!.containsKey(expectedFirstNameUuid)
        })
    }

    @Test
    fun `materializeIntegrationTemplates - converts identifierKey string to deterministic UUID`() {
        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(listOf(catalogContactType))
        whenever(catalogRelationshipRepository.findByManifestId(manifestId))
            .thenReturn(emptyList())

        service.materializeIntegrationTemplates(workspaceId, integrationSlug, integrationDefinitionId)

        val expectedIdentifierUuid = UUID.nameUUIDFromBytes("$integrationSlug:hubspot-contact:email".toByteArray())

        verify(entityTypeRepository).save(argThat { entity ->
            entity.key == "hubspot-contact" &&
                entity.identifierKey == expectedIdentifierUuid
        })
    }

    @Test
    fun `materializeIntegrationTemplates - builds columns from schema with UUID keys`() {
        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(listOf(catalogContactType))
        whenever(catalogRelationshipRepository.findByManifestId(manifestId))
            .thenReturn(emptyList())

        service.materializeIntegrationTemplates(workspaceId, integrationSlug, integrationDefinitionId)

        val expectedEmailUuid = UUID.nameUUIDFromBytes("hubspot:hubspot-contact:email".toByteArray())
        val expectedFirstNameUuid = UUID.nameUUIDFromBytes("hubspot:hubspot-contact:first-name".toByteArray())

        verify(entityTypeRepository).save(argThat { entity ->
            entity.columnConfiguration != null &&
                entity.columnConfiguration!!.order == listOf(expectedEmailUuid, expectedFirstNameUuid)
        })
    }

    @Test
    fun `materializeIntegrationTemplates - throws NotFoundException when manifest not found`() {
        whenever(manifestCatalogRepository.findByKeyAndManifestType(integrationSlug, ManifestType.INTEGRATION))
            .thenReturn(null)

        assertThrows(NotFoundException::class.java) {
            service.materializeIntegrationTemplates(workspaceId, integrationSlug, integrationDefinitionId)
        }

        verify(entityTypeRepository, never()).save(any<EntityTypeEntity>())
    }

    @Test
    fun `materializeIntegrationTemplates - skips already existing non-deleted entity types`() {
        val existingEntityType = EntityTypeEntity(
            id = UUID.randomUUID(),
            key = "hubspot-contact",
            displayNameSingular = "HubSpot Contact",
            displayNamePlural = "HubSpot Contacts",
            identifierKey = UUID.randomUUID(),
            workspaceId = workspaceId,
            sourceType = SourceType.INTEGRATION,
            readonly = true,
            schema = riven.core.models.common.validation.Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT
            ),
        )

        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(listOf(catalogContactType, catalogCompanyType))
        whenever(catalogRelationshipRepository.findByManifestId(manifestId))
            .thenReturn(emptyList())
        whenever(entityTypeRepository.findByworkspaceIdAndKeyIn(eq(workspaceId), any()))
            .thenReturn(listOf(existingEntityType))

        val result = service.materializeIntegrationTemplates(workspaceId, integrationSlug, integrationDefinitionId)

        // Only the company type should be created (contact already exists)
        assertEquals(1, result.entityTypesCreated)
        assertEquals(0, result.entityTypesRestored)
    }

    // ========== Reconnection Tests ==========

    @Test
    fun `materializeIntegrationTemplates - restores soft-deleted entity types on reconnection`() {
        val softDeletedEntityType = EntityTypeEntity(
            id = UUID.randomUUID(),
            key = "hubspot-contact",
            displayNameSingular = "HubSpot Contact",
            displayNamePlural = "HubSpot Contacts",
            identifierKey = UUID.randomUUID(),
            workspaceId = workspaceId,
            sourceType = SourceType.INTEGRATION,
            readonly = true,
            schema = riven.core.models.common.validation.Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT
            ),
        )

        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(listOf(catalogContactType))
        whenever(catalogRelationshipRepository.findByManifestId(manifestId))
            .thenReturn(emptyList())
        whenever(entityTypeRepository.findSoftDeletedByWorkspaceIdAndKeyIn(eq(workspaceId), any()))
            .thenReturn(listOf(softDeletedEntityType))

        val result = service.materializeIntegrationTemplates(workspaceId, integrationSlug, integrationDefinitionId)

        assertEquals(0, result.entityTypesCreated)
        assertEquals(1, result.entityTypesRestored)

        // Should save the restored entity type (with deleted=false)
        verify(entityTypeRepository).save(argThat { entity ->
            entity.key == "hubspot-contact" && !entity.deleted
        })
    }

    /**
     * Regression: restoreEntityType used to stamp only sourceSchemaHash, leaving sourceManifestId
     * as whatever the soft-deleted row already had (null for pre-provenance rows). That broke
     * reconciliation traceability after restore. The fix also stamps the catalog manifest pointer
     * so restored rows match newly created rows on traceability.
     */
    @Test
    fun `materializeIntegrationTemplates - stamps sourceManifestId and sourceSchemaHash on restored entity type`() {
        val softDeletedEntityType = EntityTypeEntity(
            id = UUID.randomUUID(),
            key = "hubspot-contact",
            displayNameSingular = "HubSpot Contact",
            displayNamePlural = "HubSpot Contacts",
            identifierKey = UUID.randomUUID(),
            workspaceId = workspaceId,
            sourceType = SourceType.INTEGRATION,
            sourceManifestId = null,
            sourceSchemaHash = null,
            readonly = true,
            schema = riven.core.models.common.validation.Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT
            ),
        )
        val catalogWithHash = catalogContactType.copy(schemaHash = "catalog-hash-v1")

        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(listOf(catalogWithHash))
        whenever(catalogRelationshipRepository.findByManifestId(manifestId))
            .thenReturn(emptyList())
        whenever(entityTypeRepository.findSoftDeletedByWorkspaceIdAndKeyIn(eq(workspaceId), any()))
            .thenReturn(listOf(softDeletedEntityType))

        service.materializeIntegrationTemplates(workspaceId, integrationSlug, integrationDefinitionId)

        verify(entityTypeRepository).save(argThat { entity ->
            entity.key == "hubspot-contact" &&
                entity.sourceManifestId == manifestId &&
                entity.sourceSchemaHash == "catalog-hash-v1"
        })
    }

    // ========== Relationship Materialization Tests ==========

    @Test
    fun `materializeIntegrationTemplates - creates relationships with resolved entity type UUIDs`() {
        val contactEntityId = UUID.randomUUID()
        val companyEntityId = UUID.randomUUID()

        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(listOf(catalogContactType, catalogCompanyType))

        val catalogRelationship = CatalogRelationshipEntity(
            id = UUID.randomUUID(),
            manifestId = manifestId,
            key = "contact-to-company",
            sourceEntityTypeKey = "hubspot-contact",
            name = "Company",
            iconType = IconType.BUILDING,
            iconColour = IconColour.GREEN,
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_ONE,
            `protected` = true
        )

        val catalogTargetRule = CatalogRelationshipTargetRuleEntity(
            id = UUID.randomUUID(),
            catalogRelationshipId = catalogRelationship.id!!,
            targetEntityTypeKey = "hubspot-company",
            inverseName = "Contacts"
        )

        whenever(catalogRelationshipRepository.findByManifestId(manifestId))
            .thenReturn(listOf(catalogRelationship))
        whenever(catalogRelationshipTargetRuleRepository.findByCatalogRelationshipIdIn(listOf(catalogRelationship.id!!)))
            .thenReturn(listOf(catalogTargetRule))

        // Make entity type saves return entities with known IDs
        whenever(entityTypeRepository.save(any<EntityTypeEntity>())).thenAnswer { invocation ->
            val entity = invocation.getArgument<EntityTypeEntity>(0)
            when (entity.key) {
                "hubspot-contact" -> entity.copy(id = contactEntityId)
                "hubspot-company" -> entity.copy(id = companyEntityId)
                else -> entity.copy(id = UUID.randomUUID())
            }
        }

        val result = service.materializeIntegrationTemplates(workspaceId, integrationSlug, integrationDefinitionId)

        assertEquals(1, result.relationshipsCreated)

        // Verify relationship uses workspace entity type UUIDs
        verify(relationshipDefinitionRepository).save(argThat { rel ->
            rel.sourceEntityTypeId == contactEntityId &&
                rel.workspaceId == workspaceId &&
                rel.name == "Company" &&
                rel.`protected` == true
        })

        // Verify target rule uses workspace entity type UUID
        verify(relationshipTargetRuleRepository).save(argThat { rule ->
            rule.targetEntityTypeId == companyEntityId &&
                rule.inverseName == "Contacts"
        })
    }

    @Test
    fun `materializeIntegrationTemplates - handles polymorphic relationships with multiple target rules`() {
        val contactEntityId = UUID.randomUUID()
        val dealEntityId = UUID.randomUUID()
        val ticketEntityId = UUID.randomUUID()

        val catalogNoteType = CatalogEntityTypeEntity(
            id = UUID.randomUUID(),
            manifestId = manifestId,
            key = "hubspot-note",
            displayNameSingular = "HubSpot Note",
            displayNamePlural = "HubSpot Notes",
            semanticGroup = SemanticGroup.UNCATEGORIZED,
            readonly = true,
            schema = mapOf(
                "body" to mapOf(
                    "label" to "Body",
                    "key" to "TEXT",
                    "type" to "string",
                    "required" to true,
                    "unique" to false,
                    "protected" to true
                )
            )
        )

        val catalogContactForNote = catalogContactType
        val catalogDealType = CatalogEntityTypeEntity(
            id = UUID.randomUUID(),
            manifestId = manifestId,
            key = "hubspot-deal",
            displayNameSingular = "HubSpot Deal",
            displayNamePlural = "HubSpot Deals",
            semanticGroup = SemanticGroup.UNCATEGORIZED,
            readonly = true,
            schema = emptyMap()
        )
        val catalogTicketType = CatalogEntityTypeEntity(
            id = UUID.randomUUID(),
            manifestId = manifestId,
            key = "hubspot-ticket",
            displayNameSingular = "HubSpot Ticket",
            displayNamePlural = "HubSpot Tickets",
            semanticGroup = SemanticGroup.UNCATEGORIZED,
            readonly = true,
            schema = emptyMap()
        )

        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(listOf(catalogContactForNote, catalogDealType, catalogTicketType, catalogNoteType))

        val polymorphicRelationship = CatalogRelationshipEntity(
            id = UUID.randomUUID(),
            manifestId = manifestId,
            key = "note-associations",
            sourceEntityTypeKey = "hubspot-note",
            name = "Associated With",
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            `protected` = true
        )

        val targetRuleContact = CatalogRelationshipTargetRuleEntity(
            id = UUID.randomUUID(),
            catalogRelationshipId = polymorphicRelationship.id!!,
            targetEntityTypeKey = "hubspot-contact",
            inverseName = "Notes"
        )
        val targetRuleDeal = CatalogRelationshipTargetRuleEntity(
            id = UUID.randomUUID(),
            catalogRelationshipId = polymorphicRelationship.id!!,
            targetEntityTypeKey = "hubspot-deal",
            inverseName = "Notes"
        )
        val targetRuleTicket = CatalogRelationshipTargetRuleEntity(
            id = UUID.randomUUID(),
            catalogRelationshipId = polymorphicRelationship.id!!,
            targetEntityTypeKey = "hubspot-ticket",
            inverseName = "Notes"
        )

        whenever(catalogRelationshipRepository.findByManifestId(manifestId))
            .thenReturn(listOf(polymorphicRelationship))
        whenever(catalogRelationshipTargetRuleRepository.findByCatalogRelationshipIdIn(listOf(polymorphicRelationship.id!!)))
            .thenReturn(listOf(targetRuleContact, targetRuleDeal, targetRuleTicket))

        whenever(entityTypeRepository.save(any<EntityTypeEntity>())).thenAnswer { invocation ->
            val entity = invocation.getArgument<EntityTypeEntity>(0)
            when (entity.key) {
                "hubspot-contact" -> entity.copy(id = contactEntityId)
                "hubspot-deal" -> entity.copy(id = dealEntityId)
                "hubspot-ticket" -> entity.copy(id = ticketEntityId)
                else -> entity.copy(id = UUID.randomUUID())
            }
        }

        val result = service.materializeIntegrationTemplates(workspaceId, integrationSlug, integrationDefinitionId)

        assertEquals(1, result.relationshipsCreated)

        // Verify 3 target rules were created
        verify(relationshipTargetRuleRepository, times(3)).save(any<RelationshipTargetRuleEntity>())
    }

    // ========== UUID v3 Determinism Tests ==========

    @Test
    fun `UUID v3 generation is deterministic - same input always produces same UUID`() {
        // This validates the deterministic key generation approach
        val uuid1 = UUID.nameUUIDFromBytes("hubspot:hubspot-contact:email".toByteArray())
        val uuid2 = UUID.nameUUIDFromBytes("hubspot:hubspot-contact:email".toByteArray())

        assertEquals(uuid1, uuid2, "UUID v3 should be deterministic")

        // Different input produces different UUID
        val uuid3 = UUID.nameUUIDFromBytes("hubspot:hubspot-contact:first-name".toByteArray())
        assertNotEquals(uuid1, uuid3, "Different input should produce different UUID")
    }

    @Test
    fun `materializeIntegrationTemplates - returns zero counts when no catalog types exist`() {
        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(emptyList())
        whenever(catalogRelationshipRepository.findByManifestId(manifestId))
            .thenReturn(emptyList())

        val result = service.materializeIntegrationTemplates(workspaceId, integrationSlug, integrationDefinitionId)

        assertEquals(0, result.entityTypesCreated)
        assertEquals(0, result.entityTypesRestored)
        assertEquals(0, result.relationshipsCreated)
        verify(entityTypeRepository, never()).save(any<EntityTypeEntity>())
    }

    // ========== Post-Creation Initialization Tests ==========

    @Test
    fun `materializeIntegrationTemplates - initializes semantic metadata for created entity types`() {
        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(listOf(catalogContactType))
        whenever(catalogRelationshipRepository.findByManifestId(manifestId))
            .thenReturn(emptyList())

        val contactEntityId = UUID.randomUUID()
        whenever(entityTypeRepository.save(any<EntityTypeEntity>())).thenAnswer { invocation ->
            val entity = invocation.getArgument<EntityTypeEntity>(0)
            entity.copy(id = contactEntityId)
        }

        service.materializeIntegrationTemplates(workspaceId, integrationSlug, integrationDefinitionId)

        val expectedEmailUuid = UUID.nameUUIDFromBytes("$integrationSlug:hubspot-contact:email".toByteArray())
        val expectedFirstNameUuid = UUID.nameUUIDFromBytes("$integrationSlug:hubspot-contact:first-name".toByteArray())

        verify(semanticMetadataService).initializeForEntityType(
            entityTypeId = eq(contactEntityId),
            workspaceId = eq(workspaceId),
            attributeIds = argThat { ids ->
                ids.size == 2 && ids.contains(expectedEmailUuid) && ids.contains(expectedFirstNameUuid)
            }
        )
    }

    @Test
    fun `materializeIntegrationTemplates - creates fallback relationship definition for each entity type`() {
        val contactEntityId = UUID.randomUUID()
        val companyEntityId = UUID.randomUUID()

        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(listOf(catalogContactType, catalogCompanyType))
        whenever(catalogRelationshipRepository.findByManifestId(manifestId))
            .thenReturn(emptyList())

        whenever(entityTypeRepository.save(any<EntityTypeEntity>())).thenAnswer { invocation ->
            val entity = invocation.getArgument<EntityTypeEntity>(0)
            when (entity.key) {
                "hubspot-contact" -> entity.copy(id = contactEntityId)
                "hubspot-company" -> entity.copy(id = companyEntityId)
                else -> entity.copy(id = UUID.randomUUID())
            }
        }

        service.materializeIntegrationTemplates(workspaceId, integrationSlug, integrationDefinitionId)

        verify(relationshipService).createFallbackDefinition(workspaceId, contactEntityId)
        verify(relationshipService).createFallbackDefinition(workspaceId, companyEntityId)
    }

    @Test
    fun `materializeIntegrationTemplates - initializes sequence for ID-type attributes`() {
        val catalogWithIdAttr = CatalogEntityTypeEntity(
            id = UUID.randomUUID(),
            manifestId = manifestId,
            key = "hubspot-ticket",
            displayNameSingular = "HubSpot Ticket",
            displayNamePlural = "HubSpot Tickets",
            semanticGroup = SemanticGroup.UNCATEGORIZED,
            readonly = true,
            schema = mapOf(
                "ticket-id" to mapOf(
                    "label" to "Ticket ID",
                    "key" to "ID",
                    "type" to "string",
                    "required" to true,
                    "unique" to true,
                    "protected" to true
                ),
                "subject" to mapOf(
                    "label" to "Subject",
                    "key" to "TEXT",
                    "type" to "string",
                    "required" to false,
                    "unique" to false,
                    "protected" to true
                )
            )
        )

        val ticketEntityId = UUID.randomUUID()

        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(listOf(catalogWithIdAttr))
        whenever(catalogRelationshipRepository.findByManifestId(manifestId))
            .thenReturn(emptyList())
        whenever(entityTypeRepository.save(any<EntityTypeEntity>())).thenAnswer { invocation ->
            invocation.getArgument<EntityTypeEntity>(0).copy(id = ticketEntityId)
        }

        service.materializeIntegrationTemplates(workspaceId, integrationSlug, integrationDefinitionId)

        val expectedTicketIdUuid = UUID.nameUUIDFromBytes("$integrationSlug:hubspot-ticket:ticket-id".toByteArray())
        verify(sequenceService).initializeSequence(ticketEntityId, expectedTicketIdUuid)

        // TEXT attribute should NOT trigger sequence initialization
        val expectedSubjectUuid = UUID.nameUUIDFromBytes("$integrationSlug:hubspot-ticket:subject".toByteArray())
        verify(sequenceService, never()).initializeSequence(ticketEntityId, expectedSubjectUuid)
    }

    @Test
    fun `materializeIntegrationTemplates - skips semantic metadata but recreates fallback relationship for restored entity types`() {
        val restoredId = UUID.randomUUID()
        val softDeletedEntityType = EntityTypeEntity(
            id = restoredId,
            key = "hubspot-contact",
            displayNameSingular = "HubSpot Contact",
            displayNamePlural = "HubSpot Contacts",
            identifierKey = UUID.randomUUID(),
            workspaceId = workspaceId,
            sourceType = SourceType.INTEGRATION,
            readonly = true,
            schema = riven.core.models.common.validation.Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT
            ),
        )

        whenever(catalogEntityTypeRepository.findByManifestId(manifestId))
            .thenReturn(listOf(catalogContactType))
        whenever(catalogRelationshipRepository.findByManifestId(manifestId))
            .thenReturn(emptyList())
        whenever(entityTypeRepository.findSoftDeletedByWorkspaceIdAndKeyIn(eq(workspaceId), any()))
            .thenReturn(listOf(softDeletedEntityType))

        service.materializeIntegrationTemplates(workspaceId, integrationSlug, integrationDefinitionId)

        // Semantic metadata should NOT be re-initialized for restored entity types
        verify(semanticMetadataService, never()).initializeForEntityType(any(), any(), any())
        verify(sequenceService, never()).initializeSequence(any(), any())

        // Fallback relationship SHOULD be recreated since the old one was soft-deleted
        verify(relationshipService).createFallbackDefinition(workspaceId, restoredId)
    }
}
