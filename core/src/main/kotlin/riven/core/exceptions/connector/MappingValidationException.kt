package riven.core.exceptions.connector

/**
 * Thrown by [riven.core.service.connector.mapping.DataConnectorFieldMappingService]
 * when a POST /mapping request fails semantic validation (duplicate identifier
 * flag, duplicate cursor flag, unknown column name, missing attribute name on
 * a mapped column, etc.).
 *
 * Maps to HTTP 400 via [riven.core.exceptions.ExceptionHandler].
 */
class MappingValidationException(message: String) : RuntimeException(message)
