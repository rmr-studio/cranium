package riven.core.service.dev

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import riven.core.enums.common.validation.SchemaType
import riven.core.lifecycle.CoreModelAttribute
import riven.core.lifecycle.CoreModelDefinition
import riven.core.models.common.json.JsonValue
import java.time.LocalDate
import kotlin.random.Random

/**
 * Generates realistic sample entity data for dev workspace seeding.
 * Uses deterministic randomness (seed=42) and hardcoded pools per model key.
 */
@Component
@ConditionalOnProperty(name = ["riven.dev.seed.enabled"], havingValue = "true")
class DevSeedDataGenerator {

    private val random = Random(42)

    // ------ Entity Counts Per Model ------

    private val entityCounts = mapOf(
        "customer" to 20,
        "communication" to 15,
        "support-ticket" to 12,
        "subscription" to 15,
        "feature-usage-event" to 20,
        "acquisition-source" to 6,
        "billing-event" to 15,
        "churn-event" to 5,
        "order" to 25,
        "product" to 10,
        "order-line-item" to 40,
    )

    fun getEntityCount(modelKey: String): Int = entityCounts[modelKey] ?: 10

    // ------ Data Pools ------

    private val firstNames = listOf(
        "James", "Sarah", "Michael", "Emily", "David", "Jessica", "Robert", "Ashley",
        "William", "Amanda", "Daniel", "Megan", "Andrew", "Lauren", "Joshua", "Nicole",
        "Christopher", "Rachel", "Matthew", "Samantha",
    )

    private val lastNames = listOf(
        "Anderson", "Chen", "Martinez", "Thompson", "Patel", "Garcia", "Wilson", "Taylor",
        "Johnson", "Lee", "Brown", "Williams", "Jones", "Davis", "Miller", "Moore",
        "Jackson", "White", "Harris", "Clark",
    )

    private val companyNames = listOf(
        "Acme Corp", "NovaTech Solutions", "Meridian Health", "Atlas Dynamics",
        "Pinnacle Software", "Vertex Analytics", "Horizon Media", "Quantum Labs",
        "Summit Financial", "Apex Consulting", "CloudScale Inc", "DataForge",
        "BrightPath Systems", "TerraFlow", "Lunar Digital",
    )

    private val emailDomains = listOf("acme.com", "novatech.io", "meridian.co", "atlas.dev", "pinnacle.io")

    private val ticketSubjects = listOf(
        "Cannot log in after password reset", "Dashboard loading slowly",
        "Export feature returns empty CSV", "Billing discrepancy on last invoice",
        "Feature request: dark mode", "API rate limit exceeded",
        "Mobile app crashes on startup", "Integration not syncing data",
        "Permission error accessing reports", "SSO configuration help needed",
        "Webhook events not firing", "Account merger request",
    )

    private val communicationSubjects = listOf(
        "Quarterly business review", "Onboarding kickoff", "Feature walkthrough",
        "Contract renewal discussion", "Support escalation follow-up",
        "Product feedback session", "Pricing discussion", "Integration planning",
        "Executive sponsor intro", "Expansion opportunity", "Training session",
        "Churn risk mitigation", "Success milestone review", "Technical deep-dive",
        "Partnership exploration",
    )

    private val subscriptionPlans = listOf(
        "Starter", "Starter", "Starter", "Pro", "Pro", "Pro", "Pro",
        "Business", "Business", "Business", "Enterprise", "Enterprise",
        "Growth", "Growth", "Scale",
    )

    private val featureNames = listOf(
        "Dashboard", "Reports", "API", "Export", "Import", "SSO",
        "Integrations", "Workflows", "Notifications", "Search",
        "Bulk Actions", "Custom Fields", "Audit Log", "Webhooks",
        "Team Management", "Analytics", "Automations", "Templates",
        "File Uploads", "Collaboration",
    )

    private val acquisitionSources = listOf(
        "Google Ads — Brand" to "paid-search",
        "Google Ads — Non-Brand" to "paid-search",
        "LinkedIn Ads" to "paid-social",
        "Organic Search" to "organic",
        "Product Hunt Launch" to "product-hunt",
        "Customer Referrals" to "referral",
    )

    private val productNames = listOf(
        "Classic Tee" to "TEE-001", "Organic Hoodie" to "HOD-002",
        "Slim Fit Jeans" to "JNS-003", "Leather Sneakers" to "SNK-004",
        "Canvas Backpack" to "BAG-005", "Wool Beanie" to "BNE-006",
        "Silk Scarf" to "SCF-007", "Running Shorts" to "SHR-008",
        "Sunglasses" to "SNG-009", "Watch" to "WCH-010",
    )

    // ------ Generation Methods ------

