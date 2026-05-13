package cranium.core.service.block

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.*
import org.springframework.test.context.junit.jupiter.SpringExtension
import cranium.core.entity.block.BlockChildEntity
import cranium.core.models.block.display.BlockTypeNesting
import cranium.core.repository.block.BlockChildrenRepository
import cranium.core.repository.block.BlockRepository
import cranium.core.service.util.factory.block.BlockFactory
import java.util.*

/**
 * Comprehensive test suite for BlockChildrenService.
 * Tests the hierarchy management system with flat list ordering.
 */
@ExtendWith(SpringExtension::class)
class BlockChildrenServiceTest {

    private val edgeRepository: BlockChildrenRepository = mock()
    private val blockRepository: BlockRepository = mock()
    private val service = BlockChildrenService(edgeRepository, blockRepository)


    // =============================================================================================
    // READ OPERATIONS
    // =============================================================================================

    @Test
    fun `listChildren returns ordered children`() {
        val parentId = UUID.randomUUID()
        val edges = listOf(
            BlockChildEntity(UUID.randomUUID(), parentId, UUID.randomUUID(), 0),
            BlockChildEntity(UUID.randomUUID(), parentId, UUID.randomUUID(), 1),
            BlockChildEntity(UUID.randomUUID(), parentId, UUID.randomUUID(), 2)
        )

        whenever(edgeRepository.findByParentIdOrderByOrderIndexAsc(parentId)).thenReturn(edges)

        val result = service.listChildren(parentId)

        assertEquals(3, result.size)
        assertEquals(0, result[0].orderIndex)
        assertEquals(1, result[1].orderIndex)
        assertEquals(2, result[2].orderIndex)
    }

    // =============================================================================================
    // ADD CHILD
    // =============================================================================================

    @Test
    fun `addChild successfully adds child to empty parent`() {
        val orgId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val childId = UUID.randomUUID()

        val childType = BlockFactory.createType(orgId, key = "contact_card")
        val child = BlockFactory.createBlock(childId, orgId, childType)

        val parentType = BlockFactory.createType(orgId)
        val parent = BlockFactory.createBlock(parentId, orgId, parentType)

        val nesting = BlockTypeNesting(max = null, allowedTypes = listOf("contact_card"))

        whenever(blockRepository.findById(parentId)).thenReturn(Optional.of(parent))
        whenever(edgeRepository.findByChildId(childId)).thenReturn(null)
        whenever(edgeRepository.findByParentIdOrderByOrderIndexAsc(parentId)).thenReturn(emptyList())
        whenever(edgeRepository.countByParentId(parentId)).thenReturn(0)

        val saved = BlockChildEntity(UUID.randomUUID(), parentId, childId, 0)
        whenever(edgeRepository.save(any())).thenReturn(saved)

        val result = service.addChild(child, parentId, 0, nesting)

        assertEquals(parentId, result.parentId)
        assertEquals(childId, result.childId)
        assertEquals(0, result.orderIndex)

        verify(edgeRepository).save(argThat {
            this.parentId == parentId && this.childId == childId && this.orderIndex == 0
        })
    }

    @Test
    fun `addChild inserts at specified index and shifts siblings`() {
        val orgId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val childId = UUID.randomUUID()

        val childType = BlockFactory.createType(orgId, key = "contact_card")
        val child = BlockFactory.createBlock(childId, orgId, childType)

        val parentType = BlockFactory.createType(orgId)
        val parent = BlockFactory.createBlock(parentId, orgId, parentType)

        val nesting = BlockTypeNesting(max = null, allowedTypes = listOf("contact_card"))

        val existingSiblings = listOf(
            BlockChildEntity(UUID.randomUUID(), parentId, UUID.randomUUID(), 0),
            BlockChildEntity(UUID.randomUUID(), parentId, UUID.randomUUID(), 1),
            BlockChildEntity(UUID.randomUUID(), parentId, UUID.randomUUID(), 2)
        )

        whenever(blockRepository.findById(parentId)).thenReturn(Optional.of(parent))
        whenever(edgeRepository.findByChildId(childId)).thenReturn(null)
        whenever(edgeRepository.findByParentIdOrderByOrderIndexAsc(parentId)).thenReturn(existingSiblings)
        whenever(edgeRepository.countByParentId(parentId)).thenReturn(3)
        whenever(edgeRepository.save(any())).thenAnswer { it.arguments[0] }

        service.addChild(child, parentId, 1, nesting)

        // Verify siblings with orderIndex >= 1 were shifted up
        verify(edgeRepository, times(2)).save(argThat<BlockChildEntity> {
            (this.orderIndex ?: 0) >= 2 // original indices 1 and 2 should become 2 and 3
        })
    }

