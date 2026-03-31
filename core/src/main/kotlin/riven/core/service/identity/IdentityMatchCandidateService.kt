package riven.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.identity.MatchSignalType
import riven.core.enums.identity.MatchSource
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
    private val normalizationService: IdentityNormalizationService,
    private val logger: KLogger,
) {

    companion object {
        /**
         * Maximum rows returned per individual query (trigram or exact-digits).
         * This is a per-query limit, not a global cap — PHONE signals may return up to
         * 2x this count before mergeCandidates deduplication.
         */
        private const val CANDIDATE_LIMIT = 100

        /**
         * Higher fetch limit for email domain candidates. The SQL query fetches same-domain
         * rows before Kotlin-side local-part scoring filters them down. A larger window
         * prevents the best local-part matches from being lost on large corporate domains.
         */
        private const val EMAIL_DOMAIN_FETCH_LIMIT = 500
    }

    // ------ Public operations ------

    /**
     * Returns candidate matches for a given trigger entity using signal-type-aware normalization
     * and pg_trgm blocking on IDENTIFIER attributes.
     *
     * For each IDENTIFIER-classified attribute of the trigger entity, dispatches to a per-type
     * orchestrator method via a when(signalType) expression. Each orchestrator runs all strategies
     * appropriate for its signal type and returns fully-processed candidates.
     *
     * Results are merged and deduplicated by (candidateEntityId, candidateAttributeId),
     * keeping the highest-scoring match per group.
     *
     * @param triggerEntityId the entity whose attributes are used as matching signals
     * @param workspaceId workspace scope — candidate entities must be in the same workspace
     * @return deduplicated list of candidate matches, at most [CANDIDATE_LIMIT] per query
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
            // signal_type from semantic metadata takes precedence over schema type derivation —
            // allows NAME/COMPANY signals even though SchemaType has no NAME/COMPANY variants.
            val rawSignalType = row[3]?.toString()
            val signalType = MatchSignalType.fromColumnValue(rawSignalType)
                ?: MatchSignalType.fromSchemaType(schemaType)
            val normalizedValue = normalizationService.normalize(rawValue, signalType)
            if (normalizedValue.isBlank()) {
                logger.debug { "Normalized value is blank for attribute $attributeId (type=$signalType) — skipping candidate scan" }
                continue
            }

            logger.debug { "Scanning candidates for attribute $attributeId (type=$signalType)" }

            val candidates = when (signalType) {
                MatchSignalType.NAME              -> findNameCandidates(triggerEntityId, workspaceId, normalizedValue, signalType)
                MatchSignalType.PHONE             -> findPhoneCandidates(triggerEntityId, workspaceId, normalizedValue, signalType)
                MatchSignalType.EMAIL             -> findEmailCandidates(triggerEntityId, workspaceId, normalizedValue, signalType)
                MatchSignalType.COMPANY,
                MatchSignalType.CUSTOM_IDENTIFIER -> findDefaultCandidates(triggerEntityId, workspaceId, normalizedValue, signalType)
            }
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
            val rawSignalType = row[3]?.toString()
            val signalType = MatchSignalType.fromColumnValue(rawSignalType)
                ?: MatchSignalType.fromSchemaType(schemaType)
            if (!result.containsKey(signalType)) {
                result[signalType] = normalizationService.normalize(rawValue, signalType)
            }
        }
        return result
    }

    // ------ Per-type orchestrator methods ------

    /**
     * Runs all candidate strategies for NAME signals:
     * 1. Trigram candidates, re-scored with token overlap coefficient
     * 2. Nickname expansion candidates
     * 3. Phonetic (dmetaphone) candidates
     *
     * @param triggerEntityId the entity to exclude from results
     * @param workspaceId workspace scope
     * @param normalizedValue normalized trigger name value (space-separated tokens)
     * @param signalType NAME signal type to assign to returned candidates
     */
    private fun findNameCandidates(
        triggerEntityId: UUID,
        workspaceId: UUID,
        normalizedValue: String,
        signalType: MatchSignalType,
    ): List<CandidateMatch> {
        val result = mutableListOf<CandidateMatch>()

        // Step 1: Trigram candidates, re-scored with token overlap
        val trigramCandidates = runCandidateQuery(triggerEntityId, workspaceId, normalizedValue, signalType)
        val reScored = trigramCandidates.map { candidate ->
            val tokenOverlap = TokenSimilarity.overlap(normalizedValue, candidate.candidateValue)
            val finalScore = maxOf(candidate.similarityScore, tokenOverlap)
            if (finalScore > candidate.similarityScore) candidate.copy(similarityScore = finalScore) else candidate
        }
        result.addAll(reScored)

        // Step 2: Nickname expansion candidates
        result.addAll(findNicknameCandidates(triggerEntityId, workspaceId, normalizedValue, signalType))

        // Step 3: Phonetic candidates
        result.addAll(findPhoneticCandidates(triggerEntityId, workspaceId, normalizedValue, signalType))

        return result
    }

    /**
     * Runs all candidate strategies for PHONE signals:
     * 1. Trigram candidates
     * 2. Exact-digits candidates (catches differently-formatted phone numbers)
     *
     * @param triggerEntityId the entity to exclude from results
     * @param workspaceId workspace scope
     * @param normalizedValue digits-only phone number (as returned by IdentityNormalizationService for PHONE)
     * @param signalType PHONE signal type to assign to returned candidates
     */
    private fun findPhoneCandidates(
        triggerEntityId: UUID,
        workspaceId: UUID,
        normalizedValue: String,
        signalType: MatchSignalType,
    ): List<CandidateMatch> {
        val result = mutableListOf<CandidateMatch>()
        result.addAll(runCandidateQuery(triggerEntityId, workspaceId, normalizedValue, signalType))
        result.addAll(findPhoneExactDigitsCandidates(triggerEntityId, workspaceId, normalizedValue))
        return result
    }

    /**
     * Runs all candidate strategies for EMAIL signals:
     * 1. Trigram candidates
     * 2. Corporate email domain candidates (only when the domain is not a free provider)
     *
     * @param triggerEntityId the entity to exclude from results
     * @param workspaceId workspace scope
     * @param normalizedValue normalized trigger email address
     * @param signalType EMAIL signal type to assign to returned candidates
     */
    private fun findEmailCandidates(
        triggerEntityId: UUID,
        workspaceId: UUID,
        normalizedValue: String,
        signalType: MatchSignalType,
    ): List<CandidateMatch> {
        val result = mutableListOf<CandidateMatch>()
        result.addAll(runCandidateQuery(triggerEntityId, workspaceId, normalizedValue, signalType))

        val domain = EmailMatcher.extractDomain(normalizedValue)
        if (domain != null && !EmailMatcher.isFreeEmailDomain(domain)) {
            result.addAll(findEmailDomainCandidates(triggerEntityId, workspaceId, normalizedValue, domain, signalType))
        }

        return result
    }

    /**
     * Runs the default candidate strategy (trigram only) for COMPANY and CUSTOM_IDENTIFIER signals.
     *
     * @param triggerEntityId the entity to exclude from results
     * @param workspaceId workspace scope
     * @param normalizedValue normalized trigger attribute value
     * @param signalType signal type to assign to returned candidates
     */
    private fun findDefaultCandidates(
        triggerEntityId: UUID,
        workspaceId: UUID,
        normalizedValue: String,
        signalType: MatchSignalType,
    ): List<CandidateMatch> {
        return runCandidateQuery(triggerEntityId, workspaceId, normalizedValue, signalType)
    }

    // ------ Private helpers ------

    /**
     * Queries IDENTIFIER-classified attributes for the given entity.
     *
     * Returns a list of rows, each as:
     * [attributeId (UUID/String), attrValue (String), schemaType (String), signalType (String?)].
     *
     * The `signal_type` column from semantic metadata takes precedence over the derived schema type
     * for signal routing. This allows NAME and COMPANY signals to be identified even though
     * [SchemaType] has no NAME/COMPANY variants — the workspace configures these via `signal_type`.
     */
    @Suppress("UNCHECKED_CAST")
    private fun queryTriggerIdentifierAttributes(entityId: UUID, workspaceId: UUID): List<Array<Any>> {
        val sql = """
            SELECT ea.attribute_id, ea.value->>'value' AS attr_value, ea.schema_type, sm.signal_type
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
     * Executes the pg_trgm candidate blocking query for a single trigger attribute value.
     *
     * Uses `%` for GIN index leverage and `similarity()` for score extraction.
     * DISTINCT ON has been intentionally removed so that a candidate entity with multiple
     * IDENTIFIER attributes can appear as multiple rows — one per matching attribute.
     */
    @Suppress("UNCHECKED_CAST")
    private fun runCandidateQuery(
        triggerEntityId: UUID,
        workspaceId: UUID,
        inputValue: String,
        signalType: MatchSignalType,
    ): List<CandidateMatch> {
        val sql = """
            SELECT ea.entity_id AS candidate_entity_id,
                   ea.attribute_id AS candidate_attribute_id,
                   ea.value->>'value' AS candidate_value,
                   similarity(ea.value->>'value', :inputValue) AS sim_score,
                   sm.signal_type AS candidate_signal_type,
                   ea.schema_type AS candidate_schema_type
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
            ORDER BY sim_score DESC
            LIMIT $CANDIDATE_LIMIT
        """.trimIndent()

        // NOTE: Candidates are not filtered by schema type — cross-type matches use the trigger's
        // signal type. candidateSignalType from sm.signal_type enables cross-type discounting downstream.
        val query = entityManager.createNativeQuery(sql)
        query.setParameter("workspaceId", workspaceId)
        query.setParameter("triggerEntityId", triggerEntityId)
        query.setParameter("inputValue", inputValue)

        val rows = query.resultList as List<Array<Any>>
        return rows.map { row ->
            val rawSignalType = row[4]?.toString()
            val rawSchemaType = row[5].toString()
            val candidateSignalType = MatchSignalType.fromColumnValue(rawSignalType)
                ?: MatchSignalType.fromSchemaType(SchemaType.valueOf(rawSchemaType))
            CandidateMatch(
                candidateEntityId = parseUuid(row[0]),
                candidateAttributeId = parseUuid(row[1]),
                candidateValue = row[2].toString(),
                signalType = signalType,
                similarityScore = (row[3] as Number).toDouble(),
                candidateSignalType = candidateSignalType,
                matchSource = MatchSource.TRIGRAM,
            )
        }
    }

    /**
     * Runs an exact-digits candidate lookup for PHONE signals.
     *
     * Strips all non-digit characters from stored values and compares against [normalizedDigits]
     * (already a digits-only string from [IdentityNormalizationService]). Returns sim_score=1.0
     * for all matches since equality is exact.
     *
     * This query runs in parallel with the trigram query for PHONE signals; [mergeCandidates]
     * deduplicates overlapping results by (entityId, attributeId), keeping the higher score.
     *
     * @param triggerEntityId the entity to exclude from results
     * @param workspaceId workspace scope
     * @param normalizedDigits digits-only phone number (as returned by IdentityNormalizationService for PHONE)
     */
    @Suppress("UNCHECKED_CAST")
    private fun findPhoneExactDigitsCandidates(
        triggerEntityId: UUID,
        workspaceId: UUID,
        normalizedDigits: String,
    ): List<CandidateMatch> {
        val sql = """
            SELECT ea.entity_id   AS candidate_entity_id,
                   ea.attribute_id AS candidate_attribute_id,
                   ea.value->>'value' AS candidate_value,
                   1.0            AS sim_score,
                   sm.signal_type AS candidate_signal_type,
                   ea.schema_type AS candidate_schema_type
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
              AND regexp_replace(ea.value->>'value', '[^0-9]', '', 'g') = :normalizedDigits
            LIMIT $CANDIDATE_LIMIT
        """.trimIndent()

        val query = entityManager.createNativeQuery(sql)
        query.setParameter("workspaceId", workspaceId)
        query.setParameter("triggerEntityId", triggerEntityId)
        query.setParameter("normalizedDigits", normalizedDigits)

        val rows = query.resultList as List<Array<Any>>
        return rows.map { row ->
            val rawSignalType = row[4]?.toString()
            val rawSchemaType = row[5].toString()
            val candidateSignalType = MatchSignalType.fromColumnValue(rawSignalType)
                ?: MatchSignalType.fromSchemaType(SchemaType.valueOf(rawSchemaType))
            CandidateMatch(
                candidateEntityId = parseUuid(row[0]),
                candidateAttributeId = parseUuid(row[1]),
                candidateValue = row[2].toString(),
                signalType = MatchSignalType.PHONE,
                similarityScore = (row[3] as Number).toDouble(),
                candidateSignalType = candidateSignalType,
                matchSource = MatchSource.EXACT_NORMALIZED,
            )
        }
    }

    /**
     * Runs a nickname expansion candidate lookup for NAME signals.
     *
     * Tokenizes [normalizedValue] by whitespace, expands each token via [NicknameExpander],
     * and queries the DB for attribute values matching any nickname variant using an IN-clause.
     * Also matches the full normalized value exactly (e.g. "bill smith" when the trigger is
     * "william smith") via an OR clause, since the IN-clause only covers individual tokens.
     * Returns an empty list immediately if no known nickname variants exist for any token —
     * this avoids an empty IN-clause SQL error.
     *
     * All matches receive a fixed similarity score of 0.95 and [MatchSource.NICKNAME].
     *
     * @param triggerEntityId the entity to exclude from results
     * @param workspaceId workspace scope
     * @param normalizedValue normalized trigger name value (space-separated tokens)
     * @param signalType the signal type to assign to returned candidates (NAME)
     */
    @Suppress("UNCHECKED_CAST")
    private fun findNicknameCandidates(
        triggerEntityId: UUID,
        workspaceId: UUID,
        normalizedValue: String,
        signalType: MatchSignalType,
    ): List<CandidateMatch> {
        // Tokenize trigger value and expand each token to all known nickname variants
        val tokens = normalizedValue.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val variants: Set<String> = tokens.flatMap { token ->
            NicknameExpander.expand(token) + token
        }.map { it.lowercase() }.toSet()

        // Empty variants means no known nicknames for any token — skip SQL to avoid empty IN-clause
        if (variants.isEmpty()) return emptyList()

        val sql = """
            SELECT ea.entity_id   AS candidate_entity_id,
                   ea.attribute_id AS candidate_attribute_id,
                   ea.value->>'value' AS candidate_value,
                   0.95            AS sim_score,
                   sm.signal_type  AS candidate_signal_type,
                   ea.schema_type  AS candidate_schema_type
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
              AND (LOWER(ea.value->>'value') IN (:variants) OR LOWER(ea.value->>'value') = :fullNormalizedValue)
            LIMIT $CANDIDATE_LIMIT
        """.trimIndent()

        val fullNormalizedValue = normalizedValue.lowercase()

        val query = entityManager.createNativeQuery(sql)
        query.setParameter("workspaceId", workspaceId)
        query.setParameter("triggerEntityId", triggerEntityId)
        query.setParameter("variants", variants)
        query.setParameter("fullNormalizedValue", fullNormalizedValue)

        val rows = query.resultList as List<Array<Any>>
        return rows.map { row ->
            val rawSignalType = row[4]?.toString()
            val rawSchemaType = row[5].toString()
            val candidateSignalType = MatchSignalType.fromColumnValue(rawSignalType)
                ?: MatchSignalType.fromSchemaType(SchemaType.valueOf(rawSchemaType))
            CandidateMatch(
                candidateEntityId = parseUuid(row[0]),
                candidateAttributeId = parseUuid(row[1]),
                candidateValue = row[2].toString(),
                signalType = signalType,
                similarityScore = (row[3] as Number).toDouble(),
                candidateSignalType = candidateSignalType,
                matchSource = MatchSource.NICKNAME,
            )
        }
    }

    /**
     * Computes dmetaphone phonetic codes for each token using PostgreSQL's fuzzystrmatch extension.
     *
     * Issues a JDBC scalar query `SELECT dmetaphone(:token)` per token rather than using a
     * Kotlin-side phonetic library — ensures the phonetic algorithm is identical on both sides
     * of the comparison (trigger tokens vs. stored DB values).
     *
     * Tokens shorter than 2 characters are filtered out before issuing queries; single-char
     * initials produce unreliable codes. Tokens that produce an empty code (e.g. all-vowel
     * strings) are excluded from the result set to prevent false-positive matches.
     *
     * @param tokens list of whitespace-split name tokens (caller must pre-filter non-empty)
     * @return set of non-empty phonetic codes; may be empty if no token produces a valid code
     */
    private fun computePhoneticCodes(tokens: List<String>): Set<String> {
        return tokens
            .filter { it.length >= 2 }
            .mapNotNull { token ->
                val q = entityManager.createNativeQuery("SELECT dmetaphone(:token)")
                q.setParameter("token", token)
                (q.singleResult as? String)?.takeIf { it.isNotEmpty() }
            }
            .toSet()
    }

    /**
     * Runs a phonetic candidate lookup for NAME signals using PostgreSQL's dmetaphone() function.
     *
     * Two-phase approach:
     * 1. Kotlin phase: tokenizes [normalizedValue], computes dmetaphone codes per token via JDBC
     *    scalar queries. Early return with empty list if no valid codes (prevents SQL error on
     *    empty collection parameter).
     * 2. SQL phase: uses EXISTS with regexp_split_to_table to tokenize stored DB values and
     *    compare per-token dmetaphone codes against the pre-computed trigger codes.
     *
     * All matches receive a fixed similarity score of 0.85 and [MatchSource.PHONETIC].
     * Results flow into [mergeCandidates] for deduplication; a phonetic match for the same
     * (entityId, attributeId) as a trigram match will be preferred by the tiebreaker ordering.
     *
     * @param triggerEntityId the entity to exclude from results
     * @param workspaceId workspace scope
     * @param normalizedValue normalized trigger name value (space-separated tokens)
     * @param signalType the signal type to assign to returned candidates (NAME)
     */
    private fun findPhoneticCandidates(
        triggerEntityId: UUID,
        workspaceId: UUID,
        normalizedValue: String,
        signalType: MatchSignalType,
    ): List<CandidateMatch> {
        val tokens = normalizedValue.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val phoneticCodes = computePhoneticCodes(tokens)
        if (phoneticCodes.isEmpty()) return emptyList()

        val rows = executePhoneticQuery(triggerEntityId, workspaceId, phoneticCodes)
        return mapPhoneticRows(rows, signalType)
    }

    /** Executes the dmetaphone phonetic candidate SQL query and returns raw result rows. */
    @Suppress("UNCHECKED_CAST")
    private fun executePhoneticQuery(
        triggerEntityId: UUID,
        workspaceId: UUID,
        phoneticCodes: Set<String>,
    ): List<Array<Any>> {
        val sql = """
            SELECT ea.entity_id   AS candidate_entity_id,
                   ea.attribute_id AS candidate_attribute_id,
                   ea.value->>'value' AS candidate_value,
                   0.85            AS sim_score,
                   sm.signal_type  AS candidate_signal_type,
                   ea.schema_type  AS candidate_schema_type
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
              AND EXISTS (
                  SELECT 1
                  FROM regexp_split_to_table(LOWER(ea.value->>'value'), '\s+') AS token
                  WHERE LENGTH(token) >= 2
                    AND dmetaphone(token) != ''
                    AND dmetaphone(token) = ANY(:phoneticCodes)
              )
            LIMIT $CANDIDATE_LIMIT
        """.trimIndent()

        val query = entityManager.createNativeQuery(sql)
        query.setParameter("workspaceId", workspaceId)
        query.setParameter("triggerEntityId", triggerEntityId)
        query.setParameter("phoneticCodes", phoneticCodes.toTypedArray())
        return query.resultList as List<Array<Any>>
    }

    /** Maps raw phonetic query result rows to [CandidateMatch] instances. */
    private fun mapPhoneticRows(rows: List<Array<Any>>, signalType: MatchSignalType): List<CandidateMatch> {
        return rows.map { row ->
            val rawSignalType = row[4]?.toString()
            val rawSchemaType = row[5].toString()
            val candidateSignalType = MatchSignalType.fromColumnValue(rawSignalType)
                ?: MatchSignalType.fromSchemaType(SchemaType.valueOf(rawSchemaType))
            CandidateMatch(
                candidateEntityId = parseUuid(row[0]),
                candidateAttributeId = parseUuid(row[1]),
                candidateValue = row[2].toString(),
                signalType = signalType,
                similarityScore = (row[3] as Number).toDouble(),
                candidateSignalType = candidateSignalType,
                matchSource = MatchSource.PHONETIC,
            )
        }
    }

    /**
     * Finds candidates sharing the same corporate email domain as the trigger entity.
     *
     * Two-phase approach:
     * 1. SQL phase: fetches all same-domain candidates in the workspace (no row limit) using
     *    `split_part` to extract and compare the domain from stored email values. Only the
     *    domain is checked at this stage — no similarity filtering in SQL.
     * 2. Kotlin phase: extracts the local part from each candidate email and computes overlap
     *    coefficient via [EmailMatcher.localPartSimilarity]. Candidates with overlap below 0.5
     *    are discarded; results are capped at [EMAIL_DOMAIN_FETCH_LIMIT] after scoring so the
     *    best local-part matches are always retained even for large corporate domains.
     *
     * The free-domain guard (skip gmail.com, yahoo.com, etc.) is enforced in [findEmailCandidates]
     * before calling this method — so this method assumes the domain is already known to
     * be corporate.
     *
     * @param triggerEntityId the entity to exclude from results
     * @param workspaceId workspace scope
     * @param normalizedEmail normalized trigger email address
     * @param domain the corporate domain extracted from [normalizedEmail]
     * @param signalType the signal type to assign to returned candidates (EMAIL)
     */
    @Suppress("UNCHECKED_CAST")
    private fun findEmailDomainCandidates(
        triggerEntityId: UUID,
        workspaceId: UUID,
        normalizedEmail: String,
        domain: String,
        signalType: MatchSignalType,
    ): List<CandidateMatch> {
        val sql = """
            SELECT ea.entity_id   AS candidate_entity_id,
                   ea.attribute_id AS candidate_attribute_id,
                   ea.value->>'value' AS candidate_value,
                   sm.signal_type  AS candidate_signal_type,
                   ea.schema_type  AS candidate_schema_type
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
              AND LOWER(split_part(ea.value->>'value', '@', 2)) = :domain
        """.trimIndent()

        val rows = executeEmailDomainQuery(sql, workspaceId, triggerEntityId, domain)
        return scoreAndFilterByLocalPart(rows, normalizedEmail, signalType)
    }

    /** Executes the email domain SQL query and returns raw result rows. */
    @Suppress("UNCHECKED_CAST")
    private fun executeEmailDomainQuery(
        sql: String,
        workspaceId: UUID,
        triggerEntityId: UUID,
        domain: String,
    ): List<Array<Any>> {
        val query = entityManager.createNativeQuery(sql)
        query.setParameter("workspaceId", workspaceId)
        query.setParameter("triggerEntityId", triggerEntityId)
        query.setParameter("domain", domain)
        return query.resultList as List<Array<Any>>
    }

    /**
     * Scores email domain candidate rows by local-part overlap and filters below threshold.
     *
     * Extracts the local part from each candidate email and computes overlap coefficient
     * via [EmailMatcher.localPartSimilarity]. Candidates with overlap below 0.5 are discarded.
     * Results are capped at [EMAIL_DOMAIN_FETCH_LIMIT] after scoring to bound output size.
     */
    private fun scoreAndFilterByLocalPart(
        rows: List<Array<Any>>,
        normalizedEmail: String,
        signalType: MatchSignalType,
    ): List<CandidateMatch> {
        val triggerLocal = EmailMatcher.extractLocal(normalizedEmail) ?: return emptyList()

        return rows.mapNotNull { row ->
            val candidateValue = row[2].toString()
            val candidateLocal = EmailMatcher.extractLocal(candidateValue) ?: return@mapNotNull null
            val overlap = EmailMatcher.localPartSimilarity(triggerLocal, candidateLocal)
            if (overlap < 0.5) return@mapNotNull null

            val rawSignalType = row[3]?.toString()
            val rawSchemaType = row[4].toString()
            val candidateSignalType = MatchSignalType.fromColumnValue(rawSignalType)
                ?: MatchSignalType.fromSchemaType(SchemaType.valueOf(rawSchemaType))
            CandidateMatch(
                candidateEntityId = parseUuid(row[0]),
                candidateAttributeId = parseUuid(row[1]),
                candidateValue = candidateValue,
                signalType = signalType,
                similarityScore = overlap,
                candidateSignalType = candidateSignalType,
                matchSource = MatchSource.EMAIL_DOMAIN,
            )
        }.take(EMAIL_DOMAIN_FETCH_LIMIT)
    }

    /**
     * Merges candidate rows by (candidateEntityId, candidateAttributeId), keeping the entry
     * with the highest similarity score per group.
     *
     * Grouping by attribute (not signal type) preserves multi-attribute matches — a candidate
     * entity with two distinct IDENTIFIER attributes produces two result rows. Deduplication
     * only collapses results for the same (entity, attribute) pair.
     *
     * When scores are equal, the tiebreaker prefers higher-fidelity match sources:
     * NICKNAME > PHONETIC > EMAIL_DOMAIN > EXACT_NORMALIZED > TRIGRAM
     */
    private fun mergeCandidates(candidates: List<CandidateMatch>): List<CandidateMatch> {
        return candidates
            .groupBy { it.candidateEntityId to it.candidateAttributeId }
            .values
            .map { group ->
                requireNotNull(
                    group.maxWithOrNull(
                        compareBy<CandidateMatch> { it.similarityScore }
                            .thenBy {
                                when (it.matchSource) {
                                    MatchSource.NICKNAME         -> 4
                                    MatchSource.PHONETIC         -> 3
                                    MatchSource.EMAIL_DOMAIN     -> 2
                                    MatchSource.EXACT_NORMALIZED -> 1
                                    MatchSource.TRIGRAM          -> 0
                                }
                            }
                    )
                ) {
                    "Candidate group was empty - groupBy should never produce an empty group"
                }
            }
    }

    /** Parses a UUID from either a [UUID] instance or its string representation. */
    private fun parseUuid(value: Any): UUID = when (value) {
        is UUID -> value
        else -> UUID.fromString(value.toString())
    }
}
