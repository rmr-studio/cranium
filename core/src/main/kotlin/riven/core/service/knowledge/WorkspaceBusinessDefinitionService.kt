package riven.core.service.knowledge

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.transaction.annotation.Transactional
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import riven.core.service.auth.AuthTokenService
import riven.core.entity.knowledge.WorkspaceBusinessDefinitionEntity
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.knowledge.DefinitionCategory
import riven.core.enums.knowledge.DefinitionStatus
import riven.core.enums.util.OperationType
import riven.core.exceptions.ConflictException
import riven.core.exceptions.UniqueConstraintViolationException
import riven.core.models.common.markDeleted
import riven.core.models.knowledge.WorkspaceBusinessDefinition
import riven.core.models.request.knowledge.CreateBusinessDefinitionRequest
import riven.core.models.request.knowledge.UpdateBusinessDefinitionRequest
import riven.core.repository.knowledge.WorkspaceBusinessDefinitionRepository
import riven.core.service.activity.ActivityService
import riven.core.service.activity.log
import riven.core.util.ServiceUtil.findOrThrow
import riven.core.util.TermNormalizationUtil
import java.util.*

/**
 * Manages workspace-scoped business definitions — natural language descriptions of terms like
 * "retention" or "active customer" that the AI query pipeline uses for query generation.
 */
