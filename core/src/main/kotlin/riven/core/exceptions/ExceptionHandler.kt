package riven.core.exceptions

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import riven.core.configuration.properties.ApplicationConfigurationProperties
import com.fasterxml.jackson.core.JsonProcessingException
import riven.core.enums.common.ApiError
import riven.core.exceptions.customsource.ReadOnlyVerificationException
import riven.core.exceptions.customsource.SsrfRejectedException
import riven.core.exceptions.query.QueryExecutionException
import riven.core.exceptions.query.QueryFilterException
import riven.core.models.response.common.ErrorResponse

@ControllerAdvice
class ExceptionHandler(private val logger: KLogger, private val config: ApplicationConfigurationProperties) {

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(ex: AccessDeniedException): ResponseEntity<ErrorResponse> {
        storeExceptionForAnalytics(ex)
        return ErrorResponse(
            statusCode = HttpStatus.FORBIDDEN,
            error = ApiError.ACCESS_DENIED,
            message = ex.message ?: "Access denied",
            stackTrace = config.includeStackTrace.takeIf { it }?.let { ex.stackTraceToString() }
        ).also { logger.error { it } }.let {
            ResponseEntity(it, it.statusCode)
        }
    }

    @ExceptionHandler(InvalidRelationshipException::class)
    fun handleInvalidRelationshipException(ex: InvalidRelationshipException): ResponseEntity<ErrorResponse> {
        storeExceptionForAnalytics(ex)
        return ErrorResponse(
            statusCode = HttpStatus.BAD_REQUEST,
            error = ApiError.INVALID_RELATIONSHIP,
            message = ex.message ?: "Invalid relationship",
            stackTrace = config.includeStackTrace.takeIf { it }?.let { ex.stackTraceToString() }
        ).also { logger.error { it } }.let {
            ResponseEntity(it, it.statusCode)
        }
    }

    @ExceptionHandler(SchemaValidationException::class)
    fun handleSchemaValidationException(ex: SchemaValidationException): ResponseEntity<ErrorResponse> {
        storeExceptionForAnalytics(ex)
        return ErrorResponse(
            statusCode = HttpStatus.BAD_REQUEST,
            error = ApiError.SCHEMA_VALIDATION_FAILED,
            message = "Schema validation failed: ${ex.reasons.joinToString("; ")}",
            stackTrace = config.includeStackTrace.takeIf { it }?.let { ex.stackTraceToString() }
        ).also { logger.error { it } }.let {
            ResponseEntity(it, it.statusCode)
        }
    }

    @ExceptionHandler(AuthorizationDeniedException::class)
    fun handleAuthorizationDenied(ex: AuthorizationDeniedException): ResponseEntity<ErrorResponse> {
        storeExceptionForAnalytics(ex)
        return ErrorResponse(
            statusCode = HttpStatus.FORBIDDEN,
            error = ApiError.AUTHORIZATION_DENIED,
            message = ex.message ?: "Authorisation denied",
            stackTrace = config.includeStackTrace.takeIf { it }?.let { ex.stackTraceToString() }
        ).also { logger.error { it } }.let {
            ResponseEntity(it, it.statusCode)
        }
    }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFoundException(ex: NotFoundException): ResponseEntity<ErrorResponse> {
        storeExceptionForAnalytics(ex)
        return ErrorResponse(
            statusCode = HttpStatus.NOT_FOUND,
            error = ApiError.RESOURCE_NOT_FOUND,
            message = ex.message ?: "Resource not found",
            stackTrace = config.includeStackTrace.takeIf { it }?.let { ex.stackTraceToString() }
        ).also { logger.error { it } }.let {
            ResponseEntity(it, it.statusCode)
        }
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        storeExceptionForAnalytics(ex)
        return ErrorResponse(
            statusCode = HttpStatus.BAD_REQUEST,
            error = ApiError.INVALID_ARGUMENT,
            message = ex.message ?: "Invalid argument provided",
            stackTrace = config.includeStackTrace.takeIf { it }?.let { ex.stackTraceToString() }
        ).also { logger.error { it } }.let {
            ResponseEntity(it, it.statusCode)
        }
    }

