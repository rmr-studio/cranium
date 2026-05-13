package cranium.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import cranium.core.enums.entity.semantics.SemanticAttributeClassification
import cranium.core.enums.entity.semantics.SemanticMetadataTargetType
import cranium.core.enums.identity.MatchSignalType
import cranium.core.repository.entity.EntityTypeSemanticMetadataRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Provides cached IDENTIFIER-attribute classification lookups for entity types.
 *
 * Cache entries are evicted when metadata changes via [EntityTypeSemanticMetadataService],
 * which calls [invalidate] on every upsert (single or bulk). This ensures the cache
 * reflects the current classification and signal-type state.
 *
 * The cache maps entityTypeId to a map of (IDENTIFIER attribute ID -> MatchSignalType).
 * The signal type is resolved from the `signal_type` column on the metadata row; if null,
 * falls back to [MatchSignalType.CUSTOM_IDENTIFIER].
 */
@Service
class EntityTypeClassificationService(
    private val semanticMetadataRepository: EntityTypeSemanticMetadataRepository,
    private val logger: KLogger,
) {

    private val identifierCache = ConcurrentHashMap<UUID, Map<UUID, MatchSignalType>>()

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
     * Delegates to [getIdentifierSignalTypes] and returns its key set. Results are cached.
     * An empty set indicates no IDENTIFIER attributes.
     *
     * @param entityTypeId The entity type to inspect.
     */
    fun getIdentifierAttributeIds(entityTypeId: UUID): Set<UUID> =
        getIdentifierSignalTypes(entityTypeId).keys

    /**
     * Returns a map of IDENTIFIER attribute ID to [MatchSignalType] for the given entity type.
     *
     * Signal type is resolved from the `signal_type` column on the metadata row using
     * [MatchSignalType.fromColumnValue]. When the column is null (pre-existing rows), falls
     * back to [MatchSignalType.CUSTOM_IDENTIFIER].
     *
     * Results are cached. An empty map indicates no IDENTIFIER attributes.
     *
     * @param entityTypeId The entity type to inspect.
     */
    fun getIdentifierSignalTypes(entityTypeId: UUID): Map<UUID, MatchSignalType> =
        identifierCache.getOrPut(entityTypeId) {
            logger.debug { "Cache miss — querying IDENTIFIER attributes for entity type $entityTypeId" }
            semanticMetadataRepository
                .findByEntityTypeIdAndTargetType(entityTypeId, SemanticMetadataTargetType.ATTRIBUTE)
                .filter { it.classification == SemanticAttributeClassification.IDENTIFIER }
                .associate { entity ->
                    val signalType = entity.signalType ?: MatchSignalType.CUSTOM_IDENTIFIER
                    entity.targetId to signalType
                }
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
