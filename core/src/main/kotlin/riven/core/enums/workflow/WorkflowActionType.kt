package cranium.core.enums.workflow

enum class WorkflowActionType {
    CREATE_ENTITY,
    UPDATE_ENTITY,
    DELETE_ENTITY,
    QUERY_ENTITY,
    BULK_UPDATE_ENTITY,
    LINK_ENTITY,
    INTEGRATION_REQUEST,
    HTTP_REQUEST,
    SET_ENVIRONMENT_VARIABLE,
    MAP_DATA,
}