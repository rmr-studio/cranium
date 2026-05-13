package cranium.core.service.connotation

import cranium.core.models.catalog.ConnotationSignals
import cranium.core.models.common.json.JsonValue
import cranium.core.models.connotation.SentimentAnalysisOutcome

sealed interface AbstractConnotationMapper
{
        fun analyze(
            signals: ConnotationSignals,
            sourceValue: JsonValue,
            themeValues: Map<String, JsonValue>,
            activeVersion: String,
        ): SentimentAnalysisOutcome
}