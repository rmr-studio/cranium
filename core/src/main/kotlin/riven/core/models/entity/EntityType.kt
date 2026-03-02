package riven.core.models.entity

import riven.core.entity.util.AuditableModel
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.models.common.Icon
import riven.core.models.common.display.DisplayName
import riven.core.models.common.validation.Schema
import riven.core.models.entity.configuration.EntityTypeAttributeColumn
import java.time.ZonedDateTime
import java.util.*

/**
 * Domain model for an entity type - defines the schema and behavior for a class of entities.
 *
 * Unlike BlockTypes (which are immutable/versioned), EntityTypes are mutable and updated in-place.
 * All entities reference the same entity type, regardless of when they were created.
 */
data class EntityType(
    val id: UUID,
    val key: String,
    val version: Int,
    val icon: Icon,
    val name: DisplayName,
    /**
     * Each workspace will have a handful of system-generated default entity types to handle
     * core/common use cases, which cannot be deleted.
     * They should allow for modification of schema and other properties, but not deletion.
     * */
    val protected: Boolean,

    /**
     * A unique identifier key for the entity type. This will be taken from one
     * of the attributes within the schema (default to ID).
     * The attribute used as the identifier key must be set to unique within the schema.
     */
    val identifierKey: UUID,
    val semanticGroup: SemanticGroup = SemanticGroup.UNCATEGORIZED,
    val workspaceId: UUID?,
    // Schema will always be created with a unique, non-nullable 'name' attribute
    // Each attribute in the schema will be uniquely identified with a UUID key
    val schema: EntityTypeSchema,
    // Configuration for how attributes are displayed in tabular/list views (ie. Column ordering, widths, etc)
    val columns: List<EntityTypeAttributeColumn>,
    val entitiesCount: Long = 0L,
    val relationships: List<RelationshipDefinition> = emptyList(),
    val semantics: SemanticMetadataBundle? = null,
    override var createdAt: ZonedDateTime?,
    override var updatedAt: ZonedDateTime?,
    override var createdBy: UUID?,
    override var updatedBy: UUID?
) : AuditableModel

typealias EntityTypeSchema = Schema<UUID>