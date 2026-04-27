package riven.core.models.core

import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.models.core.models.AcquisitionSourceModel
import riven.core.models.core.models.OrderLineItemModel
import riven.core.models.core.models.OrderModel
import riven.core.models.core.models.ProductModel
import riven.core.models.core.models.SupportTicketModel
import riven.core.models.core.models.dtc.DtcBillingEventModel
import riven.core.models.core.models.dtc.DtcChurnEventModel
import riven.core.models.core.models.dtc.DtcCommunicationModel
import riven.core.models.core.models.dtc.DtcCustomerModel
import riven.core.models.core.models.dtc.commerce.CollectionModel
import riven.core.models.core.models.dtc.commerce.DiscountModel
import riven.core.models.core.models.dtc.commerce.ProductReviewModel
import riven.core.models.core.models.dtc.commerce.ProductVariantModel
import riven.core.models.core.models.dtc.commerce.ReturnModel
import riven.core.models.core.models.dtc.fulfillment.CarrierModel
import riven.core.models.core.models.dtc.fulfillment.ShipmentEventModel
import riven.core.models.core.models.dtc.fulfillment.ShipmentModel
import riven.core.models.core.models.dtc.marketing.AdCreativeModel
import riven.core.models.core.models.dtc.marketing.AdSpendEventModel
import riven.core.models.core.models.dtc.marketing.CampaignModel
import riven.core.models.core.models.dtc.social.SocialCommentModel
import riven.core.models.core.models.dtc.social.SocialMentionModel
import riven.core.models.core.models.dtc.social.SocialPostModel

