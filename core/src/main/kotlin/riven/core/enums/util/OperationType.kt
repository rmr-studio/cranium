package riven.core.enums.util

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    READ,
    RESTORE,
    ANALYZE,
    REANALYZE;

    companion object {
        fun fromString(value: String): OperationType? {
            return entries.find { it.name.equals(value, ignoreCase = true) }
        }
    }
}