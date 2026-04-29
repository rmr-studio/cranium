package riven.core.models.core.models.base

import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.entity.EntityTypeRole
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.models.common.validation.SchemaOptions
import riven.core.models.core.AttributeSemantics
import riven.core.models.core.CoreModelAttribute
import riven.core.models.core.CoreModelDefinition

/**
 * Canonical Glossary Term entity type. KNOWLEDGE surface role — receives a
 * 1.3x retrieval boost (wired in CEO plan Step 3 retrieval SQL; not in scope
 * for this plan). Replaces workspace_business_definitions rows post-cutover.
 */
object GlossaryTermModel : CoreModelDefinition(
    key = "glossary",
    role = EntityTypeRole.KNOWLEDGE,
    displayNameSingular = "Glossary Term",
    displayNamePlural = "Glossary",
    iconType = IconType.BOOK_OPEN,
    iconColour = IconColour.PURPLE,
    semanticGroup = SemanticGroup.UNCATEGORIZED,
    lifecycleDomain = LifecycleDomain.UNCATEGORIZED,
    identifierKey = "normalized_term",
    semanticDefinition = "A glossary term defines a piece of internal vocabulary or business concept used elsewhere in the workspace. The term may also DEFINE specific entity types or attributes (with target_kind=ENTITY_TYPE / ATTRIBUTE), and is referenced from other entity content via MENTION relationships.",
    semanticTags = listOf("knowledge", "glossary", "vocabulary", "definition"),
    attributes = mapOf(
        "term" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Term", dataType = DataType.STRING,
            required = true,
            semantics = AttributeSemantics(
                definition = "The display form of the term as authored by the user.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("display-name"),
            ),
        ),
        "normalized_term" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Normalized Term", dataType = DataType.STRING,
            required = true, unique = true,
            semantics = AttributeSemantics(
                definition = "Lowercased / trim / depluralized form, used for uniqueness and lookup.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("normalized", "key"),
            ),
        ),
        "definition" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Definition", dataType = DataType.STRING,
            required = true,
            semantics = AttributeSemantics(
                definition = "The authored definition of the term.",
                classification = SemanticAttributeClassification.FREETEXT,
                tags = listOf("definition"),
            ),
        ),
        "category" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Category", dataType = DataType.STRING,
            options = SchemaOptions(enum = listOf("BUSINESS", "TECHNICAL", "PRODUCT", "OPERATIONAL", "OTHER")),
            semantics = AttributeSemantics(
                definition = "Coarse classification used for filtering the glossary panel.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("category"),
            ),
        ),
        "source" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Source", dataType = DataType.STRING,
            options = SchemaOptions(enum = listOf("MANUAL", "ONBOARDING", "INTEGRATION")),
            semantics = AttributeSemantics(
                definition = "Where the term originated.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("provenance"),
            ),
        ),
        "is_customised" to CoreModelAttribute(
            schemaType = SchemaType.CHECKBOX, label = "Customised", dataType = DataType.BOOLEAN,
            semantics = AttributeSemantics(
                definition = "Whether the user modified the seeded definition.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("customisation"),
            ),
        ),
    ),
)
