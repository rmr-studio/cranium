package riven.core.service.connector.mapping

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Phase 3 Wave-0 placeholder for CustomSourceSchemaInferenceService
 * (owned by plan 03-03, covers MAP-01, MAP-06).
 * Downstream plan removes @Disabled and replaces placeholder() with real assertions.
 */
@Disabled("populated by plan 03-03")
class CustomSourceSchemaInferenceServiceTest {

    @Test fun getSchemaReturnsTablesWithComputedSchemaHash() = placeholder()
    @Test fun getSchemaSurfacesDriftWhenStoredHashDiffers() = placeholder()
    @Test fun getSchemaSurfacesAddedColumnsAsUnmappedDriftEntries() = placeholder()
    @Test fun getSchemaMarksDroppedColumnsStaleInMappingTable() = placeholder()
    @Test fun getSchemaSurfacesFkMetadataPerColumn() = placeholder()
    @Test fun getSchemaIncludesCursorIndexWarningWhenChosenCursorColumnUnindexed() = placeholder()
    @Test fun getSchemaScopedToWorkspaceViaPreAuthorize() = placeholder()

    private fun placeholder() {}
}
