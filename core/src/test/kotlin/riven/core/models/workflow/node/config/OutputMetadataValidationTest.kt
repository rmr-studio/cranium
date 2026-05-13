package cranium.core.models.workflow.node.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import cranium.core.enums.workflow.OutputFieldType
import cranium.core.models.workflow.engine.state.*
import cranium.core.models.workflow.node.config.actions.WorkflowBulkUpdateEntityActionConfig
import cranium.core.models.workflow.node.config.actions.WorkflowCreateEntityActionConfig
import cranium.core.models.workflow.node.config.actions.WorkflowQueryEntityActionConfig
import java.util.UUID
import java.util.stream.Stream

/**
 * Validation tests for workflow node output metadata.
 *
 * These tests ensure that output metadata declarations match actual NodeOutput.toMap() behavior:
 * - All declared keys exist in toMap() results
 * - All declared OutputFieldType values match actual Kotlin runtime types
 * - Coverage tracking for nodes without outputMetadata (Phase 3 TODO)
 *
 * Tests use parameterized approach to validate all node types consistently.
 */
class OutputMetadataValidationTest {

    /**
     * Test case combining output metadata, example output instance, and node identification.
     */
    data class OutputMetadataTestCase(
        val nodeLabel: String,
        val outputMetadata: WorkflowNodeOutputMetadata?,
        val exampleOutput: NodeOutput
    )

