package riven.core.models.request.storage

import java.util.UUID

data class GenerateSignedUrlRequest(
    val fileId: UUID,
    val expiresInSeconds: Long? = null
)
