package riven.core.service.workflow.queue

import io.github.oshai.kotlinlogging.KLogger
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Service that processes the execution queue and dispatches to Temporal.
 *
 * Runs on a schedule with distributed locking (ShedLock) to ensure
 * only one instance processes the queue at a time across deployments.
 *
 * Flow:
 * 1. Claim pending items via SKIP LOCKED (short transaction)
 * 2. For each item, delegate to ExecutionItemProcessor (separate transaction per item)
 * 3. Each item's transaction commits independently
 *
 * Transaction boundaries:
 * - claimBatch(): Single transaction to atomically claim rows
 * - ExecutionItemProcessor.processItem(): REQUIRES_NEW transaction per item
 *
 * This ensures row locks are released as soon as each item completes,
 * rather than holding all locks until the entire batch finishes.
 */
@Service
class WorkflowExecutionDispatcherService(
    private val workflowExecutionQueueService: WorkflowExecutionQueueService,
    private val workflowExecutionQueueProcessorService: WorkflowExecutionQueueProcessorService,
    private val logger: KLogger
) {

    companion object {
        /** Batch size for queue processing */
        const val BATCH_SIZE = 10

        /** Polling interval in milliseconds (5 seconds) */
        const val POLL_INTERVAL_MS = 5000L
    }

    /**
     * Process the execution queue.
     *
     * Called on fixed delay schedule with distributed lock.
     * Only one instance across the cluster processes at a time.
     *
     * Note: No @Transactional here - each item gets its own transaction
     * via ExecutionItemProcessor.processItem().
     */
    @Scheduled(fixedDelay = POLL_INTERVAL_MS)
    @SchedulerLock(
        name = "processExecutionQueue",
        lockAtMostFor = "4m",
        lockAtLeastFor = "10s"
    )
    fun processQueue() {
        val pending = workflowExecutionQueueProcessorService.claimBatch(BATCH_SIZE)

        if (pending.isEmpty()) {
            return
        }

        logger.debug { "Processing ${pending.size} queue items" }

        for (item in pending) {
            // Each item processed in its own transaction (REQUIRES_NEW)
            workflowExecutionQueueProcessorService.processItem(item)
        }
    }


    /**
     * Recover stale claimed items.
     *
     * Runs less frequently than main queue processing.
     * Recovers items stuck in CLAIMED state after dispatcher crash.
     */
    @Scheduled(fixedDelay = 60000) // Every minute
    @SchedulerLock(
        name = "recoverStaleQueueItems",
        lockAtMostFor = "2m",
        lockAtLeastFor = "30s"
    )
    fun recoverStaleItems() {
        val recovered = workflowExecutionQueueService.recoverStaleItems(5)
        if (recovered > 0) {
            logger.info { "Recovered $recovered stale queue items" }
        }
    }
}
