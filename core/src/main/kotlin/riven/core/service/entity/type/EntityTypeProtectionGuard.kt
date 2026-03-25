package riven.core.service.entity.type

import org.springframework.stereotype.Component
import riven.core.entity.entity.EntityTypeEntity
import java.util.*

/**
 * Centralized protection enforcement for the three-tier entity model:
 *
 *   READONLY   (integration types)  — blocks ALL structural modifications
 *   PROTECTED  (lifecycle spine)    — blocks deletion + core attribute removal/modification, allows adding new attributes
 *   NONE       (user types)         — fully editable (except individually protected attributes)
 *
 * Protection hierarchy:
 *   entity_types.readonly=true    → READONLY tier
 *   entity_types.protected=true   → PROTECTED tier
 *   both false                    → user tier
 *
 * Per-attribute protection (Schema.protected) is enforced **regardless of entity type tier**.
 * Any attribute with protected=true is immutable, whether on a PROTECTED spine type or a
 * user-created type (e.g. identifier attributes marked protected during template installation).
 * Core spine attributes are marked protected=true during manifest installation.
 * User-added attributes default to protected=false.
 */
@Component
class EntityTypeProtectionGuard {

    /**
     * Assert the entity type can be deleted.
     * Blocked for READONLY and PROTECTED types.
     */
    fun assertCanDelete(type: EntityTypeEntity) {
        require(!type.readonly) {
            "Cannot delete a readonly entity type '${type.key}'"
        }
        require(!type.protected) {
            "Cannot delete a protected entity type '${type.key}'. Protected entity types are part of the lifecycle spine."
        }
    }

    /**
     * Assert the entity type schema can be modified (add/update attribute or relationship definitions).
     * Blocked for READONLY types. PROTECTED types allow additions but block modifications to protected attributes.
     */
    fun assertCanModifySchema(type: EntityTypeEntity) {
        require(!type.readonly) {
            "Cannot modify definitions on a readonly entity type '${type.key}'"
        }
    }

    /**
     * Assert a specific attribute can be modified (label, type, required, etc.).
     * Blocked for READONLY types and for any individually protected attribute (regardless of type tier).
     */
    fun assertCanModifyAttribute(type: EntityTypeEntity, attributeId: UUID) {
        require(!type.readonly) {
            "Cannot modify schema attributes of a readonly entity type '${type.key}'"
        }
        val existingAttribute = type.schema.properties?.get(attributeId)
        if (existingAttribute != null) {
            require(!existingAttribute.protected) {
                "Cannot modify protected attribute on entity type '${type.key}'. Protected attributes are part of the lifecycle spine and are immutable."
            }
        }
    }

    /**
     * Assert a specific attribute can be removed.
     * Blocked for READONLY types and for protected attributes on any type.
     */
    fun assertCanRemoveAttribute(type: EntityTypeEntity, attributeId: UUID) {
        require(!type.readonly) {
            "Cannot remove schema attributes from a readonly entity type '${type.key}'"
        }
        val attribute = type.schema.properties?.get(attributeId)
        require(attribute?.protected != true) {
            "Cannot remove protected attribute from entity type '${type.key}'. Protected attributes are part of the lifecycle spine."
        }
    }

    /**
     * Assert entity type configuration can be updated (name, icon, semantic group, lifecycle domain).
     * READONLY types block all configuration changes (caller must handle column-only path).
     * PROTECTED types allow cosmetic changes but block semanticGroup and lifecycleDomain changes.
     */
    fun assertCanUpdateConfiguration(type: EntityTypeEntity, changingSemanticGroup: Boolean, changingLifecycleDomain: Boolean) {
        require(!type.readonly) {
            "Cannot update configuration on a readonly entity type '${type.key}'"
        }
        if (type.protected) {
            require(!changingSemanticGroup) {
                "Cannot change semantic group on a protected entity type '${type.key}'"
            }
            require(!changingLifecycleDomain) {
                "Cannot change lifecycle domain on a protected entity type '${type.key}'"
            }
        }
    }
}
