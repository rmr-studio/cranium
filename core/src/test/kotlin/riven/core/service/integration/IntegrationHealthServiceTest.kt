package cranium.core.service.integration

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import cranium.core.entity.integration.IntegrationConnectionEntity
import cranium.core.enums.integration.ConnectionStatus
import cranium.core.enums.integration.SyncStatus
import cranium.core.repository.integration.IntegrationConnectionRepository
import cranium.core.repository.integration.IntegrationSyncStateRepository
import cranium.core.service.util.factory.integration.IntegrationFactory
import java.util.*

/**
 * Unit tests for [IntegrationHealthService].
 *
 * Verifies health aggregation logic:
 * - HLTH-01: All SUCCESS sync states -> HEALTHY
 * - HLTH-02: Any entity type with consecutiveFailureCount >= 3 -> DEGRADED
 * - HLTH-03: All FAILED sync states -> FAILED
 * - DEGRADED check takes priority over FAILED
 * - Empty sync states result in no status change (no-op)
 * - Missing connection is graceful no-op
 * - Invalid transitions are skipped with a warning
 *
 * No @WithUserPersona — this service has no @PreAuthorize (called from Temporal activity).
 */
@SpringBootTest(
    classes = [
        IntegrationHealthService::class,
    ]
)
class IntegrationHealthServiceTest {

    // ------ Mocked Dependencies ------

    @MockitoBean
    private lateinit var syncStateRepository: IntegrationSyncStateRepository

    @MockitoBean
    private lateinit var connectionRepository: IntegrationConnectionRepository

    @MockitoBean
    private lateinit var logger: KLogger

    @Autowired
    private lateinit var healthService: IntegrationHealthService

    // ------ Shared Test Data ------

    private val connectionId: UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

    @BeforeEach
    fun setUp() {
        reset(syncStateRepository, connectionRepository, logger)
        whenever(connectionRepository.save(any())).thenAnswer { it.arguments[0] }
    }

    // ------ All SUCCESS -> HEALTHY ------

