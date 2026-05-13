package cranium.core.models.request.entity.type

import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticGroup
import cranium.core.models.common.Icon
import cranium.core.models.common.display.DisplayName
import cranium.core.models.entity.configuration.ColumnConfiguration
import java.util.*

data class UpdateEntityTypeConfigurationRequest(
    val id: UUID,
    val name: DisplayName,
    val icon: Icon,
    val semanticGroup: SemanticGroup? = null,
    val lifecycleDomain: LifecycleDomain? = null,
    val columnConfiguration: ColumnConfiguration? = null,
    val semantics: SaveSemanticMetadataRequest? = null,
)
