package riven.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import riven.core.repository.workflow.ExecutionQueueRepository
import riven.core.service.workflow.queue.WorkflowExecutionQueueService

/**
 * Scheduled dispatcher for the IDENTITY_MATCH execution queue.
 *
 * Runs on a fixed-delay schedule with distributed locking (ShedLock) to ensure
 * only one instance across a cluster processes the queue at a time.
 *
 * Flow:
 * 1. Claim pending IDENTITY_MATCH items via SKIP LOCKED (short transaction)
 * 2. For each item, delegate to [IdentityMatchQueueProcessorService] (separate transaction per item)
 * 3. Each item's transaction commits independently
 *
 * ShedLock names are distinct from the workflow execution dispatcher to prevent
 * contention: [LOCK_PROCESS_QUEUE] and [LOCK_RECOVER_STALE].
 */
@Service
class IdentityMatchDispatcherService(
    private val processorService: IdentityMatchQueueProcessorService,
    private val executionQueueRepository: ExecutionQueueRepository,
    private val workflowExecutionQueueService: WorkflowExecutionQueueService,
    private val logger: KLogger,
) {

    companion object {
        /** Batch size for queue processing */
        const val BATCH_SIZE = 10

        /** Polling interval in milliseconds (5 seconds) */
        const val POLL_INTERVAL_MS = 5000L

        private const val LOCK_PROCESS_QUEUE = "processIdentityMatchQueue"
        private const val LOCK_RECOVER_STALE = "recoverStaleIdentityMatchItems"
    }

    // ------ Scheduled operations ------

    /**
     * Process the IDENTITY_MATCH execution queue.
     *
     * Called on fixed-delay schedule with distributed lock.
     * Only one instance across the cluster processes at a time.
     *
     * No @Transactional — each item gets its own transaction via
     * [IdentityMatchQueueProcessorService.processItem].
     */
    @Scheduled(fixedDelay = POLL_INTERVAL_MS)
    @SchedulerLock(
        name = LOCK_PROCESS_QUEUE,
        lockAtMostFor = "4m",
        lockAtLeastFor = "10s",
    )
    fun processQueue() {
        val pending = processorService.claimBatch(BATCH_SIZE)

        if (pending.isEmpty()) {
            return
        }

        logger.debug { "Processing ${pending.size} IDENTITY_MATCH queue items" }

        for (item in pending) {
            try {
                processorService.processItem(item)
            } catch (e: Exception) {
                logger.error(e) { "Failed to process IDENTITY_MATCH queue item ${item.id}" }
            }
        }
    }

    /**
     * Recover stale IDENTITY_MATCH items stuck in CLAIMED state.
     *
     * Runs less frequently than main queue processing.
     * Releases items stuck in CLAIMED after dispatcher crash or timeout.
     */
    @Scheduled(fixedDelay = 60000)
    @SchedulerLock(
        name = LOCK_RECOVER_STALE,
        lockAtMostFor = "2m",
        lockAtLeastFor = "30s",
    )
    fun recoverStaleItems() {
        val staleItems = executionQueueRepository.findStaleClaimedIdentityMatchItems(5)

        if (staleItems.isEmpty()) {
            return
        }

        for (item in staleItems) {
            try {
                workflowExecutionQueueService.releaseToPending(item)
            } catch (e: Exception) {
                logger.error(e) { "Failed to recover stale IDENTITY_MATCH queue item ${item.id}" }
            }
        }

        logger.info { "Recovered ${staleItems.size} stale IDENTITY_MATCH queue items" }
    }
}
