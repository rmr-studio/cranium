package riven.core.service.catalog

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.enums.activity.Activity
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.core.DataFormat
import riven.core.enums.core.DataType
import riven.core.enums.entity.validation.EntityTypeChangeType
import riven.core.enums.util.OperationType
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.models.catalog.ReconciliationImpact
import riven.core.models.catalog.ReconciliationResult
import riven.core.models.catalog.SchemaHealthResponse
import riven.core.models.catalog.SchemaHealthStatusType
import riven.core.models.common.validation.Schema
import riven.core.models.entity.EntityTypeSchema
import riven.core.repository.catalog.CatalogEntityTypeRepository
import riven.core.repository.entity.EntityAttributeRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.enrichment.EnrichmentService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import riven.core.service.util.factory.catalog.CatalogFactory
import riven.core.service.util.factory.entity.EntityFactory
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        SchemaReconciliationServiceTest.TestConfig::class,
        SchemaReconciliationService::class,
    ]
)
@WithUserPersona(
    userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
    email = "test@test.com",
    displayName = "Test User",
    roles = [
        WorkspaceRole(
            workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
            role = WorkspaceRoles.OWNER
        )
    ]
)
class SchemaReconciliationServiceTest : BaseServiceTest() {

    @Configuration
    class TestConfig {
        /**
         * Inline no-op PlatformTransactionManager so [SchemaReconciliationService]'s
         * REQUIRES_NEW TransactionTemplate executes its callback without needing a real
         * transactional resource. Rollback semantics are exercised via the callback's
         * thrown exception, which TransactionTemplate rethrows after invoking rollback.
         */
        @Bean
        fun transactionManager(): PlatformTransactionManager = object : PlatformTransactionManager {
            override fun getTransaction(definition: TransactionDefinition?): TransactionStatus =
                SimpleTransactionStatus(true)

            override fun commit(status: TransactionStatus) {}
            override fun rollback(status: TransactionStatus) {}
        }
    }

    @MockitoBean
    private lateinit var catalogEntityTypeRepository: CatalogEntityTypeRepository

    @MockitoBean
    private lateinit var entityTypeRepository: EntityTypeRepository

    @MockitoBean
    private lateinit var entityAttributeRepository: EntityAttributeRepository

    @MockitoBean
    private lateinit var activityService: ActivityService

    @MockitoBean
    private lateinit var enrichmentService: EnrichmentService

    @Autowired
    private lateinit var service: SchemaReconciliationService