    @ExceptionHandler(ConflictException::class)
    fun handleConflictException(ex: ConflictException): ResponseEntity<ErrorResponse> {
        storeExceptionForAnalytics(ex)
        return ErrorResponse(
            statusCode = HttpStatus.CONFLICT,
            error = ApiError.CONFLICT,
            message = ex.message ?: "Conflict occurred",
            stackTrace = config.includeStackTrace.takeIf { it }?.let { ex.stackTraceToString() }
        ).also { logger.error { it } }.let {
            ResponseEntity(it, it.statusCode)
        }
    }

    @ExceptionHandler(UniqueConstraintViolationException::class)
    fun handleConflictException(ex: UniqueConstraintViolationException): ResponseEntity<ErrorResponse> {
        storeExceptionForAnalytics(ex)
        return ErrorResponse(
            statusCode = HttpStatus.CONFLICT,
            error = ApiError.CONFLICT,
            message = ex.message ?: "Unique constraint violation occurred",
            stackTrace = config.includeStackTrace.takeIf { it }?.let { ex.stackTraceToString() }
        ).also { logger.error { it } }.let {
            ResponseEntity(it, it.statusCode)
        }
    }

    @ExceptionHandler(JsonProcessingException::class)
    fun handleJsonProcessingException(ex: JsonProcessingException): ResponseEntity<ErrorResponse> {
        storeExceptionForAnalytics(ex)
        return ErrorResponse(
            statusCode = HttpStatus.BAD_REQUEST,
            error = ApiError.INVALID_JSON,
            message = ex.originalMessage ?: "Malformed JSON in request",
            stackTrace = config.includeStackTrace.takeIf { it }?.let { ex.stackTraceToString() }
        ).also { logger.error { it } }.let {
            ResponseEntity(it, it.statusCode)
        }
    }

    // ------ Query Exception Handlers ------

    @ExceptionHandler(QueryFilterException::class)
    fun handleQueryFilterException(ex: QueryFilterException): ResponseEntity<ErrorResponse> {
        storeExceptionForAnalytics(ex)
        return ErrorResponse(
            statusCode = HttpStatus.BAD_REQUEST,
            error = ApiError.QUERY_VALIDATION_FAILED,
            message = ex.message ?: "Query filter validation failed",
            stackTrace = config.includeStackTrace.takeIf { it }?.let { ex.stackTraceToString() }
        ).also { logger.error { it } }.let {
            ResponseEntity(it, it.statusCode)
        }
    }

