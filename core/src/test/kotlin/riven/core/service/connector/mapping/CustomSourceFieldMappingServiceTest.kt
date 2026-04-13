package riven.core.service.connector.mapping

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Phase 3 Wave-0 placeholder for CustomSourceFieldMappingService
 * (owned by plan 03-03, covers MAP-02, MAP-08).
 * Downstream plan removes @Disabled and replaces placeholder() with real assertions.
 */
@Disabled("populated by plan 03-03")
class CustomSourceFieldMappingServiceTest {

    @Test fun saveCreatesEntityTypeWithSourceTypeConnectorAndReadonlyTrue() = placeholder()
    @Test fun saveCreatesAttributeDefinitionsForEachMappedColumn() = placeholder()
    @Test fun saveCreatesRelationshipDefinitionWhenBothFkEndsArePublished() = placeholder()
    @Test fun saveStoresFkMetadataOnlyWhenTargetTableUnpublished() = placeholder()
    @Test fun saveSkipsCompositeFkWithUnsupportedNote() = placeholder()
    @Test fun saveTransitionsTableMappingPublishedTrue() = placeholder()
    @Test fun saveSurfacesCursorIndexWarningInResponse() = placeholder()
    @Test fun reSavePropagatesAddedColumnsToEntityType() = placeholder()
    @Test fun reSaveMarksDroppedFieldsStaleAndKeepsExistingAttributes() = placeholder()
    @Test fun saveScopedToWorkspaceViaPreAuthorize() = placeholder()

    private fun placeholder() {}
}