    @Test
    fun `addChild throws when child already exists as a child elsewhere`() {
        val orgId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val childId = UUID.randomUUID()
        val otherParentId = UUID.randomUUID()

        val childType = BlockFactory.createType(orgId, key = "contact_card")
        val child = BlockFactory.createBlock(childId, orgId, childType)

        val parentType = BlockFactory.createType(orgId, key = "contact_card")
        val parent = BlockFactory.createBlock(parentId, orgId, parentType)

        val nesting = BlockTypeNesting(max = null, allowedTypes = listOf("contact_card"))

        val existingEdge = BlockChildEntity(UUID.randomUUID(), otherParentId, childId, 0)
        whenever(blockRepository.findById(parentId)).thenReturn(Optional.of(parent))
        whenever(edgeRepository.findByChildId(childId)).thenReturn(existingEdge)

        val exception = assertThrows<IllegalStateException> {
            service.addChild(child, parentId, 0, nesting)
        }

        assertTrue(exception.message!!.contains("already exists as a child"))
    }

    @Test
    fun `addChild throws when workspaces do not match`() {
        val orgA = UUID.randomUUID()
        val orgB = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val childId = UUID.randomUUID()

        val childType = BlockFactory.createType(orgB, key = "contact_card")
        val child = BlockFactory.createBlock(childId, orgB, childType)

        val parentType = BlockFactory.createType(orgA)
        val parent = BlockFactory.createBlock(parentId, orgA, parentType)

        val nesting = BlockTypeNesting(max = null, allowedTypes = listOf("contact_card"))

        whenever(blockRepository.findById(parentId)).thenReturn(Optional.of(parent))
        whenever(edgeRepository.findByChildId(childId)).thenReturn(null)

        val exception = assertThrows<IllegalArgumentException> {
            service.addChild(child, parentId, 0, nesting)
        }

        assertTrue(exception.message!!.contains("different workspace"))
    }

    @Test
    fun `addChild throws when child type not allowed in nesting rules`() {
        val orgId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val childId = UUID.randomUUID()

        val childType = BlockFactory.createType(orgId, key = "not_a_contact_card")
        val child = BlockFactory.createBlock(childId, orgId, childType)

        val parentType = BlockFactory.createType(
            orgId,
            nesting = BlockTypeNesting(max = null, allowedTypes = listOf("contact_card"))
        )
        val parent = BlockFactory.createBlock(parentId, orgId, parentType)

        val nesting = BlockTypeNesting(max = null, allowedTypes = listOf("contact_card"))

        whenever(blockRepository.findById(parentId)).thenReturn(Optional.of(parent))
        whenever(edgeRepository.findByChildId(childId)).thenReturn(null)

        val exception = assertThrows<IllegalArgumentException> {
            service.addChild(child, parentId, 0, nesting)
        }

        assertTrue(exception.message!!.contains("not allowed in parent's nesting rules"))
    }

