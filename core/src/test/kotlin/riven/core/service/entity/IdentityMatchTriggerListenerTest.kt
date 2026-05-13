package cranium.core.service.entity

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Test
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import cranium.core.models.identity.IdentityMatchTriggerEvent
import cranium.core.service.identity.EntityTypeClassificationService
import cranium.core.service.identity.IdentityMatchQueueService
import java.util.UUID

/**
 * Unit tests for [IdentityMatchTriggerListener].
 *
 * This listener does not access the JWT principal, so @WithUserPersona is not needed.
 * KLogger is mocked via @MockitoBean to satisfy constructor injection.
 *
 * Tests cover all decision paths:
 * - Create event with IDENTIFIER attributes → enqueue
 * - Create event without IDENTIFIER attributes → skip
 * - Update with changed IDENTIFIER attributes → enqueue
 * - Update with unchanged IDENTIFIER attributes → skip
 * - Create with empty attribute values but IDENTIFIER attributes on type → enqueue (always trigger on create)
 */
@SpringBootTest(classes = [IdentityMatchTriggerListener::class])
class IdentityMatchTriggerListenerTest {

    @MockitoBean
    private lateinit var entityTypeClassificationService: EntityTypeClassificationService

    @MockitoBean
    private lateinit var identityMatchQueueService: IdentityMatchQueueService

    @MockitoBean
    private lateinit var logger: KLogger

    @Autowired
    private lateinit var listener: IdentityMatchTriggerListener

    private val entityId = UUID.fromString("11111111-0000-0000-0000-000000000001")
    private val workspaceId = UUID.fromString("22222222-0000-0000-0000-000000000002")
    private val entityTypeId = UUID.fromString("33333333-0000-0000-0000-000000000003")
    private val attrId = UUID.fromString("44444444-0000-0000-0000-000000000004")

    @Test
    fun `enqueues job on create when entity type has IDENTIFIER attributes`() {
        whenever(entityTypeClassificationService.hasIdentifierAttributes(entityTypeId)).thenReturn(true)

        val event = createEvent(
            isUpdate = false,
            previousIdentifiers = emptyMap(),
            newIdentifiers = mapOf(attrId to "test@example.com"),
        )

        listener.onEntitySaved(event)

        verify(identityMatchQueueService).enqueueIfNotPending(entityId, workspaceId)
    }

    @Test
    fun `skips enqueue when entity type has no IDENTIFIER attributes`() {
        whenever(entityTypeClassificationService.hasIdentifierAttributes(entityTypeId)).thenReturn(false)

        val event = createEvent(
            isUpdate = false,
            previousIdentifiers = emptyMap(),
            newIdentifiers = mapOf(attrId to "test@example.com"),
        )

        listener.onEntitySaved(event)

        verify(identityMatchQueueService, never()).enqueueIfNotPending(entityId, workspaceId)
    }

    @Test
    fun `enqueues job on update when IDENTIFIER attributes changed`() {
        whenever(entityTypeClassificationService.hasIdentifierAttributes(entityTypeId)).thenReturn(true)

        val event = createEvent(
            isUpdate = true,
            previousIdentifiers = mapOf(attrId to "old@example.com"),
            newIdentifiers = mapOf(attrId to "new@example.com"),
        )

        listener.onEntitySaved(event)

        verify(identityMatchQueueService).enqueueIfNotPending(entityId, workspaceId)
    }

    @Test
    fun `skips enqueue on update when IDENTIFIER attributes unchanged`() {
        whenever(entityTypeClassificationService.hasIdentifierAttributes(entityTypeId)).thenReturn(true)

        val event = createEvent(
            isUpdate = true,
            previousIdentifiers = mapOf(attrId to "same@example.com"),
            newIdentifiers = mapOf(attrId to "same@example.com"),
        )

        listener.onEntitySaved(event)

        verify(identityMatchQueueService, never()).enqueueIfNotPending(entityId, workspaceId)
    }

    @Test
    fun `enqueues job on create even with empty new attribute values when entity type has IDENTIFIER attributes`() {
        // Per CONTEXT.md: "On create, always trigger if the entity type has IDENTIFIER-classified attributes"
        // This covers the case where the entity was saved without identifier attribute values yet,
        // but the entity type is configured with IDENTIFIER attributes.
        whenever(entityTypeClassificationService.hasIdentifierAttributes(entityTypeId)).thenReturn(true)

        val event = createEvent(
            isUpdate = false,
            previousIdentifiers = emptyMap(),
            newIdentifiers = emptyMap(),
        )

        listener.onEntitySaved(event)

        verify(identityMatchQueueService).enqueueIfNotPending(entityId, workspaceId)
    }

    // ------ helpers ------

    private fun createEvent(
        isUpdate: Boolean,
        previousIdentifiers: Map<UUID, Any?>,
        newIdentifiers: Map<UUID, Any?>,
    ) = IdentityMatchTriggerEvent(
        entityId = entityId,
        workspaceId = workspaceId,
        entityTypeId = entityTypeId,
        isUpdate = isUpdate,
        previousIdentifierAttributes = previousIdentifiers,
        newIdentifierAttributes = newIdentifiers,
    )
}
