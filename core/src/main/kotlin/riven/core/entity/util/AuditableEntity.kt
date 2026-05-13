package cranium.core.entity.util

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.ZonedDateTime
import java.util.*

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class AuditableEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: ZonedDateTime? = null

    @LastModifiedDate
    @Column(name = "updated_at")
    var updatedAt: ZonedDateTime? = null

    @CreatedBy
    @Column(name = "created_by", updatable = false, columnDefinition = "uuid")
    var createdBy: UUID? = null

    @LastModifiedBy
    @Column(name = "updated_by", columnDefinition = "uuid")
    var updatedBy: UUID? = null

    /**
     * Populates `createdAt` with the current `ZonedDateTime` before the entity is persisted when it is not already set.
     *
     * Leaves an existing `createdAt` value unchanged.
     */
    @PrePersist
    fun prePersist() {
        if (createdAt != null) return
        createdAt = ZonedDateTime.now()
    }
}

interface AuditableModel {
    var createdAt: ZonedDateTime?
    var updatedAt: ZonedDateTime?
    var createdBy: UUID?
    var updatedBy: UUID?
}