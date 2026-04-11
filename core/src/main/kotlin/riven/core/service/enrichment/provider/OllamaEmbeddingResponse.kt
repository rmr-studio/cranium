package riven.core.service.enrichment.provider

/**
 * Jackson deserialization DTO for the Ollama embeddings API response.
 *
 * Ollama returns an array-of-arrays structure: `{ "embeddings": [[0.1, 0.2, ...]] }`.
 * The first element (embeddings[0]) is the embedding for the single input text.
 */
data class OllamaEmbeddingResponse(
    val embeddings: List<List<Float>>
)
