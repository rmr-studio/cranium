package cranium.core.models.entity.configuration

import cranium.core.models.common.structure.FormStructure

data class EntityConfig(
    val form: FormStructure,
    val summary: EntityDisplayConfig
)