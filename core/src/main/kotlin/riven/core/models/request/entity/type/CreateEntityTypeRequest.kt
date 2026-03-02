package riven.core.models.request.entity.type

import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.models.common.Icon
import riven.core.models.common.display.DisplayName

data class CreateEntityTypeRequest(
    val name: DisplayName,
    val key: String,
    val icon: Icon,
    val semanticGroup: SemanticGroup = SemanticGroup.UNCATEGORIZED,
    val semantics: SaveSemanticMetadataRequest? = null,
)
