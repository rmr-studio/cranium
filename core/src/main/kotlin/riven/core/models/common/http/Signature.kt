package cranium.core.models.common.http

import cranium.core.enums.common.http.SignatureAlgorithmType
import cranium.core.enums.common.http.SignatureHeaderType

data class Signature(
    val signatureHeader: SignatureHeaderType,
    val signatureAlgorithm: SignatureAlgorithmType
)