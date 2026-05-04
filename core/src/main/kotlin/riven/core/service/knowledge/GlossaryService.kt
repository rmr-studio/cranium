package riven.core.service.knowledge

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.integration.SourceType
import riven.core.enums.knowledge.DefinitionCategory
import riven.core.enums.knowledge.DefinitionStatus
import riven.core.enums.knowledge.KnowledgeEntityTypeKey
import riven.core.enums.util.OperationType
import riven.core.exceptions.ConflictException
import riven.core.exceptions.NotFoundException
import riven.core.models.knowledge.GlossaryTerm
import riven.core.models.request.knowledge.CreateBusinessDefinitionRequest
import riven.core.models.request.knowledge.UpdateBusinessDefinitionRequest
import riven.core.service.activity.ActivityService
import riven.core.service.activity.log
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.EntityIngestionService
import riven.core.util.TermNormalizationUtil
import java.util.UUID

/**
 * Manages workspace-scoped glossary terms — natural language descriptions of terms defined
 * by a workspace that give further unique insights into the business context. Glossary terms are a key input for the AI query pipeline
 * and are used to enrich the entity metadata that surfaces in the query builder and results.
 *
 * Each glossary term corresponds to a "glossary entity" in the entity layer, with a `typeKey` of `glossary_term`.
 */