    companion object {
        /**
         * Provides test cases for all known NodeOutput types.
         *
         * Each test case includes:
         * - nodeLabel: Display name for test output (e.g., "CREATE_ENTITY")
         * - outputMetadata: Companion object metadata (nullable during rollout)
         * - exampleOutput: Instance of NodeOutput for toMap() validation
         */
        @JvmStatic
        fun nodeOutputTestCases(): Stream<Arguments> = Stream.of(
            Arguments.of(
                OutputMetadataTestCase(
                    nodeLabel = "CREATE_ENTITY",
                    outputMetadata = WorkflowCreateEntityActionConfig.outputMetadata,
                    exampleOutput = CreateEntityOutput(
                        entityId = UUID.randomUUID(),
                        entityTypeId = UUID.randomUUID(),
                        payload = mapOf(UUID.randomUUID() to "test")
                    )
                )
            ),
            Arguments.of(
                OutputMetadataTestCase(
                    nodeLabel = "UPDATE_ENTITY",
                    outputMetadata = null,  // Not yet declared (Phase 3)
                    exampleOutput = UpdateEntityOutput(
                        entityId = UUID.randomUUID(),
                        updated = true,
                        payload = mapOf(UUID.randomUUID() to "test")
                    )
                )
            ),
            Arguments.of(
                OutputMetadataTestCase(
                    nodeLabel = "DELETE_ENTITY",
                    outputMetadata = null,  // Not yet declared (Phase 3)
                    exampleOutput = DeleteEntityOutput(
                        entityId = UUID.randomUUID(),
                        deleted = true,
                        impactedEntities = 3
                    )
                )
            ),
            Arguments.of(
                OutputMetadataTestCase(
                    nodeLabel = "QUERY_ENTITY",
                    outputMetadata = WorkflowQueryEntityActionConfig.outputMetadata,
                    exampleOutput = QueryEntityOutput(
                        entities = listOf(mapOf("id" to "1")),
                        totalCount = 1,
                        hasMore = false
                    )
                )
            ),
            Arguments.of(
                OutputMetadataTestCase(
                    nodeLabel = "HTTP_REQUEST",
                    outputMetadata = null,  // Not yet declared (Phase 3)
                    exampleOutput = HttpResponseOutput(
                        statusCode = 200,
                        headers = mapOf("Content-Type" to "application/json"),
                        body = "{}",
                        url = "https://example.com",
                        method = "GET"
                    )
                )
            ),
            Arguments.of(
                OutputMetadataTestCase(
                    nodeLabel = "CONDITION",
                    outputMetadata = null,  // Not yet declared (Phase 3)
                    exampleOutput = ConditionOutput(
                        result = true,
                        evaluatedExpression = "x > 5"
                    )
                )
            ),
            Arguments.of(
                OutputMetadataTestCase(
                    nodeLabel = "BULK_UPDATE_ENTITY",
                    outputMetadata = WorkflowBulkUpdateEntityActionConfig.outputMetadata,
                    exampleOutput = BulkUpdateEntityOutput(
                        entitiesUpdated = 25,
                        entitiesFailed = 0,
                        failedEntityDetails = emptyList(),
                        totalProcessed = 25
                    )
                )
            )
        )
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("nodeOutputTestCases")
    fun `outputMetadata keys exist in toMap results`(testCase: OutputMetadataTestCase) {
        // Skip nodes without outputMetadata (Phase 3 rollout)
        if (testCase.outputMetadata == null) {
            println("WARNING: ${testCase.nodeLabel} missing outputMetadata (Phase 3 TODO)")
            return
        }

        val outputMap = testCase.exampleOutput.toMap()
        val declaredKeys = testCase.outputMetadata.fields.map { it.key }

        // Every declared key must exist in toMap() result
        for (field in testCase.outputMetadata.fields) {
            assertTrue(
                field.key in outputMap.keys,
                "${testCase.nodeLabel}: Declared key '${field.key}' not found in ${testCase.exampleOutput::class.simpleName}.toMap(). " +
                        "Available keys: ${outputMap.keys}"
            )
        }

        // Note: toMap() MAY have extra keys not in outputMetadata (e.g., HttpResponseOutput.success)
        // This is allowed - outputMetadata declares the public API, toMap() can include internal/computed fields
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("nodeOutputTestCases")
    fun `outputMetadata types match toMap value types`(testCase: OutputMetadataTestCase) {
        // Skip nodes without outputMetadata (Phase 3 rollout)
        if (testCase.outputMetadata == null) {
            return
        }

        val outputMap = testCase.exampleOutput.toMap()

        for (field in testCase.outputMetadata.fields) {
            val value = outputMap[field.key]

            // If field is nullable and value is null, skip type check
            if (field.nullable && value == null) {
                continue
            }

            // Validate type matches
            when (field.type) {
                OutputFieldType.UUID -> {
                    assertTrue(
                        value is UUID,
                        "${testCase.nodeLabel}: Field '${field.key}' declared as UUID but got ${value?.javaClass?.simpleName}"
                    )
                }
                OutputFieldType.STRING -> {
                    assertTrue(
                        value is String,
                        "${testCase.nodeLabel}: Field '${field.key}' declared as STRING but got ${value?.javaClass?.simpleName}"
                    )
                }
                OutputFieldType.BOOLEAN -> {
                    assertTrue(
                        value is Boolean,
                        "${testCase.nodeLabel}: Field '${field.key}' declared as BOOLEAN but got ${value?.javaClass?.simpleName}"
                    )
                }
                OutputFieldType.NUMBER -> {
                    assertTrue(
                        value is Number,
                        "${testCase.nodeLabel}: Field '${field.key}' declared as NUMBER but got ${value?.javaClass?.simpleName}"
                    )
                }
                OutputFieldType.MAP -> {
                    assertTrue(
                        value is Map<*, *>,
                        "${testCase.nodeLabel}: Field '${field.key}' declared as MAP but got ${value?.javaClass?.simpleName}"
                    )
                }
                OutputFieldType.LIST -> {
                    assertTrue(
                        value is List<*> || value is Collection<*>,
                        "${testCase.nodeLabel}: Field '${field.key}' declared as LIST but got ${value?.javaClass?.simpleName}"
                    )
                }
                OutputFieldType.OBJECT -> {
                    // OBJECT is any non-primitive type (skip if nullable and null)
                    assertNotNull(
                        value,
                        "${testCase.nodeLabel}: Field '${field.key}' declared as OBJECT but got null (set nullable=true if null is valid)"
                    )
                }
                OutputFieldType.ENTITY -> {
                    // Entity serializes as Map
                    assertTrue(
                        value is Map<*, *>,
                        "${testCase.nodeLabel}: Field '${field.key}' declared as ENTITY but got ${value?.javaClass?.simpleName} (entities serialize as maps)"
                    )
                }
                OutputFieldType.ENTITY_LIST -> {
                    // Entity list serializes as List
                    assertTrue(
                        value is List<*>,
                        "${testCase.nodeLabel}: Field '${field.key}' declared as ENTITY_LIST but got ${value?.javaClass?.simpleName}"
                    )
                }
            }
        }
    }

    @Test
    fun `all node types are accounted for in test cases`() {
        // Expected count of NodeOutput sealed interface implementations
        // Current: CreateEntity, UpdateEntity, DeleteEntity, QueryEntity, HttpResponse, Condition, BulkUpdateEntity
        // Excluded: UnsupportedNodeOutput (utility type), GenericMapOutput (utility type)
        val expectedNodeOutputTypes = 7

        val testCases = nodeOutputTestCases().toList()

        assertEquals(
            expectedNodeOutputTypes,
            testCases.size,
            "Test cases count doesn't match expected NodeOutput types. " +
                    "If you added a new NodeOutput subclass, add it to nodeOutputTestCases(). " +
                    "Current test cases: ${testCases.map { (it.get()[0] as OutputMetadataTestCase).nodeLabel }}"
        )
    }

    @Test
    fun `nodes without outputMetadata are tracked for Phase 3`() {
        val testCases = nodeOutputTestCases().map { it.get()[0] as OutputMetadataTestCase }.toList()
        val nodesWithoutMetadata = testCases.filter { it.outputMetadata == null }

        // Print warnings for tracking purposes
        if (nodesWithoutMetadata.isNotEmpty()) {
            println("\n=== Phase 3 TODO: Nodes Missing outputMetadata ===")
            nodesWithoutMetadata.forEach { testCase ->
                println("WARNING: ${testCase.nodeLabel} missing outputMetadata (Phase 3 TODO)")
            }
            println("=== End Phase 3 TODO List ===\n")
        }

        // This test intentionally doesn't fail - it's a tracker
        // During Phase 3 rollout, nodes without outputMetadata are expected
        assertTrue(true, "Phase 3 TODO tracker executed")
    }
}
