package riven.core.models.note

/**
 * Parsed noteEmbedding configuration from an integration manifest.
 * Defines how a Nango sync model's records are converted to NoteEntity records.
 */
data class NoteEmbeddingConfig(
    val syncModel: String,
    val bodyField: String,
    val contentFormat: NoteContentFormat,
    val timestampField: String? = null,
    val associations: Map<String, String>,
)

enum class NoteContentFormat {
    HTML,
    PLAINTEXT,
}
