package cranium.core.models.common

import cranium.core.enums.common.icon.IconColour
import cranium.core.enums.common.icon.IconType

data class Icon(
    var type: IconType,
    var colour: IconColour,
)
