package cranium.core.entity.block

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import org.hibernate.annotations.Type
import cranium.core.entity.util.AuditableSoftDeletableEntity
import cranium.core.models.block.Block
import cranium.core.models.block.metadata.Metadata
import java.util.*

@Entity
@Table(
    name = "blocks"
)
@SQLRestriction("deleted = false")
data class BlockEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id", nullable = false)
    val type: BlockTypeEntity,

    @Column(name = "name", nullable = true)
    var name: String? = null,

    @Type(JsonBinaryType::class)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    var payload: Metadata,
) : AuditableSoftDeletableEntity() {

    fun toModel(audit: Boolean = false): Block {
        val id = requireNotNull(this.id) { "BlockEntity ID cannot be null when converting to model" }
        return Block(
            id = id,
            workspaceId = this.workspaceId,
            type = this.type.toModel(),
            name = this.name,
            payload = this.payload,
            validationErrors = this.payload.meta.validationErrors.ifEmpty { null },
            createdAt = if (audit) this.createdAt else null,
            updatedAt = if (audit) this.updatedAt else null,
            createdBy = if (audit) this.createdBy else null,
            updatedBy = if (audit) this.updatedBy else null,
        )
    }


}

