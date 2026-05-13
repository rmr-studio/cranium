package cranium.core.service.util.factory.identity

import cranium.core.entity.entity.EntityTypeSemanticMetadataEntity
import cranium.core.entity.identity.IdentityClusterEntity
import cranium.core.entity.identity.IdentityClusterMemberEntity
import cranium.core.entity.identity.MatchSuggestionEntity
import cranium.core.enums.entity.semantics.SemanticAttributeClassification
import cranium.core.enums.entity.semantics.SemanticMetadataTargetType
import cranium.core.enums.identity.MatchSignalType
import cranium.core.enums.identity.MatchSource
import cranium.core.enums.identity.MatchSuggestionStatus
import cranium.core.models.identity.CandidateMatch
import cranium.core.models.identity.MatchSignal
import cranium.core.models.identity.ScoredCandidate
import java.math.BigDecimal
import java.util.UUID

/**
 * Test factory for identity domain objects.
 *
 * All factory methods provide sensible defaults so callers only need to specify
 * the fields relevant to the scenario under test.
 */
object IdentityFactory {

    /**
     * Creates a [MatchSuggestionEntity] with canonical UUID ordering on source/target.
     *
     * If [sourceEntityId] and [targetEntityId] are both null, two random UUIDs are
     * generated and ordered so that source < target (matching the DB CHECK constraint).
     */
    fun createMatchSuggestionEntity(
        workspaceId: UUID = UUID.randomUUID(),
        sourceEntityId: UUID? = null,
        targetEntityId: UUID? = null,
        status: MatchSuggestionStatus = MatchSuggestionStatus.PENDING,
        confidenceScore: BigDecimal = BigDecimal("0.8500"),
        signals: List<Map<String, Any?>> = emptyList(),
    ): MatchSuggestionEntity {
        val a = sourceEntityId ?: UUID.randomUUID()
        val b = targetEntityId ?: UUID.randomUUID()
        val (source, target) = if (a < b) a to b else b to a
        return MatchSuggestionEntity(
            workspaceId = workspaceId,
            sourceEntityId = source,
            targetEntityId = target,
            status = status,
            confidenceScore = confidenceScore,
            signals = signals,
        )
    }

    /**
     * Creates a [MatchSignal] with EMAIL signal type and identical source/target values by default.
     */
    fun createMatchSignal(
        type: MatchSignalType = MatchSignalType.EMAIL,
        sourceValue: String = "test@example.com",
        targetValue: String = "test@example.com",
        similarity: Double = 0.95,
        weight: Double = 0.9,
        matchSource: MatchSource = MatchSource.TRIGRAM,
        crossType: Boolean = false,
    ): MatchSignal = MatchSignal(
        type = type,
        sourceValue = sourceValue,
        targetValue = targetValue,
        similarity = similarity,
        weight = weight,
        matchSource = matchSource,
        crossType = crossType,
    )

    /**
     * Creates a [CandidateMatch] test instance representing a candidate match row.
     *
     * The [matchSource] parameter controls the origin strategy (defaults to [MatchSource.TRIGRAM]
     * but can be set to any [MatchSource] — NICKNAME, PHONETIC, EMAIL_DOMAIN, EXACT_NORMALIZED).
     */
    fun createCandidateMatch(
        candidateEntityId: UUID = UUID.randomUUID(),
        candidateAttributeId: UUID = UUID.randomUUID(),
        candidateValue: String = "test@example.com",
        signalType: MatchSignalType = MatchSignalType.EMAIL,
        similarityScore: Double = 0.85,
        candidateSignalType: MatchSignalType? = signalType,
        matchSource: MatchSource = MatchSource.TRIGRAM,
    ): CandidateMatch = CandidateMatch(
        candidateEntityId = candidateEntityId,
        candidateAttributeId = candidateAttributeId,
        candidateValue = candidateValue,
        signalType = signalType,
        similarityScore = similarityScore,
        candidateSignalType = candidateSignalType,
        matchSource = matchSource,
    )

    /**
     * Creates a [ScoredCandidate] representing a fully scored entity pair.
     */
    fun createScoredCandidate(
        sourceEntityId: UUID = UUID.randomUUID(),
        targetEntityId: UUID = UUID.randomUUID(),
        compositeScore: Double = 0.85,
        signals: List<MatchSignal> = listOf(createMatchSignal()),
    ): ScoredCandidate = ScoredCandidate(
        sourceEntityId = sourceEntityId,
        targetEntityId = targetEntityId,
        compositeScore = compositeScore,
        signals = signals,
    )

    /**
     * Creates an [EntityTypeSemanticMetadataEntity] with sensible defaults.
     *
     * Useful for testing semantic classification lookups without constructing
     * the entity inline in each test.
     */
    fun createEntityTypeSemanticMetadataEntity(
        id: UUID? = UUID.randomUUID(),
        workspaceId: UUID = UUID.randomUUID(),
        entityTypeId: UUID = UUID.randomUUID(),
        targetType: SemanticMetadataTargetType = SemanticMetadataTargetType.ATTRIBUTE,
        targetId: UUID = UUID.randomUUID(),
        classification: SemanticAttributeClassification? = SemanticAttributeClassification.IDENTIFIER,
        signalType: MatchSignalType? = null,
        definition: String? = null,
        tags: List<String> = emptyList(),
    ): EntityTypeSemanticMetadataEntity = EntityTypeSemanticMetadataEntity(
        id = id,
        workspaceId = workspaceId,
        entityTypeId = entityTypeId,
        targetType = targetType,
        targetId = targetId,
        classification = classification,
        signalType = signalType,
        definition = definition,
        tags = tags,
    )

    /**
     * Creates an [IdentityClusterEntity] with sensible defaults.
     */
    fun createIdentityClusterEntity(
        workspaceId: UUID = UUID.randomUUID(),
        name: String? = "Test Cluster",
        memberCount: Int = 1,
    ): IdentityClusterEntity = IdentityClusterEntity(
        workspaceId = workspaceId,
        name = name,
        memberCount = memberCount,
    )

    /**
     * Creates an [IdentityClusterMemberEntity] with sensible defaults.
     */
    fun createIdentityClusterMemberEntity(
        clusterId: UUID = UUID.randomUUID(),
        entityId: UUID = UUID.randomUUID(),
        joinedBy: UUID? = null,
    ): IdentityClusterMemberEntity = IdentityClusterMemberEntity(
        clusterId = clusterId,
        entityId = entityId,
        joinedBy = joinedBy,
    )
}