    /**
     * Generates sample entity data for the given model.
     * Returns list of maps keyed by attribute string key (e.g., "email" → "jane@acme.com").
     */
    fun generate(model: CoreModelDefinition): List<Map<String, JsonValue>> {
        val count = getEntityCount(model.key)
        return when (model.key) {
            "customer" -> generateCustomers(model, count)
            "communication" -> generateCommunications(model, count)
            "support-ticket" -> generateSupportTickets(model, count)
            "subscription" -> generateSubscriptions(count)
            "feature-usage-event" -> generateFeatureUsageEvents(count)
            "acquisition-source" -> generateAcquisitionSources()
            "billing-event" -> generateBillingEvents(model, count)
            "churn-event" -> generateChurnEvents(model, count)
            "order" -> generateOrders(count)
            "product" -> generateProducts()
            "order-line-item" -> generateOrderLineItems(count)
            else -> generateGeneric(model, count)
        }
    }

    // ------ Model-Specific Generators ------

    private fun generateCustomers(model: CoreModelDefinition, count: Int): List<Map<String, JsonValue>> {
        val hasCompany = model.attributes.containsKey("company")
        return (0 until count).map { i ->
            val first = firstNames[i % firstNames.size]
            val last = lastNames[i % lastNames.size]
            val domain = emailDomains[i % emailDomains.size]
            buildMap {
                put("name", "$first $last")
                put("email", "${first.lowercase()}.${last.lowercase()}@$domain")
                put("phone", "+1${randomDigits(10)}")
                put("status", pickFromEnum(model, "status"))
                put("created-date", randomPastDate(730))
                if (hasCompany) put("company", companyNames[i % companyNames.size])
            }
        }
    }

    private fun generateCommunications(model: CoreModelDefinition, count: Int): List<Map<String, JsonValue>> {
        return (0 until count).map { i ->
            buildMap {
                put("subject", communicationSubjects[i % communicationSubjects.size])
                put("direction", if (random.nextBoolean()) "outbound" else "inbound")
                put("date", randomPastDate(180))
                put("summary", "Discussion notes for ${communicationSubjects[i % communicationSubjects.size].lowercase()}")
                put("outcome", pickFromEnum(model, "outcome"))
                put("channel", pickFromEnum(model, "channel"))
                put("type", pickFromEnum(model, "type"))
                if (model.attributes.containsKey("follow-up-date") && random.nextFloat() > 0.3f) {
                    put("follow-up-date", randomFutureDate(90))
                }
            }
        }
    }

    private fun generateSupportTickets(model: CoreModelDefinition, count: Int): List<Map<String, JsonValue>> {
        return (0 until count).map { i ->
            val isResolved = random.nextFloat() > 0.4f
            buildMap {
                put("subject", ticketSubjects[i % ticketSubjects.size])
                put("description", "Detailed description for: ${ticketSubjects[i % ticketSubjects.size]}")
                put("priority", pickFromEnum(model, "priority"))
                put("status", if (isResolved) "resolved" else pickFromEnum(model, "status"))
                put("channel", pickFromEnum(model, "channel"))
                put("created-date", randomPastDate(365))
                if (isResolved) put("resolved-date", randomPastDate(30))
                put("category", pickFromEnum(model, "category"))
            }
        }
    }

    private fun generateSubscriptions(count: Int): List<Map<String, JsonValue>> {
        return (0 until count).map { i ->
            val plan = subscriptionPlans[i % subscriptionPlans.size]
            val mrr = when (plan) {
                "Starter" -> random.nextInt(29, 79)
                "Pro" -> random.nextInt(79, 199)
                "Business" -> random.nextInt(199, 499)
                "Enterprise" -> random.nextInt(499, 1999)
                "Growth" -> random.nextInt(149, 399)
                "Scale" -> random.nextInt(399, 999)
                else -> random.nextInt(29, 499)
            }
            val cancelled = random.nextFloat() < 0.15f
            buildMap {
                put("plan-name", "$plan Plan")
                put("status", if (cancelled) "cancelled" else listOf("trialing", "active", "active", "active", "past-due", "paused")[random.nextInt(6)])
                put("mrr", mrr)
                put("billing-interval", listOf("monthly", "monthly", "quarterly", "annual")[random.nextInt(4)])
                put("start-date", randomPastDate(730))
                if (cancelled) put("cancel-date", randomPastDate(60))
            }
        }
    }

    private fun generateFeatureUsageEvents(count: Int): List<Map<String, JsonValue>> {
        return (0 until count).map { i ->
            mapOf(
                "feature-name" to featureNames[i % featureNames.size],
                "action" to listOf("viewed", "used", "used", "completed", "error")[random.nextInt(5)],
                "date" to randomPastDate(90),
                "count" to random.nextInt(1, 150),
            )
        }
    }

    private fun generateAcquisitionSources(): List<Map<String, JsonValue>> {
        return acquisitionSources.map { (name, type) ->
            mapOf<String, JsonValue>(
                "name" to name,
                "type" to type,
                "spend" to random.nextInt(500, 25000),
                "active" to (random.nextFloat() > 0.2f),
            )
        }
    }