@Service
class WorkspaceBusinessDefinitionService(
    private val repository: WorkspaceBusinessDefinitionRepository,
    private val authTokenService: AuthTokenService,
    private val activityService: ActivityService,
    private val logger: KLogger,
) {

    // ------ Public read operations ------

    /**
     * List all definitions for a workspace, optionally filtered by status and/or category.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun listDefinitions(
        workspaceId: UUID,
        status: DefinitionStatus? = null,
        category: DefinitionCategory? = null,
    ): List<WorkspaceBusinessDefinition> {
        return repository.findByWorkspaceIdWithFilters(workspaceId, status, category)
            .map { it.toModel() }
    }

    /**
     * Get a single definition by ID within a workspace.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getDefinition(workspaceId: UUID, id: UUID): WorkspaceBusinessDefinition {
        val entity = findOrThrow { repository.findByIdAndWorkspaceId(id, workspaceId) }
        return entity.toModel()
    }

    // ------ Public mutations ------

    /**
     * Create a new business definition.
     *
     * Normalizes the term for uniqueness checking. Throws ConflictException if a definition
     * with the same normalized term already exists in the workspace.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId) and @workspaceSecurity.hasWorkspaceRoleOrHigher(#workspaceId, 'ADMIN')")
    fun createDefinition(
        workspaceId: UUID,
        request: CreateBusinessDefinitionRequest,
    ): WorkspaceBusinessDefinition {
        val userId = authTokenService.getUserId()
        val normalizedTerm = TermNormalizationUtil.normalize(request.term)

        validateTermLength(request.term)
        validateDefinitionLength(request.definition)
        checkForDuplicateTerm(workspaceId, normalizedTerm)

        val entity = WorkspaceBusinessDefinitionEntity(
            workspaceId = workspaceId,
            term = request.term.trim(),
            normalizedTerm = normalizedTerm,
            definition = request.definition,
            category = request.category,
            source = request.source,
            entityTypeRefs = request.entityTypeRefs,
            attributeRefs = request.attributeRefs,
            isCustomized = request.isCustomized,
        )

        val saved = try {
            repository.save(entity)
        } catch (e: DataIntegrityViolationException) {
            throw UniqueConstraintViolationException("A business definition with this term already exists in the workspace")
        } catch (e: ObjectOptimisticLockingFailureException) {
            throw ConflictException("Definition '${entity.term}' was modified concurrently. Please try again.", e)
        }

        activityService.log(
            activity = Activity.BUSINESS_DEFINITION,
            operation = OperationType.CREATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.BUSINESS_DEFINITION,
            entityId = saved.id,
            "term" to saved.term,
            "category" to saved.category.name,
            "source" to saved.source.name,
        )

        logger.info { "Created business definition '${saved.term}' for workspace $workspaceId" }
        return saved.toModel()
    }

    /**
     * Update an existing business definition. Uses optimistic locking via the version field.
     *
     * The client must send the version it last read. If the entity has been modified since,
     * a ConflictException is thrown. Re-normalizes the term and checks for uniqueness if changed.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId) and @workspaceSecurity.hasWorkspaceRoleOrHigher(#workspaceId, 'ADMIN')")
    fun updateDefinition(
        workspaceId: UUID,
        id: UUID,
        request: UpdateBusinessDefinitionRequest,
    ): WorkspaceBusinessDefinition {
        val userId = authTokenService.getUserId()
        val entity = findOrThrow { repository.findByIdAndWorkspaceId(id, workspaceId) }

        validateTermLength(request.term)
        validateDefinitionLength(request.definition)
        verifyVersion(entity, request.version)

        val newNormalizedTerm = TermNormalizationUtil.normalize(request.term)
        if (newNormalizedTerm != entity.normalizedTerm) {
            checkForDuplicateTerm(workspaceId, newNormalizedTerm)
        }

        entity.term = request.term.trim()
        entity.normalizedTerm = newNormalizedTerm
        entity.definition = request.definition
        entity.category = request.category
        entity.entityTypeRefs = request.entityTypeRefs
        entity.attributeRefs = request.attributeRefs
        entity.compiledParams = null

        val saved = try {
            repository.save(entity)
        } catch (e: ObjectOptimisticLockingFailureException) {
            throw ConflictException("Definition '${entity.term}' was modified concurrently. Please refresh and try again.", e)
        }

        activityService.log(
            activity = Activity.BUSINESS_DEFINITION,
            operation = OperationType.UPDATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.BUSINESS_DEFINITION,
            entityId = saved.id,
            "term" to saved.term,
            "category" to saved.category.name,
            "version" to saved.version,
        )

        logger.info { "Updated business definition '${saved.term}' (v${saved.version}) for workspace $workspaceId" }
        return saved.toModel()
    }

    /**
     * Soft-delete a business definition.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId) and @workspaceSecurity.hasWorkspaceRoleOrHigher(#workspaceId, 'ADMIN')")
    fun deleteDefinition(workspaceId: UUID, id: UUID) {
        val userId = authTokenService.getUserId()
        val entity = findOrThrow { repository.findByIdAndWorkspaceId(id, workspaceId) }

        entity.markDeleted()
        try {
            repository.save(entity)
        } catch (e: ObjectOptimisticLockingFailureException) {
            throw ConflictException("Definition '${entity.term}' was modified concurrently. Please refresh and try again.", e)
        }

        activityService.log(
            activity = Activity.BUSINESS_DEFINITION,
            operation = OperationType.DELETE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.BUSINESS_DEFINITION,
            entityId = entity.id,
            "term" to entity.term,
        )

        logger.info { "Soft-deleted business definition '${entity.term}' from workspace $workspaceId" }
    }

    // ------ Internal operations ------

    /**
     * Create a business definition without workspace security checks.
     *
     * Used by OnboardingService where the workspace was just created and the JWT
     * does not yet contain the new workspace's role authorities.
     */
    @Transactional
    internal fun createDefinitionInternal(
        workspaceId: UUID,
        userId: UUID,
        request: CreateBusinessDefinitionRequest,
    ): WorkspaceBusinessDefinition {
        val normalizedTerm = TermNormalizationUtil.normalize(request.term)

        validateTermLength(request.term)
        validateDefinitionLength(request.definition)
        checkForDuplicateTerm(workspaceId, normalizedTerm)

        val entity = WorkspaceBusinessDefinitionEntity(
            workspaceId = workspaceId,
            term = request.term.trim(),
            normalizedTerm = normalizedTerm,
            definition = request.definition,
            category = request.category,
            source = request.source,
            entityTypeRefs = request.entityTypeRefs,
            attributeRefs = request.attributeRefs,
            isCustomized = request.isCustomized,
        )

        val saved = try {
            repository.save(entity)
        } catch (e: DataIntegrityViolationException) {
            throw UniqueConstraintViolationException("A business definition with this term already exists in the workspace")
        } catch (e: ObjectOptimisticLockingFailureException) {
            throw ConflictException("Definition '${entity.term}' was modified concurrently. Please try again.", e)
        }

        activityService.log(
            activity = Activity.BUSINESS_DEFINITION,
            operation = OperationType.CREATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.BUSINESS_DEFINITION,
            entityId = saved.id,
            "term" to saved.term,
            "category" to saved.category.name,
            "source" to saved.source.name,
        )

        logger.info { "Created business definition '${saved.term}' for workspace $workspaceId (internal)" }
        return saved.toModel()
    }

    // ------ Private helpers ------

    private fun checkForDuplicateTerm(workspaceId: UUID, normalizedTerm: String) {
        val existing = repository.findByWorkspaceIdAndNormalizedTerm(workspaceId, normalizedTerm)
        if (existing.isPresent) {
            throw ConflictException(
                "A business definition with term '${existing.get().term}' already exists in this workspace"
            )
        }
    }

    private fun verifyVersion(entity: WorkspaceBusinessDefinitionEntity, requestVersion: Int) {
        if (requestVersion != entity.version) {
            throw ConflictException(
                "Stale version for definition '${entity.term}': expected ${entity.version}, got $requestVersion"
            )
        }
    }

    private fun validateTermLength(term: String) {
        require(term.isNotBlank()) { "Term must not be blank" }
        require(term.length <= 255) { "Term must not exceed 255 characters" }
    }

    private fun validateDefinitionLength(definition: String) {
        require(definition.isNotBlank()) { "Definition must not be blank" }
        require(definition.length <= 2000) { "Definition must not exceed 2000 characters" }
    }
}
