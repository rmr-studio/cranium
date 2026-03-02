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
import riven.core.models.response.common.ErrorResponse

@ControllerAdvice
class ExceptionHandler(private val logger: KLogger, private val config: ApplicationConfigurationProperties) {

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(ex: AccessDeniedException): ResponseEntity<ErrorResponse> {
        storeExceptionForAnalytics(ex)
        return ErrorResponse(
            statusCode = HttpStatus.FORBIDDEN,
            error = "ACCESS DENIED",
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
            error = "INVALID RELATIONSHIP",
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
            error = "SCHEMA VALIDATION FAILED",
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
            error = "AUTHORIZATION DENIED",
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
            error = "RESOURCE NOT FOUND",
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
            error = "INVALID ARGUMENT",
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
            error = "CONFLICT",
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
            error = "CONFLICT",
            message = ex.message ?: "Unique constraint violation occurred",
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