    @ExceptionHandler(QueryExecutionException::class)
    fun handleQueryExecutionException(ex: QueryExecutionException): ResponseEntity<ErrorResponse> {
        storeExceptionForAnalytics(ex)
        return ErrorResponse(
            statusCode = HttpStatus.INTERNAL_SERVER_ERROR,
            error = ApiError.QUERY_EXECUTION_FAILED,
            message = "An error occurred while executing the query",
            stackTrace = config.includeStackTrace.takeIf { it }?.let { ex.stackTraceToString() }
        ).also { logger.error { it } }.let {
            ResponseEntity(it, it.statusCode)
        }
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(ex: IllegalStateException): ResponseEntity<ErrorResponse> {
        storeExceptionForAnalytics(ex)
        return ErrorResponse(
            statusCode = HttpStatus.INTERNAL_SERVER_ERROR,
            error = ApiError.INTERNAL_ERROR,
            message = ex.message ?: "An unexpected server error occurred",
            stackTrace = config.includeStackTrace.takeIf { it }?.let { ex.stackTraceToString() }
        ).also { logger.error { it } }.let {
            ResponseEntity(it, it.statusCode)
        }
    }

    // ------ Storage Exception Handlers ------

    @ExceptionHandler(ContentTypeNotAllowedException::class)
    fun handleContentTypeNotAllowedException(ex: ContentTypeNotAllowedException): ResponseEntity<ErrorResponse> {
        storeExceptionForAnalytics(ex)
        return ErrorResponse(
            statusCode = HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            error = ApiError.UNSUPPORTED_MEDIA_TYPE,
            message = ex.message ?: "Content type not allowed",
            stackTrace = config.includeStackTrace.takeIf { it }?.let { ex.stackTraceToString() }
        ).also { logger.error { it } }.let {
            ResponseEntity(it, it.statusCode)
        }
    }

    @ExceptionHandler(FileSizeLimitExceededException::class)
    fun handleFileSizeLimitExceededException(ex: FileSizeLimitExceededException): ResponseEntity<ErrorResponse> {
        storeExceptionForAnalytics(ex)
        return ErrorResponse(
            statusCode = HttpStatus.PAYLOAD_TOO_LARGE,
            error = ApiError.PAYLOAD_TOO_LARGE,
            message = ex.message ?: "File size limit exceeded",
            stackTrace = config.includeStackTrace.takeIf { it }?.let { ex.stackTraceToString() }
        ).also { logger.error { it } }.let {
            ResponseEntity(it, it.statusCode)
        }
    }

    @ExceptionHandler(StorageNotFoundException::class)
    fun handleStorageNotFoundException(ex: StorageNotFoundException): ResponseEntity<ErrorResponse> {
        storeExceptionForAnalytics(ex)
        return ErrorResponse(
            statusCode = HttpStatus.NOT_FOUND,
            error = ApiError.STORAGE_NOT_FOUND,
            message = ex.message ?: "File not found in storage",
            stackTrace = config.includeStackTrace.takeIf { it }?.let { ex.stackTraceToString() }
        ).also { logger.error { it } }.let {
            ResponseEntity(it, it.statusCode)
        }
    }

    @ExceptionHandler(SignedUrlExpiredException::class)
    fun handleSignedUrlExpiredException(ex: SignedUrlExpiredException): ResponseEntity<ErrorResponse> {
        storeExceptionForAnalytics(ex)
        return ErrorResponse(
            statusCode = HttpStatus.FORBIDDEN,
            error = ApiError.SIGNED_URL_EXPIRED,
            message = ex.message ?: "Signed URL has expired or is invalid",
            stackTrace = config.includeStackTrace.takeIf { it }?.let { ex.stackTraceToString() }
        ).also { logger.error { it } }.let {
            ResponseEntity(it, it.statusCode)
        }
    }

    @ExceptionHandler(StorageProviderException::class)
    fun handleStorageProviderException(ex: StorageProviderException): ResponseEntity<ErrorResponse> {
        storeExceptionForAnalytics(ex)
        return ErrorResponse(
            statusCode = HttpStatus.BAD_GATEWAY,
            error = ApiError.STORAGE_PROVIDER_ERROR,
            message = ex.message ?: "Storage provider encountered an error",
            stackTrace = config.includeStackTrace.takeIf { it }?.let { ex.stackTraceToString() }
        ).also { logger.error { it } }.let {
            ResponseEntity(it, it.statusCode)
        }
    }

    // ------ Custom Source Connection Exception Handlers ------

    @ExceptionHandler(SsrfRejectedException::class)
    fun handleSsrfRejectedException(ex: SsrfRejectedException): ResponseEntity<ErrorResponse> {
        storeExceptionForAnalytics(ex)
        return ErrorResponse(
            statusCode = HttpStatus.BAD_REQUEST,
            error = ApiError.SSRF_REJECTED,
            message = ex.message ?: "Host rejected",
            stackTrace = config.includeStackTrace.takeIf { it }?.let { ex.stackTraceToString() }
        ).also { logger.error { it } }.let {
            ResponseEntity(it, it.statusCode)
        }
    }

    @ExceptionHandler(ReadOnlyVerificationException::class)
    fun handleReadOnlyVerificationException(ex: ReadOnlyVerificationException): ResponseEntity<ErrorResponse> {
        storeExceptionForAnalytics(ex)
        return ErrorResponse(
            statusCode = HttpStatus.BAD_REQUEST,
            error = ApiError.ROLE_VERIFICATION_FAILED,
            message = ex.message ?: "Role verification failed",
            stackTrace = config.includeStackTrace.takeIf { it }?.let { ex.stackTraceToString() }
        ).also { logger.error { it } }.let {
            ResponseEntity(it, it.statusCode)
        }
    }

    // ------ Analytics Integration ------

    private fun storeExceptionForAnalytics(ex: Exception) {
        val requestAttributes = RequestContextHolder.getRequestAttributes()
            as? ServletRequestAttributes ?: return
        val request = requestAttributes.request
        request.setAttribute("posthog.error.class", ex::class.simpleName)
    }
}
