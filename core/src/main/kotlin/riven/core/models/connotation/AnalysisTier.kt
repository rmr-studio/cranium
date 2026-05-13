package cranium.core.models.connotation

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Analysis strategy used to populate the SENTIMENT metadata.
 *
 * - DETERMINISTIC — manifest-dcranium source mapper from `connotationSignals` (Phase B).
 * - CLASSIFIER — local Ollama classifier (deferred until Layer 4 needs FREETEXT-only coverage).
 * - INFERENCE — LLM API inference (deferred — no email/Slack manifests yet).
 */
enum class AnalysisTier {
    @JsonProperty("DETERMINISTIC")
    DETERMINISTIC,

    @JsonProperty("CLASSIFIER")
    CLASSIFIER,

    @JsonProperty("INFERENCE")
    INFERENCE,
}
