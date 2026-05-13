package cranium.core.service.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import cranium.core.entity.identity.MatchSuggestionEntity
import cranium.core.enums.activity.Activity
import cranium.core.enums.core.ApplicationEntityType
import cranium.core.enums.identity.MatchSuggestionStatus
import cranium.core.enums.util.OperationType
import cranium.core.models.common.json.JsonObject
import cranium.core.repository.identity.IdentityClusterMemberRepository
import cranium.core.repository.identity.IdentityClusterRepository
import cranium.core.repository.identity.MatchSuggestionRepository
import cranium.core.service.activity.ActivityService
import cranium.core.service.util.BaseServiceTest
import cranium.core.service.util.WithUserPersona
import cranium.core.service.util.WorkspaceRole
import cranium.core.service.util.factory.identity.IdentityFactory
import cranium.core.enums.workspace.WorkspaceRoles
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for [IdentityMatchSuggestionService].
 *
 * This service has no @PreAuthorize and is not called from JWT-authenticated contexts.
 * @WithUserPersona is inherited from BaseServiceTest but has no effect on this service's
 * behaviour — it is included only to provide the mock KLogger bean required by constructor injection.
 */
@SpringBootTest(
    classes = [
        IdentityMatchSuggestionService::class,
        IdentityMatchSuggestionServiceTest.TestConfig::class,
    ]
)
class IdentityMatchSuggestionServiceTest : BaseServiceTest() {

    @Configuration
    class TestConfig

    @MockitoBean
    private lateinit var repository: MatchSuggestionRepository

    @MockitoBean
    private lateinit var memberRepository: IdentityClusterMemberRepository

    @MockitoBean
    private lateinit var clusterRepository: IdentityClusterRepository

    @MockitoBean
    private lateinit var activityService: ActivityService

    @Autowired
    private lateinit var service: IdentityMatchSuggestionService

    // ------ persistSuggestions — happy path ------

    /**
     * A ScoredCandidate with compositeScore >= 0.5 should create a PENDING suggestion
     * with canonical UUID ordering enforced and signals mapped via toMap().
     */
    @Test
    fun `persistSuggestions with above-threshold candidate creates PENDING suggestion`() {
        val candidate = IdentityFactory.createScoredCandidate(compositeScore = 0.85)

        whenever(repository.findActiveSuggestion(any(), any(), any())).thenReturn(null)
        whenever(repository.findRejectedSuggestion(any(), any(), any())).thenReturn(null)
        whenever(repository.saveAndFlush(any())).thenAnswer { invocation ->
            buildSavedEntity(invocation.getArgument(0))
        }
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(buildActivityLog())

        val count = service.persistSuggestions(workspaceId, listOf(candidate), userId)

        assertEquals(1, count)
        verify(repository).saveAndFlush(any())
    }

    /**
     * Canonical ordering is enforced: when sourceEntityId > targetEntityId in the input,
     * the entity is built with them swapped so source < target.
     */
    @Test
    fun `persistSuggestions applies canonical UUID ordering before saving`() {
        val higherUuid = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")
        val lowerUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
        // Pass higher as source, lower as target — should be swapped
        val candidate = IdentityFactory.createScoredCandidate(
            sourceEntityId = higherUuid,
            targetEntityId = lowerUuid,
            compositeScore = 0.9,
        )

        val captor = argumentCaptor<MatchSuggestionEntity>()
        whenever(repository.findActiveSuggestion(any(), any(), any())).thenReturn(null)
        whenever(repository.findRejectedSuggestion(any(), any(), any())).thenReturn(null)
        whenever(repository.saveAndFlush(captor.capture())).thenAnswer { invocation ->
            buildSavedEntity(invocation.getArgument(0))
        }
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(buildActivityLog())

        service.persistSuggestions(workspaceId, listOf(candidate), userId)

        val saved = captor.firstValue
        assert(saved.sourceEntityId < saved.targetEntityId) {
            "Expected source ${saved.sourceEntityId} < target ${saved.targetEntityId}"
        }
    }

    /**
     * Signals are converted to List<Map<String, Any?>> via MatchSignal.toMap().
     */
    @Test
    fun `persistSuggestions converts signals via toMap`() {
        val signal = IdentityFactory.createMatchSignal()
        val candidate = IdentityFactory.createScoredCandidate(
            compositeScore = 0.8,
            signals = listOf(signal),
        )
        val captor = argumentCaptor<MatchSuggestionEntity>()

        whenever(repository.findActiveSuggestion(any(), any(), any())).thenReturn(null)
        whenever(repository.findRejectedSuggestion(any(), any(), any())).thenReturn(null)
        whenever(repository.saveAndFlush(captor.capture())).thenAnswer { invocation ->
            buildSavedEntity(invocation.getArgument(0))
        }
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(buildActivityLog())

        service.persistSuggestions(workspaceId, listOf(candidate), userId)

        val saved = captor.firstValue
        assertEquals(1, saved.signals.size)
        assertEquals(signal.type.name, saved.signals[0]["type"])
        assertEquals(signal.sourceValue, saved.signals[0]["sourceValue"])
        assertEquals(signal.targetValue, saved.signals[0]["targetValue"])
    }