    private fun generateBillingEvents(model: CoreModelDefinition, count: Int): List<Map<String, JsonValue>> {
        return (0 until count).map {
            val type = pickFromEnum(model, "type") as String
            val amount = when (type) {
                "charge" -> random.nextInt(29, 999)
                "refund" -> random.nextInt(29, 499)
                "credit" -> random.nextInt(10, 100)
                "shipping-fee" -> random.nextInt(5, 30)
                else -> random.nextInt(10, 200)
            }
            buildMap {
                put("description", "${type.replaceFirstChar { c -> c.uppercase() }} — \$$amount")
                put("amount", amount)
                put("date", randomPastDate(365))
                put("type", type)
            }
        }
    }

    private fun generateChurnEvents(model: CoreModelDefinition, count: Int): List<Map<String, JsonValue>> {
        return (0 until count).map {
            val revenueLost = random.nextInt(50, 2000)
            buildMap {
                put("date", randomPastDate(365))
                put("type", if (random.nextFloat() > 0.3f) "voluntary" else "involuntary")
                put("reason", pickFromEnum(model, "reason"))
                val revenueKey = if (model.attributes.containsKey("mrr-lost")) "mrr-lost" else "revenue-lost"
                if (model.attributes.containsKey(revenueKey)) put(revenueKey, revenueLost)
            }
        }
    }

    private fun generateOrders(count: Int): List<Map<String, JsonValue>> {
        return (0 until count).map { i ->
            val total = random.nextInt(15, 500)
            mapOf<String, JsonValue>(
                "order-number" to "ORD-${1001 + i}",
                "total" to total,
                "status" to listOf("pending", "confirmed", "shipped", "delivered", "delivered", "delivered")[random.nextInt(6)],
                "order-date" to randomPastDate(365),
                "payment-status" to listOf("pending", "paid", "paid", "paid", "refunded")[random.nextInt(5)],
            )
        }
    }

    private fun generateProducts(): List<Map<String, JsonValue>> {
        return productNames.map { (name, sku) ->
            mapOf<String, JsonValue>(
                "name" to name,
                "sku" to sku,
                "price" to random.nextInt(15, 250),
                "category" to listOf("apparel", "apparel", "apparel", "electronics", "accessories", "home", "beauty")[random.nextInt(7)],
            )
        }
    }

    private fun generateOrderLineItems(count: Int): List<Map<String, JsonValue>> {
        return (0 until count).map {
            val unitPrice = random.nextInt(10, 200)
            val hasDiscount = random.nextFloat() > 0.6f
            buildMap {
                put("quantity", random.nextInt(1, 6))
                put("unit-price", unitPrice)
                if (hasDiscount) put("discount", random.nextInt(1, unitPrice / 3 + 1))
                put("variant-id", "VAR-${random.nextInt(100, 999)}")
            }
        }
    }

    /**
     * Fallback generator for any model not explicitly handled.
     * Generates data based on attribute SchemaType.
     */
    private fun generateGeneric(model: CoreModelDefinition, count: Int): List<Map<String, JsonValue>> {
        return (0 until count).map { i ->
            model.attributes.mapValues { (_, attr) ->
                generateValueForAttribute(attr, i)
            }
        }
    }

    // ------ Helpers ------

    private fun pickFromEnum(model: CoreModelDefinition, attrKey: String): JsonValue {
        val attr = model.attributes[attrKey] ?: return null
        val options = attr.options?.enum ?: return null
        return options[random.nextInt(options.size)]
    }

    private fun generateValueForAttribute(attr: CoreModelAttribute, index: Int): JsonValue {
        return when (attr.schemaType) {
            SchemaType.TEXT -> "Sample ${attr.label} $index"
            SchemaType.EMAIL -> "${firstNames[index % firstNames.size].lowercase()}@example.com"
            SchemaType.PHONE -> "+1${randomDigits(10)}"
            SchemaType.URL -> "https://example.com/${index}"
            SchemaType.NUMBER -> random.nextInt(1, 1000)
            SchemaType.CURRENCY -> random.nextInt(10, 10000)
            SchemaType.PERCENTAGE -> random.nextDouble(0.0, 1.0)
            SchemaType.RATING -> random.nextInt(1, 6)
            SchemaType.CHECKBOX -> random.nextBoolean()
            SchemaType.DATE, SchemaType.DATETIME -> randomPastDate(365)
            SchemaType.SELECT -> attr.options?.enum?.let { it[random.nextInt(it.size)] }
            SchemaType.MULTI_SELECT -> attr.options?.enum?.let { listOf(it[random.nextInt(it.size)]) }
            SchemaType.ID -> null // auto-generated by system
            else -> null
        }
    }

    private fun randomPastDate(maxDaysAgo: Int): String {
        val daysAgo = random.nextInt(1, maxDaysAgo + 1)
        return LocalDate.now().minusDays(daysAgo.toLong()).toString()
    }

    private fun randomFutureDate(maxDaysAhead: Int): String {
        val daysAhead = random.nextInt(1, maxDaysAhead + 1)
        return LocalDate.now().plusDays(daysAhead.toLong()).toString()
    }

    private fun randomDigits(count: Int): String {
        return buildString { repeat(count) { append(random.nextInt(0, 10)) } }
    }
}
