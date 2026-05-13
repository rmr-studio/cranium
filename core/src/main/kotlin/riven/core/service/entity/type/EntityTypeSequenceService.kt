package cranium.core.service.entity.type

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import cranium.core.entity.entity.EntityTypeSequenceEntity
import cranium.core.entity.entity.EntityTypeSequenceId
import cranium.core.repository.entity.EntityTypeSequenceRepository
import java.util.*

/**
 * Manages sequential ID generation for entity types with ID-type attributes.
 *
 * Uses REQUIRES_NEW propagation for nextValue to minimise row-lock duration —
 * the sequence row lock is released as soon as the increment completes,
 * rather than being held for the entire entity save transaction.
 * Trade-off: gaps may occur if the outer transaction rolls back.
 */
@Service
class EntityTypeSequenceService(
    private val sequenceRepository: EntityTypeSequenceRepository,
    private val logger: KLogger,
) {

    /**
     * Idempotently initialize a sequence counter for a new ID-type attribute.
     * Called when an ID attribute is added to an entity type (template install or manual).
     * Safe to call multiple times — skips creation if the sequence already exists.
     */
    fun initializeSequence(entityTypeId: UUID, attributeId: UUID) {
        val id = EntityTypeSequenceId(entityTypeId, attributeId)
        if (sequenceRepository.existsById(id)) {
            logger.debug { "Sequence already exists for entity type $entityTypeId, attribute $attributeId" }
            return
        }
        sequenceRepository.save(
            EntityTypeSequenceEntity(
                entityTypeId = entityTypeId,
                attributeId = attributeId,
                currentValue = 0,
            )
        )
        logger.debug { "Initialized sequence for entity type $entityTypeId, attribute $attributeId" }
    }

    /**
     * Atomically increment the sequence and return the new value.
     * Runs in a separate transaction to minimize lock hold time.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun nextValue(entityTypeId: UUID, attributeId: UUID): Long {
        return sequenceRepository.incrementAndGet(entityTypeId, attributeId)
            ?: error(
                "Sequence row missing for entity type $entityTypeId, attribute $attributeId. " +
                    "Call initializeSequence() before generating IDs."
            )
    }

    /**
     * Format a sequential ID with the given prefix.
     * Example: formatId("PKR", 42) -> "PKR-42"
     */
    fun formatId(prefix: String, sequenceValue: Long): String {
        return "$prefix-$sequenceValue"
    }
}
