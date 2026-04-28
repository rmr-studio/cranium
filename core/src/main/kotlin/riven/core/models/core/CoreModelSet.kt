package riven.core.models.core

import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.models.core.models.saas.SaasBillingEventModel
import riven.core.models.core.models.saas.SaasChurnEventModel
import riven.core.models.core.models.saas.SaasCommunicationModel
import riven.core.models.core.models.saas.SaasCustomerModel
import riven.core.models.core.models.dtc.DtcBillingEventModel
import riven.core.models.core.models.dtc.DtcChurnEventModel
import riven.core.models.core.models.dtc.DtcCommunicationModel
import riven.core.models.core.models.dtc.DtcCustomerModel
import riven.core.models.core.models.AcquisitionSourceModel
import riven.core.models.core.models.FeatureUsageEventModel
import riven.core.models.core.models.OrderLineItemModel
import riven.core.models.core.models.OrderModel
import riven.core.models.core.models.ProductModel
import riven.core.models.core.models.SubscriptionModel
import riven.core.models.core.models.SupportTicketModel

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
    val models: List<riven.core.models.core.CoreModelDefinition>,
    val additionalRelationships: List<riven.core.models.core.CoreModelRelationship> = emptyList(),
)

// ------ Business Type Model Sets ------

val B2C_SAAS_MODELS = CoreModelSet(
    manifestKey = "b2c-saas",
    name = "B2C SaaS",
    description = "Lifecycle template for B2C and prosumer SaaS businesses. Traces the full customer journey from acquisition through subscription, feature usage, support, and billing to retention or churn.",
    models = listOf(
        SaasCustomerModel,
        SaasCommunicationModel,
        SupportTicketModel,
        SubscriptionModel,
        FeatureUsageEventModel,
        AcquisitionSourceModel,
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
        SupportTicketModel,
        OrderModel,
        ProductModel,
        OrderLineItemModel,
        AcquisitionSourceModel,
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
            key = "order-line-items",
            name = "Line Items",
            sourceModelKey = "order",
            targetModelKey = "order-line-item",
            cardinality = EntityRelationshipCardinality.ONE_TO_MANY,
            inverseName = "Order",
            semantics = RelationshipSemantics(
                definition = "Individual product entries within an order, each with quantity and pricing.",
                tags = listOf("line-items", "transaction"),
            ),
        ),
        CoreModelRelationship(
            key = "line-item-product",
            name = "Product",
            sourceModelKey = "order-line-item",
            targetModelKey = "product",
            cardinality = EntityRelationshipCardinality.MANY_TO_ONE,
            inverseName = "Line Items",
            semantics = RelationshipSemantics(
                definition = "The product referenced by this line item.",
                tags = listOf("catalogue", "line-items"),
            ),
        ),
    ),
)
