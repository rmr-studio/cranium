package riven.core.lifecycle.models.saas

import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.lifecycle.AttributeOptions
import riven.core.lifecycle.AttributeSemantics
import riven.core.lifecycle.CoreModelAttribute
import riven.core.lifecycle.CoreModelDefinition
import riven.core.lifecycle.models.base.ChurnEventBase

/**
 * SaaS Churn Event — records when and why a SaaS subscription ended.
 * Includes MRR impact tracking and product-specific churn reasons.
 */
object SaasChurnEventModel : CoreModelDefinition(
    key = "churn-event",
    displayNameSingular = "Churn Event",
    displayNamePlural = "Churn Events",
    iconType = IconType.USER_MINUS,
    iconColour = IconColour.RED,
    semanticGroup = SemanticGroup.FINANCIAL,
    lifecycleDomain = LifecycleDomain.RETENTION,
    identifierKey = "reason",
    semanticDefinition = "Records when and why a subscription ended. The terminal lifecycle event for SaaS, with MRR impact tracking.",
    semanticTags = listOf("churn", "retention", "lifecycle", "revenue"),
    attributes = ChurnEventBase.attributes + mapOf(
        "reason" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Reason", dataType = DataType.STRING,
            options = AttributeOptions(enum = listOf("price", "competitor", "no-longer-needed", "poor-experience", "missing-feature", "onboarding-failure", "product-issue", "unknown")),
            semantics = AttributeSemantics(
                definition = "The stated or inferred reason for churning from the SaaS product.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("churn-reason", "analysis"),
            ),
        ),
        "mrr-lost" to CoreModelAttribute(
            schemaType = SchemaType.CURRENCY, label = "MRR Lost", dataType = DataType.NUMBER,
            format = "currency",
            semantics = AttributeSemantics(
                definition = "Monthly recurring revenue lost from this churn event.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("revenue-impact", "financial"),
            ),
        ),
    ),
)
