package riven.core.projection.entity

import riven.core.enums.entity.RelationshipTargetKind
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Projection for [riven.core.repository.entity.EntityRelationshipRepository.findGlossaryDefinitionsForType] —
 * surfaces glossary DEFINES edges that target ENTITY_TYPE, ATTRIBUTE, or RELATIONSHIP UUIDs on the
 * given entity type. Drives TypeNarrativeSection.glossaryDefinitions and AttributeSection.glossaryNarrative.
 *
 * The [getNarrative] getter returns an empty string from the JPQL — narrative text is resolved on the
 * Kotlin side inside the assembler via EntityAttributeService.getAttributesForEntities, which avoids
 * coupling the query to a hard-coded "definition" attribute slug name.
 */
interface GlossaryDefinitionRow {
    fun getRelationshipId(): UUID            // entity_relationships.id
    fun getSourceEntityId(): UUID            // glossary entity id
    fun getSourceLabel(): String             // glossary entity identifier value (COALESCE → entity id text)
    fun getTargetKind(): RelationshipTargetKind
    fun getTargetId(): UUID                  // ENTITY_TYPE id, ATTRIBUTE id, or RELATIONSHIP id
    fun getNarrative(): String               // always empty string from JPQL; assembler fills at Kotlin layer
    fun getCreatedAt(): ZonedDateTime
}
