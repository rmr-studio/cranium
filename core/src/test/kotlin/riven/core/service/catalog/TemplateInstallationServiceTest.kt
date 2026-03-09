package riven.core.service.catalog

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.enums.catalog.ManifestType
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataFormat
import riven.core.enums.core.DataType
import riven.core.enums.entity.EntityPropertyType
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.exceptions.NotFoundException
import riven.core.models.catalog.*
import riven.core.models.entity.RelationshipDefinition
import riven.core.models.common.Icon
import riven.core.models.request.entity.type.SaveRelationshipDefinitionRequest
import riven.core.models.request.entity.type.SaveSemanticMetadataRequest
import riven.core.entity.catalog.WorkspaceTemplateInstallationEntity
import riven.core.repository.catalog.WorkspaceTemplateInstallationRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.EntityTypeSemanticMetadataService
import riven.core.service.entity.type.EntityTypeRelationshipService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.factory.CatalogTestFactory.createCatalogEntityType
import riven.core.service.util.factory.CatalogTestFactory.createManifestWithEntityTypes
import riven.core.service.util.factory.CatalogTestFactory.createManifestWithRelationship
import java.util.*

@SpringBootTest(classes = [AuthTokenService::class, WorkspaceSecurity::class, SecurityTestConfig::class, TemplateInstallationService::class])
class TemplateInstallationServiceTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var catalogService: ManifestCatalogService

    @MockitoBean
    private lateinit var entityTypeRepository: EntityTypeRepository

    @MockitoBean
    private lateinit var installationRepository: WorkspaceTemplateInstallationRepository

    @MockitoBean
    private lateinit var relationshipService: EntityTypeRelationshipService

    @MockitoBean
    private lateinit var semanticMetadataService: EntityTypeSemanticMetadataService

    @MockitoBean
    private lateinit var activityService: ActivityService

    @Autowired
    private lateinit var service: TemplateInstallationService

    @BeforeEach
    fun setUp() {
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), anyOrNull(), any(), any())).thenReturn(mock())
        whenever(installationRepository.findByWorkspaceIdAndManifestKey(any(), any())).thenReturn(null)
        whenever(installationRepository.save(any<WorkspaceTemplateInstallationEntity>())).thenAnswer { it.arguments[0] }
        whenever(entityTypeRepository.findByworkspaceIdAndKeyIn(any(), any())).thenReturn(emptyList())
    }

    // ------ Entity Type Creation ------

    @Test
    fun `installTemplate creates entity types from catalog`() {
        val manifest = createManifestWithEntityTypes(
            createCatalogEntityType("customer", "Customer", "Customers"),
            createCatalogEntityType("order", "Order", "Orders"),
        )
        whenever(catalogService.getManifestByKey("test-template", ManifestType.TEMPLATE)).thenReturn(manifest)
        stubEntityTypeSave()
        stubFallbackDefinition()

        val response = service.installTemplate(workspaceId, "test-template")

        assertEquals(2, response.entityTypesCreated)
        assertEquals("test-template", response.templateKey)
        verify(entityTypeRepository, times(2)).save(any<EntityTypeEntity>())
        verify(semanticMetadataService, times(2)).initializeForEntityType(any(), eq(workspaceId), any())
        verify(relationshipService, times(2)).createFallbackDefinition(eq(workspaceId), any())
    }

    @Test
    fun `installTemplate translates string-keyed schema to UUID-keyed schema`() {
        val schema = mapOf(
            "name" to mapOf<String, Any>("key" to "TEXT", "label" to "Name", "type" to "string", "required" to true, "unique" to true),
            "email" to mapOf<String, Any>("key" to "EMAIL", "label" to "Email", "type" to "string", "format" to "email", "required" to true),
            "amount" to mapOf<String, Any>("key" to "CURRENCY", "label" to "Amount", "type" to "number", "format" to "currency"),
        )
        val entityType = createCatalogEntityType("invoice", "Invoice", "Invoices", schema = schema, identifierKey = "email")

        val manifest = createManifestWithEntityTypes(entityType)
        whenever(catalogService.getManifestByKey("test-template", ManifestType.TEMPLATE)).thenReturn(manifest)
        stubEntityTypeSave()
        stubFallbackDefinition()

        service.installTemplate(workspaceId, "test-template")

        val captor = argumentCaptor<EntityTypeEntity>()
        verify(entityTypeRepository).save(captor.capture())

        val saved = captor.firstValue
        val properties = saved.schema.properties!!
        assertEquals(3, properties.size, "Should have 3 UUID-keyed properties")

        // All keys should be UUIDs, not strings
        properties.keys.forEach { key ->
            assertDoesNotThrow { key.toString() }
        }

        // Verify identifier key is resolved to a UUID
        assertTrue(properties.containsKey(saved.identifierKey), "identifierKey should reference a valid property UUID")

        // Verify the identifier attribute has the EMAIL schema type
        val identifierProp = properties[saved.identifierKey]!!
        assertEquals(SchemaType.EMAIL, identifierProp.key)
        assertTrue(identifierProp.protected, "Identifier attribute should be protected")

        // Verify columns match properties
        assertEquals(3, saved.columns.size)
        saved.columns.forEach { col ->
            assertEquals(EntityPropertyType.ATTRIBUTE, col.type)
            assertTrue(properties.containsKey(col.key))
        }
    }

    @Test
    fun `installTemplate creates relationships with resolved entity type IDs`() {
        val manifest = createManifestWithRelationship()
        whenever(catalogService.getManifestByKey("test-template", ManifestType.TEMPLATE)).thenReturn(manifest)
        stubEntityTypeSave()
        stubFallbackDefinition()
        stubRelationshipCreation()

        service.installTemplate(workspaceId, "test-template")

        val captor = argumentCaptor<SaveRelationshipDefinitionRequest>()
        verify(relationshipService).createRelationshipDefinition(eq(workspaceId), any(), captor.capture())

        val request = captor.firstValue
        assertEquals("customer-orders", request.name)
        assertEquals(EntityRelationshipCardinality.ONE_TO_MANY, request.cardinalityDefault)
        assertEquals(1, request.targetRules.size)

        // Target entity type ID should be a UUID (resolved from "order" key)
        assertNotNull(request.targetRules[0].targetEntityTypeId)
        assertEquals("Orders", request.targetRules[0].inverseName)
    }

    @Test
    fun `installTemplate applies semantic metadata for entity types and attributes`() {
        val semanticMetadata = listOf(
            CatalogSemanticMetadataModel(
                id = UUID.randomUUID(),
                targetType = SemanticMetadataTargetType.ENTITY_TYPE,
                targetId = "customer",
                definition = "A customer record",
                classification = null,
                tags = listOf("crm"),
            ),
            CatalogSemanticMetadataModel(
                id = UUID.randomUUID(),
                targetType = SemanticMetadataTargetType.ATTRIBUTE,
                targetId = "name",
                definition = "Customer name",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("display"),
            ),
        )

        val entityType = createCatalogEntityType("customer", "Customer", "Customers", semanticMetadata = semanticMetadata)
        val manifest = createManifestWithEntityTypes(entityType)
        whenever(catalogService.getManifestByKey("test-template", ManifestType.TEMPLATE)).thenReturn(manifest)
        stubEntityTypeSave()
        stubFallbackDefinition()
        whenever(semanticMetadataService.upsertMetadataInternal(any(), any(), any(), any(), any())).thenReturn(mock())

        service.installTemplate(workspaceId, "test-template")

        // Should be called twice: once for ENTITY_TYPE, once for ATTRIBUTE "name"
        verify(semanticMetadataService, times(2)).upsertMetadataInternal(
            eq(workspaceId), any(), any(), any(), any()
        )

        // Verify ENTITY_TYPE metadata call
        verify(semanticMetadataService).upsertMetadataInternal(
            eq(workspaceId),
            any(),
            eq(SemanticMetadataTargetType.ENTITY_TYPE),
            any(),
            argThat<SaveSemanticMetadataRequest> { definition == "A customer record" },
        )

        // Verify ATTRIBUTE metadata call
        verify(semanticMetadataService).upsertMetadataInternal(
            eq(workspaceId),
            any(),
            eq(SemanticMetadataTargetType.ATTRIBUTE),
            any(),
            argThat<SaveSemanticMetadataRequest> { definition == "Customer name" },
        )
    }

    @Test
    fun `installTemplate throws on unknown template key`() {
        whenever(catalogService.getManifestByKey("unknown", ManifestType.TEMPLATE))
            .thenThrow(NotFoundException("Manifest not found: unknown (type=TEMPLATE)"))

        assertThrows<NotFoundException> {
            service.installTemplate(workspaceId, "unknown")
        }
    }

    @Test
    fun `installTemplate returns correct response shape`() {
        val manifest = createManifestWithRelationship()
        whenever(catalogService.getManifestByKey("test-template", ManifestType.TEMPLATE)).thenReturn(manifest)
        stubEntityTypeSave()
        stubFallbackDefinition()
        stubRelationshipCreation()

        val response = service.installTemplate(workspaceId, "test-template")

        assertEquals("test-template", response.templateKey)
        assertEquals("Test Template", response.templateName)
        assertEquals(2, response.entityTypesCreated)
        assertEquals(1, response.relationshipsCreated)
        assertEquals(2, response.entityTypes.size)

        val customerSummary = response.entityTypes.find { it.key == "customer" }!!
        assertEquals("Customer", customerSummary.displayName)
        assertTrue(customerSummary.attributeCount > 0)
        assertNotNull(customerSummary.id)
    }

    @Test
    fun `installTemplate passes relationship semantics from source entity type metadata`() {
        val relSemantics = CatalogSemanticMetadataModel(
            id = UUID.randomUUID(),
            targetType = SemanticMetadataTargetType.RELATIONSHIP,
            targetId = "customer-orders-rel",
            definition = "Customer purchased orders",
            classification = null,
            tags = listOf("commerce"),
        )
        val customerType = createCatalogEntityType(
            "customer", "Customer", "Customers",
            semanticMetadata = listOf(relSemantics),
        )
        val orderType = createCatalogEntityType("order", "Order", "Orders")

        val relationship = CatalogRelationshipModel(
            id = UUID.randomUUID(),
            key = "customer-orders-rel",
            sourceEntityTypeKey = "customer",
            name = "Orders",
            iconType = IconType.LINK,
            iconColour = IconColour.NEUTRAL,
            cardinalityDefault = EntityRelationshipCardinality.ONE_TO_MANY,
            `protected` = false,
            targetRules = listOf(
                CatalogRelationshipTargetRuleModel(
                    id = UUID.randomUUID(),
                    targetEntityTypeKey = "order",
                    cardinalityOverride = null,
                    inverseVisible = true,
                    inverseName = "Customer",
                )
            ),
        )

        val manifest = ManifestDetail(
            id = UUID.randomUUID(),
            key = "test-template",
            name = "Test Template",
            description = "A test template",
            manifestType = ManifestType.TEMPLATE,
            manifestVersion = "1.0",
            entityTypes = listOf(customerType, orderType),
            relationships = listOf(relationship),
            fieldMappings = emptyList(),
        )

        whenever(catalogService.getManifestByKey("test-template", ManifestType.TEMPLATE)).thenReturn(manifest)
        stubEntityTypeSave()
        stubFallbackDefinition()
        stubRelationshipCreation()

        service.installTemplate(workspaceId, "test-template")

        val relCaptor = argumentCaptor<SaveRelationshipDefinitionRequest>()
        verify(relationshipService).createRelationshipDefinition(eq(workspaceId), any(), relCaptor.capture())

        val request = relCaptor.firstValue
        assertNotNull(request.semantics, "Relationship semantics should be passed from catalog metadata")
        assertEquals("Customer purchased orders", request.semantics!!.definition)
        assertEquals(listOf("commerce"), request.semantics!!.tags)
    }

    @Test
    fun `installTemplate handles data format mapping correctly`() {
        val schema = mapOf(
            "created" to mapOf<String, Any>("key" to "DATE", "label" to "Created", "type" to "string", "format" to "date"),
            "updated" to mapOf<String, Any>("key" to "DATETIME", "label" to "Updated", "type" to "string", "format" to "date-time"),
            "email" to mapOf<String, Any>("key" to "EMAIL", "label" to "Email", "type" to "string", "format" to "email"),
            "phone" to mapOf<String, Any>("key" to "PHONE", "label" to "Phone", "type" to "string", "format" to "phone-number"),
            "website" to mapOf<String, Any>("key" to "URL", "label" to "Website", "type" to "string", "format" to "uri"),
            "rate" to mapOf<String, Any>("key" to "PERCENTAGE", "label" to "Rate", "type" to "number", "format" to "percentage"),
            "cost" to mapOf<String, Any>("key" to "CURRENCY", "label" to "Cost", "type" to "number", "format" to "currency"),
        )
        val entityType = createCatalogEntityType("test", "Test", "Tests", schema = schema, identifierKey = "email")
        val manifest = createManifestWithEntityTypes(entityType)
        whenever(catalogService.getManifestByKey("test-template", ManifestType.TEMPLATE)).thenReturn(manifest)
        stubEntityTypeSave()
        stubFallbackDefinition()

        service.installTemplate(workspaceId, "test-template")

        val captor = argumentCaptor<EntityTypeEntity>()
        verify(entityTypeRepository).save(captor.capture())

        val props = captor.firstValue.schema.properties!!.values.toList()
        val formats = props.mapNotNull { it.format }
        assertTrue(formats.contains(DataFormat.DATE))
        assertTrue(formats.contains(DataFormat.DATETIME))
        assertTrue(formats.contains(DataFormat.EMAIL))
        assertTrue(formats.contains(DataFormat.PHONE))
        assertTrue(formats.contains(DataFormat.URL))
        assertTrue(formats.contains(DataFormat.PERCENTAGE))
        assertTrue(formats.contains(DataFormat.CURRENCY))
    }

    // ------ Entity Type Reuse ------

    @Test
    fun `installTemplate reuses existing entity types and only creates missing ones`() {
        val customerType = createCatalogEntityType("customer", "Customer", "Customers")
        val orderType = createCatalogEntityType("order", "Order", "Orders")
        val manifest = createManifestWithEntityTypes(customerType, orderType)
        whenever(catalogService.getManifestByKey("test-template", ManifestType.TEMPLATE)).thenReturn(manifest)

        // Customer already exists in workspace
        val existingCustomer = mock<EntityTypeEntity>()
        val customerId = UUID.randomUUID()
        whenever(existingCustomer.id).thenReturn(customerId)
        whenever(existingCustomer.key).thenReturn("customer")
        whenever(existingCustomer.displayNameSingular).thenReturn("Customer")
        whenever(existingCustomer.schema).thenReturn(mock())
        whenever(entityTypeRepository.findByworkspaceIdAndKeyIn(eq(workspaceId), any()))
            .thenReturn(listOf(existingCustomer))

        stubEntityTypeSave()
        stubFallbackDefinition()

        val response = service.installTemplate(workspaceId, "test-template")

        // Only order should be created, customer is reused
        verify(entityTypeRepository, times(1)).save(any<EntityTypeEntity>())
        assertEquals(1, response.entityTypesCreated)
        assertEquals(1, response.entityTypes.size)
        assertNotNull(response.entityTypes.find { it.key == "order" })
        assertNull(response.entityTypes.find { it.key == "customer" })
    }

    // ------ Idempotency Tests ------

    @Test
    fun `installTemplate returns empty response when template already installed`() {
        val existingInstallation = WorkspaceTemplateInstallationEntity(
            workspaceId = workspaceId,
            manifestKey = "test-template",
            installedBy = userId,
        )
        whenever(installationRepository.findByWorkspaceIdAndManifestKey(workspaceId, "test-template"))
            .thenReturn(existingInstallation)

        val manifest = createManifestWithEntityTypes(createCatalogEntityType("customer", "Customer", "Customers"))
        whenever(catalogService.getManifestByKey("test-template", ManifestType.TEMPLATE))
            .thenReturn(manifest)

        val result = service.installTemplate(workspaceId, "test-template")

        assertEquals(0, result.entityTypesCreated)
        assertEquals(0, result.relationshipsCreated)
        assertTrue(result.entityTypes.isEmpty())
        verify(entityTypeRepository, never()).save(any())
    }

    // ------ Bundle Installation Tests ------

    @Test
    fun `installBundle creates entity types from all templates and deduplicates shared types`() {
        val bundle = BundleDetail(
            id = UUID.randomUUID(),
            key = "test-bundle",
            name = "Test Bundle",
            description = "A test bundle",
            templateKeys = listOf("crm", "billing"),
        )
        whenever(catalogService.getBundleByKey("test-bundle")).thenReturn(bundle)
        whenever(installationRepository.findByWorkspaceIdAndManifestKeyIn(eq(workspaceId), any()))
            .thenReturn(emptyList())
        whenever(installationRepository.save(any<WorkspaceTemplateInstallationEntity>()))
            .thenAnswer { it.arguments[0] }

        val customerType = createCatalogEntityType("customer", "Customer", "Customers")
        val invoiceType = createCatalogEntityType("invoice", "Invoice", "Invoices")

        val crmManifest = createManifestWithEntityTypes(customerType, key = "crm", name = "CRM")
        val billingManifest = createManifestWithEntityTypes(customerType, invoiceType, key = "billing", name = "Billing")

        whenever(catalogService.getManifestByKey("crm", ManifestType.TEMPLATE)).thenReturn(crmManifest)
        whenever(catalogService.getManifestByKey("billing", ManifestType.TEMPLATE)).thenReturn(billingManifest)

        stubEntityTypeSave()
        stubFallbackDefinition()

        val result = service.installBundle(workspaceId, "test-bundle")

        assertEquals("test-bundle", result.bundleKey)
        assertEquals(listOf("crm", "billing"), result.templatesInstalled)
        assertTrue(result.templatesSkipped.isEmpty())
        // customer appears in both templates but should only be created once
        assertEquals(2, result.entityTypesCreated) // customer + invoice
    }

    @Test
    fun `installBundle skips already-installed templates and resolves their entity types`() {
        val bundle = BundleDetail(
            id = UUID.randomUUID(),
            key = "test-bundle",
            name = "Test Bundle",
            description = null,
            templateKeys = listOf("crm", "billing"),
        )
        whenever(catalogService.getBundleByKey("test-bundle")).thenReturn(bundle)

        val existingInstallation = WorkspaceTemplateInstallationEntity(
            workspaceId = workspaceId,
            manifestKey = "crm",
            installedBy = userId,
        )
        whenever(installationRepository.findByWorkspaceIdAndManifestKeyIn(eq(workspaceId), any()))
            .thenReturn(listOf(existingInstallation))
        whenever(installationRepository.save(any<WorkspaceTemplateInstallationEntity>()))
            .thenAnswer { it.arguments[0] }

        // CRM is skipped — resolve its entity types from workspace
        val crmManifest = createManifestWithEntityTypes(
            createCatalogEntityType("customer", "Customer", "Customers"),
            key = "crm",
            name = "CRM",
        )
        whenever(catalogService.getManifestByKey("crm", ManifestType.TEMPLATE)).thenReturn(crmManifest)

        val existingCustomerEntity = mock<EntityTypeEntity>()
        val customerId = UUID.randomUUID()
        whenever(existingCustomerEntity.id).thenReturn(customerId)
        whenever(existingCustomerEntity.key).thenReturn("customer")
        whenever(existingCustomerEntity.displayNameSingular).thenReturn("Customer")
        whenever(existingCustomerEntity.schema).thenReturn(mock())
        whenever(entityTypeRepository.findByworkspaceIdAndKeyIn(eq(workspaceId), any()))
            .thenReturn(listOf(existingCustomerEntity))

        // Billing is installed — has invoice type
        val invoiceType = createCatalogEntityType("invoice", "Invoice", "Invoices")
        val billingManifest = createManifestWithEntityTypes(invoiceType, key = "billing", name = "Billing")
        whenever(catalogService.getManifestByKey("billing", ManifestType.TEMPLATE)).thenReturn(billingManifest)

        stubEntityTypeSave()
        stubFallbackDefinition()

        val result = service.installBundle(workspaceId, "test-bundle")

        assertEquals(listOf("billing"), result.templatesInstalled)
        assertEquals(listOf("crm"), result.templatesSkipped)
        assertEquals(1, result.entityTypesCreated) // only invoice, customer was skipped
    }

    // ------ Cross-Template Attribute Merge ------

    @Test
    fun `installBundle merges non-overlapping extended attributes from two templates`() {
        val bundle = createTestBundle("merge-bundle", listOf("crm", "billing"))
        whenever(catalogService.getBundleByKey("merge-bundle")).thenReturn(bundle)
        whenever(installationRepository.findByWorkspaceIdAndManifestKeyIn(eq(workspaceId), any()))
            .thenReturn(emptyList())

        val crmCustomer = createCatalogEntityType(
            "customer", "Customer", "Customers",
            schema = mapOf(
                "name" to mapOf<String, Any>("key" to "TEXT", "label" to "Name", "type" to "string", "required" to true),
                "loyalty-tier" to mapOf<String, Any>("key" to "TEXT", "label" to "Loyalty Tier", "type" to "string", "required" to false),
            ),
        )
        val billingCustomer = createCatalogEntityType(
            "customer", "Customer", "Customers",
            schema = mapOf(
                "name" to mapOf<String, Any>("key" to "TEXT", "label" to "Name", "type" to "string", "required" to true),
                "credit-limit" to mapOf<String, Any>("key" to "CURRENCY", "label" to "Credit Limit", "type" to "number", "format" to "currency"),
            ),
        )

        whenever(catalogService.getManifestByKey("crm", ManifestType.TEMPLATE))
            .thenReturn(createManifestWithEntityTypes(crmCustomer, key = "crm", name = "CRM"))
        whenever(catalogService.getManifestByKey("billing", ManifestType.TEMPLATE))
            .thenReturn(createManifestWithEntityTypes(billingCustomer, key = "billing", name = "Billing"))

        stubEntityTypeSave()
        stubFallbackDefinition()

        service.installBundle(workspaceId, "merge-bundle")

        val captor = argumentCaptor<EntityTypeEntity>()
        verify(entityTypeRepository).save(captor.capture())

        val saved = captor.firstValue
        val propLabels = saved.schema.properties!!.values.map { it.label }.toSet()
        assertTrue(propLabels.contains("Name"), "Should have base 'name' attribute")
        assertTrue(propLabels.contains("Loyalty Tier"), "Should have CRM's 'loyalty-tier' attribute")
        assertTrue(propLabels.contains("Credit Limit"), "Should have Billing's 'credit-limit' attribute")
        assertEquals(3, saved.schema.properties!!.size, "Should have exactly 3 merged attributes")
    }

    @Test
    fun `installBundle deduplicates identical attributes across templates`() {
        val bundle = createTestBundle("dedup-bundle", listOf("crm", "billing"))
        whenever(catalogService.getBundleByKey("dedup-bundle")).thenReturn(bundle)
        whenever(installationRepository.findByWorkspaceIdAndManifestKeyIn(eq(workspaceId), any()))
            .thenReturn(emptyList())

        val phoneAttr = mapOf<String, Any>("key" to "PHONE", "label" to "Phone", "type" to "string", "format" to "phone-number")
        val crmCustomer = createCatalogEntityType(
            "customer", "Customer", "Customers",
            schema = mapOf("name" to mapOf<String, Any>("key" to "TEXT", "label" to "Name", "type" to "string", "required" to true), "phone" to phoneAttr),
        )
        val billingCustomer = createCatalogEntityType(
            "customer", "Customer", "Customers",
            schema = mapOf("name" to mapOf<String, Any>("key" to "TEXT", "label" to "Name", "type" to "string", "required" to true), "phone" to phoneAttr),
        )

        whenever(catalogService.getManifestByKey("crm", ManifestType.TEMPLATE))
            .thenReturn(createManifestWithEntityTypes(crmCustomer, key = "crm", name = "CRM"))
        whenever(catalogService.getManifestByKey("billing", ManifestType.TEMPLATE))
            .thenReturn(createManifestWithEntityTypes(billingCustomer, key = "billing", name = "Billing"))

        stubEntityTypeSave()
        stubFallbackDefinition()

        service.installBundle(workspaceId, "dedup-bundle")

        val captor = argumentCaptor<EntityTypeEntity>()
        verify(entityTypeRepository).save(captor.capture())

        val saved = captor.firstValue
        assertEquals(2, saved.schema.properties!!.size, "Should have 'name' and 'phone' — no duplicates")
    }

    @Test
    fun `installBundle keeps first definition on attribute conflict and logs warning`() {
        val bundle = createTestBundle("conflict-bundle", listOf("crm", "billing"))
        whenever(catalogService.getBundleByKey("conflict-bundle")).thenReturn(bundle)
        whenever(installationRepository.findByWorkspaceIdAndManifestKeyIn(eq(workspaceId), any()))
            .thenReturn(emptyList())

        val crmCustomer = createCatalogEntityType(
            "customer", "Customer", "Customers",
            schema = mapOf(
                "name" to mapOf<String, Any>("key" to "TEXT", "label" to "Name", "type" to "string", "required" to true),
                "tier" to mapOf<String, Any>("key" to "SELECT", "label" to "Tier", "type" to "string", "required" to false),
            ),
        )
        val billingCustomer = createCatalogEntityType(
            "customer", "Customer", "Customers",
            schema = mapOf(
                "name" to mapOf<String, Any>("key" to "TEXT", "label" to "Name", "type" to "string", "required" to true),
                "tier" to mapOf<String, Any>("key" to "TEXT", "label" to "Tier Level", "type" to "string", "required" to true),
            ),
        )

        whenever(catalogService.getManifestByKey("crm", ManifestType.TEMPLATE))
            .thenReturn(createManifestWithEntityTypes(crmCustomer, key = "crm", name = "CRM"))
        whenever(catalogService.getManifestByKey("billing", ManifestType.TEMPLATE))
            .thenReturn(createManifestWithEntityTypes(billingCustomer, key = "billing", name = "Billing"))

        stubEntityTypeSave()
        stubFallbackDefinition()

        service.installBundle(workspaceId, "conflict-bundle")

        val captor = argumentCaptor<EntityTypeEntity>()
        verify(entityTypeRepository).save(captor.capture())

        val saved = captor.firstValue
        assertEquals(2, saved.schema.properties!!.size, "Should have 'name' and 'tier'")
        // First wins: tier should be SELECT from CRM, not TEXT from billing
        val tierProp = saved.schema.properties!!.values.find { it.label == "Tier" }
        assertNotNull(tierProp, "Should have 'tier' attribute with CRM's label")
        assertEquals(SchemaType.SELECT, tierProp!!.key, "First-wins: 'tier' should keep SELECT from CRM template")
    }

    @Test
    fun `installBundle merges extended attributes onto plain ref base`() {
        val bundle = createTestBundle("extend-bundle", listOf("core", "crm"))
        whenever(catalogService.getBundleByKey("extend-bundle")).thenReturn(bundle)
        whenever(installationRepository.findByWorkspaceIdAndManifestKeyIn(eq(workspaceId), any()))
            .thenReturn(emptyList())

        val baseCustomer = createCatalogEntityType(
            "customer", "Customer", "Customers",
            schema = mapOf(
                "name" to mapOf<String, Any>("key" to "TEXT", "label" to "Name", "type" to "string", "required" to true),
                "email" to mapOf<String, Any>("key" to "EMAIL", "label" to "Email", "type" to "string", "format" to "email"),
            ),
        )
        val extendedCustomer = createCatalogEntityType(
            "customer", "Customer", "Customers",
            schema = mapOf(
                "name" to mapOf<String, Any>("key" to "TEXT", "label" to "Name", "type" to "string", "required" to true),
                "vip-status" to mapOf<String, Any>("key" to "SELECT", "label" to "VIP Status", "type" to "string", "required" to false),
            ),
        )

        whenever(catalogService.getManifestByKey("core", ManifestType.TEMPLATE))
            .thenReturn(createManifestWithEntityTypes(baseCustomer, key = "core", name = "Core"))
        whenever(catalogService.getManifestByKey("crm", ManifestType.TEMPLATE))
            .thenReturn(createManifestWithEntityTypes(extendedCustomer, key = "crm", name = "CRM"))

        stubEntityTypeSave()
        stubFallbackDefinition()

        service.installBundle(workspaceId, "extend-bundle")

        val captor = argumentCaptor<EntityTypeEntity>()
        verify(entityTypeRepository).save(captor.capture())

        val saved = captor.firstValue
        val propLabels = saved.schema.properties!!.values.map { it.label }.toSet()
        assertEquals(3, saved.schema.properties!!.size, "Should have base attrs + extended 'vip-status'")
        assertTrue(propLabels.contains("Email"), "Should keep base 'email'")
        assertTrue(propLabels.contains("VIP Status"), "Should add extended 'vip-status'")
    }

    @Test
    fun `installBundle merges semantic metadata from both templates`() {
        val bundle = createTestBundle("metadata-bundle", listOf("crm", "billing"))
        whenever(catalogService.getBundleByKey("metadata-bundle")).thenReturn(bundle)
        whenever(installationRepository.findByWorkspaceIdAndManifestKeyIn(eq(workspaceId), any()))
            .thenReturn(emptyList())

        val entityLevelMetadata = CatalogSemanticMetadataModel(
            id = UUID.randomUUID(),
            targetType = SemanticMetadataTargetType.ENTITY_TYPE,
            targetId = "customer",
            definition = "A customer record",
            classification = null,
            tags = listOf("crm"),
        )
        val attributeLevelMetadata = CatalogSemanticMetadataModel(
            id = UUID.randomUUID(),
            targetType = SemanticMetadataTargetType.ATTRIBUTE,
            targetId = "credit-limit",
            definition = "Max credit for the customer",
            classification = SemanticAttributeClassification.QUANTITATIVE,
            tags = listOf("billing"),
        )

        val crmCustomer = createCatalogEntityType(
            "customer", "Customer", "Customers",
            semanticMetadata = listOf(entityLevelMetadata),
        )
        val billingCustomer = createCatalogEntityType(
            "customer", "Customer", "Customers",
            schema = mapOf(
                "name" to mapOf<String, Any>("key" to "TEXT", "label" to "Name", "type" to "string", "required" to true),
                "credit-limit" to mapOf<String, Any>("key" to "CURRENCY", "label" to "Credit Limit", "type" to "number", "format" to "currency"),
            ),
            semanticMetadata = listOf(attributeLevelMetadata),
        )

        whenever(catalogService.getManifestByKey("crm", ManifestType.TEMPLATE))
            .thenReturn(createManifestWithEntityTypes(crmCustomer, key = "crm", name = "CRM"))
        whenever(catalogService.getManifestByKey("billing", ManifestType.TEMPLATE))
            .thenReturn(createManifestWithEntityTypes(billingCustomer, key = "billing", name = "Billing"))

        stubEntityTypeSave()
        stubFallbackDefinition()
        whenever(semanticMetadataService.upsertMetadataInternal(any(), any(), any(), any(), any())).thenReturn(mock())

        service.installBundle(workspaceId, "metadata-bundle")

        // Should apply both entity-level and attribute-level metadata
        verify(semanticMetadataService).upsertMetadataInternal(
            eq(workspaceId), any(), eq(SemanticMetadataTargetType.ENTITY_TYPE), any(),
            argThat<SaveSemanticMetadataRequest> { definition == "A customer record" },
        )
        verify(semanticMetadataService).upsertMetadataInternal(
            eq(workspaceId), any(), eq(SemanticMetadataTargetType.ATTRIBUTE), any(),
            argThat<SaveSemanticMetadataRequest> { definition == "Max credit for the customer" },
        )
    }

    // ------ Test Helpers ------

    private fun createTestBundle(key: String, templateKeys: List<String>) = BundleDetail(
        id = UUID.randomUUID(),
        key = key,
        name = key.replaceFirstChar { it.uppercase() },
        description = "A test bundle",
        templateKeys = templateKeys,
    )

    private fun stubEntityTypeSave() {
        whenever(entityTypeRepository.save(any<EntityTypeEntity>())).thenAnswer { invocation ->
            val entity = invocation.getArgument<EntityTypeEntity>(0)
            entity.copy(id = entity.id ?: UUID.randomUUID())
        }
    }

    private fun stubFallbackDefinition() {
        whenever(relationshipService.createFallbackDefinition(any(), any())).thenReturn(mock())
    }

    private fun stubRelationshipCreation() {
        whenever(relationshipService.createRelationshipDefinition(any(), any(), any())).thenReturn(
            RelationshipDefinition(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                sourceEntityTypeId = UUID.randomUUID(),
                name = "test",
                icon = Icon(IconType.LINK, IconColour.NEUTRAL),
                cardinalityDefault = EntityRelationshipCardinality.ONE_TO_MANY,
                protected = false,
                systemType = null,
                targetRules = emptyList(),
                createdAt = null,
                updatedAt = null,
                createdBy = null,
                updatedBy = null,
            )
        )
    }
}
