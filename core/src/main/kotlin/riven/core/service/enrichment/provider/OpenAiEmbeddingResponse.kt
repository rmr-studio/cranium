package riven.core.service.enrichment.provider

/**
 * Jackson deserialization DTOs for the OpenAI embeddings API response.
 *
 * Uses List<Float> (not FloatArray) for Jackson compatibility — converted to FloatArray
 * after deserialization in OpenAiEmbeddingProvider.
 */
data class OpenAiEmbeddingResponse(
    val data: List<OpenAiEmbeddingData>
)

data class OpenAiEmbeddingData(
    val embedding: List<Float>
)
