package riven.core.models.request.entity.type

import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.models.common.Icon
import riven.core.models.common.display.DisplayName
import riven.core.models.entity.configuration.ColumnConfiguration
import java.util.*

data class UpdateEntityTypeConfigurationRequest(
    val id: UUID,
    val name: DisplayName,
    val icon: Icon,
    val semanticGroup: SemanticGroup? = null,
    val columnConfiguration: ColumnConfiguration? = null,
    val semantics: SaveSemanticMetadataRequest? = null,
)
