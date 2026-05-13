package cranium.core.service.entity.query

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import cranium.core.models.entity.query.filter.QueryFilter
import cranium.core.models.entity.query.filter.RelationshipFilter
import java.util.*

/**
 * Unit tests for [QueryFilterValidator] handling of [QueryFilter.IsRelatedTo].
 *
 * Verifies that IsRelatedTo filters are accepted without errors and do not
 * require a relationshipId (since they match across all definitions).
 */
class QueryFilterValidatorIsRelatedToTest {

    private val validator = QueryFilterValidator()

    @Test
    fun `validate - accepts IsRelatedTo without errors`() {
        val filter = QueryFilter.IsRelatedTo(
            condition = RelationshipFilter.Exists,
        )

        val errors = validator.validate(
            filter = filter,
            relationshipDefinitions = emptyMap(),
            maxDepth = 2,
        )

        assertTrue(errors.isEmpty(), "IsRelatedTo should not produce validation errors")
    }

    @Test
    fun `validate - IsRelatedTo does not require a relationshipId`() {
        // IsRelatedTo is definition-agnostic -- it checks any relationship.
        // Passing an empty definitions map should still produce zero errors.
        val filter = QueryFilter.IsRelatedTo(
            condition = RelationshipFilter.NotExists,
        )

        val errors = validator.validate(
            filter = filter,
            relationshipDefinitions = emptyMap(),
            maxDepth = 3,
        )

        assertTrue(errors.isEmpty(), "IsRelatedTo should not require relationship definitions")
    }
}
