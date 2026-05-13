package cranium.core.entity.integration

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import org.hibernate.annotations.UpdateTimestamp
import cranium.core.enums.integration.IntegrationCategory
import cranium.core.models.integration.IntegrationDefinitionModel
import java.time.ZonedDateTime
import java.util.*

/**
 * JPA entity for integration definitions (global catalog).
 *
 * This is a global catalog of supported integrations (HubSpot, Salesforce, etc.)
 * that all workspaces can use. Does NOT extend AuditableEntity because it has
 * no created_by/updated_by columns - it's a global catalog, not user-owned.
 */
@Entity
@Table(name = "integration_definitions")
data class IntegrationDefinitionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "slug", nullable = false, unique = true, length = 100)
    val slug: String,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "icon_url")
    var iconUrl: String? = null,

    @Column(name = "description")
    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    var category: IntegrationCategory,

    @Column(name = "nango_provider_key", nullable = false, length = 100)
    var nangoProviderKey: String,

    @Type(JsonBinaryType::class)
    @Column(name = "capabilities", columnDefinition = "jsonb", nullable = false)
    var capabilities: Map<String, Any> = emptyMap(),

    @Type(JsonBinaryType::class)
    @Column(name = "sync_config", columnDefinition = "jsonb", nullable = false)
    var syncConfig: Map<String, Any> = emptyMap(),

    @Type(JsonBinaryType::class)
    @Column(name = "auth_config", columnDefinition = "jsonb", nullable = false)
    var authConfig: Map<String, Any> = emptyMap(),

    @Column(name = "stale", nullable = false)
    var stale: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: ZonedDateTime = ZonedDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: ZonedDateTime = ZonedDateTime.now()
) {
    fun toModel() = IntegrationDefinitionModel(
        id = requireNotNull(id) { "IntegrationDefinitionEntity.id must not be null when mapping to model" },
        slug = slug,
        name = name,
        iconUrl = iconUrl,
        description = description,
        category = category,
        nangoProviderKey = nangoProviderKey,
        capabilities = capabilities,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
