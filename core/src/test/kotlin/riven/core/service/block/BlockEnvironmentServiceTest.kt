package riven.core.service.block

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.block.BlockEntity
import riven.core.enums.common.validation.ValidationScope
import riven.core.enums.util.OperationType
import riven.core.models.response.block.internal.CascadeRemovalResult
import riven.core.models.response.block.internal.MovePreparationResult
import riven.core.models.websocket.BlockEnvironmentEvent
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.factory.block.BlockFactory
import java.time.ZonedDateTime
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        BlockEnvironmentService::class
    ]
)
@RecordApplicationEvents
class BlockEnvironmentServiceTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var blockService: BlockService

    @MockitoBean
    private lateinit var blockReferenceService: BlockReferenceHydrationService

    @MockitoBean
    private lateinit var blockTreeLayoutService: BlockTreeLayoutService

    @MockitoBean
    private lateinit var blockChildrenService: BlockChildrenService

    @MockitoBean
    private lateinit var activityService: ActivityService

    @MockitoBean
    private lateinit var defaultBlockEnvironmentService: DefaultBlockEnvironmentService

    @Autowired
    private lateinit var blockEnvironmentService: BlockEnvironmentService

    @Autowired
    private lateinit var applicationEvents: ApplicationEvents

    private val orgId get() = workspaceId

    // Helper to create a block type for tests
    private fun createTestBlockType() = BlockFactory.createType(
        orgId = orgId,
        key = "test_block",
        version = 1,
        strictness = ValidationScope.SOFT
    ).toModel()

    // ------------------------------------------------------------------
    // reduceBlockOperations: Edge cases
    // ------------------------------------------------------------------

    @Test
    fun `reduceBlockOperations with block added then removed returns empty list`() {
        // Scenario: A block is added at t1, then removed at t2
        // Expect: Both operations are skipped, empty list returned
        val blockId = UUID.randomUUID()
        val type = createTestBlockType()

        val t1 = ZonedDateTime.now()
        val t2 = t1.plusSeconds(1)

        val addOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createAddOperation(blockId = blockId, orgId = orgId, type = type),
            timestamp = t1
        )
        val removeOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createRemoveOperation(blockId = blockId),
            timestamp = t2
        )

        val result = blockEnvironmentService.reduceBlockOperations(listOf(addOp, removeOp))

        assertTrue(result.isEmpty(), "ADD followed by REMOVE should result in empty list")
    }

    @Test
    fun `reduceBlockOperations with block removed then added returns empty list`() {
        // Scenario: Operations arrive out of order - remove at t2, add at t1
        // Expect: Still treated as add+remove, returns empty list
        val blockId = UUID.randomUUID()
        val type = createTestBlockType()

        val t1 = ZonedDateTime.now()
        val t2 = t1.plusSeconds(1)

        val addOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createAddOperation(blockId = blockId, orgId = orgId, type = type),
            timestamp = t1
        )
        val removeOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createRemoveOperation(blockId = blockId),
            timestamp = t2
        )

        // Pass in reverse order
        val result = blockEnvironmentService.reduceBlockOperations(listOf(removeOp, addOp))

        assertTrue(result.isEmpty(), "ADD + REMOVE (regardless of order) should result in empty list")
    }

    @Test
    fun `reduceBlockOperations with only remove operation returns remove operation`() {
        // Scenario: Only a remove operation exists (block existed prior to this batch)
        // Expect: Remove operation is returned
        val blockId = UUID.randomUUID()

        val removeOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createRemoveOperation(blockId = blockId),
            timestamp = ZonedDateTime.now()
        )

        val result = blockEnvironmentService.reduceBlockOperations(listOf(removeOp))

        assertEquals(1, result.size)
        assertEquals(removeOp, result[0])
    }

    @Test
    fun `reduceBlockOperations with remove ignores other operations`() {
        // Scenario: Block has update and move operations, then is removed
        // Expect: Only remove operation is returned
        val blockId = UUID.randomUUID()
        val type = createTestBlockType()

        val t1 = ZonedDateTime.now()
        val t2 = t1.plusSeconds(1)
        val t3 = t2.plusSeconds(1)

        val updateOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = blockId, orgId = orgId, type = type),
            timestamp = t1
        )
        val moveOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createMoveOperation(
                blockId = blockId,
                fromParentId = UUID.randomUUID(),
                toParentId = UUID.randomUUID()
            ),
            timestamp = t2
        )
        val removeOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createRemoveOperation(blockId = blockId),
            timestamp = t3
        )

        val result = blockEnvironmentService.reduceBlockOperations(listOf(updateOp, moveOp, removeOp))

        assertEquals(1, result.size)
        assertEquals(removeOp, result[0])
    }

    @Test
    fun `reduceBlockOperations with add ensures add is first and drops ops before it`() {
        // Scenario: Block has update at t1, add at t2, update at t3
        // Expect: Only add at t2 and update at t3 are returned, add is first
        val blockId = UUID.randomUUID()
        val type = createTestBlockType()

        val t1 = ZonedDateTime.now()
        val t2 = t1.plusSeconds(1)
        val t3 = t2.plusSeconds(1)

        val earlyUpdateOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = blockId, orgId = orgId, type = type),
            timestamp = t1
        )
        val addOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createAddOperation(blockId = blockId, orgId = orgId, type = type),
            timestamp = t2
        )
        val lateUpdateOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = blockId, orgId = orgId, type = type),
            timestamp = t3
        )

        val result = blockEnvironmentService.reduceBlockOperations(listOf(earlyUpdateOp, addOp, lateUpdateOp))

        assertEquals(2, result.size)
        assertEquals(addOp, result[0], "ADD should be first")
        assertEquals(lateUpdateOp, result[1], "Late UPDATE should be second")
    }

    @Test
    fun `reduceBlockOperations keeps only last operation per type`() {
        // Scenario: Multiple updates and multiple reorders
        // Expect: Only the last update and last reorder are kept
        val blockId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val type = createTestBlockType()

        val t1 = ZonedDateTime.now()
        val t2 = t1.plusSeconds(1)
        val t3 = t2.plusSeconds(1)
        val t4 = t3.plusSeconds(1)

        val update1 = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = blockId, orgId = orgId, type = type),
            timestamp = t1
        )
        val reorder1 = BlockFactory.createOperationRequest(
            operation = BlockFactory.createReorderOperation(
                blockId = blockId,
                parentId = parentId,
                fromIndex = 0,
                toIndex = 1
            ),
            timestamp = t2
        )
        val update2 = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = blockId, orgId = orgId, type = type),
            timestamp = t3
        )
        val reorder2 = BlockFactory.createOperationRequest(
            operation = BlockFactory.createReorderOperation(
                blockId = blockId,
                parentId = parentId,
                fromIndex = 1,
                toIndex = 2
            ),
            timestamp = t4
        )

        val result = blockEnvironmentService.reduceBlockOperations(listOf(update1, reorder1, update2, reorder2))

        assertEquals(2, result.size)
        // Results should be sorted by timestamp
        assertEquals(update2, result[0], "Should keep last UPDATE")
        assertEquals(reorder2, result[1], "Should keep last REORDER")
    }

    @Test
    fun `reduceBlockOperations with add plus other operations keeps all with add first`() {
        // Scenario: Block is added, then updated, moved, and reordered
        // Expect: All operations kept, ADD is first, others sorted by timestamp
        val blockId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val type = createTestBlockType()

        val t1 = ZonedDateTime.now()
        val t2 = t1.plusSeconds(1)
        val t3 = t2.plusSeconds(1)
        val t4 = t3.plusSeconds(1)

        val addOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createAddOperation(blockId = blockId, orgId = orgId, type = type),
            timestamp = t1
        )
        val updateOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = blockId, orgId = orgId, type = type),
            timestamp = t2
        )
        val moveOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createMoveOperation(
                blockId = blockId,
                fromParentId = UUID.randomUUID(),
                toParentId = UUID.randomUUID()
            ),
            timestamp = t3
        )
        val reorderOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createReorderOperation(
                blockId = blockId,
                parentId = parentId,
                fromIndex = 0,
                toIndex = 1
            ),
            timestamp = t4
        )

        val result = blockEnvironmentService.reduceBlockOperations(listOf(addOp, updateOp, moveOp, reorderOp))

        assertEquals(4, result.size)
        assertEquals(addOp, result[0], "ADD should be first")
        assertEquals(updateOp, result[1], "UPDATE should be second")
        assertEquals(moveOp, result[2], "MOVE should be third")
        assertEquals(reorderOp, result[3], "REORDER should be fourth")
    }

    @Test
    fun `reduceBlockOperations with empty list returns empty list`() {
        val result = blockEnvironmentService.reduceBlockOperations(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `reduceBlockOperations with single operation returns that operation`() {
        val blockId = UUID.randomUUID()
        val type = createTestBlockType()

        val updateOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = blockId, orgId = orgId, type = type),
            timestamp = ZonedDateTime.now()
        )

        val result = blockEnvironmentService.reduceBlockOperations(listOf(updateOp))

        assertEquals(1, result.size)
        assertEquals(updateOp, result[0])
    }

    @Test
    fun `reduceBlockOperations handles multiple adds by keeping the last one`() {
        // Scenario: Multiple ADD operations (unusual but possible)
        // Expect: Only the last ADD is kept
        val blockId = UUID.randomUUID()
        val type = createTestBlockType()

        val t1 = ZonedDateTime.now()
        val t2 = t1.plusSeconds(1)

        val add1 = BlockFactory.createOperationRequest(
            operation = BlockFactory.createAddOperation(blockId = blockId, orgId = orgId, type = type),
            timestamp = t1
        )
        val add2 = BlockFactory.createOperationRequest(
            operation = BlockFactory.createAddOperation(blockId = blockId, orgId = orgId, type = type),
            timestamp = t2
        )

        val result = blockEnvironmentService.reduceBlockOperations(listOf(add1, add2))

        assertEquals(1, result.size)
        assertEquals(add2, result[0], "Should keep last ADD")
    }

    // ------------------------------------------------------------------
    // normalizeOperations: Edge cases
    // ------------------------------------------------------------------

    @Test
    fun `normalizeOperations with empty list returns empty map`() {
        val result = blockEnvironmentService.normalizeOperations(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `normalizeOperations groups operations by blockId`() {
        // Scenario: Two blocks, each with different operations
        // Expect: Map with two entries, each containing reduced operations for that block
        val block1Id = UUID.randomUUID()
        val block2Id = UUID.randomUUID()
        val type = createTestBlockType()

        val t1 = ZonedDateTime.now()
        val t2 = t1.plusSeconds(1)

        val block1Update = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = block1Id, orgId = orgId, type = type),
            timestamp = t1
        )
        val block2Update = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = block2Id, orgId = orgId, type = type),
            timestamp = t2
        )

        val result = blockEnvironmentService.normalizeOperations(listOf(block1Update, block2Update))

        assertEquals(2, result.size)
        assertTrue(result.containsKey(block1Id))
        assertTrue(result.containsKey(block2Id))
        assertEquals(1, result[block1Id]?.size)
        assertEquals(1, result[block2Id]?.size)
    }

    @Test
    fun `normalizeOperations applies reduction rules per block`() {
        // Scenario: Block1 has add+remove (should be empty), Block2 has update+move (should keep both)
        // Expect: Map has only block2 entry (block1 was reduced to nothing)
        val block1Id = UUID.randomUUID()
        val block2Id = UUID.randomUUID()
        val type = createTestBlockType()

        val t1 = ZonedDateTime.now()
        val t2 = t1.plusSeconds(1)
        val t3 = t2.plusSeconds(1)
        val t4 = t3.plusSeconds(1)

        val block1Add = BlockFactory.createOperationRequest(
            operation = BlockFactory.createAddOperation(blockId = block1Id, orgId = orgId, type = type),
            timestamp = t1
        )
        val block1Remove = BlockFactory.createOperationRequest(
            operation = BlockFactory.createRemoveOperation(blockId = block1Id),
            timestamp = t2
        )
        val block2Update = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = block2Id, orgId = orgId, type = type),
            timestamp = t3
        )
        val block2Move = BlockFactory.createOperationRequest(
            operation = BlockFactory.createMoveOperation(
                blockId = block2Id,
                fromParentId = UUID.randomUUID(),
                toParentId = UUID.randomUUID()
            ),
            timestamp = t4
        )

        val result = blockEnvironmentService.normalizeOperations(
            listOf(block1Add, block1Remove, block2Update, block2Move)
        )

        assertEquals(1, result.size, "Block1 should be removed (empty after reduction)")
        assertTrue(result.containsKey(block2Id))
        assertEquals(2, result[block2Id]?.size)
    }

    @Test
    fun `normalizeOperations sorts results by timestamp within each block`() {
        // Scenario: Operations arrive out of order
        // Expect: Results are sorted by timestamp
        val blockId = UUID.randomUUID()
        val type = createTestBlockType()

        val t1 = ZonedDateTime.now()
        val t2 = t1.plusSeconds(1)
        val t3 = t2.plusSeconds(1)

        val update1 = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = blockId, orgId = orgId, type = type),
            timestamp = t3  // Latest
        )
        val update2 = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = blockId, orgId = orgId, type = type),
            timestamp = t1  // Earliest
        )
        val update3 = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = blockId, orgId = orgId, type = type),
            timestamp = t2  // Middle
        )

        // Pass in random order
        val result = blockEnvironmentService.normalizeOperations(listOf(update1, update2, update3))

        assertEquals(1, result.size)
        assertEquals(1, result[blockId]?.size, "Should only keep the last update (t3)")
        assertEquals(update1, result[blockId]?.get(0), "Should keep the latest update")
    }

    @Test
    fun `normalizeOperations handles complex multi-block scenario`() {
        // Scenario: 3 blocks with different operation patterns
        // - Block A: add, update, update → should keep add + last update
        // - Block B: update, remove → should keep only remove
        // - Block C: add, remove → should be empty (removed from results)
        val blockAId = UUID.randomUUID()
        val blockBId = UUID.randomUUID()
        val blockCId = UUID.randomUUID()
        val type = createTestBlockType()

        val baseTime = ZonedDateTime.now()

        // Block A operations
        val aAdd = BlockFactory.createOperationRequest(
            operation = BlockFactory.createAddOperation(blockId = blockAId, orgId = orgId, type = type),
            timestamp = baseTime
        )
        val aUpdate1 = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = blockAId, orgId = orgId, type = type),
            timestamp = baseTime.plusSeconds(1)
        )
        val aUpdate2 = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = blockAId, orgId = orgId, type = type),
            timestamp = baseTime.plusSeconds(2)
        )

        // Block B operations
        val bUpdate = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = blockBId, orgId = orgId, type = type),
            timestamp = baseTime.plusSeconds(3)
        )
        val bRemove = BlockFactory.createOperationRequest(
            operation = BlockFactory.createRemoveOperation(blockId = blockBId),
            timestamp = baseTime.plusSeconds(4)
        )

        // Block C operations
        val cAdd = BlockFactory.createOperationRequest(
            operation = BlockFactory.createAddOperation(blockId = blockCId, orgId = orgId, type = type),
            timestamp = baseTime.plusSeconds(5)
        )
        val cRemove = BlockFactory.createOperationRequest(
            operation = BlockFactory.createRemoveOperation(blockId = blockCId),
            timestamp = baseTime.plusSeconds(6)
        )

        val result = blockEnvironmentService.normalizeOperations(
            listOf(aAdd, aUpdate1, aUpdate2, bUpdate, bRemove, cAdd, cRemove)
        )

        assertEquals(2, result.size, "Only blocks A and B should remain")

        // Check Block A
        assertTrue(result.containsKey(blockAId))
        assertEquals(2, result[blockAId]?.size)
        assertEquals(aAdd, result[blockAId]?.get(0), "Block A should start with ADD")
        assertEquals(aUpdate2, result[blockAId]?.get(1), "Block A should have last UPDATE")

        // Check Block B
        assertTrue(result.containsKey(blockBId))
        assertEquals(1, result[blockBId]?.size)
        assertEquals(bRemove, result[blockBId]?.get(0), "Block B should only have REMOVE")

        // Check Block C is not present
        assertFalse(result.containsKey(blockCId), "Block C should be removed (add+remove)")
    }

    @Test
    fun `normalizeOperations with single block delegates to reduceBlockOperations`() {
        // Scenario: Single block with multiple operations
        // Expect: Same behavior as reduceBlockOperations
        val blockId = UUID.randomUUID()
        val type = createTestBlockType()

        val t1 = ZonedDateTime.now()
        val t2 = t1.plusSeconds(1)

        val update1 = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = blockId, orgId = orgId, type = type),
            timestamp = t1
        )
        val update2 = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = blockId, orgId = orgId, type = type),
            timestamp = t2
        )

        val normalizeResult = blockEnvironmentService.normalizeOperations(listOf(update1, update2))
        val reduceResult = blockEnvironmentService.reduceBlockOperations(listOf(update1, update2))

        assertEquals(1, normalizeResult.size)
        assertEquals(reduceResult, normalizeResult[blockId])
    }

    // ------------------------------------------------------------------
    // filterCascadeDeletedOperations: Edge Cases
    // ------------------------------------------------------------------

    @Test
    fun `filterCascadeDeletedOperations with ADD then cascade REMOVE filters all operations for child`() {
        // Scenario: Block is added, moved to parent, then parent is removed (cascade deletes child)
        // Expect: ADD and MOVE operations for child are filtered out, only parent REMOVE remains
        val parentId = UUID.randomUUID()
        val childId = UUID.randomUUID()
        val type = createTestBlockType()

        val t1 = ZonedDateTime.now()
        val t2 = t1.plusSeconds(1)
        val t3 = t2.plusSeconds(1)

        val childAdd = BlockFactory.createOperationRequest(
            operation = BlockFactory.createAddOperation(blockId = childId, orgId = orgId, type = type),
            timestamp = t1
        )

        val childMove = BlockFactory.createOperationRequest(
            operation = BlockFactory.createMoveOperation(
                blockId = childId,
                fromParentId = null,
                toParentId = parentId
            ),
            timestamp = t2
        )

        val parentRemove = BlockFactory.createOperationRequest(
            operation = BlockFactory.createRemoveOperation(
                blockId = parentId,
                childrenIds = mapOf(childId to parentId)
            ),
            timestamp = t3
        )

        val result = blockEnvironmentService.filterCascadeDeletedOperations(
            listOf(childAdd, childMove, parentRemove)
        )

        assertEquals(1, result.size, "Only parent REMOVE should remain")
        assertEquals(parentRemove, result[0])
    }

    @Test
    fun `filterCascadeDeletedOperations with UPDATE before cascade REMOVE filters UPDATE`() {
        // Scenario: Block exists, is updated, then parent is removed (cascade deletes child)
        // Expect: UPDATE is filtered out, only parent REMOVE remains
        val parentId = UUID.randomUUID()
        val childId = UUID.randomUUID()
        val type = createTestBlockType()

        val t1 = ZonedDateTime.now()
        val t2 = t1.plusSeconds(1)

        val childUpdate = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = childId, orgId = orgId, type = type),
            timestamp = t1
        )

        val parentRemove = BlockFactory.createOperationRequest(
            operation = BlockFactory.createRemoveOperation(
                blockId = parentId,
                childrenIds = mapOf(childId to parentId)
            ),
            timestamp = t2
        )

        val result = blockEnvironmentService.filterCascadeDeletedOperations(
            listOf(childUpdate, parentRemove)
        )

        assertEquals(1, result.size, "Only parent REMOVE should remain")
        assertEquals(parentRemove, result[0])
    }

    @Test
    fun `filterCascadeDeletedOperations with nested cascade deletion filters all descendants`() {
        // Scenario: Parent removed with multiple levels of children
        // Expect: All operations for descendants are filtered out
        val parentId = UUID.randomUUID()
        val child1Id = UUID.randomUUID()
        val child2Id = UUID.randomUUID()
        val grandchildId = UUID.randomUUID()
        val type = createTestBlockType()

        val t1 = ZonedDateTime.now()

        val child1Update = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = child1Id, orgId = orgId, type = type),
            timestamp = t1
        )

        val child2Update = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = child2Id, orgId = orgId, type = type),
            timestamp = t1
        )

        val grandchildUpdate = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = grandchildId, orgId = orgId, type = type),
            timestamp = t1
        )

        val parentRemove = BlockFactory.createOperationRequest(
            operation = BlockFactory.createRemoveOperation(
                blockId = parentId,
                childrenIds = mapOf(
                    child1Id to parentId,
                    child2Id to parentId,
                    grandchildId to child1Id
                )
            ),
            timestamp = t1.plusSeconds(1)
        )

        val result = blockEnvironmentService.filterCascadeDeletedOperations(
            listOf(child1Update, child2Update, grandchildUpdate, parentRemove)
        )

        assertEquals(1, result.size, "Only parent REMOVE should remain")
        assertEquals(parentRemove, result[0])
    }

    @Test
    fun `filterCascadeDeletedOperations with multiple parents removed filters correctly`() {
        // Scenario: Two separate parents removed, each with children
        // Expect: Only operations for non-removed blocks remain
        val parent1Id = UUID.randomUUID()
        val parent2Id = UUID.randomUUID()
        val child1Id = UUID.randomUUID()
        val child2Id = UUID.randomUUID()
        val independentBlockId = UUID.randomUUID()
        val type = createTestBlockType()

        val t1 = ZonedDateTime.now()

        val child1Update = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = child1Id, orgId = orgId, type = type),
            timestamp = t1
        )

        val child2Update = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = child2Id, orgId = orgId, type = type),
            timestamp = t1
        )

        val independentUpdate = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = independentBlockId, orgId = orgId, type = type),
            timestamp = t1
        )

        val parent1Remove = BlockFactory.createOperationRequest(
            operation = BlockFactory.createRemoveOperation(
                blockId = parent1Id,
                childrenIds = mapOf(child1Id to parent1Id)
            ),
            timestamp = t1.plusSeconds(1)
        )

        val parent2Remove = BlockFactory.createOperationRequest(
            operation = BlockFactory.createRemoveOperation(
                blockId = parent2Id,
                childrenIds = mapOf(child2Id to parent2Id)
            ),
            timestamp = t1.plusSeconds(1)
        )

        val result = blockEnvironmentService.filterCascadeDeletedOperations(
            listOf(child1Update, child2Update, independentUpdate, parent1Remove, parent2Remove)
        )

        assertEquals(3, result.size, "Should keep independent update and both parent removes")
        assertTrue(result.contains(independentUpdate))
        assertTrue(result.contains(parent1Remove))
        assertTrue(result.contains(parent2Remove))
        assertFalse(result.contains(child1Update), "child1 update should be filtered")
        assertFalse(result.contains(child2Update), "child2 update should be filtered")
    }

    @Test
    fun `filterCascadeDeletedOperations with ADD UPDATE MOVE REORDER then cascade REMOVE filters all`() {
        // Scenario: Block has all operation types, then is cascade deleted
        // Expect: All operations for the block are filtered out
        val parentId = UUID.randomUUID()
        val childId = UUID.randomUUID()
        val type = createTestBlockType()

        val t1 = ZonedDateTime.now()

        val addOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createAddOperation(blockId = childId, orgId = orgId, type = type),
            timestamp = t1
        )

        val updateOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = childId, orgId = orgId, type = type),
            timestamp = t1.plusSeconds(1)
        )

        val moveOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createMoveOperation(
                blockId = childId,
                fromParentId = null,
                toParentId = parentId
            ),
            timestamp = t1.plusSeconds(2)
        )

        val reorderOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createReorderOperation(
                blockId = childId,
                parentId = parentId,
                fromIndex = 0,
                toIndex = 1
            ),
            timestamp = t1.plusSeconds(3)
        )

        val parentRemove = BlockFactory.createOperationRequest(
            operation = BlockFactory.createRemoveOperation(
                blockId = parentId,
                childrenIds = mapOf(childId to parentId)
            ),
            timestamp = t1.plusSeconds(4)
        )

        val result = blockEnvironmentService.filterCascadeDeletedOperations(
            listOf(addOp, updateOp, moveOp, reorderOp, parentRemove)
        )

        assertEquals(1, result.size, "Only parent REMOVE should remain")
        assertEquals(parentRemove, result[0])
    }

    @Test
    fun `filterCascadeDeletedOperations with no REMOVE operations returns all operations`() {
        // Scenario: No remove operations present
        // Expect: All operations returned unchanged
        val blockId = UUID.randomUUID()
        val type = createTestBlockType()

        val addOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createAddOperation(blockId = blockId, orgId = orgId, type = type),
            timestamp = ZonedDateTime.now()
        )

        val updateOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = blockId, orgId = orgId, type = type),
            timestamp = ZonedDateTime.now().plusSeconds(1)
        )

        val result = blockEnvironmentService.filterCascadeDeletedOperations(listOf(addOp, updateOp))

        assertEquals(2, result.size)
        assertTrue(result.contains(addOp))
        assertTrue(result.contains(updateOp))
    }

    @Test
    fun `filterCascadeDeletedOperations with empty list returns empty list`() {
        val result = blockEnvironmentService.filterCascadeDeletedOperations(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `filterCascadeDeletedOperations with direct REMOVE keeps operation`() {
        // Scenario: Block is directly removed (not cascade)
        // Expect: REMOVE operation is kept
        val blockId = UUID.randomUUID()

        val removeOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createRemoveOperation(blockId = blockId),
            timestamp = ZonedDateTime.now()
        )

        val result = blockEnvironmentService.filterCascadeDeletedOperations(listOf(removeOp))

        assertEquals(1, result.size)
        assertEquals(removeOp, result[0])
    }

    @Test
    fun `filterCascadeDeletedOperations integrates with normalizeOperations correctly`() {
        // Scenario: Complete flow from filtering through normalization
        // Block is added, updated, then cascade deleted
        // Expect: After filtering, normalization should only see parent REMOVE
        val parentId = UUID.randomUUID()
        val childId = UUID.randomUUID()
        val type = createTestBlockType()

        val t1 = ZonedDateTime.now()

        val childAdd = BlockFactory.createOperationRequest(
            operation = BlockFactory.createAddOperation(blockId = childId, orgId = orgId, type = type),
            timestamp = t1
        )

        val childUpdate = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = childId, orgId = orgId, type = type),
            timestamp = t1.plusSeconds(1)
        )

        val parentRemove = BlockFactory.createOperationRequest(
            operation = BlockFactory.createRemoveOperation(
                blockId = parentId,
                childrenIds = mapOf(childId to parentId)
            ),
            timestamp = t1.plusSeconds(2)
        )

        val filtered = blockEnvironmentService.filterCascadeDeletedOperations(
            listOf(childAdd, childUpdate, parentRemove)
        )

        val normalized = blockEnvironmentService.normalizeOperations(filtered)

        assertEquals(1, normalized.size, "Should only have parent block")
        assertTrue(normalized.containsKey(parentId))
        assertEquals(1, normalized[parentId]?.size)
        assertEquals(parentRemove, normalized[parentId]?.get(0))
    }

    // ------------------------------------------------------------------
    // saveBlockEnvironment: Basic Operations
    // ------------------------------------------------------------------

    @Test
    fun `saveBlockEnvironment with ADD operation returns ID mapping`() {
        // Scenario: Add a new block
        // Expect: Success response with temp ID → real ID mapping
        val layoutId = UUID.randomUUID()
        val tempBlockId = UUID.randomUUID()
        val realBlockId = UUID.randomUUID()
        val type = createTestBlockType()

        val layout = BlockFactory.createTreeLayoutEntity(
            id = layoutId,
            workspaceId = orgId,
            version = 1
        )

        val addOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createAddOperation(blockId = tempBlockId, orgId = orgId, type = type),
            timestamp = ZonedDateTime.now()
        )

        val savedBlock = BlockFactory.createBlockEntity(
            id = realBlockId,
            workspaceId = orgId,
            type = BlockFactory.createTypeEntity(orgId = orgId, key = "test_block")
        )

        val typeEntity = BlockFactory.createTypeEntity(orgId = orgId, key = "test_block")

        // Mock service calls
        whenever(blockTreeLayoutService.fetchLayoutById(layoutId)).thenReturn(layout)
        whenever(blockService.getBlocks(any())).thenReturn(emptyMap())
        whenever(blockService.getBlockTypeEntity(any())).thenReturn(typeEntity)
        whenever(blockService.saveAll(any())).thenReturn(listOf(savedBlock))
        whenever(blockChildrenService.getChildrenForBlocks(any())).thenReturn(emptyMap())

        val request = BlockFactory.createSaveEnvironmentRequest(
            layoutId = layoutId,
            workspaceId = orgId,
            operations = listOf(addOp),
            version = 2
        )

        val response = blockEnvironmentService.saveBlockEnvironment(request)

        assertTrue(response.success)
        assertFalse(response.conflict)
        assertEquals(1, response.idMappings.size)
        assertTrue(response.idMappings.containsKey(tempBlockId))
        assertEquals(realBlockId, response.idMappings[tempBlockId])

        verify(blockService).saveAll(any())
        verify(activityService).logActivities(any())
    }

    @Test
    fun `saveBlockEnvironment with UPDATE operation modifies existing block`() {
        // Scenario: Update an existing block
        // Expect: Block is updated, no ID mappings
        val layoutId = UUID.randomUUID()
        val blockId = UUID.randomUUID()
        val type = createTestBlockType()

        val layout = BlockFactory.createTreeLayoutEntity(
            id = layoutId,
            workspaceId = orgId,
            version = 1
        )

        val existingBlock = BlockFactory.createBlockEntity(
            id = blockId,
            workspaceId = orgId,
            type = BlockFactory.createTypeEntity(orgId = orgId, key = "test_block")
        )

        val updateOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = blockId, orgId = orgId, type = type),
            timestamp = ZonedDateTime.now()
        )

        // Mock service calls
        whenever(blockTreeLayoutService.fetchLayoutById(layoutId)).thenReturn(layout)
        whenever(blockService.getBlocks(setOf(blockId))).thenReturn(mapOf(blockId to existingBlock))
        whenever(blockChildrenService.getChildrenForBlocks(any())).thenReturn(emptyMap())
        whenever(blockService.saveAll(any())).thenReturn(listOf(existingBlock))

        val request = BlockFactory.createSaveEnvironmentRequest(
            layoutId = layoutId,
            workspaceId = orgId,
            operations = listOf(updateOp),
            version = 2
        )

        val response = blockEnvironmentService.saveBlockEnvironment(request)

        assertTrue(response.success)
        assertEquals(0, response.idMappings.size)
        verify(blockService).saveAll(any())
    }

    @Test
    fun `saveBlockEnvironment with REMOVE operation deletes block and children`() {
        // Scenario: Remove a block with children (childrenIds provided in operation)
        // Expect: Cascade delete triggered
        val layoutId = UUID.randomUUID()
        val blockId = UUID.randomUUID()
        val childId = UUID.randomUUID()

        val layout = BlockFactory.createTreeLayoutEntity(
            id = layoutId,
            workspaceId = orgId,
            version = 1
        )

        val removeOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createRemoveOperation(
                blockId = blockId,
                childrenIds = mapOf(childId to blockId)
            ),
            timestamp = ZonedDateTime.now()
        )

        // Mock service calls
        whenever(blockTreeLayoutService.fetchLayoutById(layoutId)).thenReturn(layout)
        whenever(blockService.getBlocks(setOf(blockId))).thenReturn(emptyMap())
        whenever(blockChildrenService.getChildrenForBlocks(any())).thenReturn(emptyMap())

        val request = BlockFactory.createSaveEnvironmentRequest(
            layoutId = layoutId,
            workspaceId = orgId,
            operations = listOf(removeOp),
            version = 2
        )

        val response = blockEnvironmentService.saveBlockEnvironment(request)

        assertTrue(response.success)
        verify(blockChildrenService).deleteAllInBatch(setOf(blockId))
        verify(blockService).deleteAllById(setOf(blockId, childId))
    }

    @Test
    fun `saveBlockEnvironment with MOVE operation updates parent-child relationships`() {
        // Scenario: Move block from one parent to another
        // Expect: Old edge deleted, new edge created
        val layoutId = UUID.randomUUID()
        val blockId = UUID.randomUUID()
        val oldParentId = UUID.randomUUID()
        val newParentId = UUID.randomUUID()

        val layout = BlockFactory.createTreeLayoutEntity(
            id = layoutId,
            workspaceId = orgId,
            version = 1
        )

        val moveOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createMoveOperation(
                blockId = blockId,
                fromParentId = oldParentId,
                toParentId = newParentId
            ),
            timestamp = ZonedDateTime.now()
        )

        val oldEdge = BlockFactory.createBlockChildEntity(parentId = oldParentId, childId = blockId)
        val newEdge = BlockFactory.createBlockChildEntity(parentId = newParentId, childId = blockId)

        val moveResult = MovePreparationResult(
            childEntitiesToDelete = listOf(oldEdge),
            childEntitiesToSave = listOf(newEdge)
        )

        // Mock service calls
        whenever(blockTreeLayoutService.fetchLayoutById(layoutId)).thenReturn(layout)
        whenever(blockService.getBlocks(setOf(blockId))).thenReturn(emptyMap())
        whenever(blockChildrenService.getChildrenForBlocks(any())).thenReturn(emptyMap())
        whenever(blockChildrenService.prepareChildMoves(any(), any())).thenReturn(moveResult)

        val request = BlockFactory.createSaveEnvironmentRequest(
            layoutId = layoutId,
            workspaceId = orgId,
            operations = listOf(moveOp),
            version = 2
        )

        val response = blockEnvironmentService.saveBlockEnvironment(request)

        assertTrue(response.success)
        verify(blockChildrenService).deleteAllInBatch(listOf(oldEdge))
        verify(blockChildrenService).saveAll(listOf(newEdge))
    }

    @Test
    fun `saveBlockEnvironment with REORDER operation updates child indices`() {
        // Scenario: Reorder child within parent
        // Expect: Child indices updated
        val layoutId = UUID.randomUUID()
        val blockId = UUID.randomUUID()
        val parentId = UUID.randomUUID()

        val layout = BlockFactory.createTreeLayoutEntity(
            id = layoutId,
            workspaceId = orgId,
            version = 1
        )

        val reorderOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createReorderOperation(
                blockId = blockId,
                parentId = parentId,
                fromIndex = 0,
                toIndex = 2
            ),
            timestamp = ZonedDateTime.now()
        )

        val reorderedEdges = listOf(
            BlockFactory.createBlockChildEntity(parentId = parentId, childId = UUID.randomUUID(), orderIndex = 0),
            BlockFactory.createBlockChildEntity(parentId = parentId, childId = UUID.randomUUID(), orderIndex = 1),
            BlockFactory.createBlockChildEntity(parentId = parentId, childId = blockId, orderIndex = 2)
        )

        // Mock service calls
        whenever(blockTreeLayoutService.fetchLayoutById(layoutId)).thenReturn(layout)
        whenever(blockService.getBlocks(setOf(blockId))).thenReturn(emptyMap())
        whenever(blockChildrenService.getChildrenForBlocks(any())).thenReturn(emptyMap())
        whenever(blockChildrenService.prepareChildReorders(any(), any())).thenReturn(reorderedEdges)

        val request = BlockFactory.createSaveEnvironmentRequest(
            layoutId = layoutId,
            workspaceId = orgId,
            operations = listOf(reorderOp),
            version = 2
        )

        val response = blockEnvironmentService.saveBlockEnvironment(request)

        assertTrue(response.success)
        verify(blockChildrenService).saveAll(reorderedEdges)
    }

    // ------------------------------------------------------------------
    // saveBlockEnvironment: ID Mapping Resolution
    // ------------------------------------------------------------------

    @Test
    fun `saveBlockEnvironment resolves temp ID in UPDATE after ADD`() {
        // Scenario: ADD block with temp ID, then UPDATE it
        // Expect: UPDATE uses real ID, both operations succeed
        val layoutId = UUID.randomUUID()
        val tempBlockId = UUID.randomUUID()
        val realBlockId = UUID.randomUUID()
        val type = createTestBlockType()

        val layout = BlockFactory.createTreeLayoutEntity(
            id = layoutId,
            workspaceId = orgId,
            version = 1
        )

        val addOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createAddOperation(blockId = tempBlockId, orgId = orgId, type = type),
            timestamp = ZonedDateTime.now()
        )

        val updateOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = tempBlockId, orgId = orgId, type = type),
            timestamp = ZonedDateTime.now().plusSeconds(1)
        )

        val savedBlock = BlockFactory.createBlockEntity(
            id = realBlockId,
            workspaceId = orgId,
            type = BlockFactory.createTypeEntity(orgId = orgId, key = "test_block")
        )

        val typeEntity = BlockFactory.createTypeEntity(orgId = orgId, key = "test_block")

        // Mock service calls
        whenever(blockTreeLayoutService.fetchLayoutById(layoutId)).thenReturn(layout)
        whenever(blockService.getBlocks(any())).thenReturn(emptyMap())
        whenever(blockService.getBlockTypeEntity(any())).thenReturn(typeEntity)
        whenever(blockService.saveAll(any())).thenAnswer { invocation ->
            val blocks = invocation.getArgument<List<BlockEntity>>(0)
            if (blocks.any { it.id == null }) {
                listOf(savedBlock) // First call: ADD
            } else {
                blocks // Second call: UPDATE
            }
        }
        whenever(blockChildrenService.getChildrenForBlocks(any())).thenReturn(emptyMap())

        val request = BlockFactory.createSaveEnvironmentRequest(
            layoutId = layoutId,
            workspaceId = orgId,
            operations = listOf(addOp, updateOp),
            version = 2
        )

        val response = blockEnvironmentService.saveBlockEnvironment(request)

        assertTrue(response.success)
        assertEquals(1, response.idMappings.size)
        assertEquals(realBlockId, response.idMappings[tempBlockId])
        verify(blockService, times(2)).saveAll(any()) // Once for ADD, once for UPDATE
    }

    @Test
    fun `saveBlockEnvironment resolves temp ID in MOVE after ADD`() {
        // Scenario: ADD block with temp ID, then MOVE it
        // Expect: MOVE uses real ID for child
        val layoutId = UUID.randomUUID()
        val tempBlockId = UUID.randomUUID()
        val realBlockId = UUID.randomUUID()
        val newParentId = UUID.randomUUID()
        val type = createTestBlockType()

        val layout = BlockFactory.createTreeLayoutEntity(
            id = layoutId,
            workspaceId = orgId,
            version = 1
        )

        val addOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createAddOperation(blockId = tempBlockId, orgId = orgId, type = type),
            timestamp = ZonedDateTime.now()
        )

        val moveOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createMoveOperation(
                blockId = tempBlockId,
                fromParentId = null,
                toParentId = newParentId
            ),
            timestamp = ZonedDateTime.now().plusSeconds(1)
        )

        val savedBlock = BlockFactory.createBlockEntity(
            id = realBlockId,
            workspaceId = orgId,
            type = BlockFactory.createTypeEntity(orgId = orgId, key = "test_block")
        )

        val typeEntity = BlockFactory.createTypeEntity(orgId = orgId, key = "test_block")
        val newEdge = BlockFactory.createBlockChildEntity(parentId = newParentId, childId = realBlockId)

        val moveResult = MovePreparationResult(
            childEntitiesToDelete = emptyList(),
            childEntitiesToSave = listOf(newEdge)
        )

        // Mock service calls
        whenever(blockTreeLayoutService.fetchLayoutById(layoutId)).thenReturn(layout)
        whenever(blockService.getBlocks(any())).thenReturn(emptyMap())
        whenever(blockService.getBlockTypeEntity(any())).thenReturn(typeEntity)
        whenever(blockService.saveAll(any())).thenReturn(listOf(savedBlock))
        whenever(blockChildrenService.getChildrenForBlocks(any())).thenReturn(emptyMap())
        whenever(blockChildrenService.prepareChildMoves(any(), any())).thenReturn(moveResult)

        val request = BlockFactory.createSaveEnvironmentRequest(
            layoutId = layoutId,
            workspaceId = orgId,
            operations = listOf(addOp, moveOp),
            version = 2
        )

        val response = blockEnvironmentService.saveBlockEnvironment(request)

        assertTrue(response.success)
        assertEquals(realBlockId, response.idMappings[tempBlockId])
        verify(blockChildrenService).saveAll(listOf(newEdge))
    }

    @Test
    fun `saveBlockEnvironment resolves temp parent ID in nested ADD`() {
        // Scenario: ADD parent with temp ID, ADD child with parent = temp ID
        // Expect: Child's parent reference resolved to real parent ID
        val layoutId = UUID.randomUUID()
        val tempParentId = UUID.randomUUID()
        val tempChildId = UUID.randomUUID()
        val realParentId = UUID.randomUUID()
        val realChildId = UUID.randomUUID()
        val type = createTestBlockType()

        val layout = BlockFactory.createTreeLayoutEntity(
            id = layoutId,
            workspaceId = orgId,
            version = 1
        )

        val parentAddOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createAddOperation(blockId = tempParentId, orgId = orgId, type = type),
            timestamp = ZonedDateTime.now()
        )

        val childAddOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createAddOperation(
                blockId = tempChildId,
                orgId = orgId,
                type = type,
                parentId = tempParentId
            ),
            timestamp = ZonedDateTime.now().plusSeconds(1)
        )

        val savedParent = BlockFactory.createBlockEntity(
            id = realParentId,
            workspaceId = orgId,
            type = BlockFactory.createTypeEntity(orgId = orgId, key = "test_block")
        )

        val savedChild = BlockFactory.createBlockEntity(
            id = realChildId,
            workspaceId = orgId,
            type = BlockFactory.createTypeEntity(orgId = orgId, key = "test_block")
        )

        val typeEntity = BlockFactory.createTypeEntity(orgId = orgId, key = "test_block")
        val childEdge = BlockFactory.createBlockChildEntity(parentId = realParentId, childId = realChildId)

        // Mock service calls
        whenever(blockTreeLayoutService.fetchLayoutById(layoutId)).thenReturn(layout)
        whenever(blockService.getBlocks(any())).thenReturn(emptyMap())
        whenever(blockService.getBlockTypeEntity(any())).thenReturn(typeEntity)
        whenever(blockService.saveAll(any())).thenAnswer { invocation ->
            val blocks = invocation.getArgument<List<BlockEntity>>(0)
            if (blocks.size == 1 && blocks[0].id == null) {
                listOf(if (blocks[0].name == savedParent.name) savedParent else savedChild)
            } else {
                blocks.map { if (it.id == null) savedChild else it }
            }
        }
        whenever(blockChildrenService.getChildrenForBlocks(any())).thenReturn(emptyMap())
        whenever(blockChildrenService.prepareChildAdditions(any(), any())).thenReturn(listOf(childEdge))
        whenever(blockChildrenService.saveAll(any())).thenReturn(listOf(childEdge))

        val request = BlockFactory.createSaveEnvironmentRequest(
            layoutId = layoutId,
            workspaceId = orgId,
            operations = listOf(parentAddOp, childAddOp),
            version = 2
        )

        val response = blockEnvironmentService.saveBlockEnvironment(request)

        assertTrue(response.success)
        assertEquals(2, response.idMappings.size)
        assertTrue(response.idMappings.containsKey(tempParentId))
        assertTrue(response.idMappings.containsKey(tempChildId))
    }

    @Test
    fun `saveBlockEnvironment with multiple ADDs returns all ID mappings`() {
        // Scenario: Add multiple blocks in one request
        // Expect: All temp → real ID mappings returned
        val layoutId = UUID.randomUUID()
        val tempId1 = UUID.randomUUID()
        val tempId2 = UUID.randomUUID()
        val tempId3 = UUID.randomUUID()
        val realId1 = UUID.randomUUID()
        val realId2 = UUID.randomUUID()
        val realId3 = UUID.randomUUID()
        val type = createTestBlockType()

        val layout = BlockFactory.createTreeLayoutEntity(
            id = layoutId,
            workspaceId = orgId,
            version = 1
        )

        val ops = listOf(tempId1, tempId2, tempId3).map { tempId ->
            BlockFactory.createOperationRequest(
                operation = BlockFactory.createAddOperation(blockId = tempId, orgId = orgId, type = type),
                timestamp = ZonedDateTime.now()
            )
        }

        val savedBlock1 = BlockFactory.createBlockEntity(
            id = realId1,
            workspaceId = orgId,
            type = BlockFactory.createTypeEntity(orgId = orgId, key = "test_block")
        )

        val savedBlock2 = BlockFactory.createBlockEntity(
            id = realId2,
            workspaceId = orgId,
            type = BlockFactory.createTypeEntity(orgId = orgId, key = "test_block")
        )


        val savedBlock3 = BlockFactory.createBlockEntity(
            id = realId3,
            workspaceId = orgId,
            type = BlockFactory.createTypeEntity(orgId = orgId, key = "test_block")
        )


        val typeEntity = BlockFactory.createTypeEntity(orgId = orgId, key = "test_block")

        // Mock service calls
        whenever(blockTreeLayoutService.fetchLayoutById(layoutId)).thenReturn(layout)
        whenever(blockService.getBlocks(any())).thenReturn(emptyMap())
        whenever(blockService.getBlockTypeEntity(any())).thenReturn(typeEntity)
        whenever(blockService.saveAll(any())).thenReturn(listOf(savedBlock1), listOf(savedBlock2), listOf(savedBlock3))
        whenever(blockChildrenService.getChildrenForBlocks(any())).thenReturn(emptyMap())

        val request = BlockFactory.createSaveEnvironmentRequest(
            layoutId = layoutId,
            workspaceId = orgId,
            operations = ops,
            version = 2
        )

        val response = blockEnvironmentService.saveBlockEnvironment(request)

        assertTrue(response.success)
        assertEquals(3, response.idMappings.size)
        assertEquals(realId1, response.idMappings[tempId1])
        assertEquals(realId2, response.idMappings[tempId2])
        assertEquals(realId3, response.idMappings[tempId3])
    }

    // ------------------------------------------------------------------
    // saveBlockEnvironment: Edge Cases
    // ------------------------------------------------------------------

    @Test
    fun `saveBlockEnvironment with empty operations succeeds`() {
        // Scenario: Save with no operations
        // Expect: Success with no changes
        val layoutId = UUID.randomUUID()

        val layout = BlockFactory.createTreeLayoutEntity(
            id = layoutId,
            workspaceId = orgId,
            version = 1
        )

        whenever(blockTreeLayoutService.fetchLayoutById(layoutId)).thenReturn(layout)

        val request = BlockFactory.createSaveEnvironmentRequest(
            layoutId = layoutId,
            workspaceId = orgId,
            operations = emptyList(),
            version = 2
        )

        val response = blockEnvironmentService.saveBlockEnvironment(request)

        assertTrue(response.success)
        assertEquals(0, response.idMappings.size)
        verify(blockService, never()).saveAll(any())
        verify(blockService, never()).deleteAllById(any())
    }

    @Test
    fun `saveBlockEnvironment publishes BlockEnvironmentEvent on success`() {
        val layoutId = UUID.randomUUID()

        val layout = BlockFactory.createTreeLayoutEntity(
            id = layoutId,
            workspaceId = orgId,
            version = 1
        )

        whenever(blockTreeLayoutService.fetchLayoutById(layoutId)).thenReturn(layout)

        val request = BlockFactory.createSaveEnvironmentRequest(
            layoutId = layoutId,
            workspaceId = orgId,
            operations = emptyList(),
            version = 2
        )

        blockEnvironmentService.saveBlockEnvironment(request)

        val events = applicationEvents.stream(BlockEnvironmentEvent::class.java).toList()
        assertEquals(1, events.size)

        val event = events[0]
        assertEquals(orgId, event.workspaceId)
        assertEquals(userId, event.userId)
        assertEquals(OperationType.UPDATE, event.operation)
        assertEquals(layoutId, event.layoutId)
        assertEquals(2, event.version)
    }

    @Test
    fun `saveBlockEnvironment with version conflict returns conflict response`() {
        // Scenario: Request version <= layout version
        // Expect: Conflict response with latest metadata
        val layoutId = UUID.randomUUID()

        val layout = BlockFactory.createTreeLayoutEntity(
            id = layoutId,
            workspaceId = orgId,
            version = 5
        )

        whenever(blockTreeLayoutService.fetchLayoutById(layoutId)).thenReturn(layout)

        val request = BlockFactory.createSaveEnvironmentRequest(
            layoutId = layoutId,
            workspaceId = orgId,
            operations = emptyList(),
            version = 4 // Lower than layout version
        )

        val response = blockEnvironmentService.saveBlockEnvironment(request)

        assertFalse(response.success)
        assertTrue(response.conflict)
        assertEquals(5, response.latestVersion)
        verify(blockService, never()).saveAll(any())
    }

    @Test
    fun `saveBlockEnvironment cascade deletes all descendants`() {
        // Scenario: Remove parent with nested children (childrenIds provided in operation)
        // Expect: All descendants deleted
        val layoutId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val child1Id = UUID.randomUUID()
        val child2Id = UUID.randomUUID()
        val grandchildId = UUID.randomUUID()

        val layout = BlockFactory.createTreeLayoutEntity(
            id = layoutId,
            workspaceId = orgId,
            version = 1
        )

        val removeOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createRemoveOperation(
                blockId = parentId,
                childrenIds = mapOf(
                    child1Id to parentId,
                    child2Id to parentId,
                    grandchildId to child1Id
                )
            ),
            timestamp = ZonedDateTime.now()
        )

        whenever(blockTreeLayoutService.fetchLayoutById(layoutId)).thenReturn(layout)
        whenever(blockService.getBlocks(any())).thenReturn(emptyMap())
        whenever(blockChildrenService.getChildrenForBlocks(any())).thenReturn(emptyMap())

        val request = BlockFactory.createSaveEnvironmentRequest(
            layoutId = layoutId,
            workspaceId = orgId,
            operations = listOf(removeOp),
            version = 2
        )

        val response = blockEnvironmentService.saveBlockEnvironment(request)

        assertTrue(response.success)
        verify(blockChildrenService).deleteAllInBatch(setOf(parentId, child1Id))
        verify(blockService).deleteAllById(setOf(parentId, child1Id, child2Id, grandchildId))
    }

    @Test
    fun `saveBlockEnvironment with ADD then REMOVE returns empty mappings`() {
        // Scenario: Block added and removed in same request
        // Expect: Normalization drops both, no mappings
        val layoutId = UUID.randomUUID()
        val blockId = UUID.randomUUID()
        val type = createTestBlockType()

        val layout = BlockFactory.createTreeLayoutEntity(
            id = layoutId,
            workspaceId = orgId,
            version = 1
        )

        val addOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createAddOperation(blockId = blockId, orgId = orgId, type = type),
            timestamp = ZonedDateTime.now()
        )

        val removeOp = BlockFactory.createOperationRequest(
            operation = BlockFactory.createRemoveOperation(blockId = blockId),
            timestamp = ZonedDateTime.now().plusSeconds(1)
        )

        whenever(blockTreeLayoutService.fetchLayoutById(layoutId)).thenReturn(layout)

        val request = BlockFactory.createSaveEnvironmentRequest(
            layoutId = layoutId,
            workspaceId = orgId,
            operations = listOf(addOp, removeOp),
            version = 2
        )

        val response = blockEnvironmentService.saveBlockEnvironment(request)

        assertTrue(response.success)
        assertEquals(0, response.idMappings.size)
        verify(blockService, never()).saveAll(any())
        verify(blockService, never()).deleteAllById(any())
    }

    // ------------------------------------------------------------------
    // saveBlockEnvironment: Complex Scenarios
    // ------------------------------------------------------------------

    @Test
    fun `saveBlockEnvironment with complex multi-block scenario`() {
        // Scenario: Multiple blocks with different operation types
        // - Block A: ADD
        // - Block B: UPDATE
        // - Block C: REMOVE
        // - Block D: MOVE
        // Expect: All operations processed correctly
        val layoutId = UUID.randomUUID()
        val tempIdA = UUID.randomUUID()
        val realIdA = UUID.randomUUID()
        val blockBId = UUID.randomUUID()
        val blockCId = UUID.randomUUID()
        val blockDId = UUID.randomUUID()
        val type = createTestBlockType()

        val layout = BlockFactory.createTreeLayoutEntity(
            id = layoutId,
            workspaceId = orgId,
            version = 1
        )

        val opA = BlockFactory.createOperationRequest(
            operation = BlockFactory.createAddOperation(blockId = tempIdA, orgId = orgId, type = type),
            timestamp = ZonedDateTime.now()
        )

        val opB = BlockFactory.createOperationRequest(
            operation = BlockFactory.createUpdateOperation(blockId = blockBId, orgId = orgId, type = type),
            timestamp = ZonedDateTime.now()
        )

        val opC = BlockFactory.createOperationRequest(
            operation = BlockFactory.createRemoveOperation(blockId = blockCId),
            timestamp = ZonedDateTime.now()
        )

        val opD = BlockFactory.createOperationRequest(
            operation = BlockFactory.createMoveOperation(
                blockId = blockDId,
                fromParentId = UUID.randomUUID(),
                toParentId = UUID.randomUUID()
            ),
            timestamp = ZonedDateTime.now()
        )

        val savedBlockA = BlockFactory.createBlockEntity(
            id = realIdA,
            workspaceId = orgId,
            type = BlockFactory.createTypeEntity(orgId = orgId, key = "test_block")
        )

        val existingBlockB = BlockFactory.createBlockEntity(
            id = blockBId,
            workspaceId = orgId,
            type = BlockFactory.createTypeEntity(orgId = orgId, key = "test_block")
        )

        val typeEntity = BlockFactory.createTypeEntity(orgId = orgId, key = "test_block")

        val cascadeResult = CascadeRemovalResult(
            blocksToDelete = setOf(blockCId),
            childEntitiesToDelete = emptyList()
        )

        val moveResult = MovePreparationResult(
            childEntitiesToDelete = emptyList(),
            childEntitiesToSave = emptyList()
        )

        whenever(blockTreeLayoutService.fetchLayoutById(layoutId)).thenReturn(layout)
        whenever(blockService.getBlocks(any())).thenAnswer { invocation ->
            val ids = invocation.getArgument<Set<UUID>>(0)
            mapOf(blockBId to existingBlockB).filterKeys { it in ids }
        }
        whenever(blockService.getBlockTypeEntity(any())).thenReturn(typeEntity)
        whenever(blockService.saveAll(any())).thenAnswer { invocation ->
            val blocks = invocation.getArgument<List<BlockEntity>>(0)
            if (blocks.any { it.id == null }) listOf(savedBlockA) else blocks
        }
        whenever(blockChildrenService.getChildrenForBlocks(any())).thenReturn(emptyMap())
        whenever(blockChildrenService.prepareRemovalCascade(setOf(blockCId))).thenReturn(cascadeResult)
        whenever(blockChildrenService.prepareChildMoves(any(), any())).thenReturn(moveResult)

        val request = BlockFactory.createSaveEnvironmentRequest(
            layoutId = layoutId,
            workspaceId = orgId,
            operations = listOf(opA, opB, opC, opD),
            version = 2
        )

        val response = blockEnvironmentService.saveBlockEnvironment(request)

        assertTrue(response.success)
        assertEquals(1, response.idMappings.size)
        assertEquals(realIdA, response.idMappings[tempIdA])
    }
}
