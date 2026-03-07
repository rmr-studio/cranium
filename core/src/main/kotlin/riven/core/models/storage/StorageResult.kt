package riven.core.models.storage

data class StorageResult(
    val storageKey: String,
    val contentType: String,
    val contentLength: Long
)