    /**
     * Activity is logged with MATCH_SUGGESTION / CREATE when a suggestion is created.
     * Details must include sourceEntityId, targetEntityId, confidenceScore, signalCount.
     */
    @Test
    fun `persistSuggestions logs activity on successful creation`() {
        val candidate = IdentityFactory.createScoredCandidate(compositeScore = 0.85)

        whenever(repository.findActiveSuggestion(any(), any(), any())).thenReturn(null)
        whenever(repository.findRejectedSuggestion(any(), any(), any())).thenReturn(null)
        whenever(repository.saveAndFlush(any())).thenAnswer { invocation ->
            buildSavedEntity(invocation.getArgument(0))
        }
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(buildActivityLog())

        service.persistSuggestions(workspaceId, listOf(candidate), userId)

        val detailsCaptor = argumentCaptor<JsonObject>()
        verify(activityService).logActivity(
            eq(Activity.MATCH_SUGGESTION),
            eq(OperationType.CREATE),
            eq(userId),
            eq(workspaceId),
            eq(ApplicationEntityType.MATCH_SUGGESTION),
            any(),
            any(),
            detailsCaptor.capture(),
        )
        val details = detailsCaptor.firstValue
        assertNotNull(details["sourceEntityId"])
        assertNotNull(details["targetEntityId"])
        assertNotNull(details["confidenceScore"])
        assertNotNull(details["signalCount"])
    }

    /**
     * When userId is null (system-triggered Temporal activity), activity is logged
     * using the SYSTEM_ACTOR_ID sentinel UUID.
     */
    @Test
    fun `persistSuggestions logs activity with SYSTEM_ACTOR_ID when userId is null`() {
        val candidate = IdentityFactory.createScoredCandidate(compositeScore = 0.85)

        whenever(repository.findActiveSuggestion(any(), any(), any())).thenReturn(null)
        whenever(repository.findRejectedSuggestion(any(), any(), any())).thenReturn(null)
        whenever(repository.saveAndFlush(any())).thenAnswer { invocation ->
            buildSavedEntity(invocation.getArgument(0))
        }
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(buildActivityLog())

        service.persistSuggestions(workspaceId, listOf(candidate), userId = null)

        verify(activityService).logActivity(
            any(), any(), eq(IdentityMatchSuggestionService.SYSTEM_ACTOR_ID), any(), any(), any(), any(), any()
        )
    }

    // ------ persistSuggestions — idempotency ------

    /**
     * When saveAndFlush throws DataIntegrityViolationException (duplicate constraint),
     * the exception is caught silently and returns 0 persisted (idempotent).
     */
    @Test
    fun `persistSuggestions catches DataIntegrityViolationException and returns 0`() {
        val candidate = IdentityFactory.createScoredCandidate(compositeScore = 0.85)

        whenever(repository.findActiveSuggestion(any(), any(), any())).thenReturn(null)
        whenever(repository.findRejectedSuggestion(any(), any(), any())).thenReturn(null)
        whenever(repository.saveAndFlush(any())).thenThrow(
            DataIntegrityViolationException("uq_match_suggestions_pair: duplicate key value violates unique constraint")
        )

        val count = service.persistSuggestions(workspaceId, listOf(candidate), userId)

        assertEquals(0, count)
        verify(activityService, never()).logActivity(any(), any(), any(), any(), any(), any(), any(), any())
    }

    /**
     * When findActiveSuggestion returns a non-null entity, the pair is skipped (active exists).
     */
    @Test
    fun `persistSuggestions skips when active suggestion already exists`() {
        val candidate = IdentityFactory.createScoredCandidate(compositeScore = 0.85)
        val existing = IdentityFactory.createMatchSuggestionEntity(workspaceId = workspaceId)

        whenever(repository.findActiveSuggestion(any(), any(), any())).thenReturn(existing)

        val count = service.persistSuggestions(workspaceId, listOf(candidate), userId)

        assertEquals(0, count)
        verify(repository, never()).saveAndFlush(any())
    }

    // ------ persistSuggestions — re-suggestion ------

