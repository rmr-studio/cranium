package riven.core.entity.integration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import riven.core.enums.integration.SyncStatus
import riven.core.service.util.factory.integration.IntegrationFactory
import java.util.*

class IntegrationSyncStateEntityTest {

    // ------ Default values ------

    @Test
    fun `default status is PENDING`() {
        val entity = IntegrationFactory.createIntegrationSyncState()
        assertEquals(SyncStatus.PENDING, entity.status)
    }

    @Test
    fun `default consecutiveFailureCount is 0`() {
        val entity = IntegrationFactory.createIntegrationSyncState()
        assertEquals(0, entity.consecutiveFailureCount)
    }

    @Test
    fun `default nullable fields are null`() {
        val entity = IntegrationFactory.createIntegrationSyncState()
        assertNull(entity.lastCursor)
        assertNull(entity.lastErrorMessage)
        assertNull(entity.lastRecordsSynced)
        assertNull(entity.lastRecordsFailed)
        assertNull(entity.lastPipelineStep)
        assertNull(entity.projectionResult)
    }

    // ------ toModel mapping ------

    @Test
    fun `toModel maps all fields correctly`() {
        val id = UUID.randomUUID()
        val connectionId = UUID.randomUUID()
        val entityTypeId = UUID.randomUUID()

        val entity = IntegrationFactory.createIntegrationSyncState(
            id = id,
            integrationConnectionId = connectionId,
            entityTypeId = entityTypeId,
            status = SyncStatus.SUCCESS,
            lastCursor = "cursor-abc",
            consecutiveFailureCount = 3,
            lastErrorMessage = "some error",
            lastRecordsSynced = 100,
            lastRecordsFailed = 2,
            lastPipelineStep = "PROJECTION",
            projectionResult = mapOf("created" to 5, "updated" to 3),
        )

        val model = entity.toModel()

        assertEquals(id, model.id)
        assertEquals(connectionId, model.integrationConnectionId)
        assertEquals(entityTypeId, model.entityTypeId)
        assertEquals(SyncStatus.SUCCESS, model.status)
        assertEquals("cursor-abc", model.lastCursor)
        assertEquals(3, model.consecutiveFailureCount)
        assertEquals("some error", model.lastErrorMessage)
        assertEquals(100, model.lastRecordsSynced)
        assertEquals(2, model.lastRecordsFailed)
        assertEquals("PROJECTION", model.lastPipelineStep)
        assertEquals(mapOf("created" to 5, "updated" to 3), model.projectionResult)
    }

    @Test
    fun `toModel throws IllegalArgumentException when id is null`() {
        val entity = IntegrationFactory.createIntegrationSyncState(id = null)
        assertThrows<IllegalArgumentException> { entity.toModel() }
    }
}
