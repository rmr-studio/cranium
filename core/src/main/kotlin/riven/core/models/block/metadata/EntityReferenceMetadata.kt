package riven.core.models.block.metadata

import com.fasterxml.jackson.annotation.JsonTypeName
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import riven.core.enums.block.structure.BlockMetadataType
import riven.core.enums.block.structure.BlockReferenceFetchPolicy
import riven.core.models.entity.Entity

/**
 * Metadata when a block is referencing a list of external entities
 */
@JsonTypeName("entity_reference")
@JsonDeserialize(using = ValueDeserializer.None::class)
data class EntityReferenceMetadata(
    override val fetchPolicy: BlockReferenceFetchPolicy = BlockReferenceFetchPolicy.LAZY,
    override val path: String = "\$.items",           // <— used by service to scope rows
    // How the referenced entities should be presented
    val presentation: Presentation = Presentation.SUMMARY,
    val items: List<ReferenceItem>,
    val projection: Projection = Projection(),
    override val deletable: Boolean = true,
    override val readonly: Boolean = false,
    override val listType: Entity? = null,
    override val display: ListDisplayConfig = ListDisplayConfig(),
    override val config: ListConfig = ListConfig(),
    override val allowDuplicates: Boolean = false,          // <— optional guard
    override val meta: BlockMeta = BlockMeta()
) : ReferenceMetadata, ListMetadata<Entity> {
    override val type: BlockMetadataType = BlockMetadataType.ENTITY_REFERENCE
}