    /**
     * When a REJECTED suggestion exists and the new score is strictly higher,
     * a fresh PENDING suggestion is created. The rejected row is already soft-deleted
     * (findRejectedSuggestion queries WHERE deleted = true) so no additional soft-delete is needed.
     */
    @Test
    fun `persistSuggestions re-suggests when new score is higher than rejected`() {
        val rejectedEntity = IdentityFactory.createMatchSuggestionEntity(
            workspaceId = workspaceId,
            status = MatchSuggestionStatus.REJECTED,
            confidenceScore = BigDecimal("0.6000"),
        )

        // New candidate has higher score — uses same IDs (already canonical from factory)
        val candidate = IdentityFactory.createScoredCandidate(
            sourceEntityId = rejectedEntity.sourceEntityId,
            targetEntityId = rejectedEntity.targetEntityId,
            compositeScore = 0.85, // higher than 0.60
        )

        whenever(repository.findActiveSuggestion(any(), any(), any())).thenReturn(null)
        whenever(repository.findRejectedSuggestion(any(), any(), any())).thenReturn(rejectedEntity)
        whenever(repository.saveAndFlush(any())).thenAnswer { invocation ->
            buildSavedEntity(invocation.getArgument(0))
        }
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(buildActivityLog())

        val count = service.persistSuggestions(workspaceId, listOf(candidate), userId)

        assertEquals(1, count)
        // New suggestion persisted via saveAndFlush, no save() for softDeleteRejected needed
        verify(repository).saveAndFlush(any())
    }

    /**
     * When a REJECTED suggestion exists but the new score is equal-or-lower,
     * no re-suggestion is performed (returns 0).
     */
    @Test
    fun `persistSuggestions skips re-suggestion when new score is not higher than rejected`() {
        val rejectedEntity = IdentityFactory.createMatchSuggestionEntity(
            workspaceId = workspaceId,
            status = MatchSuggestionStatus.REJECTED,
            confidenceScore = BigDecimal("0.8500"),
        )

        // New candidate has same score (not higher)
        val candidate = IdentityFactory.createScoredCandidate(
            sourceEntityId = rejectedEntity.sourceEntityId,
            targetEntityId = rejectedEntity.targetEntityId,
            compositeScore = 0.85,
        )

        whenever(repository.findActiveSuggestion(any(), any(), any())).thenReturn(null)
        whenever(repository.findRejectedSuggestion(any(), any(), any())).thenReturn(rejectedEntity)

        val count = service.persistSuggestions(workspaceId, listOf(candidate), userId)

        assertEquals(0, count)
        verify(repository, never()).saveAndFlush(any())
    }

    /**
     * Multiple candidates are processed and the count reflects only successfully persisted ones.
     */
    @Test
    fun `persistSuggestions returns count of successfully persisted suggestions`() {
        val c1 = IdentityFactory.createScoredCandidate(compositeScore = 0.85)
        val c2 = IdentityFactory.createScoredCandidate(compositeScore = 0.75)

        whenever(repository.findActiveSuggestion(any(), any(), any())).thenReturn(null)
        whenever(repository.findRejectedSuggestion(any(), any(), any())).thenReturn(null)
        whenever(repository.saveAndFlush(any())).thenAnswer { invocation ->
            buildSavedEntity(invocation.getArgument(0))
        }
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(buildActivityLog())

        val count = service.persistSuggestions(workspaceId, listOf(c1, c2), userId)

        assertEquals(2, count)
    }

    // ------ persistSuggestions — cluster awareness ------

