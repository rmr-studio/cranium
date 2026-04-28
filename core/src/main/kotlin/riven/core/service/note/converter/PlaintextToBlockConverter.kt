package riven.core.service.note.converter

import org.springframework.stereotype.Component
import java.util.*

@Component
class PlaintextToBlockConverter : NoteContentConverter {
    override fun convert(body: String): NoteConversionResult {
        val id = UUID.randomUUID().toString().take(10)
        val block = mapOf(
            "id" to id,
            "type" to "paragraph",
            "props" to defaultProps(),
            "content" to listOf(mapOf("type" to "text", "text" to body, "styles" to emptyMap<String, Any>())),
            "children" to emptyList<Any>(),
        )
        val title = body.take(255)
        return NoteConversionResult(
            blocks = listOf(block),
            plaintext = body,
            title = title,
        )
    }
}

internal fun defaultProps(): Map<String, Any> = mapOf(
    "textColor" to "default",
    "backgroundColor" to "default",
    "textAlignment" to "left",
)
