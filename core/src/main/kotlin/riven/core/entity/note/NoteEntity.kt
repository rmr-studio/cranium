package riven.core.entity.note

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import riven.core.entity.util.AuditableEntity
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

    @Column(name = "entity_id", nullable = false)
    val entityId: UUID,

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
) : AuditableEntity() {

    fun toModel(): Note {
        val id = requireNotNull(this.id) { "NoteEntity ID cannot be null" }
        return Note(
            id = id,
            entityId = entityId,
            workspaceId = workspaceId,
            title = title,
            content = content,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdBy = createdBy,
            updatedBy = updatedBy,
        )
    }
}
