package riven.core.enums.common

import com.fasterxml.jackson.annotation.JsonProperty

enum class ApiError {
    @JsonProperty("ACCESS_DENIED")
    ACCESS_DENIED,

    @JsonProperty("INVALID_RELATIONSHIP")
    INVALID_RELATIONSHIP,

    @JsonProperty("SCHEMA_VALIDATION_FAILED")
    SCHEMA_VALIDATION_FAILED,

    @JsonProperty("AUTHORIZATION_DENIED")
    AUTHORIZATION_DENIED,

    @JsonProperty("RESOURCE_NOT_FOUND")
    RESOURCE_NOT_FOUND,

    @JsonProperty("INVALID_ARGUMENT")
    INVALID_ARGUMENT,

    @JsonProperty("CONFLICT")
    CONFLICT,

    @JsonProperty("INVALID_JSON")
    INVALID_JSON,

    @JsonProperty("QUERY_VALIDATION_FAILED")
    QUERY_VALIDATION_FAILED,

    @JsonProperty("QUERY_EXECUTION_FAILED")
    QUERY_EXECUTION_FAILED,

    @JsonProperty("INTERNAL_ERROR")
    INTERNAL_ERROR,

    @JsonProperty("UNSUPPORTED_MEDIA_TYPE")
    UNSUPPORTED_MEDIA_TYPE,

    @JsonProperty("PAYLOAD_TOO_LARGE")
    PAYLOAD_TOO_LARGE,

    @JsonProperty("STORAGE_NOT_FOUND")
    STORAGE_NOT_FOUND,

    @JsonProperty("SIGNED_URL_EXPIRED")
    SIGNED_URL_EXPIRED,

    @JsonProperty("STORAGE_PROVIDER_ERROR")
    STORAGE_PROVIDER_ERROR,

    @JsonProperty("RATE_LIMIT_EXCEEDED")
    RATE_LIMIT_EXCEEDED,

    @JsonProperty("SSRF_REJECTED")
    SSRF_REJECTED,

    @JsonProperty("ROLE_VERIFICATION_FAILED")
    ROLE_VERIFICATION_FAILED,
}
