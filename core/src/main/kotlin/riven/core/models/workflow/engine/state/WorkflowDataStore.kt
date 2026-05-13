package cranium.core.models.workflow.engine.state

import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Unified workflow state management system

 * Thread-safe via ConcurrentHashMap for parallel node execution.
 *
 * This class provides thread-safe access to trigger context, step outputs,
 * user variables, and loop contexts.
 *
 * ## Thread Safety
 *
 * All internal storage uses [ConcurrentHashMap] for fine-grained concurrency:
 * - **Steps:** Write-once per key, concurrent reads
 * - **Variables:** Last-write-wins semantics
 * - **Loops:** Branch-scoped, minimal contention
 * - **Trigger:** Write-once, then read-only (uses @Volatile)
 *
 * ## Write-Once Enforcement
 *
 * Step outputs and trigger context enforce write-once semantics:
 * - [setStepOutput] throws [IllegalStateException] if key already exists
 * - [setTrigger] throws [IllegalStateException] if trigger already set
 *
 * This ensures execution results are immutable once recorded, preventing
 * accidental overwrites in parallel execution scenarios.
 *
 * ## Data Flow
 *
 * 1. DataStore created with immutable [WorkflowMetadata]
 * 2. Trigger context set once at workflow start
 * 3. Each node execution writes to steps via coordinator
 * 4. Template resolution reads from steps, trigger, variables, loops
 *
 * @property metadata Immutable workflow-level context, accessible directly
 */
class WorkflowDataStore(
    val metadata: WorkflowMetadata,
    var state: WorkflowState
) {
    /**
     * Write-once trigger context.
     *
     * Set once at workflow start, never modified afterwards.
     * Typed as TriggerContext for template resolution via toMap().
     */
    @Volatile
    private var _trigger: TriggerContext? = null

    /**
     * Step outputs keyed by node name.
     *
     * Write-once per key: each step can only write its output once.
     * Concurrent reads are safe.
     */
    private val steps = ConcurrentHashMap<String, StepOutput>()

    /**
     * User variables keyed by name.
     *
     * Uses a wrapper to support null values since ConcurrentHashMap doesn't allow nulls.
     * Last-write-wins semantics: concurrent writes to the same key
     * will result in one winning, which is acceptable for variables.
     */
    private val variables = ConcurrentHashMap<String, NullableValue>()

    /**
     * Loop contexts keyed by loop ID.
     *
     * Each parallel branch gets its own LoopContext entry.
     * Mutable per loop to track iteration state.
     */
    private val loops = ConcurrentHashMap<String, LoopContext>()

    // ==================== Trigger Operations ====================

    /**
     * Sets the trigger context for this workflow execution.
     *
     * Must be called exactly once at workflow start. Subsequent calls
     * will throw [IllegalStateException] to enforce write-once semantics.
     *
     * @param trigger The trigger context with data accessible via toMap()
     * @throws IllegalStateException if trigger has already been set
     */
    fun setTrigger(trigger: TriggerContext) {
        if (_trigger != null) {
            throw IllegalStateException("Trigger already set")
        }
        _trigger = trigger
    }

    /**
     * Gets the trigger context if set.
     *
     * @return The trigger context, or null if not yet set
     */
    fun getTrigger(): TriggerContext? = _trigger

    // ==================== Step Operations ====================

    /**
     * Stores the output of a completed step.
     *
     * Each step can only store its output once. Attempting to store
     * output for the same step name again will throw [IllegalStateException].
     * This enforces write-once semantics and prevents accidental overwrites.
     *
     * @param name The step/node name (used as key for template resolution)
     * @param output The step's execution output
     * @throws IllegalStateException if output already exists for this step name
     */
    fun setStepOutput(name: String, output: StepOutput) {
        val existing = steps.putIfAbsent(name, output)
        if (existing != null) {
            throw IllegalStateException("Step output already exists for: $name")
        }
    }

    /**
     * Gets the output of a specific step by name.
     *
     * @param name The step/node name
     * @return The step output, or null if not yet executed
     */
    fun getStepOutput(name: String): StepOutput? = steps[name]

    /**
     * Gets the output of a specific step by uuid.
     *
     * @param id The step/node uuid
     * @return The step output, or null if not yet executed
     */
    fun getStepOutput(id: UUID): StepOutput? = steps.values.find { it.nodeId == id }


    /**
     * Gets all step outputs as an immutable copy.
     *
     * The returned map is a snapshot and modifications to it
     * will not affect the datastore.
     *
     * @return Immutable copy of all step outputs
     */
    fun getAllStepOutputs(): Map<String, StepOutput> = steps.toMap()

    // ==================== Variable Operations ====================

    /**
     * Sets or updates a variable.
     *
     * Uses last-write-wins semantics: if multiple parallel branches
     * write to the same variable, one will win (non-deterministic).
     * This is acceptable behavior for variables.
     *
     * @param name The variable name
     * @param value The variable value (can be null)
     */
    fun setVariable(name: String, value: Any?) {
        variables[name] = NullableValue(value)
    }

    /**
     * Gets a variable by name.
     *
     * @param name The variable name
     * @return The variable value, or null if not set or if explicitly set to null
     */
    fun getVariable(name: String): Any? = variables[name]?.value

    /**
     * Checks if a variable has been set (including if set to null).
     *
     * @param name The variable name
     * @return true if the variable has been set, false otherwise
     */
    fun hasVariable(name: String): Boolean = variables.containsKey(name)

    /**
     * Gets all variables as an immutable copy.
     *
     * The returned map is a snapshot and modifications to it
     * will not affect the datastore.
     *
     * @return Immutable copy of all variables
     */
    fun getAllVariables(): Map<String, Any?> = variables.mapValues { it.value.value }

    // ==================== Loop Operations ====================

    /**
     * Sets or updates a loop context.
     *
     * Each parallel branch should use a unique loopId to avoid conflicts.
     * Loop contexts are mutable to track iteration progress.
     *
     * @param loopId The unique loop identifier (branch-scoped)
     * @param context The loop context with iteration state
     */
    fun setLoopContext(loopId: String, context: LoopContext) {
        loops[loopId] = context
    }

    /**
     * Gets a loop context by ID.
     *
     * @param loopId The loop identifier
     * @return The loop context, or null if not found
     */
    fun getLoopContext(loopId: String): LoopContext? = loops[loopId]
}

/**
 * Wrapper to support null values in ConcurrentHashMap.
 *
 * ConcurrentHashMap doesn't allow null keys or values, so we wrap
 * nullable values in this class to store them safely.
 */
@JvmInline
internal value class NullableValue(val value: Any?)

/**
 * Placeholder loop context for iteration tracking.
 *
 * Used to track the current state of a loop during parallel branch execution.
 * Each parallel branch gets its own LoopContext entry keyed by branch/loop ID.
 *
 * @property loopId Unique identifier for this loop instance
 * @property currentIndex Zero-based index of the current iteration
 * @property currentItem The current item being processed (from the collection)
 * @property totalItems Total number of items in the loop collection
 */
data class LoopContext(
    val loopId: String,
    val currentIndex: Int,
    val currentItem: Any?,
    val totalItems: Int
)
