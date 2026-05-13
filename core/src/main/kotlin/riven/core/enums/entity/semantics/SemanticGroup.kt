package cranium.core.enums.entity.semantics

/**
 * Categorical classification for entity types, enabling semantic constraint-based
 * relationship targeting.
 *
 * - CUSTOMER: customer, contact, lead, or person entities
 * - PRODUCT: product, service, or offering entities
 * - PRODUCT_VARIANT: SKU-level granularity of products (size, colour, etc.)
 * - COLLECTION: merchandising grouping of products
 * - TRANSACTION: order, invoice, payment, or exchange entities
 * - LINE_ITEM: individual product entry within a transaction
 * - COMMUNICATION: email, message, note, or interaction entities
 * - SUPPORT: ticket, case, or issue entities
 * - FINANCIAL: account, budget, spend events, or monetary entities
 * - OPERATIONAL: task, project, process, carrier, or workflow entities
 * - CAMPAIGN: paid marketing campaigns across ad platforms
 * - CREATIVE: ad creative assets (image, video, copy)
 * - PROMOTION: discount codes, promotional offers, vouchers, and other marketing-incentive entities
 * - SOCIAL_POST: owned-account social content
 * - SOCIAL_MENTION: brand mentions from non-owned accounts
 * - SHIPMENT: a package in transit
 * - SHIPMENT_EVENT: status change on a shipment
 * - REVIEW: product or service review
 * - CUSTOM: user-defined classification that doesn't fit standard groups
 * - UNCATEGORIZED: default for entity types without explicit classification
 */
enum class SemanticGroup {
    CUSTOMER,
    PRODUCT,
    PRODUCT_VARIANT,
    COLLECTION,
    TRANSACTION,
    LINE_ITEM,
    COMMUNICATION,
    SUPPORT,
    FINANCIAL,
    OPERATIONAL,
    CAMPAIGN,
    CREATIVE,
    PROMOTION,
    SOCIAL_POST,
    SOCIAL_MENTION,
    SHIPMENT,
    SHIPMENT_EVENT,
    REVIEW,
    CUSTOM,
    UNCATEGORIZED
}
