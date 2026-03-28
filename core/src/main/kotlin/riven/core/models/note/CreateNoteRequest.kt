package riven.core.models.note

data class CreateNoteRequest(
    val title: String? = null,
    val content: List<Map<String, Any>>,
)
