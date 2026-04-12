package riven.core.enums.integration

enum class SourceType {
    USER_CREATED,
    INTEGRATION,
    IMPORT,
    API,
    WORKFLOW,
    IDENTITY_MATCH,
    TEMPLATE,
    PROJECTED,

    /** Custom data source (Postgres, CSV, etc.) introspected at runtime. Phase 1 foundation. */
    CUSTOM_SOURCE,
}
