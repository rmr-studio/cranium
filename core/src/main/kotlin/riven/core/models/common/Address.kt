package cranium.core.models.common

data class Address(
    val street: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String
)