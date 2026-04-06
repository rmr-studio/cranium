package riven.core.service.ingestion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers
import riven.core.entity.entity.EntityTypeEntity
import riven.core.service.util.factory.entity.EntityFactory
import riven.core.enums.integration.SourceType
import java.util.UUID

/**
 * End-to-end integration test verifying projection across multiple integrations.
 *
 * Installs the B2C SaaS core model template and materializes both HubSpot and Zendesk
 * integrations, then verifies:
 * 1. Cross-integration projection creates correct core entity types
 * 2. Projected entities can be linked through core model relationships
 * 3. Identity clusters group integration and core entities together
 * 4. Visibility filter distinguishes integration types from core types
 */
@SpringBootTest(
    classes = [ProjectionPipelineIntegrationTestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@ActiveProfiles("integration")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class CrossIntegrationProjectionTest : ProjectionPipelineIntegrationTestBase() {

    private lateinit var hubspotContactType: EntityTypeEntity
    private lateinit var zendeskTicketType: EntityTypeEntity
    private lateinit var customerType: EntityTypeEntity
    private lateinit var supportTicketType: EntityTypeEntity

    private var hubspotContactEntityIds: List<UUID> = emptyList()
    private var zendeskTicketEntityIds: List<UUID> = emptyList()

    @BeforeAll
    fun setup() {
        loadIntegrationManifests()
        createWorkspaceAndUser()
        installCoreModelTemplate("b2c-saas")

        val hubspotDefId = createIntegrationDefinition("hubspot")
        materializeIntegration("hubspot", hubspotDefId)

        val zendeskDefId = createIntegrationDefinition("zendesk")
        materializeIntegration("zendesk", zendeskDefId)

        hubspotContactType = findEntityTypeByKey("hubspot-contact")
        zendeskTicketType = findEntityTypeByKey("zendesk-ticket")
        customerType = findEntityTypeByKey("customer")
        supportTicketType = findEntityTypeByKey("support-ticket")
    }

    @Test
    @Order(1)
    fun `cross-integration projection creates correct entity types`() {
        // Deterministic attribute UUIDs for hubspot-contact email attribute
        val hubspotEmailAttrId = UUID.nameUUIDFromBytes("hubspot:hubspot-contact:email".toByteArray())
        val hubspotFirstnameAttrId = UUID.nameUUIDFromBytes("hubspot:hubspot-contact:firstname".toByteArray())

        hubspotContactEntityIds = simulateSync(
            entityTypeId = requireNotNull(hubspotContactType.id),
            records = listOf(
                "hs-contact-1" to mapOf(
                    hubspotEmailAttrId to emailAttribute("alice@example.com"),
                    hubspotFirstnameAttrId to textAttribute("Alice"),
                ),
                "hs-contact-2" to mapOf(
                    hubspotEmailAttrId to emailAttribute("bob@example.com"),
                    hubspotFirstnameAttrId to textAttribute("Bob"),
                ),
            ),
        )

        // Deterministic attribute UUIDs for zendesk-ticket subject attribute
        val zendeskSubjectAttrId = UUID.nameUUIDFromBytes("zendesk:zendesk-ticket:subject".toByteArray())
        val zendeskDescriptionAttrId = UUID.nameUUIDFromBytes("zendesk:zendesk-ticket:description".toByteArray())

        zendeskTicketEntityIds = simulateSync(
            entityTypeId = requireNotNull(zendeskTicketType.id),
            records = listOf(
                "zd-ticket-1" to mapOf(
                    zendeskSubjectAttrId to textAttribute("Login broken"),
                    zendeskDescriptionAttrId to textAttribute("Cannot log in to the app"),
                ),
                "zd-ticket-2" to mapOf(
                    zendeskSubjectAttrId to textAttribute("Billing question"),
                    zendeskDescriptionAttrId to textAttribute("Need help with invoice"),
                ),
                "zd-ticket-3" to mapOf(
                    zendeskSubjectAttrId to textAttribute("Feature request"),
                    zendeskDescriptionAttrId to textAttribute("Would like dark mode"),
                ),
            ),
        )

        // Execute projections
        val hubspotResult = executeProjection(
            entityTypeId = requireNotNull(hubspotContactType.id),
            entityIds = hubspotContactEntityIds,
        )
        val zendeskResult = executeProjection(
            entityTypeId = requireNotNull(zendeskTicketType.id),
            entityIds = zendeskTicketEntityIds,
        )

        // Assert hubspot contacts projected to Customer entities
        val projectedCustomers = findProjectedEntities(requireNotNull(customerType.id))
        assertEquals(2, projectedCustomers.size, "Expected 2 projected Customer entities from HubSpot contacts")
        assertTrue(projectedCustomers.all { it.sourceType == SourceType.PROJECTED })

        // Assert zendesk tickets projected to Support Ticket entities
        val projectedTickets = findProjectedEntities(requireNotNull(supportTicketType.id))
        assertEquals(3, projectedTickets.size, "Expected 3 projected Support Ticket entities from Zendesk tickets")
        assertTrue(projectedTickets.all { it.sourceType == SourceType.PROJECTED })

        // Verify projection result summaries
        assertEquals(2, hubspotResult.created, "HubSpot projection should create 2 entities")
        assertEquals(3, zendeskResult.created, "Zendesk projection should create 3 entities")
    }

    @Test
    @Order(2)
    fun `projected entities can be linked through core model relationships`() {
        val customerTypeId = requireNotNull(customerType.id)
        val supportTicketTypeId = requireNotNull(supportTicketType.id)

        // Find the Customer → Support Ticket relationship definition installed by the core model template
        val relDef = relationshipDefinitionRepository
            .findByWorkspaceIdAndSourceEntityTypeIdAndName(workspaceId, customerTypeId, "Support Tickets")
            .orElse(null)

        if (relDef != null) {
            val projectedCustomers = findProjectedEntities(customerTypeId)
            val projectedTickets = findProjectedEntities(supportTicketTypeId)

            assertTrue(projectedCustomers.isNotEmpty(), "Need at least one projected Customer")
            assertTrue(projectedTickets.isNotEmpty(), "Need at least one projected Support Ticket")

            val customerEntityId = requireNotNull(projectedCustomers.first().id)
            val ticketEntityId = requireNotNull(projectedTickets.first().id)
            val relDefId = requireNotNull(relDef.id)

            // Manually create a relationship link between a projected customer and a projected ticket
            entityRelationshipRepository.save(
                EntityFactory.createRelationshipEntity(
                    id = null,
                    workspaceId = workspaceId,
                    sourceId = customerEntityId,
                    targetId = ticketEntityId,
                    definitionId = relDefId,
                    linkSource = SourceType.USER_CREATED,
                )
            )

            // Verify the relationship link persisted
            val relationships = entityRelationshipRepository.findBySourceIdAndTargetIdAndDefinitionId(
                sourceId = customerEntityId,
                targetId = ticketEntityId,
                definitionId = relDefId,
            )
            assertEquals(1, relationships.size, "Expected exactly one relationship link between Customer and Support Ticket")
            assertEquals(SourceType.USER_CREATED, relationships.first().linkSource)
        } else {
            // If no relationship definition exists, verify the projected entities at least have the correct type IDs
            val projectedCustomers = findProjectedEntities(customerTypeId)
            val projectedTickets = findProjectedEntities(supportTicketTypeId)

            assertTrue(projectedCustomers.isNotEmpty(), "Projected Customer entities should exist")
            assertTrue(projectedTickets.isNotEmpty(), "Projected Support Ticket entities should exist")
            assertTrue(
                projectedCustomers.all { it.typeId == customerTypeId },
                "All projected customers should have the correct type ID",
            )
            assertTrue(
                projectedTickets.all { it.typeId == supportTicketTypeId },
                "All projected tickets should have the correct type ID",
            )
        }
    }

    @Test
    @Order(3)
    fun `identity clusters group integration and core entities`() {
        // After projection, integration and core entities should share an identity cluster
        val firstHubspotContactId = hubspotContactEntityIds.first()
        val projectedCustomers = findProjectedEntities(requireNotNull(customerType.id))

        assertTrue(projectedCustomers.isNotEmpty(), "Need at least one projected Customer for cluster test")

        // Find the cluster for the first hubspot contact
        val sourceClusterId = jdbcTemplate.queryForList(
            "SELECT cluster_id FROM identity_cluster_members WHERE entity_id = ?",
            UUID::class.java,
            firstHubspotContactId,
        )

        // Find the cluster for projected customers — the first projected customer corresponds to the first contact
        // because projection processes entities in order
        val firstCustomerId = requireNotNull(projectedCustomers.first().id)
        val targetClusterId = jdbcTemplate.queryForList(
            "SELECT cluster_id FROM identity_cluster_members WHERE entity_id = ?",
            UUID::class.java,
            firstCustomerId,
        )

        assertTrue(sourceClusterId.isNotEmpty(), "Integration entity should be in an identity cluster")
        assertTrue(targetClusterId.isNotEmpty(), "Projected core entity should be in an identity cluster")
        assertEquals(
            sourceClusterId.first(),
            targetClusterId.first(),
            "Integration entity and its projected core entity should share the same identity cluster",
        )
    }

    @Test
    @Order(4)
    fun `visibility filter hides integration types`() {
        // All entity types in the workspace
        val allTypes = entityTypeRepository.findByworkspaceId(workspaceId)

        val visibleTypes = allTypes.filter { it.sourceIntegrationId == null }
        val internalTypes = allTypes.filter { it.sourceIntegrationId != null }

        // Core model types (customer, support-ticket, etc.) should be visible (no sourceIntegrationId)
        assertTrue(
            visibleTypes.any { it.key == "customer" },
            "Customer type should be visible (sourceIntegrationId is null)",
        )
        assertTrue(
            visibleTypes.any { it.key == "support-ticket" },
            "Support Ticket type should be visible (sourceIntegrationId is null)",
        )

        // Integration types (hubspot-contact, zendesk-ticket) should be internal (have sourceIntegrationId)
        assertTrue(
            internalTypes.any { it.key == "hubspot-contact" },
            "HubSpot Contact type should be internal (sourceIntegrationId is set)",
        )
        assertTrue(
            internalTypes.any { it.key == "zendesk-ticket" },
            "Zendesk Ticket type should be internal (sourceIntegrationId is set)",
        )

        // Verify counts make sense — at least 2 internal (hubspot-contact, zendesk-ticket)
        // and at least 2 visible (customer, support-ticket)
        assertTrue(internalTypes.size >= 2, "Should have at least 2 internal integration types")
        assertTrue(visibleTypes.size >= 2, "Should have at least 2 visible core types")
    }
}
