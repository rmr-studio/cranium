package riven.core.service.enrichment.provider

/**
 * Contract for embedding generation providers.
 *
 * Implementations are Spring-conditional beans — only one is active at runtime
 * based on the `riven.enrichment.provider` configuration property.
 *
 * Methods are blocking (not suspend) because Temporal activities execute on
 * dedicated thread pools, and WebClient.block() is appropriate in that context.
 */
interface EmbeddingProvider {

    /**
     * Generates a vector embedding for the given text.
     *
     * @param text the input text to embed
     * @return FloatArray of length [getDimensions]
     * @throws IllegalStateException if the API returns an empty or null response
     */
    fun generateEmbedding(text: String): FloatArray

    /**
     * Returns the name of the embedding model in use (e.g. "text-embedding-3-small").
     * Stored alongside the embedding for provenance tracking.
     */
    fun getModelName(): String

    /**
     * Returns the vector dimension count for the configured model (e.g. 1536).
     * Used to validate storage compatibility at embedding time.
     */
    fun getDimensions(): Int
}
