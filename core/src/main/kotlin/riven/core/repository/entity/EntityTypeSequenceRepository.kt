package cranium.core.repository.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import cranium.core.entity.entity.EntityTypeSequenceEntity
import cranium.core.entity.entity.EntityTypeSequenceId
import java.util.*

/**
 * Repository for entity type sequence counters.
 */
interface EntityTypeSequenceRepository : JpaRepository<EntityTypeSequenceEntity, EntityTypeSequenceId> {

    /**
     * Atomically increment the sequence counter and return the new value.
     * PostgreSQL row-level lock ensures concurrent calls produce unique values.
     */
    @Modifying
    @Query(
        value = """
            UPDATE entity_type_sequences
            SET current_value = current_value + 1
            WHERE entity_type_id = :entityTypeId AND attribute_id = :attributeId
            RETURNING current_value
        """,
        nativeQuery = true
    )
    fun incrementAndGet(entityTypeId: UUID, attributeId: UUID): Long?
}
