package cranium.core.models.request.entity.type

import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticGroup
import cranium.core.models.common.Icon
import cranium.core.models.common.display.DisplayName

data class CreateEntityTypeRequest(
    val name: DisplayName,
    val key: String,
    val icon: Icon,
    val semanticGroup: SemanticGroup = SemanticGroup.UNCATEGORIZED,
    val lifecycleDomain: LifecycleDomain = LifecycleDomain.UNCATEGORIZED,
    val semantics: SaveSemanticMetadataRequest? = null,
)
