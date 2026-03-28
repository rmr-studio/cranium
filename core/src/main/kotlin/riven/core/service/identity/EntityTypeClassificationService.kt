package riven.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.repository.entity.EntityTypeSemanticMetadataRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Provides cached IDENTIFIER-attribute classification lookups for entity types.
 *
 * Classification is immutable after creation in the current system, so cached entries
 * do not require TTL eviction. The cache only needs invalidation when new attributes
 * are added to an entity type (which could introduce a new IDENTIFIER entry).
 *
 * The cache maps entityTypeId to the set of IDENTIFIER attribute IDs. An empty set
 * indicates no IDENTIFIER attributes — equivalent to [hasIdentifierAttributes] returning false.
 */
@Service
class EntityTypeClassificationService(
    private val semanticMetadataRepository: EntityTypeSemanticMetadataRepository,
    private val logger: KLogger,
) {

    private val identifierCache = ConcurrentHashMap<UUID, Set<UUID>>()

    // ------ Public read operations ------

    /**
     * Returns true if the entity type has at least one IDENTIFIER-classified attribute.
     *
     * Results are cached in-memory. Call [invalidate] when new attributes are added
     * to the entity type.
     *
     * @param entityTypeId The entity type to check.
     */
    fun hasIdentifierAttributes(entityTypeId: UUID): Boolean =
        getIdentifierAttributeIds(entityTypeId).isNotEmpty()

    /**
     * Returns the set of attribute IDs with IDENTIFIER classification for the given entity type.
     *
     * Results are cached. An empty set indicates no IDENTIFIER attributes.
     *
     * @param entityTypeId The entity type to inspect.
     */
    fun getIdentifierAttributeIds(entityTypeId: UUID): Set<UUID> =
        identifierCache.getOrPut(entityTypeId) {
            logger.debug { "Cache miss — querying IDENTIFIER attributes for entity type $entityTypeId" }
            semanticMetadataRepository
                .findByEntityTypeIdAndTargetType(entityTypeId, SemanticMetadataTargetType.ATTRIBUTE)
                .filter { it.classification == SemanticAttributeClassification.IDENTIFIER }
                .mapTo(mutableSetOf()) { it.targetId }
        }

    // ------ Cache management ------

    /**
     * Evicts the cached classification entry for [entityTypeId].
     *
     * Should be called whenever attributes are added or removed from the entity type
     * to ensure subsequent classification lookups reflect the current state.
     *
     * @param entityTypeId The entity type whose cache entry should be removed.
     */
    fun invalidate(entityTypeId: UUID) {
        identifierCache.remove(entityTypeId)
        logger.debug { "Classification cache evicted for entity type $entityTypeId" }
    }
}
