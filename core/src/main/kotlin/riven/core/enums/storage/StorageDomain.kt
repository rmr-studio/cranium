package riven.core.enums.storage

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    name = "StorageDomain",
    description = "System-defined storage domains with per-domain validation rules.",
    enumAsRef = true,
)
enum class StorageDomain(
    val allowedContentTypes: Set<String>,
    val maxFileSize: Long
) {
    AVATAR(
        allowedContentTypes = setOf(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/svg+xml"
        ),
        maxFileSize = 2 * 1024 * 1024 // 2MB
    );

    fun isContentTypeAllowed(contentType: String): Boolean =
        contentType in allowedContentTypes

    fun isFileSizeAllowed(size: Long): Boolean =
        size <= maxFileSize
}
