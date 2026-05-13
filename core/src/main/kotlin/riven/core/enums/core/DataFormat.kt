package cranium.core.enums.core

enum class DataFormat(val jsonValue: String) {
    DATE("date"),
    DATETIME("date-time"),
    EMAIL("email"),
    PHONE("phone-number"),
    CURRENCY("currency"),
    URL("uri"),
    PERCENTAGE("percentage"),
}