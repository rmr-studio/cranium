package cranium.core.service.note.converter

/**
 * Converts raw content from an integration source into BlockNote-compatible blocks.
 * Returns both the structured block content and extracted plaintext for full-text search.
 */
sealed interface NoteContentConverter {
    fun convert(body: String): NoteConversionResult
}

data class NoteConversionResult(
    val blocks: List<Map<String, Any>>,
    val plaintext: String,
    val title: String,
)
