package riven.core.models.ingestion.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Verifies the [RecordBatch] neutral contract for adapter fetch results:
 * - Three fields: records, nextCursor, hasMore
 * - nextCursor is nullable (null = terminal)
 * - Values round-trip through construction + destructuring
 */
class RecordBatchTest {

    @Test
    fun `empty batch with null cursor constructs and exposes three fields`() {
        val batch = RecordBatch(
            records = emptyList(),
            nextCursor = null,
            hasMore = false,
        )

        assertThat(batch.records).isEmpty()
        assertThat(batch.nextCursor).isNull()
        assertThat(batch.hasMore).isFalse()
    }

    @Test
    fun `populated batch preserves records, cursor, and hasMore flag`() {
        val record = SourceRecord(
            externalId = "ext-1",
            payload = mapOf("a" to 1),
            sourceMetadata = null,
        )

        val batch = RecordBatch(
            records = listOf(record),
            nextCursor = "t-42",
            hasMore = true,
        )

        assertThat(batch.records).hasSize(1)
        assertThat(batch.records.first().externalId).isEqualTo("ext-1")
        assertThat(batch.records.first().payload).containsEntry("a", 1)
        assertThat(batch.nextCursor).isEqualTo("t-42")
        assertThat(batch.hasMore).isTrue()
    }

    @Test
    fun `destructuring returns the three fields unchanged`() {
        val batch = RecordBatch(
            records = emptyList(),
            nextCursor = "cursor-x",
            hasMore = true,
        )

        val (records, nextCursor, hasMore) = batch

        assertThat(records).isEqualTo(batch.records)
        assertThat(nextCursor).isEqualTo("cursor-x")
        assertThat(hasMore).isTrue()
    }

    @Test
    fun `nextCursor accepts nullable String type`() {
        val nullable: String? = null
        val batch = RecordBatch(records = emptyList(), nextCursor = nullable, hasMore = false)
        assertThat(batch.nextCursor).isNull()
    }
}
