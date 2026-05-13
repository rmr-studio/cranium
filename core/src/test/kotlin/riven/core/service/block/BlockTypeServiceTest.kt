package cranium.core.service.block

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import cranium.core.configuration.auth.WorkspaceSecurity
import cranium.core.entity.block.BlockTypeEntity
import cranium.core.enums.common.validation.ValidationScope
import cranium.core.models.request.block.CreateBlockTypeRequest
import cranium.core.repository.block.BlockTypeRepository
import cranium.core.service.activity.ActivityService
import cranium.core.service.auth.AuthTokenService
import cranium.core.service.util.BaseServiceTest
import cranium.core.service.util.SecurityTestConfig
import cranium.core.service.util.factory.block.BlockFactory
import java.util.*

@SpringBootTest(classes = [AuthTokenService::class, WorkspaceSecurity::class, SecurityTestConfig::class, BlockTypeService::class])
class BlockTypeServiceTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var blockTypeRepository: BlockTypeRepository

    @MockitoBean
    private lateinit var activityService: ActivityService

    @Autowired
    private lateinit var blockTypeService: BlockTypeService

    private val orgId get() = workspaceId

    // ------------------------------------------------------------------
    // publishBlockType: saves from request and logs activity
    // ------------------------------------------------------------------
    @Test
    fun `publishBlockType saves and returns model, logs CREATE activity`() {
        // Scenario: A user publishes a block type. Repository saves and returns entity with id.
        // Expect: returned model matches saved entity; activity logged.
        val type = BlockFactory.createType(
            orgId = orgId,
            key = "invoice_header",
            version = 1,
            strictness = ValidationScope.SOFT
        )

        whenever(blockTypeRepository.save(any<BlockTypeEntity>())).thenReturn(type)

        val req = CreateBlockTypeRequest(
            key = "invoice_header",
            name = "Invoice Header",
            description = "desc",
            workspaceId = orgId,
            mode = ValidationScope.SOFT,
            schema = BlockFactory.generateSchema(),
            display = BlockFactory.generateDisplay()
        )

        val saved = blockTypeService.publishBlockType(req)
        verify(activityService).logActivity(
            activity = eq(cranium.core.enums.activity.Activity.BLOCK_TYPE),
            operation = eq(cranium.core.enums.util.OperationType.CREATE),
            userId = any(),
            workspaceId = eq(orgId),
            entityType = any(),
            entityId = eq(saved.id),
            timestamp = any(),
            details = any()
        )

        // Also capture what was saved to ensure request → entity mapping is sane
        val captor = argumentCaptor<BlockTypeEntity>()
        verify(blockTypeRepository).save(captor.capture())
        val persisted = captor.firstValue
        assertEquals("invoice_header", persisted.key)
        assertEquals("Invoice Header", persisted.displayName)
        assertEquals(orgId, persisted.workspaceId)
        assertEquals(ValidationScope.SOFT, persisted.strictness)
    }

    // ------------------------------------------------------------------
    // updateBlockType: append-only new row with version+1
    // ------------------------------------------------------------------
    @Test
    fun `updateBlockType creates a new version row and logs CREATE`() {
        // Scenario: Updating an existing type should append a new row (version+1), not mutate existing.
        // Expect: repo.save called with id=null and version=existing.version+1; activity logged as CREATE.

        val type = BlockFactory.createType(
            orgId = orgId,
            key = "invoice_header",
            version = 3,
            strictness = ValidationScope.SOFT
        )

        whenever(blockTypeRepository.findById(type.id!!)).thenReturn(Optional.of(type))

        // Return what we save (with generated id)
        whenever(blockTypeRepository.save(any())).thenAnswer { inv ->
            (inv.arguments[0] as BlockTypeEntity).copy(id = UUID.randomUUID())
        }

        val inputModel = type.toModel().copy(
            name = "Invoice Header v4",
            description = "new desc",
            strictness = ValidationScope.STRICT, // change strictness
            display = BlockFactory.generateDisplay(),
            // schema change too
            schema = BlockFactory.generateSchema()
        )

        blockTypeService.updateBlockType(inputModel)

        val captor = argumentCaptor<BlockTypeEntity>()
        verify(blockTypeRepository).save(captor.capture())
        val saved = captor.firstValue

        assertNull(saved.id) // append-only (id assigned by DB)
        assertEquals(type.key, saved.key)
        assertEquals(type.workspaceId, saved.workspaceId)
        assertEquals(4, saved.version) // existing.version + 1
        assertEquals("Invoice Header v4", saved.displayName)
        assertEquals(ValidationScope.STRICT, saved.strictness)
        assertFalse(saved.deleted)

        verify(activityService).logActivity(
            activity = eq(cranium.core.enums.activity.Activity.BLOCK_TYPE),
            operation = eq(cranium.core.enums.util.OperationType.CREATE),
            userId = any(),
            workspaceId = eq(requireNotNull(type.workspaceId)),
            entityType = any(),
            entityId = any(),
            timestamp = any(),
            details = any()
        )
    }


}
