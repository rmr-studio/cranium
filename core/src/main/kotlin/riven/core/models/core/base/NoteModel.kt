package cranium.core.models.core.base

import cranium.core.enums.common.icon.IconColour
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.core.DataType
import cranium.core.enums.entity.EntityTypeRole
import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticAttributeClassification
import cranium.core.enums.entity.semantics.SemanticGroup
import cranium.core.models.core.AttributeSemantics
import cranium.core.models.core.CoreModelAttribute
import cranium.core.models.core.CoreModelDefinition

/**
 * Canonical Note entity type. KNOWLEDGE surface role — surfaces in the dedicated
 * Notes panel and contextually on related entity pages via ATTACHMENT
 * (note attached to one or more entities) and MENTION (entity referenced
 * inline in a note's content) system relationships.
 *
 * `content` carries the BlockNote JSON tree verbatim. `plaintext` is the
 * derived flat string used for full-text search and synthesis input.
 */
object NoteModel : CoreModelDefinition(
    key = "note",
    role = EntityTypeRole.KNOWLEDGE,
    displayNameSingular = "Note",
    displayNamePlural = "Notes",
    iconType = IconType.STICKY_NOTE,
    iconColour = IconColour.YELLOW,
    semanticGroup = SemanticGroup.UNCATEGORIZED,
    lifecycleDomain = LifecycleDomain.UNCATEGORIZED,
    identifierKey = "title",
    semanticDefinition = "A note captures user-authored or integration-imported observations linked to one or more entities. Notes participate in retrieval, synthesis, and Iron Law backlinks.",
    semanticTags = listOf("knowledge", "note", "observation", "timeline"),
    attributes = mapOf(
        "title" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Title", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "The note's title.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("display-name"),
            ),
        ),
        "content" to CoreModelAttribute(
            schemaType = SchemaType.ARRAY, label = "Content", dataType = DataType.ARRAY,
            semantics = AttributeSemantics(
                definition = "BlockNote document tree (array of block objects) preserved verbatim.",
                classification = SemanticAttributeClassification.FREETEXT,
                tags = listOf("rich-text", "blocknote"),
            ),
        ),
        "plaintext" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Plaintext", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Flattened plaintext rendering of the content tree, used for FTS and synthesis.",
                classification = SemanticAttributeClassification.FREETEXT,
                tags = listOf("search", "derived"),
            ),
        ),
    ),
)