/**
 * A set of core models that form a complete lifecycle data model for a business type.
 * Currently DTC E-commerce is the only supported vertical.
 *
 * The model set owns:
 * - Which core models are included
 * - Cross-model relationships that are vertical-specific
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

val DTC_ECOMMERCE_MODELS = CoreModelSet(
    manifestKey = "dtc-ecommerce",
    name = "DTC E-commerce",
    description = "Lifecycle template for direct-to-consumer e-commerce businesses. Traces the full customer journey from acquisition through orders, fulfillment, engagement, and retention — across marketing, social, and shipping signals.",
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
        // Marketing
        CampaignModel,
        AdCreativeModel,
        AdSpendEventModel,
        // Social
        SocialPostModel,
        SocialCommentModel,
        SocialMentionModel,
        // Fulfillment
        ShipmentModel,
        ShipmentEventModel,
        CarrierModel,
        // Commerce
        ProductVariantModel,
        CollectionModel,
        DiscountModel,
        ReturnModel,
        ProductReviewModel,
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
        // Product ↔ Variant
        CoreModelRelationship(
            key = "product-variants",
            name = "Variants",
            sourceModelKey = "product",
            targetModelKey = "product-variant",
            cardinality = EntityRelationshipCardinality.ONE_TO_MANY,
            inverseName = "Product",
            semantics = RelationshipSemantics(
                definition = "A product has one or more SKU-level variants.",
                tags = listOf("catalogue", "variant"),
            ),
        ),
        // Product ↔ Collection
        CoreModelRelationship(
            key = "product-collections",
            name = "Collections",
            sourceModelKey = "product",
            targetModelKey = "collection",
            cardinality = EntityRelationshipCardinality.MANY_TO_MANY,
            inverseName = "Products",
            semantics = RelationshipSemantics(
                definition = "Products can belong to multiple merchandising collections.",
                tags = listOf("catalogue", "merchandising"),
            ),
        ),
        // Order ↔ Shipment
        CoreModelRelationship(
            key = "order-shipments",
            name = "Shipments",
            sourceModelKey = "order",
            targetModelKey = "shipment",
            cardinality = EntityRelationshipCardinality.ONE_TO_MANY,
            inverseName = "Order",
            semantics = RelationshipSemantics(
                definition = "An order can fulfill across one or more shipments.",
                tags = listOf("fulfillment", "shipping"),
            ),
        ),
        // Shipment ↔ Carrier
        CoreModelRelationship(
            key = "shipment-carrier",
            name = "Carrier",
            sourceModelKey = "shipment",
            targetModelKey = "carrier",
            cardinality = EntityRelationshipCardinality.MANY_TO_ONE,
            inverseName = "Shipments",
            semantics = RelationshipSemantics(
                definition = "Each shipment is routed through a single carrier.",
                tags = listOf("fulfillment", "carrier"),
            ),
        ),
        // Shipment ↔ ShipmentEvent
        CoreModelRelationship(
            key = "shipment-events",
            name = "Events",
            sourceModelKey = "shipment",
            targetModelKey = "shipment-event",
            cardinality = EntityRelationshipCardinality.ONE_TO_MANY,
            inverseName = "Shipment",
            semantics = RelationshipSemantics(
                definition = "A shipment has a stream of status events from the carrier.",
                tags = listOf("fulfillment", "tracking"),
            ),
        ),
        // Campaign ↔ AdCreative
        CoreModelRelationship(
            key = "campaign-creatives",
            name = "Creatives",
            sourceModelKey = "campaign",
            targetModelKey = "ad-creative",
            cardinality = EntityRelationshipCardinality.MANY_TO_MANY,
            inverseName = "Campaigns",
            semantics = RelationshipSemantics(
                definition = "Campaigns can run multiple creatives; creatives can belong to multiple campaigns.",
                tags = listOf("marketing", "creative"),
            ),
        ),
        // AdCreative ↔ AdSpendEvent
        CoreModelRelationship(
            key = "creative-spend",
            name = "Spend Events",
            sourceModelKey = "ad-creative",
            targetModelKey = "ad-spend-event",
            cardinality = EntityRelationshipCardinality.ONE_TO_MANY,
            inverseName = "Creative",
            semantics = RelationshipSemantics(
                definition = "Daily spend events recorded per creative.",
                tags = listOf("marketing", "spend"),
            ),
        ),
        // AcquisitionSource → Campaign
        CoreModelRelationship(
            key = "acquisition-source-campaign",
            name = "Campaign",
            sourceModelKey = "acquisition-source",
            targetModelKey = "campaign",
            cardinality = EntityRelationshipCardinality.MANY_TO_ONE,
            inverseName = "Acquisition Sources",
            semantics = RelationshipSemantics(
                definition = "Links an acquisition source back to the paid campaign that drove it.",
                tags = listOf("attribution", "marketing"),
            ),
        ),
        // Return ↔ Order
        CoreModelRelationship(
            key = "return-order",
            name = "Order",
            sourceModelKey = "return",
            targetModelKey = "order",
            cardinality = EntityRelationshipCardinality.MANY_TO_ONE,
            inverseName = "Returns",
            semantics = RelationshipSemantics(
                definition = "Each return is linked to the originating order.",
                tags = listOf("return", "order"),
            ),
        ),
        // Return ↔ ProductVariant (via line items)
        CoreModelRelationship(
            key = "return-variants",
            name = "Variants",
            sourceModelKey = "return",
            targetModelKey = "product-variant",
            cardinality = EntityRelationshipCardinality.MANY_TO_MANY,
            inverseName = "Returns",
            semantics = RelationshipSemantics(
                definition = "Returns can include one or more product variants.",
                tags = listOf("return", "variant"),
            ),
        ),
        // ProductReview ↔ Product
        CoreModelRelationship(
            key = "review-product",
            name = "Product",
            sourceModelKey = "product-review",
            targetModelKey = "product",
            cardinality = EntityRelationshipCardinality.MANY_TO_ONE,
            inverseName = "Reviews",
            semantics = RelationshipSemantics(
                definition = "A product review targets a specific product.",
                tags = listOf("review", "catalogue"),
            ),
        ),
        // ProductReview ↔ Customer
        CoreModelRelationship(
            key = "review-customer",
            name = "Author",
            sourceModelKey = "product-review",
            targetModelKey = "customer",
            cardinality = EntityRelationshipCardinality.MANY_TO_ONE,
            inverseName = "Reviews",
            semantics = RelationshipSemantics(
                definition = "The customer who authored the review.",
                tags = listOf("review", "author"),
            ),
        ),
        // SocialPost ↔ SocialComment
        CoreModelRelationship(
            key = "post-comments",
            name = "Comments",
            sourceModelKey = "social-post",
            targetModelKey = "social-comment",
            cardinality = EntityRelationshipCardinality.ONE_TO_MANY,
            inverseName = "Post",
            semantics = RelationshipSemantics(
                definition = "Comments left on an owned-account post.",
                tags = listOf("social", "engagement"),
            ),
        ),
        // SocialMention ↔ Customer (identity resolution)
        CoreModelRelationship(
            key = "mention-customer",
            name = "Customer",
            sourceModelKey = "social-mention",
            targetModelKey = "customer",
            cardinality = EntityRelationshipCardinality.MANY_TO_ONE,
            inverseName = "Mentions",
            semantics = RelationshipSemantics(
                definition = "Brand mentions resolved to a known customer via handle matching.",
                tags = listOf("social", "identity"),
            ),
        ),
    ),
)
