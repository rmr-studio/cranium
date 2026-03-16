package riven.core.service.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
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
import riven.core.entity.identity.MatchSuggestionEntity
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.identity.MatchSuggestionStatus
import riven.core.enums.util.OperationType
import riven.core.exceptions.ConflictException
import riven.core.exceptions.NotFoundException
import riven.core.models.common.json.JsonObject
import riven.core.repository.identity.MatchSuggestionRepository
import riven.core.service.activity.ActivityService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.factory.identity.IdentityFactory
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
     * When userId is null (system-generated from Temporal), activity is NOT logged
     * because ActivityService requires a non-null userId.
     */
    @Test
    fun `persistSuggestions skips activity logging when userId is null`() {
        val candidate = IdentityFactory.createScoredCandidate(compositeScore = 0.85)

        whenever(repository.findActiveSuggestion(any(), any(), any())).thenReturn(null)
        whenever(repository.findRejectedSuggestion(any(), any(), any())).thenReturn(null)
        whenever(repository.saveAndFlush(any())).thenAnswer { invocation ->
            buildSavedEntity(invocation.getArgument(0))
        }

        service.persistSuggestions(workspaceId, listOf(candidate), userId = null)

        verify(activityService, never()).logActivity(any(), any(), any(), any(), any(), any(), any(), any())
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
        whenever(repository.saveAndFlush(any())).thenThrow(DataIntegrityViolationException("duplicate key"))

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
     * the old row is soft-deleted and a fresh PENDING suggestion is created.
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
        whenever(repository.save(any<MatchSuggestionEntity>())).thenReturn(rejectedEntity)
        whenever(repository.saveAndFlush(any())).thenAnswer { invocation ->
            buildSavedEntity(invocation.getArgument(0))
        }
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(buildActivityLog())

        val count = service.persistSuggestions(workspaceId, listOf(candidate), userId)

        assertEquals(1, count)
        // The rejected row must have been soft-deleted
        verify(repository).save(argThat<MatchSuggestionEntity> { deleted })
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

    // ------ rejectSuggestion ------

    /**
     * rejectSuggestion on a PENDING suggestion sets status to REJECTED,
     * populates rejectionSignals with the current signals snapshot,
     * and sets resolvedBy and resolvedAt.
     */
    @Test
    fun `rejectSuggestion sets REJECTED status and writes rejectionSignals snapshot`() {
        val suggestionId = UUID.randomUUID()
        val signals = listOf(IdentityFactory.createMatchSignal().toMap())
        val entity = IdentityFactory.createMatchSuggestionEntity(
            workspaceId = workspaceId,
            status = MatchSuggestionStatus.PENDING,
            confidenceScore = BigDecimal("0.8500"),
            signals = signals,
        )
        val entityWithId = buildEntityWithId(entity, suggestionId)

        whenever(repository.findById(suggestionId)).thenReturn(Optional.of(entityWithId))
        whenever(repository.save(any<MatchSuggestionEntity>())).thenAnswer { invocation ->
            buildSavedEntity(invocation.getArgument(0))
        }
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(buildActivityLog())

        val result = service.rejectSuggestion(suggestionId, userId)

        assertEquals(MatchSuggestionStatus.REJECTED, result.status)
        assertNotNull(result.rejectionSignals)
        assertNotNull(result.resolvedBy)
        assertNotNull(result.resolvedAt)
        assertEquals(userId, result.resolvedBy)
    }

    /**
     * rejectSuggestion logs activity with MATCH_SUGGESTION / UPDATE and details containing "action"="rejected".
     */
    @Test
    fun `rejectSuggestion logs activity with UPDATE operation`() {
        val suggestionId = UUID.randomUUID()
        val entity = IdentityFactory.createMatchSuggestionEntity(
            workspaceId = workspaceId,
            status = MatchSuggestionStatus.PENDING,
        )
        val entityWithId = buildEntityWithId(entity, suggestionId)

        whenever(repository.findById(suggestionId)).thenReturn(Optional.of(entityWithId))
        whenever(repository.save(any<MatchSuggestionEntity>())).thenAnswer { invocation ->
            buildSavedEntity(invocation.getArgument(0))
        }
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(buildActivityLog())

        service.rejectSuggestion(suggestionId, userId)

        val detailsCaptor = argumentCaptor<JsonObject>()
        verify(activityService).logActivity(
            eq(Activity.MATCH_SUGGESTION),
            eq(OperationType.UPDATE),
            eq(userId),
            eq(workspaceId),
            eq(ApplicationEntityType.MATCH_SUGGESTION),
            eq(suggestionId),
            any(),
            detailsCaptor.capture(),
        )

        val details = detailsCaptor.firstValue
        assertEquals("rejected", details["action"])
        assertNotNull(details["sourceEntityId"])
        assertNotNull(details["targetEntityId"])
        assertNotNull(details["confidenceScore"])
    }

    /**
     * rejectSuggestion on an already-REJECTED suggestion throws ConflictException.
     */
    @Test
    fun `rejectSuggestion throws ConflictException when suggestion is already REJECTED`() {
        val suggestionId = UUID.randomUUID()
        val entity = IdentityFactory.createMatchSuggestionEntity(
            workspaceId = workspaceId,
            status = MatchSuggestionStatus.REJECTED,
        )
        val entityWithId = buildEntityWithId(entity, suggestionId)

        whenever(repository.findById(suggestionId)).thenReturn(Optional.of(entityWithId))

        assertThrows<ConflictException> {
            service.rejectSuggestion(suggestionId, userId)
        }
    }

    /**
     * rejectSuggestion on a non-existent ID throws NotFoundException.
     */
    @Test
    fun `rejectSuggestion throws NotFoundException when suggestion does not exist`() {
        val suggestionId = UUID.randomUUID()
        whenever(repository.findById(suggestionId)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            service.rejectSuggestion(suggestionId, userId)
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

    private fun buildActivityLog(): riven.core.models.activity.ActivityLog =
        riven.core.models.activity.ActivityLog(
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
