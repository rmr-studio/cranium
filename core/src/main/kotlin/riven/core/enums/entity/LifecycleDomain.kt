package riven.core.enums.entity

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Lifecycle domain classification for entity types — WHERE data sits in the customer journey.
 * Orthogonal to SemanticGroup (WHAT the data IS).
 *
 * The customer lifecycle is a concurrent domain map, not a linear pipeline.
 * Multiple domains are active simultaneously (a customer is placing orders,
 * filing support tickets, and receiving shipments at the same time).
 *
 * - ACQUISITION: Marketing channels, attribution sources, referrals
 * - MARKETING: Paid campaigns, ad creatives, ad spend events
 * - ONBOARDING: First-run events, setup completion, initial support
 * - COMMERCE: Product catalogue, variants, collections
 * - FULFILLMENT: Shipments, shipment events, carriers
 * - ENGAGEMENT: Social posts, comments, mentions, reviews
 * - SUPPORT: Help desk, tickets, feedback, complaints
 * - BILLING: Orders, payments, subscriptions, plan changes, invoices
 * - RETENTION: Churn events, win-back, reactivation, returns, derived outcomes
 * - UNCATEGORIZED: Non-lifecycle or entity types that span multiple domains (e.g., Customer, Communication)
 */
enum class LifecycleDomain {
    @JsonProperty("ACQUISITION") ACQUISITION,
    @JsonProperty("MARKETING") MARKETING,
    @JsonProperty("ONBOARDING") ONBOARDING,
    @JsonProperty("COMMERCE") COMMERCE,
    @JsonProperty("FULFILLMENT") FULFILLMENT,
    @JsonProperty("ENGAGEMENT") ENGAGEMENT,
    @JsonProperty("SUPPORT") SUPPORT,
    @JsonProperty("BILLING") BILLING,
    @JsonProperty("RETENTION") RETENTION,
    @JsonProperty("UNCATEGORIZED") UNCATEGORIZED
}
