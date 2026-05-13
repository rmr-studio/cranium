package cranium.core.repository.block

import cranium.core.entity.block.BlockChildEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.*

interface BlockChildrenRepository : JpaRepository<BlockChildEntity, UUID> {
    fun findByParentIdOrderByOrderIndexAsc(parentId: UUID): List<BlockChildEntity>
    fun findByParentIdAndChildId(parentId: UUID, childId: UUID): BlockChildEntity?
    fun findByChildId(childId: UUID): BlockChildEntity?
    fun deleteAllByParentIdIn(parentIds: Collection<UUID>)
    fun countByParentId(parentId: UUID): Int

    @Query(
        """
        select e.parentId
        from BlockChildEntity e
        where e.childId = :childId
        """
    )
    fun findParentIdsByChildId(childId: UUID): List<UUID>

    @Query(
        """
    select e
    from BlockChildEntity e
    where e.parentId in :parentIds
    order by e.parentId, e.orderIndex asc
    """
    )
    fun findByParentIdInOrderByParentIdAndOrderIndex(
        parentIds: Collection<UUID>
    ): List<BlockChildEntity>
}