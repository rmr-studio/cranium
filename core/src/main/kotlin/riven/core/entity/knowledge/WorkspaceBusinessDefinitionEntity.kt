package riven.core.entity.knowledge

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import org.hibernate.annotations.Type
import riven.core.entity.util.AuditableSoftDeletableEntity
import riven.core.enums.knowledge.DefinitionCategory
import riven.core.enums.knowledge.DefinitionSource
import riven.core.enums.knowledge.DefinitionStatus
import riven.core.models.knowledge.AttributeRef
import riven.core.models.knowledge.WorkspaceBusinessDefinition
import java.util.*

@Entity
@Table(
    name = "workspace_business_definitions",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["workspace_id", "normalized_term"])
    ],
    indexes = [
        Index(name = "idx_wbd_workspace_id", columnList = "workspace_id"),
    ]
)
@SQLRestriction("deleted = false")
data class WorkspaceBusinessDefinitionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
    val workspaceId: UUID,

    @Column(name = "term", nullable = false, length = 255)
    var term: String,

    @Column(name = "normalized_term", nullable = false, length = 255)
    var normalizedTerm: String,

    @Column(name = "definition", nullable = false, columnDefinition = "TEXT")
    var definition: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    var category: DefinitionCategory,

    @Type(JsonBinaryType::class)
    @Column(name = "compiled_params", columnDefinition = "jsonb")
    var compiledParams: Map<String, Any>? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    var status: DefinitionStatus = DefinitionStatus.ACTIVE,

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 50)
    val source: DefinitionSource = DefinitionSource.MANUAL,

    @Type(JsonBinaryType::class)
    @Column(name = "entity_type_refs", columnDefinition = "jsonb", nullable = false)
    var entityTypeRefs: List<UUID> = emptyList(),

    @Type(JsonBinaryType::class)
    @Column(name = "attribute_refs", columnDefinition = "jsonb", nullable = false)
    var attributeRefs: List<AttributeRef> = emptyList(),

    @Column(name = "is_customised", nullable = false)
    var isCustomized: Boolean = false,

    @Version
    @Column(name = "version", nullable = false)
    var version: Int = 0,
) : AuditableSoftDeletableEntity() {

    fun toModel(): WorkspaceBusinessDefinition {
        val id = requireNotNull(this.id) { "WorkspaceBusinessDefinitionEntity ID cannot be null" }
        return WorkspaceBusinessDefinition(
            id = id,
            workspaceId = this.workspaceId,
            term = this.term,
            normalizedTerm = this.normalizedTerm,
            definition = this.definition,
            category = this.category,
            compiledParams = this.compiledParams,
            status = this.status,
            source = this.source,
            entityTypeRefs = this.entityTypeRefs,
            attributeRefs = this.attributeRefs,
            isCustomized = this.isCustomized,
            version = this.version,
            createdBy = this.createdBy,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }
}
