package cranium.core.models.response.common

import org.springframework.http.HttpStatus
import cranium.core.enums.common.ApiError

data class ErrorResponse(
    val statusCode: HttpStatus,
    val message: String,
    val error: ApiError,
    var stackTrace: String? = null
)