@Service
class GlossaryService(
    private val glossaryEntityIngestionService: GlossaryEntityIngestionService,
    private val glossaryEntityProjector: GlossaryEntityProjector,
    private val entityIngestionService: EntityIngestionService,
    private val authTokenService: AuthTokenService,
    private val activityService: ActivityService,
    private val logger: KLogger,
) {

    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    @Transactional(readOnly = true)
    fun listDefinitions(
        workspaceId: UUID,
        status: DefinitionStatus? = null,
        category: DefinitionCategory? = null,
    ): List<GlossaryTerm> {
        // `status` is accepted for API compatibility while glossary entities do not yet
        // carry a per-row status field; filter only by category for now.
        val all = glossaryEntityProjector.listAll(workspaceId)
        return all.filter { def ->
            category == null || def.category == category
        }
    }

    /** Get a single definition by ID within a workspace. */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    @Transactional(readOnly = true)
    fun getDefinition(workspaceId: UUID, id: UUID): GlossaryTerm {
        val entity = entityIngestionService.findByIdInternal(workspaceId, id)
            ?: throw NotFoundException("Business definition not found: $id")
        if (entity.typeKey != KnowledgeEntityTypeKey.GLOSSARY.key) {
            throw NotFoundException("Business definition not found: $id")
        }
        return glossaryEntityProjector.project(workspaceId, entity)
    }

    // ------ Public mutations ------

    /**
     * Create a new business definition.
     *
     * Normalizes the term for uniqueness checking. Throws [ConflictException] if a
     * definition with the same normalized term already exists in the workspace.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId) and @workspaceSecurity.hasWorkspaceRoleOrHigher(#workspaceId, 'ADMIN')")
    fun createDefinition(
        workspaceId: UUID,
        request: CreateBusinessDefinitionRequest,
    ): GlossaryTerm {
        val userId = authTokenService.getUserId()
        return doCreate(workspaceId, userId, request)
    }

    /**
     * Update an existing business definition. The legacy optimistic-locking version
     * field is no longer enforced (the entity layer has no equivalent column); a stale
     * `request.version` is silently ignored.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId) and @workspaceSecurity.hasWorkspaceRoleOrHigher(#workspaceId, 'ADMIN')")
    fun updateDefinition(
        workspaceId: UUID,
        id: UUID,
        request: UpdateBusinessDefinitionRequest,
    ): GlossaryTerm {
        val userId = authTokenService.getUserId()
        val existing = requireGlossaryEntity(workspaceId, id)
        val current = glossaryEntityProjector.project(workspaceId, existing)

        validateTermLength(request.term)
        validateDefinitionLength(request.definition)

        val newNormalizedTerm = TermNormalizationUtil.normalize(request.term)
        if (newNormalizedTerm != current.normalizedTerm) {
            checkForDuplicateTerm(workspaceId, newNormalizedTerm, excludeId = id)
        }

        val saved = glossaryEntityIngestionService.upsert(
            GlossaryEntityIngestionService.GlossaryIngestionInput(
                workspaceId = workspaceId,
                term = request.term.trim(),
                normalizedTerm = newNormalizedTerm,
                definition = request.definition,
                category = request.category,
                source = current.source,
                isCustomised = current.isCustomized,
                sourceExternalId = sourceExternalIdFor(id, existing.sourceExternalId),
                sourceType = existing.sourceType,
                sourceIntegrationId = existing.sourceIntegrationId,
                entityTypeRefs = request.entityTypeRefs.toSet(),
                attributeRefs = request.attributeRefs,
                linkSource = SourceType.USER_CREATED,
            ),
        )
        val projected = glossaryEntityProjector.project(workspaceId, saved)

        activityService.log(
            activity = Activity.BUSINESS_DEFINITION,
            operation = OperationType.UPDATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.BUSINESS_DEFINITION,
            entityId = projected.id,
            "term" to projected.term,
            "category" to projected.category.name,
        )

        logger.info { "Updated business definition '${projected.term}' for workspace $workspaceId" }
        return projected
    }

    /** Soft-delete a business definition. */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId) and @workspaceSecurity.hasWorkspaceRoleOrHigher(#workspaceId, 'ADMIN')")
    fun deleteDefinition(workspaceId: UUID, id: UUID) {
        val userId = authTokenService.getUserId()
        val existing = requireGlossaryEntity(workspaceId, id)
        val term = glossaryEntityProjector.project(workspaceId, existing).term

        glossaryEntityIngestionService.softDelete(workspaceId, id)

        activityService.log(
            activity = Activity.BUSINESS_DEFINITION,
            operation = OperationType.DELETE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.BUSINESS_DEFINITION,
            entityId = id,
            "term" to term,
        )

        logger.info { "Soft-deleted business definition '$term' from workspace $workspaceId" }
    }

    // ------ Internal operations ------

    /**
     * Create a business definition without workspace security checks. Used by
     * `OnboardingService` where the workspace was just created and the JWT does not
     * yet contain the new workspace's role authorities.
     */
    @Transactional
    internal fun createDefinitionInternal(
        workspaceId: UUID,
        userId: UUID,
        request: CreateBusinessDefinitionRequest,
    ): GlossaryTerm = doCreate(workspaceId, userId, request)

    // ------ Private helpers ------

    private fun doCreate(
        workspaceId: UUID,
        userId: UUID,
        request: CreateBusinessDefinitionRequest,
    ): GlossaryTerm {
        validateTermLength(request.term)
        validateDefinitionLength(request.definition)

        val normalizedTerm = TermNormalizationUtil.normalize(request.term)
        checkForDuplicateTerm(workspaceId, normalizedTerm, excludeId = null)

        val saved = glossaryEntityIngestionService.upsert(
            GlossaryEntityIngestionService.GlossaryIngestionInput(
                workspaceId = workspaceId,
                term = request.term.trim(),
                normalizedTerm = normalizedTerm,
                definition = request.definition,
                category = request.category,
                source = request.source,
                isCustomised = request.isCustomized,
                // Mint a UUID-based external id at create time so the value is immutable across
                // the entity's lifetime. Keying off `normalizedTerm` would corrupt rename-then-
                // recreate flows: renaming term A→B leaves the row with sourceExternalId=user:a;
                // creating a fresh "A" would then collide with the renamed row's external id and
                // mutate it instead of inserting a new entity. The duplicate-check above already
                // guarantees user-facing uniqueness, so the external id only needs to remain
                // unique enough to keep idempotent lookups stable.
                sourceExternalId = "user:${UUID.randomUUID()}",
                entityTypeRefs = request.entityTypeRefs.toSet(),
                attributeRefs = request.attributeRefs,
                linkSource = SourceType.USER_CREATED,
            ),
        )
        val projected = glossaryEntityProjector.project(workspaceId, saved)

        activityService.log(
            activity = Activity.BUSINESS_DEFINITION,
            operation = OperationType.CREATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.BUSINESS_DEFINITION,
            entityId = projected.id,
            "term" to projected.term,
            "category" to projected.category.name,
            "source" to projected.source.name,
        )

        logger.info { "Created business definition '${projected.term}' for workspace $workspaceId" }
        return projected
    }

    private fun requireGlossaryEntity(workspaceId: UUID, id: UUID): riven.core.entity.entity.EntityEntity {
        val entity = entityIngestionService.findByIdInternal(workspaceId, id)
            ?: throw NotFoundException("Business definition not found: $id")
        if (entity.typeKey != KnowledgeEntityTypeKey.GLOSSARY.key) {
            throw NotFoundException("Business definition not found: $id")
        }
        return entity
    }

    private fun checkForDuplicateTerm(workspaceId: UUID, normalizedTerm: String, excludeId: UUID?) {
        val existing = glossaryEntityProjector.findByNormalizedTerm(workspaceId, normalizedTerm)
            ?: return
        if (existing.id == excludeId) return
        throw ConflictException(
            "A business definition with normalized term '$normalizedTerm' already exists in this workspace"
        )
    }

    /**
     * Preserve the original `sourceExternalId` on update if it was set (legacy backfill
     * imported rows carry `legacy:{uuid}`); otherwise synthesize one from the entity id
     * so subsequent upserts remain idempotent under the entity layer's
     * (workspaceId, sourceExternalId) lookup.
     */
    private fun sourceExternalIdFor(entityId: UUID, existing: String?): String =
        existing ?: "user:$entityId"

    private fun validateTermLength(term: String) {
        require(term.isNotBlank()) { "Term must not be blank" }
        require(term.length <= 255) { "Term must not exceed 255 characters" }
    }

    private fun validateDefinitionLength(definition: String) {
        require(definition.isNotBlank()) { "Definition must not be blank" }
        require(definition.length <= 2000) { "Definition must not exceed 2000 characters" }
    }
}
