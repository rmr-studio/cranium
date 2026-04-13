package riven.core.controller.connector

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Phase 3 Wave-0 placeholder for CustomSourceMappingController
 * (owned by plan 03-03, covers MAP-01 + MAP-02 REST surface).
 * Downstream plan removes @Disabled and replaces placeholder() with real assertions.
 */
@Disabled("populated by plan 03-03")
class CustomSourceMappingControllerTest {

    @Test fun getSchemaReturns200WithTablesAndDriftIndicator() = placeholder()
    @Test fun saveMappingReturns201WithCreatedEntityTypeId() = placeholder()
    @Test fun saveMappingResponseIncludesCursorIndexWarningWhenColumnUnindexed() = placeholder()
    @Test fun getSchemaReturns403WhenWorkspaceMismatch() = placeholder()
    @Test fun saveMappingValidatesRequestBody() = placeholder()

    private fun placeholder() {}
}
