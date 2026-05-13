package cranium.core.enums.workflow

enum class WorkflowNodeConfigFieldType {
    STRING,
    NUMBER,
    DURATION,
    BOOLEAN,
    TEMPLATE,      // Supports {{ steps.x.output.y }} expressions
    UUID,
    ENUM,
    JSON,
    KEY_VALUE,     // Map<String, String> editor
    ENTITY_TYPE,   // Entity type selector
    ENTITY_QUERY   // Entity Query Builder
}