package riven.core.service.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.entity.EntityAttributeEntity
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.repository.entity.EntityAttributeRepository
import riven.core.repository.entity.EntityRepository
import riven.core.service.auth.AuthTokenService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import riven.core.service.util.factory.entity.EntityFactory
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * Unit tests for [IdentityLookupService].
 *
 * Covers two lookup strategies:
 * - findBySourceExternalId: delegates to EntityRepository, returns matching entities
 * - findByIdentifierValue: delegates to EntityAttributeRepository native JSONB query, returns distinct entity IDs
 *
 * @WithUserPersona is required at class level because this service uses @PreAuthorize.
 */
@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        IdentityLookupService::class,
        IdentityLookupServiceTest.TestConfig::class,
    ]
)
@WithUserPersona(
    userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
    email = "test@example.com",
    displayName = "Test User",
    roles = [
        WorkspaceRole(
            workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
            role = WorkspaceRoles.ADMIN
        )
    ]
)
class IdentityLookupServiceTest : BaseServiceTest() {

    @Configuration
    class TestConfig

    @MockitoBean
    private lateinit var entityRepository: EntityRepository

    @MockitoBean
    private lateinit var entityAttributeRepository: EntityAttributeRepository

    @Autowired
    private lateinit var service: IdentityLookupService

    // ------ FindBySourceExternalId ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
                role = WorkspaceRoles.ADMIN
            )
        ]
    )
    inner class FindBySourceExternalId {

        /**
         * Happy path: returns entities matching the given sourceExternalId in the workspace.
         */
        @Test
        fun `findBySourceExternalId returns matching entities`() {
            val externalId = "ext-123"
            val entity = EntityFactory.createEntityEntity(
                id = UUID.randomUUID(),
                workspaceId = workspaceId,
                sourceExternalId = externalId,
            )

            whenever(entityRepository.findByWorkspaceIdAndSourceExternalId(workspaceId, externalId))
                .thenReturn(listOf(entity))

            val result = service.findBySourceExternalId(workspaceId, externalId)

            assertEquals(1, result.size)
            assertEquals(entity.id, result[0].id)
        }

        /**
         * Returns empty list when no entities match the external ID.
         */
        @Test
        fun `findBySourceExternalId returns empty list when no match`() {
            val externalId = "nonexistent"

            whenever(entityRepository.findByWorkspaceIdAndSourceExternalId(workspaceId, externalId))
                .thenReturn(emptyList())

            val result = service.findBySourceExternalId(workspaceId, externalId)

            assertTrue(result.isEmpty())
        }
    }

    // ------ FindByIdentifierValue ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
                role = WorkspaceRoles.ADMIN
            )
        ]
    )
    inner class FindByIdentifierValue {

        /**
         * Happy path: returns distinct entity IDs from matching attribute rows.
         */
        @Test
        fun `findByIdentifierValue returns distinct entity IDs`() {
            val attributeId = UUID.randomUUID()
            val entityId1 = UUID.randomUUID()
            val entityId2 = UUID.randomUUID()
            val textValue = "test@example.com"

            val objectMapper = ObjectMapper()
            val jsonValue = objectMapper.createObjectNode().put("value", textValue)

            val attr1 = EntityAttributeEntity(
                id = UUID.randomUUID(),
                entityId = entityId1,
                workspaceId = workspaceId,
                typeId = UUID.randomUUID(),
                attributeId = attributeId,
                schemaType = SchemaType.TEXT,
                value = jsonValue,
            )
            val attr2 = EntityAttributeEntity(
                id = UUID.randomUUID(),
                entityId = entityId2,
                workspaceId = workspaceId,
                typeId = UUID.randomUUID(),
                attributeId = attributeId,
                schemaType = SchemaType.TEXT,
                value = jsonValue,
            )

            whenever(entityAttributeRepository.findByWorkspaceIdAndAttributeIdAndTextValue(workspaceId, attributeId, textValue))
                .thenReturn(listOf(attr1, attr2))

            val result = service.findByIdentifierValue(workspaceId, attributeId, textValue)

            assertEquals(2, result.size)
            assertTrue(result.contains(entityId1))
            assertTrue(result.contains(entityId2))
        }

        /**
         * Returns empty list when no attributes match the value.
         */
        @Test
        fun `findByIdentifierValue returns empty list when no match`() {
            val attributeId = UUID.randomUUID()

            whenever(entityAttributeRepository.findByWorkspaceIdAndAttributeIdAndTextValue(workspaceId, attributeId, "no-match"))
                .thenReturn(emptyList())

            val result = service.findByIdentifierValue(workspaceId, attributeId, "no-match")

            assertTrue(result.isEmpty())
        }

        /**
         * Deduplicates entity IDs when multiple attribute rows reference the same entity.
         */
        @Test
        fun `findByIdentifierValue deduplicates entity IDs`() {
            val attributeId = UUID.randomUUID()
            val entityId = UUID.randomUUID()
            val textValue = "duplicate@example.com"

            val objectMapper = ObjectMapper()
            val jsonValue = objectMapper.createObjectNode().put("value", textValue)

            val attr1 = EntityAttributeEntity(
                id = UUID.randomUUID(),
                entityId = entityId,
                workspaceId = workspaceId,
                typeId = UUID.randomUUID(),
                attributeId = attributeId,
                schemaType = SchemaType.TEXT,
                value = jsonValue,
            )
            val attr2 = EntityAttributeEntity(
                id = UUID.randomUUID(),
                entityId = entityId,
                workspaceId = workspaceId,
                typeId = UUID.randomUUID(),
                attributeId = attributeId,
                schemaType = SchemaType.TEXT,
                value = jsonValue,
            )

            whenever(entityAttributeRepository.findByWorkspaceIdAndAttributeIdAndTextValue(workspaceId, attributeId, textValue))
                .thenReturn(listOf(attr1, attr2))

            val result = service.findByIdentifierValue(workspaceId, attributeId, textValue)

            assertEquals(1, result.size)
            assertEquals(entityId, result[0])
        }
    }

    // ------ Access Denied Tests ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "00000000-0000-0000-0000-000000000000",
                role = WorkspaceRoles.ADMIN
            )
        ]
    )
    inner class UnauthorizedAccessTests {

        /**
         * Verifies that findBySourceExternalId rejects requests when the authenticated user
         * does not have access to the target workspace.
         */
        @Test
        fun `findBySourceExternalId throws AccessDeniedException for unauthorized workspace`() {
            assertThrows<AccessDeniedException> {
                service.findBySourceExternalId(workspaceId, "ext-123")
            }
        }

        /**
         * Verifies that findByIdentifierValue rejects requests when the authenticated user
         * does not have access to the target workspace.
         */
        @Test
        fun `findByIdentifierValue throws AccessDeniedException for unauthorized workspace`() {
            assertThrows<AccessDeniedException> {
                service.findByIdentifierValue(workspaceId, UUID.randomUUID(), "value")
            }
        }
    }
}
