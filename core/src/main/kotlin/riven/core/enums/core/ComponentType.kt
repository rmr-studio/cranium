package cranium.core.enums.core

enum class ComponentType {
    // Containers
    LAYOUT_CONTAINER,
    LIST,

    // Bespoke Components
    CONTACT_CARD,
    ADDRESS_CARD,
    LINE_ITEM,

    // Primitive Components
    TABLE,
    IMAGE,
    BUTTON,
    ATTACHMENT,
    TEXT,

    // Error Handling
    FALLBACK,
}