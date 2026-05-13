package cranium.core.service.entity.type

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import cranium.core.configuration.auth.WorkspaceSecurity
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.core.DataType
import cranium.core.enums.integration.SourceType
import cranium.core.enums.workspace.WorkspaceRoles
import cranium.core.models.common.validation.Schema
import cranium.core.models.request.entity.type.SaveAttributeDefinitionRequest
import cranium.core.repository.entity.EntityRepository
import cranium.core.repository.entity.EntityUniqueValuesRepository
import cranium.core.service.auth.AuthTokenService
import cranium.core.service.entity.EntityAttributeService
import cranium.core.service.entity.EntityTypeSemanticMetadataService
import cranium.core.service.entity.EntityValidationService
import cranium.core.service.schema.SchemaService
import cranium.core.service.util.BaseServiceTest
import cranium.core.service.util.WithUserPersona
import cranium.core.service.util.WorkspaceRole
import cranium.core.service.util.factory.entity.EntityFactory
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        EntityTypeAttributeServiceTest.TestConfig::class,
        EntityTypeAttributeService::class,
        EntityTypeProtectionGuard::class,
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
class EntityTypeAttributeServiceTest : BaseServiceTest() {

    @Configuration
    class TestConfig

    @MockitoBean
    private lateinit var entityValidationService: EntityValidationService

    @MockitoBean
    private lateinit var entityRepository: EntityRepository

    @MockitoBean
    private lateinit var uniqueEntityValueRepository: EntityUniqueValuesRepository

    @MockitoBean
    private lateinit var semanticMetadataService: EntityTypeSemanticMetadataService

    @MockitoBean
    private lateinit var entityAttributeService: EntityAttributeService

    @MockitoBean
    private lateinit var schemaService: SchemaService

    @MockitoBean
    private lateinit var sequenceService: EntityTypeSequenceService

    @Autowired
    private lateinit var service: EntityTypeAttributeService

    @BeforeEach
    fun setup() {
        reset(
            entityValidationService,
            entityRepository,
            uniqueEntityValueRepository,
            semanticMetadataService,
            entityAttributeService,
            schemaService,
            sequenceService,
        )
    }

    // ------ Readonly Guard Tests ------

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
    inner class ReadonlyGuards {

        /**
         * Bug context: Integration-sourced entity types marked as readonly must not allow
         * schema attribute modifications. The guard prevents users from adding or modifying
         * attributes on types they do not own.
         *
         * Verifies that saveAttributeDefinition throws IllegalArgumentException when
         * the entity type is readonly.
         */
        @Test
        fun `saveAttributeDefinition - readonly entity type - throws IllegalArgumentException`() {
            val type = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                readonly = true,
                sourceType = SourceType.INTEGRATION,
            )

            val attrId = UUID.randomUUID()
            val request = SaveAttributeDefinitionRequest(
                key = "test_entity",
                id = attrId,
                schema = Schema(key = SchemaType.TEXT, type = DataType.STRING, label = "Name"),
            )

            val exception = assertThrows(IllegalArgumentException::class.java) {
                service.saveAttributeDefinition(workspaceId, type, request)
            }

            assertTrue(exception.message!!.contains("readonly"))
        }

        /**
         * Verifies that removeAttributeDefinition throws IllegalArgumentException when
         * the entity type is readonly.
         */
        @Test
        fun `removeAttributeDefinition - readonly entity type - throws IllegalArgumentException`() {
            val attrId = UUID.randomUUID()
            val type = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                readonly = true,
                sourceType = SourceType.INTEGRATION,
            )

            val exception = assertThrows(IllegalArgumentException::class.java) {
                service.removeAttributeDefinition(type, attrId)
            }

            assertTrue(exception.message!!.contains("readonly"))
        }

        /**
         * Verifies that saveAttributeDefinition succeeds (guard does not fire) for
         * a non-readonly entity type.
         */
        @Test
        fun `saveAttributeDefinition - non-readonly entity type - guard does not fire`() {
            val type = EntityFactory.createEntityType(
                workspaceId = workspaceId,
                readonly = false,
            )

            val attrId = UUID.randomUUID()
            val request = SaveAttributeDefinitionRequest(
                key = "test_entity",
                id = attrId,
                schema = Schema(key = SchemaType.TEXT, type = DataType.STRING, label = "Name"),
            )

            whenever(entityValidationService.detectSchemaBreakingChanges(any(), any())).thenReturn(emptyList())

            // Should not throw - guard should not fire for non-readonly types
            assertDoesNotThrow {
                service.saveAttributeDefinition(workspaceId, type, request)
            }
        }
    }
}
