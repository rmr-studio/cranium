package riven.core.lifecycle

import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.lifecycle.models.saas.SaasBillingEventModel
import riven.core.lifecycle.models.saas.SaasChurnEventModel
import riven.core.lifecycle.models.saas.SaasCommunicationModel
import riven.core.lifecycle.models.saas.SaasCustomerModel
import riven.core.lifecycle.models.dtc.DtcBillingEventModel
import riven.core.lifecycle.models.dtc.DtcChurnEventModel
import riven.core.lifecycle.models.dtc.DtcCommunicationModel
import riven.core.lifecycle.models.dtc.DtcCustomerModel

/**
 * A set of core models that form a complete lifecycle data model for a business type.
 * Each business type (B2C SaaS, DTC E-commerce) has one model set.
 *
 * The model set owns:
 * - Which core models are included (business-type variants for tailored models, shared singletons for universal models)
 * - Cross-model relationships that are vertical-specific (e.g., customer-subscriptions is B2C only)
 * - The manifest key used for catalog registration and template installation
 */
data class CoreModelSet(
    val manifestKey: String,
    val name: String,
    val description: String,
    val models: List<CoreModelDefinition>,
    val additionalRelationships: List<CoreModelRelationship> = emptyList(),
)

// ------ Business Type Model Sets ------

val B2C_SAAS_MODELS = CoreModelSet(
    manifestKey = "b2c-saas",
    name = "B2C SaaS",
    description = "Lifecycle template for B2C and prosumer SaaS businesses. Traces the full customer journey from acquisition through subscription, feature usage, support, and billing to retention or churn.",
    models = listOf(
        SaasCustomerModel,
        SaasCommunicationModel,
        riven.core.lifecycle.models.SupportTicketModel,
        riven.core.lifecycle.models.SubscriptionModel,
        riven.core.lifecycle.models.FeatureUsageEventModel,
        riven.core.lifecycle.models.AcquisitionSourceModel,
        SaasBillingEventModel,
        SaasChurnEventModel,
    ),
    additionalRelationships = listOf(
        CoreModelRelationship(
            key = "customer-subscriptions",
            name = "Subscriptions",
            sourceModelKey = "customer",
            targetModelKey = "subscription",
            cardinality = EntityRelationshipCardinality.ONE_TO_MANY,
            inverseName = "Customer",
            semantics = RelationshipSemantics(
                definition = "A customer holds one or more subscriptions to the product.",
                tags = listOf("subscription", "billing"),
            ),
        ),
        CoreModelRelationship(
            key = "customer-feature-usage",
            name = "Feature Usage",
            sourceModelKey = "customer",
            targetModelKey = "feature-usage-event",
            cardinality = EntityRelationshipCardinality.ONE_TO_MANY,
            inverseName = "Customer",
            semantics = RelationshipSemantics(
                definition = "Feature usage events tracking how the customer uses the product.",
                tags = listOf("engagement", "product-analytics"),
            ),
        ),
        CoreModelRelationship(
            key = "subscription-feature-usage",
            name = "Feature Usage",
            sourceModelKey = "subscription",
            targetModelKey = "feature-usage-event",
            cardinality = EntityRelationshipCardinality.ONE_TO_MANY,
            inverseName = "Subscription",
            semantics = RelationshipSemantics(
                definition = "Feature usage events associated with a specific subscription.",
                tags = listOf("usage", "subscription"),
            ),
        ),
    ),
)

val DTC_ECOMMERCE_MODELS = CoreModelSet(
    manifestKey = "dtc-ecommerce",
    name = "DTC E-commerce",
    description = "Lifecycle template for direct-to-consumer e-commerce businesses. Traces the full customer journey from acquisition channel through orders, support, and billing to retention or churn.",
    models = listOf(
        DtcCustomerModel,
        DtcCommunicationModel,
        riven.core.lifecycle.models.SupportTicketModel,
        riven.core.lifecycle.models.OrderModel,
        riven.core.lifecycle.models.ProductModel,
        riven.core.lifecycle.models.AcquisitionSourceModel,
        DtcBillingEventModel,
        DtcChurnEventModel,
    ),
    additionalRelationships = listOf(
        CoreModelRelationship(
            key = "customer-orders",
            name = "Orders",
            sourceModelKey = "customer",
            targetModelKey = "order",
            cardinality = EntityRelationshipCardinality.ONE_TO_MANY,
            inverseName = "Customer",
            semantics = RelationshipSemantics(
                definition = "A customer places orders to purchase products.",
                tags = listOf("purchase", "revenue"),
            ),
        ),
        CoreModelRelationship(
            key = "order-products",
            name = "Products",
            sourceModelKey = "order",
            targetModelKey = "product",
            cardinality = EntityRelationshipCardinality.MANY_TO_MANY,
            inverseName = "Orders",
            semantics = RelationshipSemantics(
                definition = "Products included in an order.",
                tags = listOf("line-items", "catalogue"),
            ),
        ),
    ),
)
