package riven.core.service.entity.query

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import riven.core.models.entity.query.filter.RelationshipFilter

/**
 * Unit tests for [RelationshipSqlGenerator.generateIsRelatedTo].
 *
 * Verifies that the bidirectional EXISTS/NOT EXISTS SQL fragments are generated
 * correctly for the IsRelatedTo filter, which matches entities with any
 * relationship regardless of definition.
 */
class RelationshipSqlGeneratorIsRelatedToTest {

    private val generator = RelationshipSqlGenerator()

    @Test
    fun `generateIsRelatedTo - Exists produces bidirectional OR EXISTS SQL`() {
        val paramGen = ParameterNameGenerator()

        val result = generator.generateIsRelatedTo(
            condition = RelationshipFilter.Exists,
            paramGen = paramGen,
            entityAlias = "e",
        )

        // Should produce two EXISTS subqueries joined by OR
        assertTrue(result.sql.contains("EXISTS ("), "Should contain EXISTS")
        assertTrue(result.sql.contains("OR EXISTS ("), "Should contain OR EXISTS for inverse direction")
        assertTrue(result.sql.contains("source_entity_id = e.id"), "Should check source side")
        assertTrue(result.sql.contains("target_id = e.id"), "Should check target side")
        assertTrue(result.sql.contains("deleted = false"), "Should filter deleted")

        // No parameters needed for IsRelatedTo (no definition-specific filtering)
        assertTrue(result.parameters.isEmpty())
    }

    @Test
    fun `generateIsRelatedTo - NotExists produces bidirectional AND NOT EXISTS SQL`() {
        val paramGen = ParameterNameGenerator()

        val result = generator.generateIsRelatedTo(
            condition = RelationshipFilter.NotExists,
            paramGen = paramGen,
            entityAlias = "e",
        )

        // Should produce two NOT EXISTS subqueries joined by AND
        assertTrue(result.sql.contains("NOT EXISTS ("), "Should contain NOT EXISTS")
        assertTrue(result.sql.contains("AND NOT EXISTS ("), "Should contain AND NOT EXISTS for inverse direction")
        assertTrue(result.sql.contains("source_entity_id = e.id"), "Should check source side")
        assertTrue(result.sql.contains("target_id = e.id"), "Should check target side")
        assertTrue(result.sql.contains("deleted = false"), "Should filter deleted")

        // No parameters needed
        assertTrue(result.parameters.isEmpty())
    }

    @Test
    fun `generateIsRelatedTo - rejects unsupported conditions`() {
        val paramGen = ParameterNameGenerator()

        assertThrows(UnsupportedOperationException::class.java) {
            generator.generateIsRelatedTo(
                condition = RelationshipFilter.TargetEquals(entityIds = listOf("some-id")),
                paramGen = paramGen,
                entityAlias = "e",
            )
        }
    }
}
