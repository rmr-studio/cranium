package riven.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.identity.MatchSignalType
import riven.core.models.identity.CandidateMatch
import java.util.UUID

/**
 * Finds candidate entities for identity matching using a two-phase pg_trgm similarity query.
 *
 * This service is called from a Temporal activity — workspace ID is always passed explicitly;
 * no Spring Security context is required.
 */
@Service
class IdentityMatchCandidateService(
    private val entityManager: EntityManager,
    private val logger: KLogger,
) {

    companion object {
        private const val CANDIDATE_LIMIT = 50
    }

    // ------ Public operations ------

    /**
     * Returns candidate matches for a given trigger entity using pg_trgm blocking on IDENTIFIER attributes.
     *
     * For each IDENTIFIER-classified attribute of the trigger entity, runs a two-phase native SQL
     * query that uses the `%` pg_trgm operator for GIN-index blocking, then filters by
     * `similarity()` score. Results are merged and deduplicated by (candidateEntityId, signalType),
     * keeping the highest-scoring match per group.
     *
     * @param triggerEntityId the entity whose attributes are used as matching signals
     * @param workspaceId workspace scope — candidate entities must be in the same workspace
     * @return deduplicated list of candidate matches, at most [CANDIDATE_LIMIT] per attribute
     */
    fun findCandidates(triggerEntityId: UUID, workspaceId: UUID): List<CandidateMatch> {
        val triggerAttrs = queryTriggerIdentifierAttributes(triggerEntityId, workspaceId)
        if (triggerAttrs.isEmpty()) {
            logger.debug { "No IDENTIFIER attributes found for entity $triggerEntityId — skipping candidate scan" }
            return emptyList()
        }

        val allCandidates = mutableListOf<CandidateMatch>()

        for (row in triggerAttrs) {
            val attributeId = parseUuid(row[0])
            val rawValue = row[1].toString()
            val schemaType = SchemaType.valueOf(row[2].toString())
            val signalType = MatchSignalType.fromSchemaType(schemaType)
            val normalizedValue = normalizeValue(rawValue)

            logger.debug { "Scanning candidates for attribute $attributeId (type=$signalType) value='$normalizedValue'" }

            val candidates = runCandidateQuery(triggerEntityId, workspaceId, normalizedValue, signalType)
            allCandidates.addAll(candidates)
        }

        return mergeCandidates(allCandidates)
    }

    /**
     * Returns a map of [MatchSignalType] to normalized attribute value for the given entity's
     * IDENTIFIER-classified attributes.
     *
     * Used by the scoring activity to supply source values when building [riven.core.models.identity.MatchSignal] breakdown.
     *
     * @param entityId the entity whose IDENTIFIER attributes to retrieve
     * @param workspaceId workspace scope
     * @return map from signal type to normalized string value; empty if entity has no IDENTIFIER attributes
     */
    fun getTriggerAttributes(entityId: UUID, workspaceId: UUID): Map<MatchSignalType, String> {
        val rows = queryTriggerIdentifierAttributes(entityId, workspaceId)
        if (rows.isEmpty()) return emptyMap()

        val result = mutableMapOf<MatchSignalType, String>()
        for (row in rows) {
            val rawValue = row[1].toString()
            val schemaType = SchemaType.valueOf(row[2].toString())
            val signalType = MatchSignalType.fromSchemaType(schemaType)
            if (!result.containsKey(signalType)) {
                result[signalType] = normalizeValue(rawValue)
            }
        }
        return result
    }

    // ------ Private helpers ------

    /**
     * Queries IDENTIFIER-classified attributes for the given entity.
     *
     * Returns a list of rows, each as [attributeId (UUID/String), attrValue (String), schemaType (String)].
     */
    @Suppress("UNCHECKED_CAST")
    private fun queryTriggerIdentifierAttributes(entityId: UUID, workspaceId: UUID): List<Array<Any>> {
        val sql = """
            SELECT ea.attribute_id, ea.value->>'value' AS attr_value, ea.schema_type
            FROM entity_attributes ea
            JOIN entity_type_semantic_metadata sm
                ON sm.workspace_id = :workspaceId
               AND sm.target_type = 'ATTRIBUTE'
               AND sm.target_id = ea.attribute_id
               AND sm.classification = 'IDENTIFIER'
               AND sm.deleted = false
            WHERE ea.entity_id = :entityId
              AND ea.workspace_id = :workspaceId
              AND ea.deleted = false
        """.trimIndent()

        val query = entityManager.createNativeQuery(sql)
        query.setParameter("entityId", entityId)
        query.setParameter("workspaceId", workspaceId)

        return query.resultList as List<Array<Any>>
    }

    /**
     * Executes the two-phase pg_trgm candidate blocking query for a single trigger attribute value.
     *
     * Uses `%` for GIN index leverage and `similarity()` for score extraction.
     */
    @Suppress("UNCHECKED_CAST")
    private fun runCandidateQuery(
        triggerEntityId: UUID,
        workspaceId: UUID,
        inputValue: String,
        signalType: MatchSignalType,
    ): List<CandidateMatch> {
        val sql = """
            SELECT candidate_entity_id, candidate_attribute_id, candidate_value, sim_score
            FROM (
                SELECT DISTINCT ON (ea.entity_id)
                    ea.entity_id AS candidate_entity_id,
                    ea.attribute_id AS candidate_attribute_id,
                    ea.value->>'value' AS candidate_value,
                    similarity(ea.value->>'value', :inputValue) AS sim_score
                FROM entity_attributes ea
                JOIN entity_type_semantic_metadata sm
                    ON sm.workspace_id = :workspaceId
                   AND sm.target_type = 'ATTRIBUTE'
                   AND sm.target_id = ea.attribute_id
                   AND sm.classification = 'IDENTIFIER'
                   AND sm.deleted = false
                WHERE ea.workspace_id = :workspaceId
                  AND ea.entity_id != :triggerEntityId
                  AND ea.deleted = false
                  AND (ea.value->>'value') % :inputValue
                  AND similarity(ea.value->>'value', :inputValue) > 0.3
                ORDER BY ea.entity_id, sim_score DESC
            ) ranked
            ORDER BY sim_score DESC
            LIMIT $CANDIDATE_LIMIT
        """.trimIndent()

        // NOTE: Candidates are not filtered by schema type — cross-type matches use the trigger's
        // signal type. See TODO-IR-008 for planned cross-type score discounting.
        val query = entityManager.createNativeQuery(sql)
        query.setParameter("workspaceId", workspaceId)
        query.setParameter("triggerEntityId", triggerEntityId)
        query.setParameter("inputValue", inputValue)

        val rows = query.resultList as List<Array<Any>>
        return rows.map { row ->
            CandidateMatch(
                candidateEntityId = parseUuid(row[0]),
                candidateAttributeId = parseUuid(row[1]),
                candidateValue = row[2].toString(),
                signalType = signalType,
                similarityScore = (row[3] as Number).toDouble(),
            )
        }
    }

    /**
     * Merges candidate rows by (candidateEntityId, signalType), keeping the entry with the highest similarity score.
     */
    private fun mergeCandidates(candidates: List<CandidateMatch>): List<CandidateMatch> {
        return candidates
            .groupBy { it.candidateEntityId to it.signalType }
            .values
            .map { group -> group.maxByOrNull { it.similarityScore }!! }
    }

    /** Trims whitespace and lowercases the value before querying. */
    private fun normalizeValue(value: String): String = value.trim().lowercase()

    /** Parses a UUID from either a [UUID] instance or its string representation. */
    private fun parseUuid(value: Any): UUID = when (value) {
        is UUID -> value
        else -> UUID.fromString(value.toString())
    }
}
