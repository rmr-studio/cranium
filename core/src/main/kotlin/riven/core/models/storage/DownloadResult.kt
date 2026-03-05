package riven.core.models.storage

import java.io.InputStream

data class DownloadResult(
    val content: InputStream,
    val contentType: String,
    val contentLength: Long,
    val originalFilename: String?
)
