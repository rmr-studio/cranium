package riven.core.models.entity.knowledge

/**
 * 8-section container for all knowledge assembled about a single entity.
 *
 * This data class is the reflection target for the VIEW-11 contract gate in
 * [riven.core.models.entity.knowledge.KnowledgeSectionsContractTest]. The property NAMES are the
 * contract surface — any rename, addition, or removal of a property will fail that test at
 * compile-test time.
 *
 * Multi-occurrence sections (attributes, catalogBacklinks, knowledgeBacklinks, clusterSiblings,
 * relationalReferences) are List<T>; singleton sections (identity, typeNarrative, entityMetadata)
 * are non-nullable val properties.
 *
 * Property order matches the section ordering documented in CONTEXT.md:
 * identity → typeNarrative → attributes → catalogBacklinks → knowledgeBacklinks →
 * entityMetadata → clusterSiblings → relationalReferences.
 */
data class KnowledgeSections(
    val identity: IdentitySection,
    val typeNarrative: TypeNarrativeSection,
    val attributes: List<AttributeSection>,
    val catalogBacklinks: List<CatalogBacklinkSection>,
    val knowledgeBacklinks: List<KnowledgeBacklinkSection>,
    val entityMetadata: EntityMetadataSection,
    val clusterSiblings: List<ClusterSiblingSection>,
    val relationalReferences: List<RelationalReferenceSection>,
)
