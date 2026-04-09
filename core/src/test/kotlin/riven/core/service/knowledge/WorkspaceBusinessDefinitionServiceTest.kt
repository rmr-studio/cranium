package riven.core.service.knowledge

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.knowledge.WorkspaceBusinessDefinitionEntity
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.knowledge.DefinitionCategory
import riven.core.enums.knowledge.DefinitionSource
import riven.core.enums.knowledge.DefinitionStatus
import riven.core.enums.util.OperationType
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.exceptions.ConflictException
import riven.core.exceptions.NotFoundException
import riven.core.models.request.knowledge.CreateBusinessDefinitionRequest
import riven.core.models.request.knowledge.UpdateBusinessDefinitionRequest
import riven.core.repository.knowledge.WorkspaceBusinessDefinitionRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import riven.core.service.util.factory.knowledge.BusinessDefinitionFactory
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        WorkspaceBusinessDefinitionService::class,
    ]
)
@WithUserPersona(
    userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
    email = "test@test.com",
    displayName = "Test User",
    roles = [
        WorkspaceRole(
            workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
            role = WorkspaceRoles.ADMIN
        )
    ]
)
class WorkspaceBusinessDefinitionServiceTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var repository: WorkspaceBusinessDefinitionRepository

    @MockitoBean
    private lateinit var activityService: ActivityService

    @Autowired
    private lateinit var service: WorkspaceBusinessDefinitionService

    @BeforeEach
    fun setup() {
        reset(repository, activityService)
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mock())
    }

    // ------ List definitions ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@test.com",
        displayName = "Test User",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class ListDefinitions {

        @Test
        fun `lists all definitions for workspace`() {
            val def1 = BusinessDefinitionFactory.createDefinition(workspaceId = workspaceId, term = "Retention Rate")
            val def2 = BusinessDefinitionFactory.createDefinition(workspaceId = workspaceId, term = "Churn Rate", normalizedTerm = "churn rate")
            whenever(repository.findByWorkspaceIdWithFilters(workspaceId, null, null)).thenReturn(listOf(def1, def2))

            val result = service.listDefinitions(workspaceId)

            assertEquals(2, result.size)
        }

        @Test
        fun `filters by status when provided`() {
            val def = BusinessDefinitionFactory.createDefinition(workspaceId = workspaceId, status = DefinitionStatus.SUGGESTED)
            whenever(repository.findByWorkspaceIdWithFilters(workspaceId, DefinitionStatus.SUGGESTED, null)).thenReturn(listOf(def))

            val result = service.listDefinitions(workspaceId, status = DefinitionStatus.SUGGESTED)

            assertEquals(1, result.size)
            assertEquals(DefinitionStatus.SUGGESTED, result.first().status)
        }

        @Test
        fun `filters by category when provided`() {
            val def = BusinessDefinitionFactory.createDefinition(workspaceId = workspaceId, category = DefinitionCategory.SEGMENT)
            whenever(repository.findByWorkspaceIdWithFilters(workspaceId, null, DefinitionCategory.SEGMENT)).thenReturn(listOf(def))

            val result = service.listDefinitions(workspaceId, category = DefinitionCategory.SEGMENT)

            assertEquals(1, result.size)
            assertEquals(DefinitionCategory.SEGMENT, result.first().category)
        }

        @Test
        fun `filters by both status and category when both provided`() {
            val def = BusinessDefinitionFactory.createDefinition(
                workspaceId = workspaceId,
                status = DefinitionStatus.ACTIVE,
                category = DefinitionCategory.METRIC,
            )
            whenever(repository.findByWorkspaceIdWithFilters(workspaceId, DefinitionStatus.ACTIVE, DefinitionCategory.METRIC))
                .thenReturn(listOf(def))

            val result = service.listDefinitions(workspaceId, status = DefinitionStatus.ACTIVE, category = DefinitionCategory.METRIC)

            assertEquals(1, result.size)
        }
    }

    // ------ Get definition ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@test.com",
        displayName = "Test User",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class GetDefinition {

        @Test
        fun `returns definition by id and workspace`() {
            val defId = UUID.randomUUID()
            val def = BusinessDefinitionFactory.createDefinition(id = defId, workspaceId = workspaceId)
            whenever(repository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(def))

            val result = service.getDefinition(workspaceId, defId)

            assertEquals(defId, result.id)
            assertEquals("Retention Rate", result.term)
        }

        @Test
        fun `throws NotFoundException when definition does not exist`() {
            val defId = UUID.randomUUID()
            whenever(repository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.empty())

            assertThrows<NotFoundException> {
                service.getDefinition(workspaceId, defId)
            }
        }
    }

    // ------ Create definition ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@test.com",
        displayName = "Test User",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class CreateDefinition {

        @Test
        fun `creates definition with normalized term and logs activity`() {
            val request = CreateBusinessDefinitionRequest(
                term = "Retention Rate",
                definition = "Customer retained if active subscription 90 days after purchase",
                category = DefinitionCategory.METRIC,
            )

            whenever(repository.findByWorkspaceIdAndNormalizedTerm(workspaceId, "retention rate"))
                .thenReturn(Optional.empty())
            whenever(repository.save(any<WorkspaceBusinessDefinitionEntity>())).thenAnswer { inv ->
                (inv.arguments[0] as WorkspaceBusinessDefinitionEntity).copy(id = UUID.randomUUID())
            }

            val result = service.createDefinition(workspaceId, request)

            assertNotNull(result.id)
            assertEquals("Retention Rate", result.term)
            assertEquals("retention rate", result.normalizedTerm)
            assertEquals(DefinitionCategory.METRIC, result.category)
            assertEquals(DefinitionSource.MANUAL, result.source)

            verify(activityService).logActivity(
                activity = eq(Activity.BUSINESS_DEFINITION),
                operation = eq(OperationType.CREATE),
                userId = any(),
                workspaceId = eq(workspaceId),
                entityType = eq(ApplicationEntityType.BUSINESS_DEFINITION),
                entityId = any(),
                timestamp = any(),
                details = any(),
            )
        }

        @Test
        fun `throws ConflictException for duplicate normalized term`() {
            val existing = BusinessDefinitionFactory.createDefinition(workspaceId = workspaceId)
            whenever(repository.findByWorkspaceIdAndNormalizedTerm(workspaceId, "retention rate"))
                .thenReturn(Optional.of(existing))

            val request = CreateBusinessDefinitionRequest(
                term = "  Retention Rates  ",
                definition = "Some definition",
                category = DefinitionCategory.METRIC,
            )

            assertThrows<ConflictException> {
                service.createDefinition(workspaceId, request)
            }
        }

        @Test
        fun `throws IllegalArgumentException for blank term`() {
            val request = CreateBusinessDefinitionRequest(
                term = "   ",
                definition = "Some definition",
                category = DefinitionCategory.METRIC,
            )

            assertThrows<IllegalArgumentException> {
                service.createDefinition(workspaceId, request)
            }
        }

        @Test
        fun `throws IllegalArgumentException for term exceeding 255 characters`() {
            val request = CreateBusinessDefinitionRequest(
                term = "x".repeat(256),
                definition = "Some definition",
                category = DefinitionCategory.METRIC,
            )

            assertThrows<IllegalArgumentException> {
                service.createDefinition(workspaceId, request)
            }
        }

        @Test
        fun `throws IllegalArgumentException for blank definition`() {
            val request = CreateBusinessDefinitionRequest(
                term = "Test Term",
                definition = "",
                category = DefinitionCategory.METRIC,
            )

            assertThrows<IllegalArgumentException> {
                service.createDefinition(workspaceId, request)
            }
        }

        @Test
        fun `throws IllegalArgumentException for definition exceeding 2000 characters`() {
            val request = CreateBusinessDefinitionRequest(
                term = "Test Term",
                definition = "x".repeat(2001),
                category = DefinitionCategory.METRIC,
            )

            assertThrows<IllegalArgumentException> {
                service.createDefinition(workspaceId, request)
            }
        }

        @Test
        fun `trims term whitespace on create`() {
            val request = CreateBusinessDefinitionRequest(
                term = "  Retention Rate  ",
                definition = "Some definition",
                category = DefinitionCategory.METRIC,
            )

            whenever(repository.findByWorkspaceIdAndNormalizedTerm(workspaceId, "retention rate"))
                .thenReturn(Optional.empty())
            whenever(repository.save(any<WorkspaceBusinessDefinitionEntity>())).thenAnswer { inv ->
                (inv.arguments[0] as WorkspaceBusinessDefinitionEntity).copy(id = UUID.randomUUID())
            }

            val captor = argumentCaptor<WorkspaceBusinessDefinitionEntity>()
            service.createDefinition(workspaceId, request)

            verify(repository).save(captor.capture())
            assertEquals("Retention Rate", captor.firstValue.term)
        }
    }

    // ------ Update definition ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@test.com",
        displayName = "Test User",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class UpdateDefinition {

        @Test
        fun `updates definition fields and nulls compiled_params`() {
            val defId = UUID.randomUUID()
            val existing = BusinessDefinitionFactory.createDefinition(
                id = defId,
                workspaceId = workspaceId,
                compiledParams = mapOf("conditions" to listOf("test")),
                version = 1,
            )
            whenever(repository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(existing))
            whenever(repository.save(any<WorkspaceBusinessDefinitionEntity>())).thenAnswer { it.arguments[0] }

            val request = UpdateBusinessDefinitionRequest(
                term = "Retention Rate",
                definition = "Updated definition text",
                category = DefinitionCategory.METRIC,
                version = 1,
            )

            val result = service.updateDefinition(workspaceId, defId, request)

            assertEquals("Updated definition text", result.definition)
            assertNull(result.compiledParams)

            verify(activityService).logActivity(
                activity = eq(Activity.BUSINESS_DEFINITION),
                operation = eq(OperationType.UPDATE),
                userId = any(),
                workspaceId = eq(workspaceId),
                entityType = eq(ApplicationEntityType.BUSINESS_DEFINITION),
                entityId = any(),
                timestamp = any(),
                details = any(),
            )
        }

        @Test
        fun `throws ConflictException on stale version`() {
            val defId = UUID.randomUUID()
            val existing = BusinessDefinitionFactory.createDefinition(id = defId, workspaceId = workspaceId, version = 3)
            whenever(repository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(existing))

            val request = UpdateBusinessDefinitionRequest(
                term = "Retention Rate",
                definition = "Updated definition",
                category = DefinitionCategory.METRIC,
                version = 1,
            )

            assertThrows<ConflictException> {
                service.updateDefinition(workspaceId, defId, request)
            }
        }

        @Test
        fun `throws ConflictException on optimistic locking failure`() {
            val defId = UUID.randomUUID()
            val existing = BusinessDefinitionFactory.createDefinition(id = defId, workspaceId = workspaceId, version = 1)
            whenever(repository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(existing))
            whenever(repository.save(any<WorkspaceBusinessDefinitionEntity>()))
                .thenThrow(ObjectOptimisticLockingFailureException(WorkspaceBusinessDefinitionEntity::class.java, defId))

            val request = UpdateBusinessDefinitionRequest(
                term = "Retention Rate",
                definition = "Updated definition",
                category = DefinitionCategory.METRIC,
                version = 1,
            )

            assertThrows<ConflictException> {
                service.updateDefinition(workspaceId, defId, request)
            }
        }

        @Test
        fun `throws ConflictException when renaming to an existing normalized term`() {
            val defId = UUID.randomUUID()
            val existing = BusinessDefinitionFactory.createDefinition(id = defId, workspaceId = workspaceId, version = 1)
            val conflicting = BusinessDefinitionFactory.createDefinition(
                workspaceId = workspaceId,
                term = "Churn Rate",
                normalizedTerm = "churn rate",
            )

            whenever(repository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(existing))
            whenever(repository.findByWorkspaceIdAndNormalizedTerm(workspaceId, "churn rate"))
                .thenReturn(Optional.of(conflicting))

            val request = UpdateBusinessDefinitionRequest(
                term = "Churn Rate",
                definition = "Updated definition",
                category = DefinitionCategory.METRIC,
                version = 1,
            )

            assertThrows<ConflictException> {
                service.updateDefinition(workspaceId, defId, request)
            }
        }

        @Test
        fun `allows update when normalized term unchanged`() {
            val defId = UUID.randomUUID()
            val existing = BusinessDefinitionFactory.createDefinition(id = defId, workspaceId = workspaceId, version = 1)
            whenever(repository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(existing))
            whenever(repository.save(any<WorkspaceBusinessDefinitionEntity>())).thenAnswer { it.arguments[0] }

            val request = UpdateBusinessDefinitionRequest(
                term = "Retention Rate",
                definition = "Updated definition",
                category = DefinitionCategory.SEGMENT,
                version = 1,
            )

            val result = service.updateDefinition(workspaceId, defId, request)

            assertEquals(DefinitionCategory.SEGMENT, result.category)
            verify(repository, never()).findByWorkspaceIdAndNormalizedTerm(any(), any())
        }
    }

    // ------ Delete definition ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@test.com",
        displayName = "Test User",
        roles = [WorkspaceRole(workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210", role = WorkspaceRoles.ADMIN)]
    )
    inner class DeleteDefinition {

        @Test
        fun `soft-deletes definition via markDeleted and logs activity`() {
            val defId = UUID.randomUUID()
            val entity = BusinessDefinitionFactory.createDefinition(id = defId, workspaceId = workspaceId)
            whenever(repository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.of(entity))
            whenever(repository.save(any<WorkspaceBusinessDefinitionEntity>())).thenAnswer { it.arguments[0] }

            service.deleteDefinition(workspaceId, defId)

            val captor = argumentCaptor<WorkspaceBusinessDefinitionEntity>()
            verify(repository).save(captor.capture())
            assertTrue(captor.firstValue.deleted)
            assertNotNull(captor.firstValue.deletedAt)

            verify(activityService).logActivity(
                activity = eq(Activity.BUSINESS_DEFINITION),
                operation = eq(OperationType.DELETE),
                userId = any(),
                workspaceId = eq(workspaceId),
                entityType = eq(ApplicationEntityType.BUSINESS_DEFINITION),
                entityId = any(),
                timestamp = any(),
                details = any(),
            )
        }

        @Test
        fun `throws NotFoundException when definition does not exist`() {
            val defId = UUID.randomUUID()
            whenever(repository.findByIdAndWorkspaceId(defId, workspaceId)).thenReturn(Optional.empty())

            assertThrows<NotFoundException> {
                service.deleteDefinition(workspaceId, defId)
            }
        }
    }

    // ------ Access control ------

    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@test.com",
        displayName = "Test User",
        roles = [WorkspaceRole(workspaceId = "a1b2c3d4-5e6f-7890-abcd-ef1234567890", role = WorkspaceRoles.ADMIN)]
    )
    inner class AccessControl {

        private val otherWorkspaceId: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef9876543210")

        @Test
        fun `listDefinitions throws AccessDeniedException for unauthorized workspace`() {
            assertThrows<AccessDeniedException> {
                service.listDefinitions(otherWorkspaceId)
            }
        }

        @Test
        fun `createDefinition throws AccessDeniedException for unauthorized workspace`() {
            val request = CreateBusinessDefinitionRequest(
                term = "Test Term",
                definition = "Test definition",
                category = DefinitionCategory.METRIC,
            )

            assertThrows<AccessDeniedException> {
                service.createDefinition(otherWorkspaceId, request)
            }
        }

        @Test
        fun `updateDefinition throws AccessDeniedException for unauthorized workspace`() {
            val request = UpdateBusinessDefinitionRequest(
                term = "Test Term",
                definition = "Updated definition",
                category = DefinitionCategory.METRIC,
                version = 0,
            )

            assertThrows<AccessDeniedException> {
                service.updateDefinition(otherWorkspaceId, UUID.randomUUID(), request)
            }
        }

        @Test
        fun `deleteDefinition throws AccessDeniedException for unauthorized workspace`() {
            assertThrows<AccessDeniedException> {
                service.deleteDefinition(otherWorkspaceId, UUID.randomUUID())
            }
        }

        @Test
        fun `getDefinition throws AccessDeniedException for unauthorized workspace`() {
            assertThrows<AccessDeniedException> {
                service.getDefinition(otherWorkspaceId, UUID.randomUUID())
            }
        }
    }
}
