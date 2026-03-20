package riven.core.models.note

data class UpdateNoteRequest(
    val title: String? = null,
    val content: List<Map<String, Any>>? = null,
)
