package riven.core.service.entity.type

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.entity.EntityPropertyType
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.models.common.display.DisplayName
import riven.core.models.common.Icon
import riven.core.models.common.validation.Schema
import riven.core.models.entity.RelationshipDefinition
import riven.core.models.entity.configuration.ColumnConfiguration
import riven.core.models.entity.configuration.ColumnOverride
import riven.core.models.entity.configuration.EntityTypeAttributeColumn
import riven.core.models.request.entity.type.*
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.RelationshipDefinitionRepository
import riven.core.repository.entity.RelationshipTargetRuleRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.EntityTypeSemanticMetadataService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import riven.core.service.util.factory.entity.EntityFactory
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        EntityTypeServiceTest.TestConfig::class,
        EntityTypeService::class,
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
class EntityTypeServiceTest : BaseServiceTest() {

    @Configuration
    class TestConfig

    @MockitoBean
    private lateinit var entityTypeRepository: EntityTypeRepository

    @MockitoBean
    private lateinit var entityTypeRelationshipService: EntityTypeRelationshipService

    @MockitoBean
    private lateinit var entityAttributeService: EntityTypeAttributeService

    @MockitoBean
    private lateinit var definitionRepository: RelationshipDefinitionRepository

    @MockitoBean
    private lateinit var entityRelationshipRepository: EntityRelationshipRepository

    @MockitoBean
    private lateinit var targetRuleRepository: RelationshipTargetRuleRepository

    @MockitoBean
    private lateinit var activityService: ActivityService

    @MockitoBean
    private lateinit var semanticMetadataService: EntityTypeSemanticMetadataService

    @Autowired
    private lateinit var service: EntityTypeService

    @BeforeEach
    fun setup() {
        reset(
            entityTypeRepository,
            entityTypeRelationshipService,
            entityAttributeService,
            definitionRepository,
            entityRelationshipRepository,
            targetRuleRepository,
            activityService,
            semanticMetadataService,
        )
    }

    // ------ Column Assembly Tests ------

