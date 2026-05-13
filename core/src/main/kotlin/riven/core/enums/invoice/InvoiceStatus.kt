package cranium.core.enums.invoice

enum class InvoiceStatus {
    // This status indicates that the invoice has been created but not yet paid.
    PENDING,

    // This status indicates that the invoice has been paid in full.
    PAID,

    // This status indicates that the invoice is currently overdue and requires immediate attention.
    OVERDUE,

    // This status indicates that the invoice is no longer valid due to changes in client details, and would need to be regenerated if needed for record keeping
    OUTDATED,

    // This status indicates that the invoice has been cancelled and will not be processed further.
    CANCELLED
}