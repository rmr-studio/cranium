package cranium.core.models.workspace

data class WorkspacePaymentDetails(
    var bsb: String? = null,
    var accountNumber: String? = null,
    var accountName: String? = null
)