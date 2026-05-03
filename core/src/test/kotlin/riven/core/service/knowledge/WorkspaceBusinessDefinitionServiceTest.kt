package riven.core.service.knowledge

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.enums.knowledge.DefinitionCategory
import riven.core.enums.knowledge.DefinitionSource
import riven.core.enums.knowledge.DefinitionStatus
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.exceptions.ConflictException
import riven.core.exceptions.NotFoundException
import riven.core.models.knowledge.AttributeRef
import riven.core.models.knowledge.WorkspaceBusinessDefinition
import riven.core.models.request.knowledge.CreateBusinessDefinitionRequest
import riven.core.models.request.knowledge.UpdateBusinessDefinitionRequest
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.EntityIngestionService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import riven.core.service.util.factory.entity.EntityFactory
import java.util.UUID

/**
 * Post-cutover WorkspaceBusinessDefinitionService coverage. The service no longer
 * touches WorkspaceBusinessDefinitionRepository — every read and write path goes
 * through the entity layer (GlossaryEntityIngestionService for mutations,
 * GlossaryEntityProjector for reads). These tests verify the controller-facing
 * contract (signatures + activity log + duplicate-term enforcement + readonly /
 * not-found behaviour) without re-asserting the legacy table behaviour.
 */
@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        WorkspaceBusinessDefinitionService::class,
    ]
)
@WithUserPersona(
    userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
    email = "test@test.com",
    displayName = "Test User",
    roles = [
        WorkspaceRole(
            workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
            role = WorkspaceRoles.ADMIN,
        ),
    ],
)
class WorkspaceBusinessDefinitionServiceTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var glossaryEntityIngestionService: GlossaryEntityIngestionService

    @MockitoBean
    private lateinit var glossaryEntityProjector: GlossaryEntityProjector

    @MockitoBean
    private lateinit var entityIngestionService: EntityIngestionService

    @MockitoBean
    private lateinit var activityService: ActivityService

    @Autowired
    private lateinit var service: WorkspaceBusinessDefinitionService

    private fun stubProjectionFor(
        id: UUID,
        term: String = "Retention Rate",
        normalizedTerm: String = "retention rate",
        category: DefinitionCategory = DefinitionCategory.METRIC,
        source: DefinitionSource = DefinitionSource.MANUAL,
        entityTypeRefs: List<UUID> = emptyList(),
        attributeRefs: List<AttributeRef> = emptyList(),
    ): WorkspaceBusinessDefinition {
        val def = WorkspaceBusinessDefinition(
            id = id,
            workspaceId = workspaceId,
            term = term,
            normalizedTerm = normalizedTerm,
            definition = "definition body",
            category = category,
            compiledParams = null,
            status = DefinitionStatus.ACTIVE,
            source = source,
            entityTypeRefs = entityTypeRefs,
            attributeRefs = attributeRefs,
            isCustomized = false,
            version = 0,
            createdBy = null,
            createdAt = null,
            updatedAt = null,
        )
        whenever(glossaryEntityProjector.project(eq(workspaceId), any())).thenReturn(def)
        return def
    }

    @BeforeEach
    fun setup() {
        reset(glossaryEntityIngestionService, glossaryEntityProjector, entityIngestionService, activityService)
    }

    // ------ List ------

    @Test
    fun `listDefinitions delegates to projector and returns all entries when no filters`() {
        val def1 = WorkspaceBusinessDefinition(
            id = UUID.randomUUID(), workspaceId = workspaceId,
            term = "Retention", normalizedTerm = "retention", definition = "x",
            category = DefinitionCategory.METRIC, compiledParams = null,
            status = DefinitionStatus.ACTIVE, source = DefinitionSource.MANUAL,
            entityTypeRefs = emptyList(), attributeRefs = emptyList(),
            isCustomized = false, version = 0,
            createdBy = null, createdAt = null, updatedAt = null,
        )
        val def2 = def1.copy(id = UUID.randomUUID(), term = "Churn", normalizedTerm = "churn")
        whenever(glossaryEntityProjector.listAll(workspaceId)).thenReturn(listOf(def1, def2))

        val result = service.listDefinitions(workspaceId)

        assertThat(result).hasSize(2)
    }

    @Test
    fun `listDefinitions filters by category`() {
        val a = stubbedDefinition(category = DefinitionCategory.METRIC)
        val b = stubbedDefinition(category = DefinitionCategory.SEGMENT)
        whenever(glossaryEntityProjector.listAll(workspaceId)).thenReturn(listOf(a, b))

        val result = service.listDefinitions(workspaceId, category = DefinitionCategory.SEGMENT)

        assertThat(result).hasSize(1)
        assertThat(result[0].category).isEqualTo(DefinitionCategory.SEGMENT)
    }

    @Test
    fun `listDefinitions filters by status — projector always emits ACTIVE so SUGGESTED yields empty`() {
        val a = stubbedDefinition()
        whenever(glossaryEntityProjector.listAll(workspaceId)).thenReturn(listOf(a))

        val active = service.listDefinitions(workspaceId, status = DefinitionStatus.ACTIVE)
        val suggested = service.listDefinitions(workspaceId, status = DefinitionStatus.SUGGESTED)

        assertThat(active).hasSize(1)
        assertThat(suggested).isEmpty()
    }

    // ------ Get ------

    @Test
    fun `getDefinition delegates to projector via findByIdInternal`() {
        val defId = UUID.randomUUID()
        val entity = EntityFactory.createEntityEntity(id = defId, workspaceId = workspaceId, typeKey = "glossary")
        whenever(entityIngestionService.findByIdInternal(workspaceId, defId)).thenReturn(entity)
        stubProjectionFor(defId)

        val result = service.getDefinition(workspaceId, defId)

        assertThat(result.id).isEqualTo(defId)
        assertThat(result.term).isEqualTo("Retention Rate")
    }

    @Test
    fun `getDefinition throws NotFoundException when entity is missing`() {
        val defId = UUID.randomUUID()
        whenever(entityIngestionService.findByIdInternal(workspaceId, defId)).thenReturn(null)

        assertThrows<NotFoundException> {
            service.getDefinition(workspaceId, defId)
        }
    }

    /**
     * Regression test for r3166515194: requesting a definition with an id that resolves
     * to a non-glossary entity must surface NotFoundException (mapped to 404), not
     * IllegalArgumentException (mapped to 400). Returning 400 leaks the existence of
     * a different entity at the same UUID.
     */
    @Test
    fun `getDefinition rejects non-glossary entity types with NotFoundException`() {
        val defId = UUID.randomUUID()
        val notAGlossary = EntityFactory.createEntityEntity(id = defId, workspaceId = workspaceId, typeKey = "company")
        whenever(entityIngestionService.findByIdInternal(workspaceId, defId)).thenReturn(notAGlossary)

        assertThrows<riven.core.exceptions.NotFoundException> {
            service.getDefinition(workspaceId, defId)
        }
    }

    // ------ Create ------

    @Test
    fun `createDefinition routes through ingestion service with normalized term`() {
        val request = CreateBusinessDefinitionRequest(
            term = "  Retention Rate  ",
            definition = "definition body",
            category = DefinitionCategory.METRIC,
        )
        val savedId = UUID.randomUUID()
        val savedEntity = EntityFactory.createEntityEntity(id = savedId, workspaceId = workspaceId, typeKey = "glossary")
        whenever(glossaryEntityProjector.findByNormalizedTerm(workspaceId, "retention rate")).thenReturn(null)
        whenever(glossaryEntityIngestionService.upsert(any())).thenReturn(savedEntity)
        stubProjectionFor(savedId)

        val result = service.createDefinition(workspaceId, request)

        assertThat(result.id).isEqualTo(savedId)
        assertThat(result.normalizedTerm).isEqualTo("retention rate")

        val captor = argumentCaptor<GlossaryEntityIngestionService.GlossaryIngestionInput>()
        verify(glossaryEntityIngestionService).upsert(captor.capture())
        val input = captor.firstValue
        assertThat(input.term).isEqualTo("Retention Rate")
        assertThat(input.normalizedTerm).isEqualTo("retention rate")
        assertThat(input.category).isEqualTo(DefinitionCategory.METRIC)
    }

    /**
     * Regression for r3176253156: the sourceExternalId minted at create time must NOT embed
     * the normalized term, since renaming a term would leave behind a stale `user:<oldterm>`
     * key. Recreating the original term then collides on the entity-layer idempotent lookup
     * and mutates the renamed row instead of inserting a new one. We mint a UUID-based
     * external id so it stays immutable across the entity's lifetime.
     */
    @Test
    fun `createDefinition mints UUID-based sourceExternalId not term-derived`() {
        val request = CreateBusinessDefinitionRequest(
            term = "Retention Rate",
            definition = "definition body",
            category = DefinitionCategory.METRIC,
        )
        val savedId = UUID.randomUUID()
        val savedEntity = EntityFactory.createEntityEntity(id = savedId, workspaceId = workspaceId, typeKey = "glossary")
        whenever(glossaryEntityProjector.findByNormalizedTerm(workspaceId, "retention rate")).thenReturn(null)
        whenever(glossaryEntityIngestionService.upsert(any())).thenReturn(savedEntity)
        stubProjectionFor(savedId)

        service.createDefinition(workspaceId, request)

        val captor = argumentCaptor<GlossaryEntityIngestionService.GlossaryIngestionInput>()
        verify(glossaryEntityIngestionService).upsert(captor.capture())
        val externalId = captor.firstValue.sourceExternalId
        assertThat(externalId).startsWith("user:")
        assertThat(externalId).doesNotContain("retention rate")
        // Suffix must parse as a UUID — proves it is not term-derived.
        UUID.fromString(externalId.removePrefix("user:"))
    }

    @Test
    fun `createDefinition throws ConflictException on duplicate normalized term`() {
        val request = CreateBusinessDefinitionRequest(
            term = "Retention Rate",
            definition = "definition body",
            category = DefinitionCategory.METRIC,
        )
        val existingEntity = EntityFactory.createEntityEntity(
            id = UUID.randomUUID(), workspaceId = workspaceId, typeKey = "glossary",
        )
        whenever(glossaryEntityProjector.findByNormalizedTerm(workspaceId, "retention rate"))
            .thenReturn(existingEntity)

        assertThrows<ConflictException> {
            service.createDefinition(workspaceId, request)
        }
        verify(glossaryEntityIngestionService, never()).upsert(any())
    }

    @Test
    fun `createDefinition rejects blank term`() {
        val request = CreateBusinessDefinitionRequest(
            term = "   ",
            definition = "x",
            category = DefinitionCategory.METRIC,
        )
        assertThrows<IllegalArgumentException> {
            service.createDefinition(workspaceId, request)
        }
    }

    // ------ Update ------

    @Test
    fun `updateDefinition routes through ingestion service preserving sourceExternalId`() {
        val defId = UUID.randomUUID()
        val existing = EntityFactory.createEntityEntity(
            id = defId, workspaceId = workspaceId, typeKey = "glossary",
            sourceExternalId = "legacy:abc",
        )
        whenever(entityIngestionService.findByIdInternal(workspaceId, defId)).thenReturn(existing)
        stubProjectionFor(defId)
        whenever(glossaryEntityIngestionService.upsert(any())).thenReturn(existing)

        val request = UpdateBusinessDefinitionRequest(
            term = "Retention Rate",
            definition = "updated",
            category = DefinitionCategory.METRIC,
            entityTypeRefs = emptyList(),
            attributeRefs = emptyList(),
            version = 0,
        )

        service.updateDefinition(workspaceId, defId, request)

        val captor = argumentCaptor<GlossaryEntityIngestionService.GlossaryIngestionInput>()
        verify(glossaryEntityIngestionService).upsert(captor.capture())
        assertThat(captor.firstValue.sourceExternalId).isEqualTo("legacy:abc")
        assertThat(captor.firstValue.normalizedTerm).isEqualTo("retention rate")
    }

    @Test
    fun `updateDefinition throws NotFoundException when missing`() {
        val defId = UUID.randomUUID()
        whenever(entityIngestionService.findByIdInternal(workspaceId, defId)).thenReturn(null)

        val request = UpdateBusinessDefinitionRequest(
            term = "x", definition = "x", category = DefinitionCategory.METRIC, version = 0,
        )
        assertThrows<NotFoundException> {
            service.updateDefinition(workspaceId, defId, request)
        }
    }

    @Test
    fun `updateDefinition throws ConflictException when normalizedTerm collides with another row`() {
        val defId = UUID.randomUUID()
        val otherId = UUID.randomUUID()
        val existing = EntityFactory.createEntityEntity(
            id = defId, workspaceId = workspaceId, typeKey = "glossary",
        )
        whenever(entityIngestionService.findByIdInternal(workspaceId, defId)).thenReturn(existing)
        // Current normalized term is "retention rate"; new request normalizes to "churn rate".
        stubProjectionFor(defId, normalizedTerm = "retention rate")
        whenever(glossaryEntityProjector.findByNormalizedTerm(workspaceId, "churn rate")).thenReturn(
            EntityFactory.createEntityEntity(id = otherId, workspaceId = workspaceId, typeKey = "glossary"),
        )

        val request = UpdateBusinessDefinitionRequest(
            term = "Churn Rate",
            definition = "x",
            category = DefinitionCategory.METRIC,
            version = 0,
        )

        assertThrows<ConflictException> {
            service.updateDefinition(workspaceId, defId, request)
        }
    }

    // ------ Delete ------

    @Test
    fun `deleteDefinition soft-deletes via ingestion service`() {
        val defId = UUID.randomUUID()
        val entity = EntityFactory.createEntityEntity(
            id = defId, workspaceId = workspaceId, typeKey = "glossary",
        )
        whenever(entityIngestionService.findByIdInternal(workspaceId, defId)).thenReturn(entity)
        stubProjectionFor(defId)

        service.deleteDefinition(workspaceId, defId)

        verify(glossaryEntityIngestionService).softDelete(workspaceId, defId)
    }

    @Test
    fun `deleteDefinition throws NotFoundException for missing entity`() {
        val defId = UUID.randomUUID()
        whenever(entityIngestionService.findByIdInternal(workspaceId, defId)).thenReturn(null)

        assertThrows<NotFoundException> {
            service.deleteDefinition(workspaceId, defId)
        }
        verify(glossaryEntityIngestionService, never()).softDelete(any(), any())
    }

    /**
     * Regression for r3172127160: @PreAuthorize on listDefinitions / createDefinition must
     * reject callers whose JWT lacks the requested workspace's role authority.
     */
    @org.junit.jupiter.api.Nested
    @WithUserPersona(
        userId = "11111111-1111-1111-1111-111111111111",
        email = "stranger@test.com",
        displayName = "Stranger",
        roles = [
            WorkspaceRole(
                workspaceId = "00000000-0000-0000-0000-000000000000",
                role = WorkspaceRoles.OWNER,
            ),
        ],
    )
    inner class UnauthorizedAccess {

        @Test
        fun `listDefinitions - persona without workspace authority - throws AccessDeniedException`() {
            assertThrows<org.springframework.security.access.AccessDeniedException> {
                service.listDefinitions(workspaceId, category = null, status = null)
            }
        }

        @Test
        fun `createDefinition - persona without workspace authority - throws AccessDeniedException`() {
            val request = CreateBusinessDefinitionRequest(
                term = "Term",
                definition = "Definition",
                category = DefinitionCategory.CUSTOM,
                source = DefinitionSource.MANUAL,
                isCustomized = false,
                entityTypeRefs = emptyList(),
                attributeRefs = emptyList(),
            )
            assertThrows<org.springframework.security.access.AccessDeniedException> {
                service.createDefinition(workspaceId, request)
            }
            verify(glossaryEntityIngestionService, never()).upsert(any())
        }
    }

    private fun stubbedDefinition(
        category: DefinitionCategory = DefinitionCategory.METRIC,
    ): WorkspaceBusinessDefinition = WorkspaceBusinessDefinition(
        id = UUID.randomUUID(),
        workspaceId = workspaceId,
        term = "Retention Rate",
        normalizedTerm = "retention rate",
        definition = "definition",
        category = category,
        compiledParams = null,
        status = DefinitionStatus.ACTIVE,
        source = DefinitionSource.MANUAL,
        entityTypeRefs = emptyList(),
        attributeRefs = emptyList(),
        isCustomized = false,
        version = 0,
        createdBy = null,
        createdAt = null,
        updatedAt = null,
    )
}
