package cranium.core.models.core.models.dtc.marketing

import cranium.core.enums.common.icon.IconColour
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.core.DataType
import cranium.core.enums.entity.EntityTypeRole
import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticAttributeClassification
import cranium.core.enums.entity.semantics.SemanticGroup
import cranium.core.models.common.validation.SchemaOptions
import cranium.core.models.core.AttributeSemantics
import cranium.core.models.core.CoreModelAttribute
import cranium.core.models.core.ProjectionAcceptRule

/**
 * Ad Creative — a creative asset (image, video, carousel, copy) used in one or more campaigns.
 */
object AdCreativeModel : cranium.core.models.core.CoreModelDefinition(
    key = "ad-creative",
    displayNameSingular = "Ad Creative",
    displayNamePlural = "Ad Creatives",
    iconType = IconType.IMAGE,
    iconColour = IconColour.PURPLE,
    semanticGroup = SemanticGroup.CREATIVE,
    lifecycleDomain = LifecycleDomain.MARKETING,
    role = EntityTypeRole.CATALOG,
    identifierKey = "name",
    semanticDefinition = "A creative asset used by one or more campaigns. Tracks creative-level performance and lifecycle (fatigue, refresh cadence).",
    semanticTags = listOf("marketing", "creative", "asset"),
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.MARKETING,
            semanticGroup = SemanticGroup.CREATIVE,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
    attributes = mapOf(
        "name" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Name", dataType = DataType.STRING,
            required = true,
            semantics = AttributeSemantics(
                definition = "Creative name or identifier from the ad platform.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("display-name"),
            ),
        ),
        "format" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Format", dataType = DataType.STRING,
            options = SchemaOptions(
                enum = listOf("image", "video", "carousel", "story", "reel", "text", "collection", "other")
            ),
            semantics = AttributeSemantics(
                definition = "Creative format or placement type.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("format"),
            ),
        ),
        "asset-url" to CoreModelAttribute(
            schemaType = SchemaType.URL, label = "Asset URL", dataType = DataType.STRING,
            format = "uri",
            semantics = AttributeSemantics(
                definition = "URL to the creative asset (image/video).",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("asset", "media"),
            ),
        ),
        "headline" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Headline", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Primary headline copy on the creative.",
                classification = SemanticAttributeClassification.FREETEXT,
                tags = listOf("copy", "headline"),
            ),
        ),
        "body" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Body", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Body/primary-text copy on the creative.",
                classification = SemanticAttributeClassification.FREETEXT,
                tags = listOf("copy", "body"),
            ),
        ),
        "status" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Status", dataType = DataType.STRING,
            options = SchemaOptions(enum = listOf("active", "paused", "retired")),
            semantics = AttributeSemantics(
                definition = "Creative run state.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("lifecycle", "status"),
            ),
        ),
    ),
)
