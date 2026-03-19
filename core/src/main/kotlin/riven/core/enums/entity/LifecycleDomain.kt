package riven.core.enums.entity

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Lifecycle domain classification for entity types — WHERE data sits in the customer journey.
 * Orthogonal to SemanticGroup (WHAT the data IS).
 *
 * The customer lifecycle is a concurrent domain map, not a linear pipeline.
 * Multiple domains are active simultaneously (a customer is using the product,
 * filing support tickets, and generating billing events at the same time).
 *
 * - ACQUISITION: Marketing channels, campaigns, ad spend, referrals
 * - ONBOARDING: First-run events, setup completion, initial support
 * - USAGE: Product activity, feature adoption, engagement
 * - SUPPORT: Help desk, tickets, feedback, complaints
 * - BILLING: Payments, subscriptions, plan changes, invoices
 * - RETENTION: Churn events, win-back, reactivation, derived outcomes
 * - UNCATEGORIZED: Non-lifecycle or entity types that span multiple domains (e.g., Customer, Communication)
 */
enum class LifecycleDomain {
    @JsonProperty("ACQUISITION") ACQUISITION,
    @JsonProperty("ONBOARDING") ONBOARDING,
    @JsonProperty("USAGE") USAGE,
    @JsonProperty("SUPPORT") SUPPORT,
    @JsonProperty("BILLING") BILLING,
    @JsonProperty("RETENTION") RETENTION,
    @JsonProperty("UNCATEGORIZED") UNCATEGORIZED
}