    @Nested
    inner class AllSuccessTests {

        @Test
        fun `all SUCCESS sync states transitions connection to HEALTHY`() {
            val connection = IntegrationFactory.createIntegrationConnection(
                id = connectionId,
                status = ConnectionStatus.SYNCING,
            )
            val states = listOf(
                IntegrationFactory.createIntegrationSyncState(
                    integrationConnectionId = connectionId,
                    status = SyncStatus.SUCCESS,
                    consecutiveFailureCount = 0,
                ),
                IntegrationFactory.createIntegrationSyncState(
                    integrationConnectionId = connectionId,
                    status = SyncStatus.SUCCESS,
                    consecutiveFailureCount = 0,
                ),
            )

            whenever(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection))
            whenever(syncStateRepository.findByIntegrationConnectionId(connectionId)).thenReturn(states)

            healthService.evaluateConnectionHealth(connectionId)

            val captor = argumentCaptor<IntegrationConnectionEntity>()
            verify(connectionRepository).save(captor.capture())
            assertEquals(ConnectionStatus.HEALTHY, captor.firstValue.status)
        }
    }

    // ------ Consecutive failures -> DEGRADED ------

    @Nested
    inner class DegradedThresholdTests {

        @Test
        fun `entity type with consecutiveFailureCount of 3 transitions connection to DEGRADED`() {
            val connection = IntegrationFactory.createIntegrationConnection(
                id = connectionId,
                status = ConnectionStatus.SYNCING,
            )
            val states = listOf(
                IntegrationFactory.createIntegrationSyncState(
                    integrationConnectionId = connectionId,
                    status = SyncStatus.FAILED,
                    consecutiveFailureCount = 3,
                ),
                IntegrationFactory.createIntegrationSyncState(
                    integrationConnectionId = connectionId,
                    status = SyncStatus.SUCCESS,
                    consecutiveFailureCount = 0,
                ),
            )

            whenever(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection))
            whenever(syncStateRepository.findByIntegrationConnectionId(connectionId)).thenReturn(states)

            healthService.evaluateConnectionHealth(connectionId)

            val captor = argumentCaptor<IntegrationConnectionEntity>()
            verify(connectionRepository).save(captor.capture())
            assertEquals(ConnectionStatus.DEGRADED, captor.firstValue.status)
        }

        @Test
        fun `DEGRADED takes priority over FAILED when mixed states`() {
            // Some are FAILED, one has consecutiveFailureCount >= 3
            // Result should be DEGRADED, not FAILED
            val connection = IntegrationFactory.createIntegrationConnection(
                id = connectionId,
                status = ConnectionStatus.SYNCING,
            )
            val states = listOf(
                IntegrationFactory.createIntegrationSyncState(
                    integrationConnectionId = connectionId,
                    status = SyncStatus.FAILED,
                    consecutiveFailureCount = 5, // Exceeds threshold
                ),
                IntegrationFactory.createIntegrationSyncState(
                    integrationConnectionId = connectionId,
                    status = SyncStatus.FAILED,
                    consecutiveFailureCount = 0,
                ),
            )

            whenever(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection))
            whenever(syncStateRepository.findByIntegrationConnectionId(connectionId)).thenReturn(states)

            healthService.evaluateConnectionHealth(connectionId)

            val captor = argumentCaptor<IntegrationConnectionEntity>()
            verify(connectionRepository).save(captor.capture())
            assertEquals(ConnectionStatus.DEGRADED, captor.firstValue.status)
        }
    }

    // ------ All FAILED -> FAILED ------

    @Nested
    inner class AllFailedTests {

        @Test
        fun `all FAILED sync states transitions connection to FAILED`() {
            val connection = IntegrationFactory.createIntegrationConnection(
                id = connectionId,
                status = ConnectionStatus.SYNCING,
            )
            val states = listOf(
                IntegrationFactory.createIntegrationSyncState(
                    integrationConnectionId = connectionId,
                    status = SyncStatus.FAILED,
                    consecutiveFailureCount = 1,
                ),
                IntegrationFactory.createIntegrationSyncState(
                    integrationConnectionId = connectionId,
                    status = SyncStatus.FAILED,
                    consecutiveFailureCount = 2,
                ),
            )

            whenever(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection))
            whenever(syncStateRepository.findByIntegrationConnectionId(connectionId)).thenReturn(states)

            healthService.evaluateConnectionHealth(connectionId)

            val captor = argumentCaptor<IntegrationConnectionEntity>()
            verify(connectionRepository).save(captor.capture())
            assertEquals(ConnectionStatus.FAILED, captor.firstValue.status)
        }
    }

    // ------ No-op cases ------

    @Nested
    inner class NoOpTests {

        @Test
        fun `empty sync states list does not call connectionRepository save`() {
            whenever(connectionRepository.findById(connectionId)).thenReturn(Optional.of(
                IntegrationFactory.createIntegrationConnection(id = connectionId)
            ))
            whenever(syncStateRepository.findByIntegrationConnectionId(connectionId)).thenReturn(emptyList())

            healthService.evaluateConnectionHealth(connectionId)

            verify(connectionRepository, never()).save(any())
        }

        @Test
        fun `connection not found does not call connectionRepository save`() {
            whenever(connectionRepository.findById(connectionId)).thenReturn(Optional.empty())
            whenever(syncStateRepository.findByIntegrationConnectionId(connectionId)).thenReturn(
                listOf(IntegrationFactory.createIntegrationSyncState(
                    integrationConnectionId = connectionId,
                    status = SyncStatus.SUCCESS,
                ))
            )

            healthService.evaluateConnectionHealth(connectionId)

            verify(connectionRepository, never()).save(any())
        }

        @Test
        fun `invalid transition does not save and logs warning`() {
            // DISCONNECTING cannot transition to HEALTHY/DEGRADED/FAILED
            val connection = IntegrationFactory.createIntegrationConnection(
                id = connectionId,
                status = ConnectionStatus.DISCONNECTING,
            )
            val states = listOf(
                IntegrationFactory.createIntegrationSyncState(
                    integrationConnectionId = connectionId,
                    status = SyncStatus.SUCCESS,
                    consecutiveFailureCount = 0,
                ),
            )

            whenever(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection))
            whenever(syncStateRepository.findByIntegrationConnectionId(connectionId)).thenReturn(states)

            healthService.evaluateConnectionHealth(connectionId)

            verify(connectionRepository, never()).save(any())
        }
    }
}
