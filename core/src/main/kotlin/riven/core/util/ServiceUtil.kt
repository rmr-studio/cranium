package cranium.core.util

import cranium.core.exceptions.NotFoundException
import java.util.*

object ServiceUtil {

    /**
     * Retrieves the value produced by the given supplier or throws when no value is present.
     *
     * @param query A zero-argument supplier that returns an Optional containing the desired value.
     * @return The contained value when present.
     * @throws NotFoundException if the supplier's Optional is empty.
     */
    @Throws(NotFoundException::class)
    fun <V> findOrThrow(query: () -> Optional<V>): V =
        query.invoke().orElseThrow { NotFoundException("Entity not found") }

    /**
     * Execute the provided zero-argument query supplier and return its result list.
     *
     * @param query A lambda that executes the query and produces a List of results.
     * @return The list of results produced by the provided query.
     */
    fun <V> findManyResults(query: () -> List<V>): List<V> =
        query.invoke()

}