package riven.core.enums.entity.semantics

/**
 * Categorical classification for entity types, enabling semantic constraint-based
 * relationship targeting.
 *
 * - CUSTOMER: customer, contact, lead, or person entities
 * - PRODUCT: product, service, or offering entities
 * - TRANSACTION: order, invoice, payment, or exchange entities
 * - COMMUNICATION: email, message, note, or interaction entities
 * - SUPPORT: ticket, case, or issue entities
 * - FINANCIAL: account, budget, or monetary entities
 * - OPERATIONAL: task, project, process, or workflow entities
 * - CUSTOM: user-defined classification that doesn't fit standard groups
 * - UNCATEGORIZED: default for entity types without explicit classification
 */
enum class SemanticGroup {
    CUSTOMER,
    PRODUCT,
    TRANSACTION,
    COMMUNICATION,
    SUPPORT,
    FINANCIAL,
    OPERATIONAL,
    CUSTOM,
    UNCATEGORIZED
}