    @Test
    fun `addChild throws when max children limit reached`() {
        val orgId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val childId = UUID.randomUUID()

        val childType = BlockFactory.createType(orgId, key = "contact_card")
        val child = BlockFactory.createBlock(childId, orgId, childType)

        val parentType = BlockFactory.createType(orgId)
        val parent = BlockFactory.createBlock(parentId, orgId, parentType)
        val nesting = BlockTypeNesting(max = 2, allowedTypes = listOf("contact_card"))

        whenever(blockRepository.findById(parentId)).thenReturn(Optional.of(parent))
        whenever(edgeRepository.findByChildId(childId)).thenReturn(null)
        whenever(edgeRepository.countByParentId(parentId)).thenReturn(2)

        val exception = assertThrows<IllegalArgumentException> {
            service.addChild(child, parentId, 0, nesting)
        }

        assertTrue(exception.message!!.contains("reached maximum children"))
    }

    // =============================================================================================
    // REORDER CHILDREN
    // =============================================================================================

    @Test
    fun `reorderChildren moves child to new position`() {
        val parentId = UUID.randomUUID()
        val child1Id = UUID.randomUUID()
        val child2Id = UUID.randomUUID()
        val child3Id = UUID.randomUUID()

        val siblings = listOf(
            BlockChildEntity(UUID.randomUUID(), parentId, child1Id, 0),
            BlockChildEntity(UUID.randomUUID(), parentId, child2Id, 1),
            BlockChildEntity(UUID.randomUUID(), parentId, child3Id, 2)
        )

        whenever(edgeRepository.findByParentIdOrderByOrderIndexAsc(parentId)).thenReturn(siblings)
        whenever(edgeRepository.save(any())).thenAnswer { it.arguments[0] }

        // Move child1 (index 0) to position 2
        service.reorderChildren(parentId, child1Id, 2)

        // Verify renumbering: child2 -> 0, child3 -> 1, child1 -> 2
        verify(edgeRepository, atLeastOnce()).save(any())
    }

    @Test
    fun `reorderChildren throws when child not found in parent`() {
        val parentId = UUID.randomUUID()
        val childId = UUID.randomUUID()

        whenever(edgeRepository.findByParentIdOrderByOrderIndexAsc(parentId)).thenReturn(emptyList())

        assertThrows<NoSuchElementException> {
            service.reorderChildren(parentId, childId, 0)
        }
    }

    // =============================================================================================
    // REPARENT CHILD
    // =============================================================================================

    @Test
    fun `reparentChild moves child to new parent`() {
        val orgId = UUID.randomUUID()
        val oldParentId = UUID.randomUUID()
        val newParentId = UUID.randomUUID()
        val childId = UUID.randomUUID()

        val childType = BlockFactory.createType(orgId, key = "contact_card")
        val child = BlockFactory.createBlock(childId, orgId, childType)

        val parentType = BlockFactory.createType(orgId)
        val newParent = BlockFactory.createBlock(newParentId, orgId, parentType)
        val nesting = BlockTypeNesting(max = null, allowedTypes = listOf("contact_card"))

        val existingEdge = BlockChildEntity(UUID.randomUUID(), oldParentId, childId, 0)

        whenever(blockRepository.findById(childId)).thenReturn(Optional.of(child))
        whenever(blockRepository.findById(newParentId)).thenReturn(Optional.of(newParent))
        whenever(edgeRepository.findByChildId(childId)).thenReturn(existingEdge)
        whenever(edgeRepository.findByParentIdOrderByOrderIndexAsc(oldParentId)).thenReturn(
            listOf(existingEdge)
        )
        whenever(edgeRepository.findByParentIdOrderByOrderIndexAsc(newParentId)).thenReturn(emptyList())
        whenever(edgeRepository.countByParentId(newParentId)).thenReturn(0)
        whenever(edgeRepository.save(any())).thenAnswer { it.arguments[0] }

        service.reparentChild(childId, newParentId, nesting)

        // Verify old edge deleted
        verify(edgeRepository).delete(existingEdge)

        // Verify new edge created
        verify(edgeRepository).save(argThat<BlockChildEntity> {
            this.parentId == newParentId && this.childId == childId
        })
    }

