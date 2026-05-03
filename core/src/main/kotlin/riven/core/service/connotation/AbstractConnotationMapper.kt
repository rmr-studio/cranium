package riven.core.service.connotation

import riven.core.models.catalog.ConnotationSignals
import riven.core.models.common.json.JsonValue
import riven.core.models.connotation.SentimentAnalysisOutcome

sealed interface AbstractConnotationMapper
{
        fun analyze(
            signals: ConnotationSignals,
            sourceValue: JsonValue,
            themeValues: Map<String, JsonValue>,
            activeVersion: String,
        ): SentimentAnalysisOutcome
}