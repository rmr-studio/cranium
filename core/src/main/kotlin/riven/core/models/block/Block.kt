package cranium.core.models.block

import io.swagger.v3.oas.annotations.media.DiscriminatorMapping
import io.swagger.v3.oas.annotations.media.Schema
import cranium.core.entity.util.AuditableModel
import cranium.core.models.block.metadata.BlockContentMetadata
import cranium.core.models.block.metadata.BlockReferenceMetadata
import cranium.core.models.block.metadata.EntityReferenceMetadata
import cranium.core.models.block.metadata.Metadata
import java.io.Serializable
import java.time.ZonedDateTime
import java.util.*

/**
 * A Block is a modular unit of content or functionality within the application.
 *
 * There are two types of blocks, governed by the Metadata structure:
 *  - Content Blocks (ie. `BlockContentMetadata`)
 *      - These hold direct child blocks. They are used to build up complex structures
 *        of content by nesting other blocks within them.
 *      - Example:
// @formatter:off
 *         {
 *               "kind": "content",
 *               "data": { "name": "Jane", "email": "jane@acme.com" },
 *               "meta": { "validationErrors": [], "lastValidatedVersion": 3 }
 *         }
// @formatter:on

 *
 *  - Reference Blocks (ie. `ReferenceListMetadata`)
 *      - These point to external resources or entities within the system. They do not hold child blocks
 *        directly, but rather reference other data.
 *      - Example:
 *
// @formatter:off
 *        {
 *           "kind": "references",
 *           "items": [
 *           { "type": "CLIENT", "id": "e1a2..." },
 *           { "type": "BLOCK",  "id": "c9f9..." }],
 *           "presentation": "SUMMARY",
 *           "projection": { "fields": ["name","domain","contact.email"] },
 *           "meta": {}
 *         }
 // @formatter:on
 */
data class Block(
    val id: UUID,
    val name: String?,
    val workspaceId: UUID,
    val type: BlockType,
    @field:Schema(
        oneOf = [EntityReferenceMetadata::class, BlockReferenceMetadata::class, BlockContentMetadata::class],
        discriminatorProperty = "type",
        discriminatorMapping = [
            DiscriminatorMapping(value = "entity_reference", schema = EntityReferenceMetadata::class),
            DiscriminatorMapping(value = "block_reference", schema = BlockReferenceMetadata::class),
            DiscriminatorMapping(value = "block_content", schema = BlockContentMetadata::class),
        ]
    )
    val payload: Metadata,
    // If there are any validation errors with this block's payload
    val validationErrors: List<String>? = null,
    // Keep these hidden unless within an internal workspace context
    override var createdAt: ZonedDateTime? = null,
    override var updatedAt: ZonedDateTime? = null,
    override var createdBy: UUID? = null,
    override var updatedBy: UUID? = null,
) : Serializable, AuditableModel