    @Nested
    inner class AssembleColumns {

        private val attr1 = UUID.randomUUID()
        private val attr2 = UUID.randomUUID()
        private val attr3 = UUID.randomUUID()
        private val rel1 = UUID.randomUUID()
        private val rel2 = UUID.randomUUID()

        private fun schemaWith(vararg attrIds: UUID): Schema<UUID> = Schema(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = attrIds.associateWith {
                Schema<UUID>(key = SchemaType.TEXT, type = DataType.STRING)
            }
        )

        private fun relDef(id: UUID): RelationshipDefinition = RelationshipDefinition(
            id = id,
            workspaceId = workspaceId,
            sourceEntityTypeId = UUID.randomUUID(),
            name = "Rel $id",
            icon = Icon(IconType.LINK, IconColour.NEUTRAL),
            cardinalityDefault = EntityRelationshipCardinality.ONE_TO_MANY,
            protected = false,
            createdAt = null,
            updatedAt = null,
            createdBy = null,
            updatedBy = null,
        )

        @Test
        fun `schema-only - attributes in schema order, no relationships`() {
            val columns = EntityTypeService.assembleColumns(
                schema = schemaWith(attr1, attr2, attr3),
                relationships = emptyList(),
                config = null,
            )

            assertEquals(3, columns.size)
            assertTrue(columns.all { it.type == EntityPropertyType.ATTRIBUTE })
            assertEquals(listOf(attr1, attr2, attr3), columns.map { it.key })
        }

        @Test
        fun `schema + relationships - attributes first, then relationships (default order)`() {
            val columns = EntityTypeService.assembleColumns(
                schema = schemaWith(attr1, attr2),
                relationships = listOf(relDef(rel1)),
                config = null,
            )

            assertEquals(3, columns.size)
            assertEquals(attr1, columns[0].key)
            assertEquals(EntityPropertyType.ATTRIBUTE, columns[0].type)
            assertEquals(attr2, columns[1].key)
            assertEquals(rel1, columns[2].key)
            assertEquals(EntityPropertyType.RELATIONSHIP, columns[2].type)
        }

        @Test
        fun `custom columnOrder - respects explicit ordering`() {
            val config = ColumnConfiguration(order = listOf(rel1, attr2, attr1))
            val columns = EntityTypeService.assembleColumns(
                schema = schemaWith(attr1, attr2),
                relationships = listOf(relDef(rel1)),
                config = config,
            )

            assertEquals(3, columns.size)
            assertEquals(listOf(rel1, attr2, attr1), columns.map { it.key })
        }

        @Test
        fun `partial columnOrder - ordered first, unordered appended`() {
            val config = ColumnConfiguration(order = listOf(attr2))
            val columns = EntityTypeService.assembleColumns(
                schema = schemaWith(attr1, attr2, attr3),
                relationships = listOf(relDef(rel1)),
                config = config,
            )

            assertEquals(4, columns.size)
            assertEquals(attr2, columns[0].key)
            // Remaining IDs are appended (attr1, attr3, rel1 in default order)
            val appendedIds = columns.drop(1).map { it.key }.toSet()
            assertTrue(attr1 in appendedIds)
            assertTrue(attr3 in appendedIds)
            assertTrue(rel1 in appendedIds)
        }

        @Test
        fun `stale IDs in columnOrder - filtered out`() {
            val staleId = UUID.randomUUID()
            val config = ColumnConfiguration(order = listOf(attr1, staleId, attr2))
            val columns = EntityTypeService.assembleColumns(
                schema = schemaWith(attr1, attr2),
                relationships = emptyList(),
                config = config,
            )

            assertEquals(2, columns.size)
            assertEquals(listOf(attr1, attr2), columns.map { it.key })
        }

        @Test
        fun `empty columnOrder list - falls back to default`() {
            val config = ColumnConfiguration(order = emptyList())
            val columns = EntityTypeService.assembleColumns(
                schema = schemaWith(attr1, attr2),
                relationships = listOf(relDef(rel1)),
                config = config,
            )

            assertEquals(3, columns.size)
            // Should fall back to default order: attributes then relationships
            assertEquals(attr1, columns[0].key)
            assertEquals(attr2, columns[1].key)
            assertEquals(rel1, columns[2].key)
        }

        @Test
        fun `overrides applied - custom width and visibility`() {
            val config = ColumnConfiguration(
                order = listOf(attr1, attr2),
                overrides = mapOf(
                    attr1 to ColumnOverride(width = 300, visible = false),
                    attr2 to ColumnOverride(width = 200),
                )
            )
            val columns = EntityTypeService.assembleColumns(
                schema = schemaWith(attr1, attr2),
                relationships = emptyList(),
                config = config,
            )

            assertEquals(2, columns.size)
            assertEquals(300, columns[0].width)
            assertEquals(false, columns[0].visible)
            assertEquals(200, columns[1].width)
            assertEquals(true, columns[1].visible) // default
        }

        @Test
        fun `no overrides - defaults to width 150, visible true`() {
            val columns = EntityTypeService.assembleColumns(
                schema = schemaWith(attr1),
                relationships = emptyList(),
                config = null,
            )

            assertEquals(1, columns.size)
            assertEquals(150, columns[0].width)
            assertEquals(true, columns[0].visible)
        }
    }

    // ------ Service Mutation Tests ------

