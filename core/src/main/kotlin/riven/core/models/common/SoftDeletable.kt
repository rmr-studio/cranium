package cranium.core.models.common

import java.time.ZonedDateTime

interface SoftDeletable {
    var deleted: Boolean
    var deletedAt: ZonedDateTime?
}

/** Marks this entity as soft-deleted and returns it for chaining. */
fun <T : SoftDeletable> T.markDeleted(): T = apply {
    deleted = true
    deletedAt = ZonedDateTime.now()
}