    // =============================================================================================
    // DETACH CHILD
    // =============================================================================================

    @Test
    fun `detachChild removes edge and compacts siblings`() {
        val parentId = UUID.randomUUID()
        val childId = UUID.randomUUID()
        val orgId = UUID.randomUUID()

        val edge = BlockChildEntity(UUID.randomUUID(), parentId, childId, 0)

        val childType = BlockFactory.createType(orgId, key = "contact_card")
        val child = BlockFactory.createBlock(childId, orgId, childType)

        whenever(edgeRepository.findByChildId(childId)).thenReturn(edge)
        whenever(edgeRepository.findByParentIdOrderByOrderIndexAsc(parentId)).thenReturn(emptyList())
        whenever(blockRepository.findById(childId)).thenReturn(Optional.of(child))
        whenever(edgeRepository.save(any())).thenAnswer { it.arguments[0] }

        service.detachChild(childId)

        verify(edgeRepository).delete(edge)
    }

    @Test
    fun `detachChild does nothing when child has no parent`() {
        val childId = UUID.randomUUID()

        whenever(edgeRepository.findByChildId(childId)).thenReturn(null)

        service.detachChild(childId)

        verify(edgeRepository, never()).delete(any())
    }

    // =============================================================================================
    // REMOVE CHILD
    // =============================================================================================

    @Test
    fun `removeChild removes child from parent and compacts ordering`() {
        val parentId = UUID.randomUUID()
        val child1Id = UUID.randomUUID()
        val child2Id = UUID.randomUUID()
        val child3Id = UUID.randomUUID()
        val orgId = UUID.randomUUID()

        val edge1 = BlockChildEntity(UUID.randomUUID(), parentId, child1Id, 0)
        val edge2 = BlockChildEntity(UUID.randomUUID(), parentId, child2Id, 1)
        val edge3 = BlockChildEntity(UUID.randomUUID(), parentId, child3Id, 2)

        val childType = BlockFactory.createType(orgId, key = "contact_card")
        val child2 = BlockFactory.createBlock(child2Id, orgId, childType)


        whenever(edgeRepository.findByParentIdAndChildId(parentId, child2Id)).thenReturn(edge2)
        whenever(edgeRepository.findByParentIdOrderByOrderIndexAsc(parentId))
            .thenReturn(listOf(edge1, edge3)) // After deletion
        whenever(blockRepository.findById(child2Id)).thenReturn(Optional.of(child2))
        whenever(edgeRepository.save(any())).thenAnswer { it.arguments[0] }

        service.removeChild(parentId, child2Id)

        verify(edgeRepository).delete(edge2)
        // Verify compaction happened (remaining children renumbered)
        verify(edgeRepository, atLeastOnce()).save(any())
    }

    // =============================================================================================
    // ADDITIONAL COVERAGE - EDGE CASES
    // =============================================================================================

    @Test
    fun `addChild throws when child already attached to same parent`() {
        val orgId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val childId = UUID.randomUUID()

        val childType = BlockFactory.createType(orgId, key = "contact_card")
        val child = BlockFactory.createBlock(childId, orgId, childType)

        val parentType = BlockFactory.createType(orgId)
        val parent = BlockFactory.createBlock(parentId, orgId, parentType)

        val nesting = BlockTypeNesting(max = null, allowedTypes = listOf("contact_card"))

        // Child is already attached to the same parent
        val existingEdge = BlockChildEntity(UUID.randomUUID(), parentId, childId, 0)

        whenever(blockRepository.findById(parentId)).thenReturn(Optional.of(parent))
        whenever(edgeRepository.findByChildId(childId)).thenReturn(existingEdge)

        // Service should throw exception because child_id is globally unique
        val exception = assertThrows<IllegalStateException> {
            service.addChild(child, parentId, 0, nesting)
        }

        assertTrue(exception.message!!.contains("already exists"))
        verify(edgeRepository, never()).save(any())
    }

}
