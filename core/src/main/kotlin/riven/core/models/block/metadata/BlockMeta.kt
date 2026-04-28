package riven.core.models.block.metadata

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import riven.core.models.common.json.JsonObject

// ---- Transient/system metadata (not business data) ----
@JsonInclude(JsonInclude.Include.NON_NULL)
data class BlockMeta(
    var validationErrors: List<String> = emptyList(),
    @get:Schema(type = "object", implementation = Any::class)
    val computedFields: JsonObject? = null,    // optional server-computed values for UI summaries
    var lastValidatedVersion: Int? = null      // BlockType.version used for last validation
)