    private val manifestId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        reset(
            catalogEntityTypeRepository,
            entityTypeRepository,
            entityAttributeRepository,
            activityService,
            enrichmentService,
        )
    }

    // ------ Helper Methods ------

    /**
     * Builds a workspace schema (UUID-keyed) from a map of string key -> attribute definition.
     * Returns the schema, the mapping (string key -> UUID string), and the UUID map for referencing.
     */
    private fun buildWorkspaceSchema(
        attributes: Map<String, SchemaAttrDef>,
    ): Triple<EntityTypeSchema, Map<String, String>, Map<String, UUID>> {
        val uuidMap = attributes.keys.associateWith { UUID.randomUUID() }
        val mapping = uuidMap.mapValues { it.value.toString() }
        val properties = uuidMap.map { (key, uuid) ->
            val def = attributes[key]!!
            uuid to Schema<UUID>(
                key = def.schemaType,
                type = def.dataType,
                label = def.label,
                format = def.format,
                required = def.required,
                unique = def.unique,
            )
        }.toMap()

        val schema = Schema<UUID>(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = properties,
        )

        return Triple(schema, mapping, uuidMap)
    }

    private data class SchemaAttrDef(
        val schemaType: SchemaType = SchemaType.TEXT,
        val dataType: DataType = DataType.STRING,
        val label: String? = null,
        val format: DataFormat? = null,
        val required: Boolean = false,
        val unique: Boolean = false,
    )

    /**
     * Builds a catalog schema (string-keyed Map<String, Any>) from attribute definitions.
     */
    private fun buildCatalogSchema(attributes: Map<String, CatalogAttrDef>): Map<String, Any> {
        return attributes.mapValues { (_, def) ->
            buildMap<String, Any> {
                put("key", def.key)
                put("type", def.type)
                if (def.label != null) put("label", def.label)
                if (def.format != null) put("format", def.format)
                put("required", def.required)
                put("unique", def.unique)
            }
        }
    }

    private data class CatalogAttrDef(
        val key: String = "TEXT",
        val type: String = "string",
        val label: String? = null,
        val format: String? = null,
        val required: Boolean = false,
        val unique: Boolean = false,
    )

    // ------ computeSchemaDiff Tests ------

    @Nested
    inner class ComputeSchemaDiff {

        @Test
        fun `returns empty list when schemas match`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(label = "Email"))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(label = "Email"))
            )

            val changes = service.computeSchemaDiff(catalogSchema, workspaceSchema, mapping)

            assertTrue(changes.isEmpty())
        }

        @Test
        fun `detects FIELD_ADDED when catalog has key not in attributeKeyMapping`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(label = "Email"))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf(
                    "email" to CatalogAttrDef(label = "Email"),
                    "phone" to CatalogAttrDef(key = "TEXT", type = "string", label = "Phone"),
                )
            )

            val changes = service.computeSchemaDiff(catalogSchema, workspaceSchema, mapping)

            assertEquals(1, changes.size)
            assertEquals(EntityTypeChangeType.FIELD_ADDED, changes[0].type)
            assertEquals("phone", changes[0].attributeKey)
            assertFalse(changes[0].breaking)
            assertNull(changes[0].workspaceAttributeId)
        }

        @Test
        fun `detects FIELD_REMOVED when attributeKeyMapping has key not in catalog`() {
            val (workspaceSchema, mapping, uuidMap) = buildWorkspaceSchema(
                mapOf(
                    "email" to SchemaAttrDef(label = "Email"),
                    "phone" to SchemaAttrDef(label = "Phone"),
                )
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(label = "Email"))
            )

            val changes = service.computeSchemaDiff(catalogSchema, workspaceSchema, mapping)

            assertEquals(1, changes.size)
            assertEquals(EntityTypeChangeType.FIELD_REMOVED, changes[0].type)
            assertEquals("phone", changes[0].attributeKey)
            assertTrue(changes[0].breaking)
            assertEquals(uuidMap["phone"], changes[0].workspaceAttributeId)
        }

        @Test
        fun `detects FIELD_TYPE_CHANGED when SchemaType differs`() {
            val (workspaceSchema, mapping, uuidMap) = buildWorkspaceSchema(
                mapOf("amount" to SchemaAttrDef(schemaType = SchemaType.TEXT, dataType = DataType.STRING))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("amount" to CatalogAttrDef(key = "NUMBER", type = "number"))
            )

            val changes = service.computeSchemaDiff(catalogSchema, workspaceSchema, mapping)

            val typeChange = changes.find { it.type == EntityTypeChangeType.FIELD_TYPE_CHANGED }
            assertNotNull(typeChange)
            assertEquals("amount", typeChange!!.attributeKey)
            assertTrue(typeChange.breaking)
            assertEquals(uuidMap["amount"], typeChange.workspaceAttributeId)
        }

        @Test
        fun `detects FIELD_REQUIRED_ADDED when catalog required and workspace optional`() {
            val (workspaceSchema, mapping, uuidMap) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(required = false))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(required = true))
            )

            val changes = service.computeSchemaDiff(catalogSchema, workspaceSchema, mapping)

            val requiredChange = changes.find { it.type == EntityTypeChangeType.FIELD_REQUIRED_ADDED }
            assertNotNull(requiredChange)
            assertEquals("email", requiredChange!!.attributeKey)
            assertTrue(requiredChange.breaking)
            assertEquals(uuidMap["email"], requiredChange.workspaceAttributeId)
        }

        @Test
        fun `detects FIELD_UNIQUE_ADDED when catalog unique and workspace non-unique`() {
            val (workspaceSchema, mapping, uuidMap) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(unique = false))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(unique = true))
            )

            val changes = service.computeSchemaDiff(catalogSchema, workspaceSchema, mapping)

            val uniqueChange = changes.find { it.type == EntityTypeChangeType.FIELD_UNIQUE_ADDED }
            assertNotNull(uniqueChange)
            assertEquals("email", uniqueChange!!.attributeKey)
            assertTrue(uniqueChange.breaking)
            assertEquals(uuidMap["email"], uniqueChange.workspaceAttributeId)
        }

        @Test
        fun `detects METADATA_CHANGED when label differs`() {
            val (workspaceSchema, mapping, uuidMap) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(label = "Email Address"))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(label = "Work Email"))
            )

            val changes = service.computeSchemaDiff(catalogSchema, workspaceSchema, mapping)

            val metaChange = changes.find { it.type == EntityTypeChangeType.METADATA_CHANGED }
            assertNotNull(metaChange)
            assertEquals("email", metaChange!!.attributeKey)
            assertFalse(metaChange.breaking)
            assertTrue(metaChange.description.contains("label"))
        }

        @Test
        fun `detects METADATA_CHANGED when format differs`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(format = null))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(format = "email"))
            )

            val changes = service.computeSchemaDiff(catalogSchema, workspaceSchema, mapping)

            val metaChange = changes.find { it.type == EntityTypeChangeType.METADATA_CHANGED }
            assertNotNull(metaChange)
            assertEquals("email", metaChange!!.attributeKey)
            assertFalse(metaChange.breaking)
            assertTrue(metaChange.description.contains("format"))
        }

        @Test
        fun `detects multiple changes at once including mixed breaking and non-breaking`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf(
                    "email" to SchemaAttrDef(label = "Email", required = false),
                    "phone" to SchemaAttrDef(label = "Phone"),
                )
            )
            val catalogSchema = buildCatalogSchema(
                mapOf(
                    "email" to CatalogAttrDef(label = "Work Email", required = true),
                    "phone" to CatalogAttrDef(label = "Phone"),
                    "website" to CatalogAttrDef(label = "Website"),
                )
            )

            val changes = service.computeSchemaDiff(catalogSchema, workspaceSchema, mapping)

            val changeTypes = changes.map { it.type }.toSet()
            assertTrue(changeTypes.contains(EntityTypeChangeType.METADATA_CHANGED))
            assertTrue(changeTypes.contains(EntityTypeChangeType.FIELD_REQUIRED_ADDED))
            assertTrue(changeTypes.contains(EntityTypeChangeType.FIELD_ADDED))
            assertTrue(changes.any { it.breaking })
            assertTrue(changes.any { !it.breaking })
        }

        @Test
        fun `excludes user-added attributes that are not in attributeKeyMapping from diff`() {
            val userAddedUuid = UUID.randomUUID()
            val catalogUuid = UUID.randomUUID()
            val mapping = mapOf("email" to catalogUuid.toString())

            val properties = mapOf(
                catalogUuid to Schema<UUID>(key = SchemaType.TEXT, type = DataType.STRING, label = "Email"),
                userAddedUuid to Schema<UUID>(key = SchemaType.TEXT, type = DataType.STRING, label = "Custom Field"),
            )
            val workspaceSchema = Schema<UUID>(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = properties,
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(label = "Email"))
            )

            val changes = service.computeSchemaDiff(catalogSchema, workspaceSchema, mapping)

            assertTrue(changes.isEmpty(), "User-added attributes should not appear in diff")
        }
    }

    // ------ reconcileIfNeeded Tests ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@test.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
                role = WorkspaceRoles.OWNER
            )
        ]
    )
    inner class ReconcileIfNeeded {

        @Test
        fun `skips entity types with no sourceManifestId`() {
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = null,
            )

            service.reconcileIfNeeded(workspaceId, listOf(entityType))

            verify(catalogEntityTypeRepository, never()).findByManifestIdAndKey(any(), any())
        }

        @Test
        fun `skips entity types with no attributeKeyMapping and logs warning`() {
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = null,
                sourceSchemaHash = "old-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schemaHash = "new-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)

            service.reconcileIfNeeded(workspaceId, listOf(entityType))

            verify(logger).warn(any<() -> Any?>())
            verify(entityTypeRepository, never()).save(any())
        }

        @Test
        fun `skips when hash matches - entity type is up to date`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(label = "Email"))
            )
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = "matching-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schemaHash = "matching-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)

            service.reconcileIfNeeded(workspaceId, listOf(entityType))

            verify(entityTypeRepository, never()).save(any())
        }

        @Test
        fun `auto-applies non-breaking changes and updates hash`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(label = "Old Label"))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(label = "New Label"))
            )
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = "old-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            service.reconcileIfNeeded(workspaceId, listOf(entityType))

            val captor = argumentCaptor<riven.core.entity.entity.EntityTypeEntity>()
            verify(entityTypeRepository).save(captor.capture())
            assertEquals("new-hash", captor.firstValue.sourceSchemaHash)
            assertFalse(captor.firstValue.pendingSchemaUpdate)
        }

        @Test
        fun `sets pendingSchemaUpdate when breaking changes detected`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf(
                    "email" to SchemaAttrDef(required = false),
                    "phone" to SchemaAttrDef(label = "Phone"),
                )
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(required = true))
            )
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = "old-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            service.reconcileIfNeeded(workspaceId, listOf(entityType))

            val captor = argumentCaptor<riven.core.entity.entity.EntityTypeEntity>()
            verify(entityTypeRepository).save(captor.capture())
            assertTrue(captor.firstValue.pendingSchemaUpdate)
        }

        @Test
        fun `applies non-breaking changes even when breaking changes are present`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf(
                    "email" to SchemaAttrDef(label = "Email", required = false),
                )
            )
            val catalogSchema = buildCatalogSchema(
                mapOf(
                    "email" to CatalogAttrDef(label = "Updated Email", required = true),
                    "phone" to CatalogAttrDef(label = "Phone"),
                )
            )
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = "old-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            service.reconcileIfNeeded(workspaceId, listOf(entityType))

            val captor = argumentCaptor<riven.core.entity.entity.EntityTypeEntity>()
            verify(entityTypeRepository).save(captor.capture())
            val saved = captor.firstValue
            val savedProps = saved.schema.properties!!
            val emailUuid = UUID.fromString(mapping["email"])
            // Non-breaking metadata change applied despite breaking changes
            assertEquals("Updated Email", savedProps[emailUuid]?.label,
                "Non-breaking label change should be applied even when breaking changes are present")
            // New field added despite breaking changes
            assertNotNull(saved.attributeKeyMapping!!["phone"],
                "Non-breaking field addition should be applied even when breaking changes are present")
            // Breaking state is set, hash is NOT updated
            assertTrue(saved.pendingSchemaUpdate)
            assertNotEquals("new-hash", saved.sourceSchemaHash,
                "Hash should NOT be updated when breaking changes are present")
        }

        @Test
        fun `stamps hash on legacy entity types with no sourceSchemaHash`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(label = "Email"))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(label = "Email"))
            )
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = null,
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "catalog-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            service.reconcileIfNeeded(workspaceId, listOf(entityType))

            val captor = argumentCaptor<riven.core.entity.entity.EntityTypeEntity>()
            verify(entityTypeRepository).save(captor.capture())
            // No changes between schemas, so hash should be stamped
            assertEquals("catalog-hash", captor.firstValue.sourceSchemaHash)
        }

        @Test
        fun `skips when catalog entry not found`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(label = "Email"))
            )
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = "some-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(null)

            service.reconcileIfNeeded(workspaceId, listOf(entityType))

            verify(entityTypeRepository, never()).save(any())
        }

        @Test
        fun `logs activity for auto-apply`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(label = "Old Label"))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(label = "New Label"))
            )
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = "old-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            service.reconcileIfNeeded(workspaceId, listOf(entityType))

            verify(activityService).logActivity(
                activity = eq(Activity.ENTITY_TYPE),
                operation = eq(OperationType.UPDATE),
                userId = eq(userId),
                workspaceId = eq(workspaceId),
                entityType = eq(ApplicationEntityType.ENTITY_TYPE),
                entityId = eq(entityType.id),
                timestamp = any(),
                details = argThat<Map<String, Any?>> { this["action"] == "AUTO_APPLY" },
            )
        }

        @Test
        fun `logs activity for breaking detection`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(required = false))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(required = true))
            )
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = "old-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            service.reconcileIfNeeded(workspaceId, listOf(entityType))

            verify(activityService).logActivity(
                activity = eq(Activity.ENTITY_TYPE),
                operation = eq(OperationType.UPDATE),
                userId = eq(userId),
                workspaceId = eq(workspaceId),
                entityType = eq(ApplicationEntityType.ENTITY_TYPE),
                entityId = eq(entityType.id),
                timestamp = any(),
                details = argThat<Map<String, Any?>> { this["action"] == "BREAKING_DETECTED" },
            )
        }

        @Test
        fun `concurrency guard prevents duplicate reconciliation`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(label = "Old Label"))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(label = "New Label"))
            )
            val entityTypeId = UUID.randomUUID()
            val entityType = EntityFactory.createEntityType(
                id = entityTypeId,
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = "old-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            // Call twice with the same entity; the first call auto-applies and updates the
            // sourceSchemaHash in memory. The second call sees hashes match (isUpToDate)
            // and skips processing. This proves the lock is released after the first call
            // (no deadlock) and re-entrant processing works correctly.
            service.reconcileIfNeeded(workspaceId, listOf(entityType))
            service.reconcileIfNeeded(workspaceId, listOf(entityType))

            // Only one save: first call applies changes; second call sees up-to-date hash and skips
            verify(entityTypeRepository, times(1)).save(any())
        }

        /**
         * Bug fix: when pendingSchemaUpdate is already true and the same breaking diff is
         * re-detected on subsequent workspace access, activity should NOT be logged again.
         * Previously, every access re-logged BREAKING_DETECTED.
         */
        @Test
        fun `does not re-log activity when pendingSchemaUpdate is already true`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(required = false))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(required = true))
            )
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = "old-hash",
                pendingSchemaUpdate = true,
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            service.reconcileIfNeeded(workspaceId, listOf(entityType))

            // Entity saved (to persist any non-breaking changes) but no activity logged
            verify(entityTypeRepository).save(any())
            verify(activityService, never()).logActivity(
                activity = any(),
                operation = any(),
                userId = any(),
                workspaceId = any(),
                entityType = any(),
                entityId = any(),
                timestamp = any(),
                details = any(),
            )
        }

        /**
         * Bug fix: when a subsequent catalog update reverses the breaking change (only
         * non-breaking diffs remain), pendingSchemaUpdate should be cleared automatically
         * and sourceSchemaHash updated.
         */
        @Test
        fun `clears pendingSchemaUpdate when breaking changes are resolved by catalog update`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(label = "Old Label"))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(label = "New Label"))
            )
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = "old-hash",
                pendingSchemaUpdate = true,
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            service.reconcileIfNeeded(workspaceId, listOf(entityType))

            val captor = argumentCaptor<riven.core.entity.entity.EntityTypeEntity>()
            verify(entityTypeRepository).save(captor.capture())
            val saved = captor.firstValue
            assertFalse(saved.pendingSchemaUpdate, "Flag should be cleared when no breaking changes remain")
            assertEquals("new-hash", saved.sourceSchemaHash, "Hash should be updated after resolution")
            assertEquals("New Label", saved.schema.properties!![UUID.fromString(mapping["email"])]?.label,
                "Non-breaking changes should be applied")

            verify(activityService).logActivity(
                activity = eq(Activity.ENTITY_TYPE),
                operation = eq(OperationType.UPDATE),
                userId = eq(userId),
                workspaceId = eq(workspaceId),
                entityType = eq(ApplicationEntityType.ENTITY_TYPE),
                entityId = eq(entityType.id),
                timestamp = any(),
                details = argThat<Map<String, Any?>> { this["action"] == "BREAKING_RESOLVED" },
            )
        }

        /**
         * Bug fix: when hashes match but pendingSchemaUpdate is still true (stale flag),
         * the flag should be cleared. This can happen when a catalog update reverses changes
         * to produce an identical schema.
         */
        @Test
        fun `clears stale pendingSchemaUpdate when hashes already match`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(label = "Email"))
            )
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = "matching-hash",
                pendingSchemaUpdate = true,
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schemaHash = "matching-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            service.reconcileIfNeeded(workspaceId, listOf(entityType))

            val captor = argumentCaptor<riven.core.entity.entity.EntityTypeEntity>()
            verify(entityTypeRepository).save(captor.capture())
            assertFalse(captor.firstValue.pendingSchemaUpdate,
                "Stale pending flag should be cleared when hashes match")

            verify(activityService).logActivity(
                activity = eq(Activity.ENTITY_TYPE),
                operation = eq(OperationType.UPDATE),
                userId = eq(userId),
                workspaceId = eq(workspaceId),
                entityType = eq(ApplicationEntityType.ENTITY_TYPE),
                entityId = eq(entityType.id),
                timestamp = any(),
                details = argThat<Map<String, Any?>> { this["action"] == "BREAKING_RESOLVED" },
            )
        }
    }

    // ------ applyBreakingChanges Tests ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@test.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
                role = WorkspaceRoles.OWNER
            )
        ]
    )
    inner class ApplyBreakingChanges {

        @Test
        fun `returns ReconciliationImpact when impactConfirmed is false`() {
            val entityTypeId = UUID.randomUUID()
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(required = false))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(required = true))
            )
            val entityType = EntityFactory.createEntityType(
                id = entityTypeId,
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                pendingSchemaUpdate = true,
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(entityTypeRepository.findAllById(listOf(entityTypeId)))
                .thenReturn(listOf(entityType))
            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityAttributeRepository.countByTypeIdAndAttributeId(any(), any()))
                .thenReturn(5L)

            val result = service.applyBreakingChanges(workspaceId, listOf(entityTypeId), impactConfirmed = false)

            assertTrue(result is ReconciliationImpact)
            val impact = result as ReconciliationImpact
            assertTrue(impact.impacts.containsKey(entityTypeId))
        }

        @Test
        fun `impact includes affected entity counts and field removal info`() {
            val entityTypeId = UUID.randomUUID()
            val (workspaceSchema, mapping, uuidMap) = buildWorkspaceSchema(
                mapOf(
                    "email" to SchemaAttrDef(label = "Email"),
                    "phone" to SchemaAttrDef(label = "Phone"),
                )
            )
            // Remove phone from catalog to trigger FIELD_REMOVED
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(label = "Email"))
            )
            val entityType = EntityFactory.createEntityType(
                id = entityTypeId,
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                pendingSchemaUpdate = true,
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(entityTypeRepository.findAllById(listOf(entityTypeId)))
                .thenReturn(listOf(entityType))
            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityAttributeRepository.countByTypeIdAndAttributeId(eq(entityTypeId), eq(uuidMap["phone"]!!)))
                .thenReturn(42L)

            val result = service.applyBreakingChanges(workspaceId, listOf(entityTypeId), impactConfirmed = false)
                as ReconciliationImpact

            val typeImpact = result.impacts[entityTypeId]!!
            assertEquals(42L, typeImpact.affectedEntities)
            assertTrue(typeImpact.fieldsRemoved.contains("phone"))
            assertTrue(typeImpact.dataLoss)
        }

        /**
         * Regression: previously buildEntityTypeImpact used maxOfOrNull across breaking changes, so the
         * confirmation payload reported only the largest single bucket, not the full destructive impact.
         * Admins could approve a reconcile while seeing a materially low estimate. After the fix it sums
         * counts across all breaking changes, so the reported affectedEntities reflects total impact.
         */
        @Test
        fun `affectedEntities sums counts across multiple breaking changes`() {
            val entityTypeId = UUID.randomUUID()
            val (workspaceSchema, mapping, uuidMap) = buildWorkspaceSchema(
                mapOf(
                    "email" to SchemaAttrDef(label = "Email"),
                    "phone" to SchemaAttrDef(label = "Phone"),
                )
            )
            // Remove both email and phone from catalog → two breaking FIELD_REMOVED changes.
            val catalogSchema = buildCatalogSchema(emptyMap())
            val entityType = EntityFactory.createEntityType(
                id = entityTypeId,
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                pendingSchemaUpdate = true,
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(entityTypeRepository.findAllById(listOf(entityTypeId)))
                .thenReturn(listOf(entityType))
            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityAttributeRepository.countByTypeIdAndAttributeId(eq(entityTypeId), eq(uuidMap["email"]!!)))
                .thenReturn(5L)
            whenever(entityAttributeRepository.countByTypeIdAndAttributeId(eq(entityTypeId), eq(uuidMap["phone"]!!)))
                .thenReturn(7L)

            val result = service.applyBreakingChanges(workspaceId, listOf(entityTypeId), impactConfirmed = false)
                as ReconciliationImpact

            val typeImpact = result.impacts[entityTypeId]!!
            assertEquals(12L, typeImpact.affectedEntities)
        }

        @Test
        fun `applies all changes when impactConfirmed is true`() {
            val entityTypeId = UUID.randomUUID()
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf(
                    "email" to SchemaAttrDef(label = "Email", required = false),
                    "phone" to SchemaAttrDef(label = "Phone"),
                )
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(label = "Updated Email", required = true))
            )
            val entityType = EntityFactory.createEntityType(
                id = entityTypeId,
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                pendingSchemaUpdate = true,
                sourceSchemaHash = "old-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(entityTypeRepository.findAllById(listOf(entityTypeId)))
                .thenReturn(listOf(entityType))
            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            val result = service.applyBreakingChanges(workspaceId, listOf(entityTypeId), impactConfirmed = true)

            assertTrue(result is ReconciliationResult)
            val reconciled = (result as ReconciliationResult).reconciled
            assertEquals(1, reconciled.size)
            assertTrue(reconciled[0].changesApplied > 0)
        }

        @Test
        fun `clears pendingSchemaUpdate flag after successful apply`() {
            val entityTypeId = UUID.randomUUID()
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(required = false))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(required = true))
            )
            val entityType = EntityFactory.createEntityType(
                id = entityTypeId,
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                pendingSchemaUpdate = true,
                sourceSchemaHash = "old-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(entityTypeRepository.findAllById(listOf(entityTypeId)))
                .thenReturn(listOf(entityType))
            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            service.applyBreakingChanges(workspaceId, listOf(entityTypeId), impactConfirmed = true)

            val captor = argumentCaptor<riven.core.entity.entity.EntityTypeEntity>()
            verify(entityTypeRepository).save(captor.capture())
            assertFalse(captor.firstValue.pendingSchemaUpdate)
        }

        @Test
        fun `updates sourceSchemaHash after apply`() {
            val entityTypeId = UUID.randomUUID()
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(required = false))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(required = true))
            )
            val entityType = EntityFactory.createEntityType(
                id = entityTypeId,
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                pendingSchemaUpdate = true,
                sourceSchemaHash = "old-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "updated-hash",
            )

            whenever(entityTypeRepository.findAllById(listOf(entityTypeId)))
                .thenReturn(listOf(entityType))
            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            service.applyBreakingChanges(workspaceId, listOf(entityTypeId), impactConfirmed = true)

            val captor = argumentCaptor<riven.core.entity.entity.EntityTypeEntity>()
            verify(entityTypeRepository).save(captor.capture())
            assertEquals("updated-hash", captor.firstValue.sourceSchemaHash)
        }

        @Test
        fun `deletes entity attributes for FIELD_REMOVED`() {
            val entityTypeId = UUID.randomUUID()
            val (workspaceSchema, mapping, uuidMap) = buildWorkspaceSchema(
                mapOf(
                    "email" to SchemaAttrDef(label = "Email"),
                    "phone" to SchemaAttrDef(label = "Phone"),
                )
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(label = "Email"))
            )
            val entityType = EntityFactory.createEntityType(
                id = entityTypeId,
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                pendingSchemaUpdate = true,
                sourceSchemaHash = "old-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(entityTypeRepository.findAllById(listOf(entityTypeId)))
                .thenReturn(listOf(entityType))
            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            service.applyBreakingChanges(workspaceId, listOf(entityTypeId), impactConfirmed = true)

            verify(entityAttributeRepository).deleteAllByTypeIdAndAttributeId(entityTypeId, uuidMap["phone"]!!)
        }

        @Test
        fun `logs activity for breaking applied`() {
            val entityTypeId = UUID.randomUUID()
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(required = false))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(required = true))
            )
            val entityType = EntityFactory.createEntityType(
                id = entityTypeId,
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                pendingSchemaUpdate = true,
                sourceSchemaHash = "old-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(entityTypeRepository.findAllById(listOf(entityTypeId)))
                .thenReturn(listOf(entityType))
            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            service.applyBreakingChanges(workspaceId, listOf(entityTypeId), impactConfirmed = true)

            verify(activityService).logActivity(
                activity = eq(Activity.ENTITY_TYPE),
                operation = eq(OperationType.UPDATE),
                userId = eq(userId),
                workspaceId = eq(workspaceId),
                entityType = eq(ApplicationEntityType.ENTITY_TYPE),
                entityId = eq(entityTypeId),
                timestamp = any(),
                details = argThat<Map<String, Any?>> { this["action"] == "BREAKING_APPLIED" },
            )
        }

        @Test
        fun `filters to only entity types with pendingSchemaUpdate true`() {
            val pendingId = UUID.randomUUID()
            val notPendingId = UUID.randomUUID()
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(required = false))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(required = true))
            )
            val pendingType = EntityFactory.createEntityType(
                id = pendingId,
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                pendingSchemaUpdate = true,
                sourceSchemaHash = "old-hash",
            )
            val notPendingType = EntityFactory.createEntityType(
                id = notPendingId,
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                pendingSchemaUpdate = false,
                sourceSchemaHash = "old-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = pendingType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(entityTypeRepository.findAllById(listOf(pendingId, notPendingId)))
                .thenReturn(listOf(pendingType, notPendingType))
            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, pendingType.key))
                .thenReturn(catalogEntry)
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            val result = service.applyBreakingChanges(
                workspaceId, listOf(pendingId, notPendingId), impactConfirmed = true,
            ) as ReconciliationResult

            assertEquals(1, result.reconciled.size)
            assertEquals(pendingId, result.reconciled[0].entityTypeId)
        }

        @Test
        fun `filters to workspace-matching entity types`() {
            val matchingId = UUID.randomUUID()
            val wrongWorkspaceId = UUID.randomUUID()
            val otherWorkspace = UUID.randomUUID()
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(required = false))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(required = true))
            )
            val matchingType = EntityFactory.createEntityType(
                id = matchingId,
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                pendingSchemaUpdate = true,
                sourceSchemaHash = "old-hash",
            )
            val wrongWorkspaceType = EntityFactory.createEntityType(
                id = wrongWorkspaceId,
                workspaceId = otherWorkspace,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                pendingSchemaUpdate = true,
                sourceSchemaHash = "old-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = matchingType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(entityTypeRepository.findAllById(listOf(matchingId, wrongWorkspaceId)))
                .thenReturn(listOf(matchingType, wrongWorkspaceType))
            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, matchingType.key))
                .thenReturn(catalogEntry)
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            val result = service.applyBreakingChanges(
                workspaceId, listOf(matchingId, wrongWorkspaceId), impactConfirmed = true,
            ) as ReconciliationResult

            assertEquals(1, result.reconciled.size)
            assertEquals(matchingId, result.reconciled[0].entityTypeId)
        }

        /**
         * Regression: previously each per-entity-type apply ran inside the outer @Transactional, so a
         * RuntimeException thrown for one entity poisoned the surrounding transaction and rolled back
         * the writes of any successful sibling iterations even though [ReconciliationResult.errors]
         * implied partial progress. The fix wraps each iteration in a REQUIRES_NEW TransactionTemplate
         * so a thrown exception only rolls back the failing iteration.
         *
         * The test exercises this contract by having entity B's deleteAllByTypeIdAndAttributeId throw,
         * and asserting that A still appears in `reconciled` and that A's save was committed (i.e. not
         * rolled back) while B's id surfaces in `errors`.
         */
        @Test
        fun `failed entity type does not roll back successful sibling iterations`() {
            val idA = UUID.randomUUID()
            val idB = UUID.randomUUID()
            val (workspaceSchemaA, mappingA, uuidMapA) = buildWorkspaceSchema(
                mapOf(
                    "email" to SchemaAttrDef(label = "Email"),
                    "phone" to SchemaAttrDef(label = "Phone"),
                )
            )
            val (workspaceSchemaB, mappingB, uuidMapB) = buildWorkspaceSchema(
                mapOf(
                    "email" to SchemaAttrDef(label = "Email"),
                    "phone" to SchemaAttrDef(label = "Phone"),
                )
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(label = "Email"))
            )
            val entityA = EntityFactory.createEntityType(
                id = idA,
                key = "type-a",
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mappingA,
                schema = workspaceSchemaA,
                pendingSchemaUpdate = true,
                sourceSchemaHash = "old-hash",
            )
            val entityB = EntityFactory.createEntityType(
                id = idB,
                key = "type-b",
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mappingB,
                schema = workspaceSchemaB,
                pendingSchemaUpdate = true,
                sourceSchemaHash = "old-hash",
            )
            val catalogA = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityA.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )
            val catalogB = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityB.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(entityTypeRepository.findAllById(listOf(idA, idB)))
                .thenReturn(listOf(entityA, entityB))
            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityA.key))
                .thenReturn(catalogA)
            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityB.key))
                .thenReturn(catalogB)
            whenever(entityAttributeRepository.deleteAllByTypeIdAndAttributeId(eq(idB), eq(uuidMapB["phone"]!!)))
                .thenThrow(RuntimeException("simulated delete failure for B"))
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            val result = service.applyBreakingChanges(
                workspaceId, listOf(idA, idB), impactConfirmed = true,
            ) as ReconciliationResult

            assertEquals(1, result.reconciled.size)
            assertEquals(idA, result.reconciled[0].entityTypeId)
            assertEquals(1, result.errors.size)
            assertTrue(result.errors[0].contains(idB.toString()))
            verify(entityAttributeRepository).deleteAllByTypeIdAndAttributeId(idA, uuidMapA["phone"]!!)
            verify(entityTypeRepository).save(entityA)
            verify(entityTypeRepository, never()).save(entityB)
        }
    }

    // ------ getSchemaHealth Tests ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@test.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
                role = WorkspaceRoles.OWNER
            )
        ]
    )
    inner class GetSchemaHealth {

        @Test
        fun `returns UP_TO_DATE when hashes match`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(label = "Email"))
            )
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = "matching-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schemaHash = "matching-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)

            val response = service.getSchemaHealth(workspaceId, listOf(entityType))

            assertEquals(1, response.entityTypes.size)
            assertEquals(SchemaHealthStatusType.UP_TO_DATE, response.entityTypes[0].status)
            assertTrue(response.entityTypes[0].pendingChanges.isEmpty())
        }

        @Test
        fun `returns PENDING_BREAKING when breaking changes detected`() {
            val (workspaceSchema, mapping, uuidMap) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(required = false))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(required = true))
            )
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = "old-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityAttributeRepository.countByTypeIdAndAttributeId(any(), any()))
                .thenReturn(10L)

            val response = service.getSchemaHealth(workspaceId, listOf(entityType))

            assertEquals(SchemaHealthStatusType.PENDING_BREAKING, response.entityTypes[0].status)
            assertTrue(response.entityTypes[0].pendingChanges.any { it.breaking })
        }

        @Test
        fun `returns PENDING_NON_BREAKING when only non-breaking changes`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(label = "Old Label"))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(label = "New Label"))
            )
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = "old-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityAttributeRepository.countByTypeIdAndAttributeId(any(), any()))
                .thenReturn(3L)

            val response = service.getSchemaHealth(workspaceId, listOf(entityType))

            assertEquals(SchemaHealthStatusType.PENDING_NON_BREAKING, response.entityTypes[0].status)
            assertTrue(response.entityTypes[0].pendingChanges.all { !it.breaking })
        }

        @Test
        fun `returns UNKNOWN when no catalog entry found`() {
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapOf("email" to UUID.randomUUID().toString()),
                sourceSchemaHash = "some-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(null)

            val response = service.getSchemaHealth(workspaceId, listOf(entityType))

            assertEquals(SchemaHealthStatusType.UNKNOWN, response.entityTypes[0].status)
        }

        @Test
        fun `returns UNKNOWN when no attributeKeyMapping`() {
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = null,
                sourceSchemaHash = "some-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schemaHash = "some-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)

            val response = service.getSchemaHealth(workspaceId, listOf(entityType))

            assertEquals(SchemaHealthStatusType.UNKNOWN, response.entityTypes[0].status)
        }

        @Test
        fun `includes affected entity counts in pending changes`() {
            val (workspaceSchema, mapping, uuidMap) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(required = false))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(required = true))
            )
            val entityTypeId = UUID.randomUUID()
            val entityType = EntityFactory.createEntityType(
                id = entityTypeId,
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = "old-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityAttributeRepository.countByTypeIdAndAttributeId(eq(entityTypeId), eq(uuidMap["email"]!!)))
                .thenReturn(25L)

            val response = service.getSchemaHealth(workspaceId, listOf(entityType))

            val pendingChange = response.entityTypes[0].pendingChanges.first()
            assertEquals(25L, pendingChange.affectedEntityCount)
        }

        /**
         * Regression: legacy entity types with sourceSchemaHash == null whose schema is structurally
         * identical to the catalog used to fall through to PENDING_NON_BREAKING with an empty
         * pendingChanges list, surfacing false drift to admins. The fix returns UP_TO_DATE when the
         * computed diff is empty regardless of whether a hash has been stamped.
         */
        @Test
        fun `returns UP_TO_DATE for legacy entity type with null sourceSchemaHash but matching schema`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(mapOf("email" to SchemaAttrDef()))
            val catalogSchema = buildCatalogSchema(mapOf("email" to CatalogAttrDef()))
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = null,
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "catalog-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)

            val response = service.getSchemaHealth(workspaceId, listOf(entityType))

            val status = response.entityTypes[0]
            assertEquals(SchemaHealthStatusType.UP_TO_DATE, status.status)
            assertTrue(status.pendingChanges.isEmpty())
            assertEquals("catalog-hash", status.catalogSchemaHash)
            assertEquals("catalog-hash", status.sourceSchemaHash)
        }

        @Test
        fun `summary counts are correct`() {
            // Create three entity types with different statuses
            val (schema1, mapping1) = buildWorkspaceSchema(mapOf("a" to SchemaAttrDef()))
            val (schema2, mapping2) = buildWorkspaceSchema(mapOf("b" to SchemaAttrDef(label = "Old")))
            val (schema3, mapping3) = buildWorkspaceSchema(mapOf("c" to SchemaAttrDef(required = false)))

            val upToDateType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                key = "type_a",
                attributeKeyMapping = mapping1,
                schema = schema1,
                sourceSchemaHash = "hash-a",
            )
            val nonBreakingType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                key = "type_b",
                attributeKeyMapping = mapping2,
                schema = schema2,
                sourceSchemaHash = "old-hash-b",
            )
            val breakingType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                key = "type_c",
                attributeKeyMapping = mapping3,
                schema = schema3,
                sourceSchemaHash = "old-hash-c",
            )

            val catA = CatalogFactory.createEntityTypeEntity(manifestId = manifestId, key = "type_a", schemaHash = "hash-a")
            val catB = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId, key = "type_b",
                schema = buildCatalogSchema(mapOf("b" to CatalogAttrDef(label = "New"))),
                schemaHash = "new-hash-b",
            )
            val catC = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId, key = "type_c",
                schema = buildCatalogSchema(mapOf("c" to CatalogAttrDef(required = true))),
                schemaHash = "new-hash-c",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, "type_a")).thenReturn(catA)
            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, "type_b")).thenReturn(catB)
            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, "type_c")).thenReturn(catC)
            whenever(entityAttributeRepository.countByTypeIdAndAttributeId(any(), any())).thenReturn(0L)

            val response = service.getSchemaHealth(
                workspaceId,
                listOf(upToDateType, nonBreakingType, breakingType),
            )

            assertEquals(3, response.summary.total)
            assertEquals(1, response.summary.upToDate)
            assertEquals(1, response.summary.pendingNonBreaking)
            assertEquals(1, response.summary.pendingBreaking)
            assertEquals(0, response.summary.unknown)
        }
    }

    // ------ Access Denied Tests ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@test.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "00000000-0000-0000-0000-000000000000",
                role = WorkspaceRoles.OWNER
            )
        ]
    )
    inner class UnauthorizedAccessTests {

        /**
         * @PreAuthorize on reconcileIfNeeded must reject access when the authenticated user has no
         * role on the target workspace, before any repository interaction. Regression coverage per
         * the project rule that every @PreAuthorize-protected service method needs a denied-access test.
         */
        @Test
        fun `reconcileIfNeeded throws AccessDeniedException for unauthorized workspace`() {
            assertThrows(AccessDeniedException::class.java) {
                service.reconcileIfNeeded(workspaceId, emptyList())
            }
        }

        @Test
        fun `applyBreakingChanges throws AccessDeniedException for unauthorized workspace`() {
            assertThrows(AccessDeniedException::class.java) {
                service.applyBreakingChanges(workspaceId, emptyList(), impactConfirmed = false)
            }
        }

        @Test
        fun `getSchemaHealth throws AccessDeniedException for unauthorized workspace`() {
            assertThrows(AccessDeniedException::class.java) {
                service.getSchemaHealth(workspaceId, emptyList())
            }
        }
    }

    // ------ Auto-Apply Mechanics Tests ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@test.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
                role = WorkspaceRoles.OWNER
            )
        ]
    )
    inner class AutoApplyMechanics {

        @Test
        fun `FIELD_ADDED generates deterministic UUID and adds to schema, mapping, and columnConfiguration`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(label = "Email"))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf(
                    "email" to CatalogAttrDef(label = "Email"),
                    "phone" to CatalogAttrDef(key = "TEXT", type = "string", label = "Phone"),
                )
            )
            val entityType = EntityFactory.createEntityType(
                key = "contacts",
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = "old-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = "contacts",
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, "contacts"))
                .thenReturn(catalogEntry)
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            service.reconcileIfNeeded(workspaceId, listOf(entityType))

            val captor = argumentCaptor<riven.core.entity.entity.EntityTypeEntity>()
            verify(entityTypeRepository).save(captor.capture())
            val saved = captor.firstValue

            // The deterministic UUID for "integration:contacts:phone"
            val expectedUuid = UUID.nameUUIDFromBytes("integration:contacts:phone".toByteArray())

            // Verify added to schema properties
            assertNotNull(saved.schema.properties?.get(expectedUuid))
            assertEquals("Phone", saved.schema.properties?.get(expectedUuid)?.label)
            assertEquals(SchemaType.TEXT, saved.schema.properties?.get(expectedUuid)?.key)

            // Verify added to attributeKeyMapping
            assertEquals(expectedUuid.toString(), saved.attributeKeyMapping?.get("phone"))

            // Verify added to columnConfiguration order
            assertTrue(saved.columnConfiguration?.order?.contains(expectedUuid) == true)
        }

        @Test
        fun `METADATA_CHANGED updates label in workspace schema`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(label = "Old Email Label"))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(label = "Updated Email Label"))
            )
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = "old-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            service.reconcileIfNeeded(workspaceId, listOf(entityType))

            val captor = argumentCaptor<riven.core.entity.entity.EntityTypeEntity>()
            verify(entityTypeRepository).save(captor.capture())
            val emailUuid = UUID.fromString(mapping["email"])
            val updatedAttr = captor.firstValue.schema.properties?.get(emailUuid)
            assertEquals("Updated Email Label", updatedAttr?.label)
        }

        @Test
        fun `METADATA_CHANGED updates format in workspace schema`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(format = null))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(format = "email"))
            )
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = "old-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            service.reconcileIfNeeded(workspaceId, listOf(entityType))

            val captor = argumentCaptor<riven.core.entity.entity.EntityTypeEntity>()
            verify(entityTypeRepository).save(captor.capture())
            val emailUuid = UUID.fromString(mapping["email"])
            val updatedAttr = captor.firstValue.schema.properties?.get(emailUuid)
            assertEquals(DataFormat.EMAIL, updatedAttr?.format)
        }
    }

    // ------ Connotation snapshot invalidation hook ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@test.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
                role = WorkspaceRoles.OWNER
            )
        ]
    )
    inner class ConnotationInvalidationHook {

        @Test
        fun `enqueues re-enrichment when non-breaking changes are auto-applied`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(label = "Old Label"))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(label = "New Label"))
            )
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = "old-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            service.reconcileIfNeeded(workspaceId, listOf(entityType))

            verify(enrichmentService).enqueueByEntityType(
                requireNotNull(entityType.id),
                workspaceId,
            )
        }

        @Test
        fun `does NOT enqueue when entity type is up to date`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(label = "Email"))
            )
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = "matching-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schemaHash = "matching-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)

            service.reconcileIfNeeded(workspaceId, listOf(entityType))

            verify(enrichmentService, never()).enqueueByEntityType(any(), any())
        }

        @Test
        fun `does NOT enqueue when only breaking changes were detected and stayed pending`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(required = false))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(required = true))
            )
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = "old-hash",
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            service.reconcileIfNeeded(workspaceId, listOf(entityType))

            verify(enrichmentService, never()).enqueueByEntityType(any(), any())
        }

        @Test
        fun `enqueues re-enrichment after applyBreakingChanges with confirmation`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(required = false))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(required = true))
            )
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = "old-hash",
                pendingSchemaUpdate = true,
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(entityTypeRepository.findAllById(listOf(requireNotNull(entityType.id))))
                .thenReturn(listOf(entityType))
            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)
            whenever(entityTypeRepository.save(any())).thenAnswer { it.arguments[0] }

            service.applyBreakingChanges(
                workspaceId = workspaceId,
                entityTypeIds = listOf(requireNotNull(entityType.id)),
                impactConfirmed = true,
            )

            verify(enrichmentService).enqueueByEntityType(
                requireNotNull(entityType.id),
                workspaceId,
            )
        }

        @Test
        fun `does NOT enqueue when applyBreakingChanges is called without confirmation`() {
            val (workspaceSchema, mapping) = buildWorkspaceSchema(
                mapOf("email" to SchemaAttrDef(required = false))
            )
            val catalogSchema = buildCatalogSchema(
                mapOf("email" to CatalogAttrDef(required = true))
            )
            val entityType = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                sourceManifestId = manifestId,
                attributeKeyMapping = mapping,
                schema = workspaceSchema,
                sourceSchemaHash = "old-hash",
                pendingSchemaUpdate = true,
            )
            val catalogEntry = CatalogFactory.createEntityTypeEntity(
                manifestId = manifestId,
                key = entityType.key,
                schema = catalogSchema,
                schemaHash = "new-hash",
            )

            whenever(entityTypeRepository.findAllById(listOf(requireNotNull(entityType.id))))
                .thenReturn(listOf(entityType))
            whenever(catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key))
                .thenReturn(catalogEntry)

            service.applyBreakingChanges(
                workspaceId = workspaceId,
                entityTypeIds = listOf(requireNotNull(entityType.id)),
                impactConfirmed = false,
            )

            verify(enrichmentService, never()).enqueueByEntityType(any(), any())
        }
    }
}
