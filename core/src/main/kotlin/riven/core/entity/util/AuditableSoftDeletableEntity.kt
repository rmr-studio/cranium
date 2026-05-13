package cranium.core.entity.util

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.SQLRestriction
import cranium.core.models.common.SoftDeletable
import java.time.ZonedDateTime

/**
 * Base entity for all soft-deletable entities.
 *
 * `@SQLRestriction("deleted = false")` automatically appends `AND deleted = false` to all
 * JPQL, derived, and Criteria queries on every inheriting entity. This means deleted rows
 * are invisible to standard repository methods including `findById`.
 *
 * To query deleted entities (e.g. for restoration), use a **native SQL query** —
 * `@SQLRestriction` does not apply to native queries:
 * ```
 * @Query(value = "SELECT * FROM table WHERE id = :id AND deleted = true", nativeQuery = true)
 * fun findDeletedById(id: UUID): MyEntity?
 * ```
 */
@MappedSuperclass
@SQLRestriction("deleted = false")
abstract class AuditableSoftDeletableEntity : AuditableEntity(), SoftDeletable {

    @Column(name = "deleted", nullable = false)
    override var deleted: Boolean = false

    @Column(name = "deleted_at", nullable = true)
    override var deletedAt: ZonedDateTime? = null
}
