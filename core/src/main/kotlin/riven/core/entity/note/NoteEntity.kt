package riven.core.entity.note

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import riven.core.entity.util.AuditableEntity
import riven.core.enums.note.NoteSourceType
import riven.core.models.note.Note
import java.util.*
import jakarta.persistence.Entity as JPAEntity

@JPAEntity
@Table(name = "notes")
data class NoteEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "entity_id")
    val entityId: UUID? = null,

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: UUID,

    @Column(name = "title", nullable = false)
    var title: String = "",

    @Type(JsonBinaryType::class)
    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    var content: List<Map<String, Any>> = emptyList(),

    @Column(name = "plaintext", nullable = false)
    var plaintext: String = "",
    // search_vector is GENERATED ALWAYS — no JPA mapping needed

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    val sourceType: NoteSourceType = NoteSourceType.USER,

    @Column(name = "source_integration_id")
    val sourceIntegrationId: UUID? = null,

    @Column(name = "source_external_id")
    val sourceExternalId: String? = null,

    @Column(name = "readonly", nullable = false)
    val readonly: Boolean = false,

    @Type(JsonBinaryType::class)
    @Column(name = "pending_associations", columnDefinition = "jsonb")
    var pendingAssociations: Map<String, List<String>>? = null,
) : AuditableEntity() {

    fun toModel(entityIds: List<UUID> = listOfNotNull(entityId)): Note {
        val id = requireNotNull(this.id) { "NoteEntity ID cannot be null" }
        return Note(
            id = id,
            entityIds = entityIds,
            workspaceId = workspaceId,
            title = title,
            content = content,
            sourceType = sourceType,
            readonly = readonly,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdBy = createdBy,
            updatedBy = updatedBy,
        )
    }
}
