package riven.core.enums.entity

/**
 * The Entity Type Surface role dictates how an entity/entity type is used within the application. As entities
 * are now the unified system for data, internal business knowledge and derived signals for proactive capabilities
 */
enum class EntityTypeRole
{
    CATALOG, // Regular Business Data that is ingested into the system and can be used for various purposes such as driving insights, populating dashboards, and enabling proactive capabilities. This is the most common type of entity and represents the core data that users interact with within the application.
    KNOWLEDGE, // Internal business knowledge that is surfaced to users. This is generated from internal data and insights, and is used to drive informed decision-making and actions within the application.
    SIGNAL // Derived signals that are surfaced to users as proactive capabilities. These are generated from internal business knowledge and data, and are used to drive proactive actions and insights within the application.
}