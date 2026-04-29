package riven.core.models.connotation

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Tier of analysis used to populate the SENTIMENT metadata.
 *
 * - TIER_1 — deterministic source mapper from manifest connotationSignals (Phase B).
 * - TIER_2 — local Ollama classifier (deferred until Layer 4 needs FREETEXT-only coverage).
 * - TIER_3 — LLM API inference (deferred — no email/Slack manifests yet).
 */
enum class AnalysisTier {
    @JsonProperty("TIER_1")
    TIER_1,

    @JsonProperty("TIER_2")
    TIER_2,

    @JsonProperty("TIER_3")
    TIER_3,
}