    @Nested
    inner class SaveDefinition {

        @Test
        fun `saveEntityTypeDefinition - create relationship - succeeds without column sync`() {
            val sourceTypeId = UUID.randomUUID()
            val targetTypeId = UUID.randomUUID()
            val defId = UUID.randomUUID()

            val sourceEntityType = EntityFactory.createEntityType(
                id = sourceTypeId,
                key = "company",
                workspaceId = workspaceId,
            )

            val request = SaveTypeDefinitionRequest(
                index = null,
                definition = SaveRelationshipDefinitionRequest(
                    key = "company",
                    name = "Has Contacts",
                    iconType = IconType.LINK,
                    iconColour = IconColour.NEUTRAL,
                    cardinalityDefault = EntityRelationshipCardinality.ONE_TO_MANY,
                    targetRules = listOf(
                        SaveTargetRuleRequest(targetEntityTypeId = targetTypeId, inverseName = "Belongs To Company")
                    ),
                )
            )

            whenever(entityTypeRepository.findByworkspaceIdAndKey(workspaceId, "company"))
                .thenReturn(Optional.of(sourceEntityType))
            whenever(entityTypeRelationshipService.createRelationshipDefinition(eq(workspaceId), eq(sourceTypeId), any()))
                .thenReturn(EntityFactory.createRelationshipDefinitionEntity(
                    id = defId,
                    workspaceId = workspaceId,
                    sourceEntityTypeId = sourceTypeId,
                    name = "Has Contacts",
                    cardinalityDefault = EntityRelationshipCardinality.ONE_TO_MANY,
                ).toModel(emptyList()))
            whenever(entityTypeRepository.save(any<EntityTypeEntity>())).thenAnswer { it.arguments[0] }

            val result = service.saveEntityTypeDefinition(workspaceId, request)

            assertNull(result.impact)
            assertNotNull(result.updatedEntityTypes)
            assertTrue(result.updatedEntityTypes!!.containsKey("company"))

            // No inverse column propagation — target entity types should NOT be loaded
            verify(entityTypeRepository, never()).findAllById(any<List<UUID>>())
        }

        @Test
        fun `saveEntityTypeDefinition - with index - appends to column order`() {
            val sourceTypeId = UUID.randomUUID()
            val attrId = UUID.randomUUID()

            val schema = Schema<UUID>(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    attrId to Schema(key = SchemaType.TEXT, type = DataType.STRING, required = true)
                )
            )

            val sourceEntityType = EntityFactory.createEntityType(
                id = sourceTypeId,
                key = "company",
                workspaceId = workspaceId,
                schema = schema,
                columnConfiguration = ColumnConfiguration(order = listOf(attrId)),
            )

            val newAttrId = UUID.randomUUID()
            val request = SaveTypeDefinitionRequest(
                index = 0,
                definition = SaveAttributeDefinitionRequest(
                    key = "company",
                    id = newAttrId,
                    schema = Schema(
                        key = SchemaType.TEXT,
                        type = DataType.STRING,
                        label = "Industry",
                    ),
                )
            )

            whenever(entityTypeRepository.findByworkspaceIdAndKey(workspaceId, "company"))
                .thenReturn(Optional.of(sourceEntityType))
            whenever(entityTypeRepository.save(any<EntityTypeEntity>())).thenAnswer { it.arguments[0] }

            val result = service.saveEntityTypeDefinition(workspaceId, request)

            assertNull(result.impact)

            // Verify column configuration was updated with the new attribute at index 0
            val saveCaptor = argumentCaptor<EntityTypeEntity>()
            verify(entityTypeRepository, atLeastOnce()).save(saveCaptor.capture())

            val savedConfig = saveCaptor.lastValue.columnConfiguration
            assertNotNull(savedConfig)
            assertEquals(newAttrId, savedConfig!!.order.first())
        }
    }

    @Nested
    inner class RemoveDefinition {

        @Test
        fun `removeEntityTypeDefinition - delete attribute - removes from column order`() {
            val sourceTypeId = UUID.randomUUID()
            val attrId = UUID.randomUUID()
            val otherAttrId = UUID.randomUUID()

            val schema = Schema<UUID>(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    attrId to Schema(key = SchemaType.TEXT, type = DataType.STRING, required = true),
                    otherAttrId to Schema(key = SchemaType.TEXT, type = DataType.STRING),
                )
            )

            val sourceEntityType = EntityFactory.createEntityType(
                id = sourceTypeId,
                key = "company",
                workspaceId = workspaceId,
                schema = schema,
                columnConfiguration = ColumnConfiguration(order = listOf(attrId, otherAttrId)),
            )

            val request = DeleteTypeDefinitionRequest(
                definition = DeleteAttributeDefinitionRequest(
                    key = "company",
                    id = attrId,
                )
            )

            whenever(entityTypeRepository.findByworkspaceIdAndKey(workspaceId, "company"))
                .thenReturn(Optional.of(sourceEntityType))
            whenever(entityTypeRepository.save(any<EntityTypeEntity>())).thenAnswer { it.arguments[0] }

            val result = service.removeEntityTypeDefinition(workspaceId, request)

            assertNull(result.impact)

            val saveCaptor = argumentCaptor<EntityTypeEntity>()
            verify(entityTypeRepository, atLeastOnce()).save(saveCaptor.capture())

            val savedConfig = saveCaptor.lastValue.columnConfiguration
            assertNotNull(savedConfig)
            assertFalse(attrId in savedConfig!!.order, "Deleted attribute should be removed from column order")
            assertTrue(otherAttrId in savedConfig.order, "Other attribute should remain in column order")
        }

        @Test
        fun `removeEntityTypeDefinition - source-side relationship delete - succeeds without column propagation`() {
            val sourceTypeId = UUID.randomUUID()
            val defId = UUID.randomUUID()

            val sourceEntityType = EntityFactory.createEntityType(
                id = sourceTypeId,
                key = "company",
                workspaceId = workspaceId,
                columnConfiguration = ColumnConfiguration(order = listOf(defId)),
            )

            val request = DeleteTypeDefinitionRequest(
                definition = DeleteRelationshipDefinitionRequest(
                    key = "company",
                    id = defId,
                )
            )

            whenever(entityTypeRepository.findByworkspaceIdAndKey(workspaceId, "company"))
                .thenReturn(Optional.of(sourceEntityType))
            whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId))
                .thenReturn(Optional.of(EntityFactory.createRelationshipDefinitionEntity(
                    id = defId,
                    workspaceId = workspaceId,
                    sourceEntityTypeId = sourceTypeId,
                    name = "Has Contacts",
                )))
            whenever(targetRuleRepository.findByRelationshipDefinitionId(defId))
                .thenReturn(emptyList())
            whenever(entityTypeRelationshipService.deleteRelationshipDefinition(workspaceId, defId, false))
                .thenReturn(null)
            whenever(entityTypeRepository.save(any<EntityTypeEntity>())).thenAnswer { it.arguments[0] }

            val result = service.removeEntityTypeDefinition(workspaceId, request)

            assertNull(result.impact)

            // No inverse column propagation to target types
            verify(entityTypeRepository, never()).findAllById(any<List<UUID>>())

            // Column order should be cleaned up
            val saveCaptor = argumentCaptor<EntityTypeEntity>()
            verify(entityTypeRepository, atLeastOnce()).save(saveCaptor.capture())
            val savedConfig = saveCaptor.lastValue.columnConfiguration
            assertNotNull(savedConfig)
            assertFalse(defId in savedConfig!!.order)
        }

        @Test
        fun `removeEntityTypeDefinition - delete with impact not confirmed - returns impact`() {
            val sourceTypeId = UUID.randomUUID()
            val defId = UUID.randomUUID()

            val sourceEntityType = EntityFactory.createEntityType(
                id = sourceTypeId,
                key = "company",
                workspaceId = workspaceId,
            )

            val request = DeleteTypeDefinitionRequest(
                definition = DeleteRelationshipDefinitionRequest(
                    key = "company",
                    id = defId,
                )
            )

            whenever(entityTypeRepository.findByworkspaceIdAndKey(workspaceId, "company"))
                .thenReturn(Optional.of(sourceEntityType))
            whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId))
                .thenReturn(Optional.of(EntityFactory.createRelationshipDefinitionEntity(
                    id = defId,
                    workspaceId = workspaceId,
                    sourceEntityTypeId = sourceTypeId,
                    name = "Has Contacts",
                )))
            whenever(targetRuleRepository.findByRelationshipDefinitionId(defId))
                .thenReturn(listOf(
                    EntityFactory.createTargetRuleEntity(relationshipDefinitionId = defId, targetEntityTypeId = UUID.randomUUID()),
                ))
            whenever(entityTypeRelationshipService.deleteRelationshipDefinition(workspaceId, defId, false))
                .thenReturn(riven.core.models.response.entity.type.DeleteDefinitionImpact(
                    definitionId = defId,
                    definitionName = "Has Contacts",
                    impactedLinkCount = 5,
                ))

            val result = service.removeEntityTypeDefinition(workspaceId, request)

            assertNotNull(result.impact)
            assertEquals(5L, result.impact!!.impactedLinkCount)
        }
    }

    // ------ Version Increment Tests ------

    @Nested
    inner class VersionIncrement {

        /**
         * Verifies that saving an attribute definition increments the entity type version.
         * The version field is used by the frontend for cache invalidation — it must bump
         * on every schema mutation that flows through buildImpactResponse().
         */
        @Test
        fun `saveEntityTypeDefinition - save attribute - increments version`() {
            val sourceTypeId = UUID.randomUUID()
            val attrId = UUID.randomUUID()

            val sourceEntityType = EntityFactory.createEntityType(
                id = sourceTypeId,
                key = "company",
                workspaceId = workspaceId,
                version = 1,
            )

            val request = SaveTypeDefinitionRequest(
                index = null,
                definition = SaveAttributeDefinitionRequest(
                    key = "company",
                    id = attrId,
                    schema = Schema(key = SchemaType.TEXT, type = DataType.STRING, label = "Name"),
                )
            )

            whenever(entityTypeRepository.findByworkspaceIdAndKey(workspaceId, "company"))
                .thenReturn(Optional.of(sourceEntityType))
            whenever(entityTypeRepository.save(any<EntityTypeEntity>())).thenAnswer { it.arguments[0] }

            service.saveEntityTypeDefinition(workspaceId, request)

            val captor = argumentCaptor<EntityTypeEntity>()
            verify(entityTypeRepository, atLeastOnce()).save(captor.capture())
            assertEquals(2, captor.lastValue.version)
        }

        /**
         * Verifies that saving a relationship definition increments the entity type version.
         */
        @Test
        fun `saveEntityTypeDefinition - save relationship - increments version`() {
            val sourceTypeId = UUID.randomUUID()
            val targetTypeId = UUID.randomUUID()
            val defId = UUID.randomUUID()

            val sourceEntityType = EntityFactory.createEntityType(
                id = sourceTypeId,
                key = "company",
                workspaceId = workspaceId,
                version = 1,
            )

            val request = SaveTypeDefinitionRequest(
                index = null,
                definition = SaveRelationshipDefinitionRequest(
                    key = "company",
                    name = "Has Contacts",
                    iconType = IconType.LINK,
                    iconColour = IconColour.NEUTRAL,
                    cardinalityDefault = EntityRelationshipCardinality.ONE_TO_MANY,
                    targetRules = listOf(
                        SaveTargetRuleRequest(targetEntityTypeId = targetTypeId, inverseName = "Belongs To")
                    ),
                )
            )

            whenever(entityTypeRepository.findByworkspaceIdAndKey(workspaceId, "company"))
                .thenReturn(Optional.of(sourceEntityType))
            whenever(entityTypeRelationshipService.createRelationshipDefinition(eq(workspaceId), eq(sourceTypeId), any()))
                .thenReturn(EntityFactory.createRelationshipDefinitionEntity(
                    id = defId,
                    workspaceId = workspaceId,
                    sourceEntityTypeId = sourceTypeId,
                    name = "Has Contacts",
                    cardinalityDefault = EntityRelationshipCardinality.ONE_TO_MANY,
                ).toModel(emptyList()))
            whenever(entityTypeRepository.save(any<EntityTypeEntity>())).thenAnswer { it.arguments[0] }

            service.saveEntityTypeDefinition(workspaceId, request)

            val captor = argumentCaptor<EntityTypeEntity>()
            verify(entityTypeRepository, atLeastOnce()).save(captor.capture())
            assertEquals(2, captor.lastValue.version)
        }

        /**
         * Verifies that removing an attribute definition increments the entity type version.
         */
        @Test
        fun `removeEntityTypeDefinition - delete attribute - increments version`() {
            val sourceTypeId = UUID.randomUUID()
            val attrId = UUID.randomUUID()

            val schema = Schema<UUID>(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    attrId to Schema(key = SchemaType.TEXT, type = DataType.STRING, required = true),
                )
            )

            val sourceEntityType = EntityFactory.createEntityType(
                id = sourceTypeId,
                key = "company",
                workspaceId = workspaceId,
                schema = schema,
                version = 3,
            )

            val request = DeleteTypeDefinitionRequest(
                definition = DeleteAttributeDefinitionRequest(key = "company", id = attrId)
            )

            whenever(entityTypeRepository.findByworkspaceIdAndKey(workspaceId, "company"))
                .thenReturn(Optional.of(sourceEntityType))
            whenever(entityTypeRepository.save(any<EntityTypeEntity>())).thenAnswer { it.arguments[0] }

            service.removeEntityTypeDefinition(workspaceId, request)

            val captor = argumentCaptor<EntityTypeEntity>()
            verify(entityTypeRepository, atLeastOnce()).save(captor.capture())
            assertEquals(4, captor.lastValue.version)
        }

        /**
         * Verifies that removing a relationship definition increments the entity type version.
         */
        @Test
        fun `removeEntityTypeDefinition - delete relationship - increments version`() {
            val sourceTypeId = UUID.randomUUID()
            val defId = UUID.randomUUID()

            val sourceEntityType = EntityFactory.createEntityType(
                id = sourceTypeId,
                key = "company",
                workspaceId = workspaceId,
                version = 1,
            )

            val request = DeleteTypeDefinitionRequest(
                definition = DeleteRelationshipDefinitionRequest(key = "company", id = defId)
            )

            whenever(entityTypeRepository.findByworkspaceIdAndKey(workspaceId, "company"))
                .thenReturn(Optional.of(sourceEntityType))
            whenever(definitionRepository.findByIdAndWorkspaceId(defId, workspaceId))
                .thenReturn(Optional.of(EntityFactory.createRelationshipDefinitionEntity(
                    id = defId,
                    workspaceId = workspaceId,
                    sourceEntityTypeId = sourceTypeId,
                    name = "Has Contacts",
                )))
            whenever(targetRuleRepository.findByRelationshipDefinitionId(defId)).thenReturn(emptyList())
            whenever(entityTypeRelationshipService.deleteRelationshipDefinition(workspaceId, defId, false))
                .thenReturn(null)
            whenever(entityTypeRepository.save(any<EntityTypeEntity>())).thenAnswer { it.arguments[0] }

            service.removeEntityTypeDefinition(workspaceId, request)

            val captor = argumentCaptor<EntityTypeEntity>()
            verify(entityTypeRepository, atLeastOnce()).save(captor.capture())
            assertEquals(2, captor.lastValue.version)
        }

        /**
         * Verifies that updateEntityTypeConfiguration does NOT increment version.
         * Configuration changes (display name, icon, column order) are cosmetic and
         * should not invalidate frontend schema caches.
         */
        @Test
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
        fun `updateEntityTypeConfiguration - does not increment version`() {
            val entityTypeId = UUID.randomUUID()

            val entityType = EntityFactory.createEntityType(
                id = entityTypeId,
                key = "company",
                workspaceId = workspaceId,
                version = 1,
            )

            val request = UpdateEntityTypeConfigurationRequest(
                id = entityTypeId,
                name = DisplayName(
                    singular = "Updated Company",
                    plural = "Updated Companies",
                ),
                icon = Icon(IconType.BUILDING, IconColour.BLUE),
            )

            whenever(entityTypeRepository.findById(entityTypeId)).thenReturn(Optional.of(entityType))
            whenever(entityTypeRepository.save(any<EntityTypeEntity>())).thenAnswer { it.arguments[0] }

            service.updateEntityTypeConfiguration(workspaceId, request)

            val captor = argumentCaptor<EntityTypeEntity>()
            verify(entityTypeRepository).save(captor.capture())
            assertEquals(1, captor.firstValue.version)
        }

        /**
         * Verifies that sequential schema mutations correctly increment the version each time.
         * Starting at version 1, three consecutive attribute saves should result in version 4.
         */
        @Test
        fun `sequential mutations - version increments correctly`() {
            val sourceTypeId = UUID.randomUUID()

            val sourceEntityType = EntityFactory.createEntityType(
                id = sourceTypeId,
                key = "company",
                workspaceId = workspaceId,
                version = 1,
            )

            whenever(entityTypeRepository.findByworkspaceIdAndKey(workspaceId, "company"))
                .thenReturn(Optional.of(sourceEntityType))
            whenever(entityTypeRepository.save(any<EntityTypeEntity>())).thenAnswer { it.arguments[0] }

            repeat(3) {
                val request = SaveTypeDefinitionRequest(
                    index = null,
                    definition = SaveAttributeDefinitionRequest(
                        key = "company",
                        id = UUID.randomUUID(),
                        schema = Schema(key = SchemaType.TEXT, type = DataType.STRING, label = "Attr $it"),
                    )
                )
                service.saveEntityTypeDefinition(workspaceId, request)
            }

            assertEquals(4, sourceEntityType.version)
        }
    }
}