    /**
     * Tests for the same-cluster guard that prevents redundant suggestions when both
     * entities are already members of the same identity cluster.
     *
     * JUnit 5 @Nested classes do not inherit the outer class's @WithUserPersona, so it is
     * re-applied here since tests in this class call code that reads the JWT principal.
     */
    @Nested
    @WithUserPersona(
        userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
        email = "test@example.com",
        displayName = "Test User",
        roles = [
            WorkspaceRole(
                workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
                role = WorkspaceRoles.ADMIN
            )
        ]
    )
    inner class ClusterAwareness {

        /**
         * When both source and target entities are members of the SAME cluster,
         * persistSuggestions skips the candidate and returns 0.
         *
         * This prevents re-suggesting pairs that have already been confirmed and merged
         * into the same identity cluster.
         */
        @Test
        fun `persistSuggestions skips candidate when both entities are in the same cluster`() {
            val sharedClusterId = UUID.randomUUID()
            val sourceId = UUID.randomUUID()
            val targetId = UUID.randomUUID()
            val candidate = IdentityFactory.createScoredCandidate(
                sourceEntityId = sourceId,
                targetEntityId = targetId,
                compositeScore = 0.9,
            )

            val sourceMember = IdentityFactory.createIdentityClusterMemberEntity(
                clusterId = sharedClusterId,
                entityId = sourceId,
            )
            val targetMember = IdentityFactory.createIdentityClusterMemberEntity(
                clusterId = sharedClusterId,
                entityId = targetId,
            )

            whenever(memberRepository.findByEntityId(sourceId)).thenReturn(sourceMember)
            whenever(memberRepository.findByEntityId(targetId)).thenReturn(targetMember)
            whenever(clusterRepository.findById(sharedClusterId)).thenReturn(
                Optional.of(IdentityFactory.createIdentityClusterEntity().copy(id = sharedClusterId))
            )

            val count = service.persistSuggestions(workspaceId, listOf(candidate), userId)

            assertEquals(0, count)
            verify(repository, never()).saveAndFlush(any())
        }

        /**
         * When source and target entities are members of DIFFERENT clusters,
         * persistSuggestions proceeds normally and creates a suggestion.
         */
        @Test
        fun `persistSuggestions proceeds when entities are in different clusters`() {
            val sourceId = UUID.randomUUID()
            val targetId = UUID.randomUUID()
            val candidate = IdentityFactory.createScoredCandidate(
                sourceEntityId = sourceId,
                targetEntityId = targetId,
                compositeScore = 0.9,
            )

            val sourceMember = IdentityFactory.createIdentityClusterMemberEntity(
                clusterId = UUID.randomUUID(),
                entityId = sourceId,
            )
            val targetMember = IdentityFactory.createIdentityClusterMemberEntity(
                clusterId = UUID.randomUUID(), // different cluster
                entityId = targetId,
            )

            whenever(memberRepository.findByEntityId(sourceId)).thenReturn(sourceMember)
            whenever(memberRepository.findByEntityId(targetId)).thenReturn(targetMember)
            whenever(repository.findActiveSuggestion(any(), any(), any())).thenReturn(null)
            whenever(repository.findRejectedSuggestion(any(), any(), any())).thenReturn(null)
            whenever(repository.saveAndFlush(any())).thenAnswer { invocation ->
                buildSavedEntity(invocation.getArgument(0))
            }
            whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(buildActivityLog())

            val count = service.persistSuggestions(workspaceId, listOf(candidate), userId)

            assertEquals(1, count)
            verify(repository).saveAndFlush(any())
        }

        /**
         * When neither entity belongs to any cluster (findByEntityId returns null for both),
         * persistSuggestions proceeds normally and creates a suggestion.
         */
        @Test
        fun `persistSuggestions proceeds when neither entity is in any cluster`() {
            val candidate = IdentityFactory.createScoredCandidate(compositeScore = 0.85)

            whenever(memberRepository.findByEntityId(any())).thenReturn(null)
            whenever(repository.findActiveSuggestion(any(), any(), any())).thenReturn(null)
            whenever(repository.findRejectedSuggestion(any(), any(), any())).thenReturn(null)
            whenever(repository.saveAndFlush(any())).thenAnswer { invocation ->
                buildSavedEntity(invocation.getArgument(0))
            }
            whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(buildActivityLog())

            val count = service.persistSuggestions(workspaceId, listOf(candidate), userId)

            assertEquals(1, count)
            verify(repository).saveAndFlush(any())
        }
    }

    // ------ Test helpers ------

    /**
     * Builds a [MatchSuggestionEntity] with a populated ID and audit timestamps so toModel() succeeds.
     * Preserves the entity's existing ID if it already has one.
     */
    private fun buildSavedEntity(
        entity: MatchSuggestionEntity,
        id: UUID = entity.id ?: UUID.randomUUID(),
    ): MatchSuggestionEntity = buildEntityWithId(entity, id)

    private fun buildEntityWithId(
        entity: MatchSuggestionEntity,
        id: UUID,
    ): MatchSuggestionEntity {
        val result = entity.copy(id = id)
        val now = ZonedDateTime.now()
        setAuditField(result, "createdAt", now)
        setAuditField(result, "updatedAt", now)
        return result
    }

    private fun setAuditField(entity: Any, fieldName: String, value: Any?) {
        var clazz: Class<*>? = entity.javaClass
        while (clazz != null) {
            try {
                val field = clazz.getDeclaredField(fieldName)
                field.isAccessible = true
                field.set(entity, value)
                return
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
    }

    private fun buildActivityLog(): cranium.core.models.activity.ActivityLog =
        cranium.core.models.activity.ActivityLog(
            id = UUID.randomUUID(),
            userId = userId,
            workspaceId = workspaceId,
            activity = Activity.MATCH_SUGGESTION,
            operation = OperationType.CREATE,
            entityType = ApplicationEntityType.MATCH_SUGGESTION,
            entityId = UUID.randomUUID(),
            timestamp = ZonedDateTime.now(),
            details = emptyMap(),
        )
}
