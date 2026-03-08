package riven.core.service.entity.query

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.models.common.Icon
import riven.core.models.entity.Entity
import riven.core.models.entity.EntityLink
import riven.core.models.entity.payload.EntityAttribute
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.models.entity.payload.EntityAttributeRelationPayload
import riven.core.models.entity.query.EntityQuery
import riven.core.models.entity.query.EntityQueryResult
import riven.core.models.entity.query.QueryProjection
import riven.core.models.entity.query.filter.QueryFilter
import riven.core.models.entity.query.pagination.QueryPagination
import riven.core.models.request.entity.EntityQueryRequest
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.EntityRelationshipService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.exceptions.query.FilterNestingDepthExceededException
import riven.core.exceptions.query.QueryExecutionException
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        EntityQueryFacadeService::class,
    ]
)
@WithUserPersona(
    userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
    email = "test@test.com",
    displayName = "Test User",
    roles = [
        WorkspaceRole(
            workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
            role = WorkspaceRoles.OWNER
        )
    ]
)
class EntityQueryFacadeServiceTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var entityQueryService: EntityQueryService

    @MockitoBean
    private lateinit var entityRelationshipService: EntityRelationshipService

    @Autowired
    private lateinit var service: EntityQueryFacadeService

    private val entityTypeId = UUID.randomUUID()
    private val attributeId = UUID.randomUUID()
    private val definitionId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        reset(entityQueryService, entityRelationshipService)
    }

    @Test
    fun `queryEntities with empty body returns paginated results`() {
        val entity = createTestEntity()
        val queryResult = EntityQueryResult(
            entities = listOf(entity),
            totalCount = 1,
            hasNextPage = false,
            projection = null,
        )

        runBlocking {
            whenever(entityQueryService.execute(any(), eq(workspaceId), any(), isNull(), any()))
                .thenReturn(queryResult)
        }
        whenever(entityRelationshipService.findRelatedEntities(any<Set<UUID>>(), eq(workspaceId)))
            .thenReturn(emptyMap())

        val response = service.queryEntities(workspaceId, entityTypeId, EntityQueryRequest())

        assertEquals(1, response.entities.size)
        assertEquals(1L, response.totalCount)
        assertFalse(response.hasNextPage)
        assertEquals(100, response.limit)
        assertEquals(0, response.offset)
    }

    @Test
    fun `queryEntities hydrates relationships into entity payload`() {
        val entityId = UUID.randomUUID()
        val entity = createTestEntity(entityId)
        val queryResult = EntityQueryResult(
            entities = listOf(entity),
            totalCount = 1,
            hasNextPage = false,
            projection = null,
        )

        val link = EntityLink(
            id = UUID.randomUUID(),
            workspaceId = workspaceId,
            definitionId = definitionId,
            sourceEntityId = entityId,
            icon = Icon(type = IconType.FILE, colour = IconColour.NEUTRAL),
            key = "target-key",
            label = "Target Entity",
        )
        val relationships = mapOf(
            entityId to mapOf(definitionId to listOf(link))
        )

        runBlocking {
            whenever(entityQueryService.execute(any(), eq(workspaceId), any(), isNull(), any()))
                .thenReturn(queryResult)
        }
        whenever(entityRelationshipService.findRelatedEntities(any<Set<UUID>>(), eq(workspaceId)))
            .thenReturn(relationships)

        val response = service.queryEntities(workspaceId, entityTypeId, EntityQueryRequest())

        val resultEntity = response.entities.first()
        assertTrue(resultEntity.payload.containsKey(definitionId))
        val relPayload = resultEntity.payload[definitionId]?.payload
        assertInstanceOf(EntityAttributeRelationPayload::class.java, relPayload)
        assertEquals(1, (relPayload as EntityAttributeRelationPayload).relations.size)
        assertEquals(link.label, relPayload.relations.first().label)
    }

    @Test
    fun `queryEntities with empty results returns empty response`() {
        val queryResult = EntityQueryResult(
            entities = emptyList(),
            totalCount = 0,
            hasNextPage = false,
            projection = null,
        )

        runBlocking {
            whenever(entityQueryService.execute(any(), eq(workspaceId), any(), isNull(), any()))
                .thenReturn(queryResult)
        }

        val response = service.queryEntities(workspaceId, entityTypeId, EntityQueryRequest())

        assertTrue(response.entities.isEmpty())
        assertEquals(0L, response.totalCount)
        assertFalse(response.hasNextPage)
        verify(entityRelationshipService, never()).findRelatedEntities(any<Set<UUID>>(), any())
    }

    @Test
    fun `queryEntities passes filter and pagination to query engine`() {
        val filter = QueryFilter.And(conditions = emptyList())
        val pagination = QueryPagination(limit = 50, offset = 10)
        val request = EntityQueryRequest(filter = filter, pagination = pagination, maxDepth = 5)

        val queryResult = EntityQueryResult(
            entities = emptyList(),
            totalCount = 0,
            hasNextPage = false,
            projection = null,
        )

        runBlocking {
            whenever(entityQueryService.execute(any(), eq(workspaceId), any(), isNull(), any()))
                .thenReturn(queryResult)
        }

        val response = service.queryEntities(workspaceId, entityTypeId, request)

        runBlocking {
            verify(entityQueryService).execute(
                argThat<EntityQuery> { this.entityTypeId == entityTypeId && this.filter == filter && this.maxDepth == 5 },
                eq(workspaceId),
                eq(pagination),
                isNull(),
                eq(true),
            )
        }
        assertEquals(50, response.limit)
        assertEquals(10, response.offset)
    }

    @Test
    fun `queryEntities with wrong workspace throws AccessDeniedException`() {
        val wrongWorkspaceId = UUID.randomUUID()

        assertThrows(AccessDeniedException::class.java) {
            service.queryEntities(wrongWorkspaceId, entityTypeId, EntityQueryRequest())
        }
    }

    // ------ Error Propagation Tests ------

    @Test
    fun `queryEntities propagates QueryFilterException from query engine`() {
        runBlocking {
            whenever(entityQueryService.execute(any(), eq(workspaceId), any(), isNull(), any()))
                .thenThrow(FilterNestingDepthExceededException(depth = 10, maxDepth = 5))
        }

        assertThrows(FilterNestingDepthExceededException::class.java) {
            service.queryEntities(workspaceId, entityTypeId, EntityQueryRequest())
        }
    }

    @Test
    fun `queryEntities propagates QueryExecutionException from query engine`() {
        runBlocking {
            whenever(entityQueryService.execute(any(), eq(workspaceId), any(), isNull(), any()))
                .thenThrow(QueryExecutionException("SQL error", null))
        }

        assertThrows(QueryExecutionException::class.java) {
            service.queryEntities(workspaceId, entityTypeId, EntityQueryRequest())
        }
    }

    @Test
    fun `queryEntities propagates relationship hydration failure`() {
        val entity = createTestEntity()
        val queryResult = EntityQueryResult(
            entities = listOf(entity),
            totalCount = 1,
            hasNextPage = false,
            projection = null,
        )

        runBlocking {
            whenever(entityQueryService.execute(any(), eq(workspaceId), any(), isNull(), any()))
                .thenReturn(queryResult)
        }
        whenever(entityRelationshipService.findRelatedEntities(any<Set<UUID>>(), eq(workspaceId)))
            .thenThrow(RuntimeException("Database connection lost"))

        assertThrows(RuntimeException::class.java) {
            service.queryEntities(workspaceId, entityTypeId, EntityQueryRequest())
        }
    }

    // ------ includeCount Tests ------

    @Test
    fun `queryEntities with includeCount false skips count and returns null totalCount`() {
        val entity = createTestEntity()
        val queryResult = EntityQueryResult(
            entities = listOf(entity),
            totalCount = null,
            hasNextPage = false,
            projection = null,
        )

        runBlocking {
            whenever(entityQueryService.execute(any(), eq(workspaceId), any(), isNull(), eq(false)))
                .thenReturn(queryResult)
        }
        whenever(entityRelationshipService.findRelatedEntities(any<Set<UUID>>(), eq(workspaceId)))
            .thenReturn(emptyMap())

        val request = EntityQueryRequest(includeCount = false)
        val response = service.queryEntities(workspaceId, entityTypeId, request)

        assertNull(response.totalCount)
        assertFalse(response.hasNextPage)
        assertEquals(1, response.entities.size)
    }

    @Test
    fun `queryEntities with includeCount false sets hasNextPage true when more results exist`() {
        val entity = createTestEntity()
        val queryResult = EntityQueryResult(
            entities = listOf(entity),
            totalCount = null,
            hasNextPage = true,
            projection = null,
        )

        runBlocking {
            whenever(entityQueryService.execute(any(), eq(workspaceId), any(), isNull(), eq(false)))
                .thenReturn(queryResult)
        }
        whenever(entityRelationshipService.findRelatedEntities(any<Set<UUID>>(), eq(workspaceId)))
            .thenReturn(emptyMap())

        val request = EntityQueryRequest(includeCount = false)
        val response = service.queryEntities(workspaceId, entityTypeId, request)

        assertNull(response.totalCount)
        assertTrue(response.hasNextPage)
    }

    @Test
    fun `queryEntities with includeCount false sets hasNextPage false on last page`() {
        val queryResult = EntityQueryResult(
            entities = emptyList(),
            totalCount = null,
            hasNextPage = false,
            projection = null,
        )

        runBlocking {
            whenever(entityQueryService.execute(any(), eq(workspaceId), any(), isNull(), eq(false)))
                .thenReturn(queryResult)
        }

        val request = EntityQueryRequest(includeCount = false)
        val response = service.queryEntities(workspaceId, entityTypeId, request)

        assertNull(response.totalCount)
        assertFalse(response.hasNextPage)
    }

    @Test
    fun `queryEntities with includeCount true preserves existing behaviour`() {
        val entity = createTestEntity()
        val queryResult = EntityQueryResult(
            entities = listOf(entity),
            totalCount = 42L,
            hasNextPage = true,
            projection = null,
        )

        runBlocking {
            whenever(entityQueryService.execute(any(), eq(workspaceId), any(), isNull(), eq(true)))
                .thenReturn(queryResult)
        }
        whenever(entityRelationshipService.findRelatedEntities(any<Set<UUID>>(), eq(workspaceId)))
            .thenReturn(emptyMap())

        val request = EntityQueryRequest(includeCount = true)
        val response = service.queryEntities(workspaceId, entityTypeId, request)

        assertEquals(42L, response.totalCount)
        assertTrue(response.hasNextPage)
    }

    // ------ Projection Tests ------

    @Test
    fun `queryEntities with projection filters payload to included attributes only`() {
        val attr1Id = UUID.randomUUID()
        val attr2Id = UUID.randomUUID()
        val entity = Entity(
            id = UUID.randomUUID(),
            workspaceId = workspaceId,
            typeId = entityTypeId,
            payload = mapOf(
                attr1Id to EntityAttribute(payload = EntityAttributePrimitivePayload(value = "keep", schemaType = SchemaType.TEXT)),
                attr2Id to EntityAttribute(payload = EntityAttributePrimitivePayload(value = "drop", schemaType = SchemaType.TEXT)),
            ),
            icon = Icon(type = IconType.FILE, colour = IconColour.NEUTRAL),
            identifierKey = attr1Id,
        )
        val queryResult = EntityQueryResult(
            entities = listOf(entity),
            totalCount = 1,
            hasNextPage = false,
            projection = null,
        )

        runBlocking {
            whenever(entityQueryService.execute(any(), eq(workspaceId), any(), any(), any()))
                .thenReturn(queryResult)
        }
        whenever(entityRelationshipService.findRelatedEntities(any<Set<UUID>>(), eq(workspaceId)))
            .thenReturn(emptyMap())

        val request = EntityQueryRequest(projection = QueryProjection(includeAttributes = listOf(attr1Id)))
        val response = service.queryEntities(workspaceId, entityTypeId, request)

        val resultPayload = response.entities.first().payload
        assertTrue(resultPayload.containsKey(attr1Id))
        assertFalse(resultPayload.containsKey(attr2Id))
    }

    @Test
    fun `queryEntities with projection preserves relationship attributes when includeRelationships specified`() {
        val entityId = UUID.randomUUID()
        val relDefId = UUID.randomUUID()
        val entity = Entity(
            id = entityId,
            workspaceId = workspaceId,
            typeId = entityTypeId,
            payload = mapOf(
                attributeId to EntityAttribute(payload = EntityAttributePrimitivePayload(value = "val", schemaType = SchemaType.TEXT)),
            ),
            icon = Icon(type = IconType.FILE, colour = IconColour.NEUTRAL),
            identifierKey = attributeId,
        )
        val queryResult = EntityQueryResult(
            entities = listOf(entity),
            totalCount = 1,
            hasNextPage = false,
            projection = null,
        )

        val link = EntityLink(
            id = UUID.randomUUID(),
            workspaceId = workspaceId,
            definitionId = relDefId,
            sourceEntityId = entityId,
            icon = Icon(type = IconType.FILE, colour = IconColour.NEUTRAL),
            key = "key",
            label = "Label",
        )
        val relationships = mapOf(entityId to mapOf(relDefId to listOf(link)))

        runBlocking {
            whenever(entityQueryService.execute(any(), eq(workspaceId), any(), any(), any()))
                .thenReturn(queryResult)
        }
        whenever(entityRelationshipService.findRelatedEntities(any<Set<UUID>>(), eq(workspaceId)))
            .thenReturn(relationships)

        val request = EntityQueryRequest(
            projection = QueryProjection(
                includeAttributes = listOf(attributeId),
                includeRelationships = listOf(relDefId),
            )
        )
        val response = service.queryEntities(workspaceId, entityTypeId, request)

        val resultPayload = response.entities.first().payload
        assertTrue(resultPayload.containsKey(attributeId))
        assertTrue(resultPayload.containsKey(relDefId))
    }

    @Test
    fun `queryEntities with attribute-only projection does not hydrate or leak relationships`() {
        val entityId = UUID.randomUUID()
        val attr1Id = UUID.randomUUID()
        val entity = Entity(
            id = entityId,
            workspaceId = workspaceId,
            typeId = entityTypeId,
            payload = mapOf(
                attr1Id to EntityAttribute(payload = EntityAttributePrimitivePayload(value = "keep", schemaType = SchemaType.TEXT)),
            ),
            icon = Icon(type = IconType.FILE, colour = IconColour.NEUTRAL),
            identifierKey = attr1Id,
        )
        val queryResult = EntityQueryResult(
            entities = listOf(entity),
            totalCount = 1,
            hasNextPage = false,
            projection = null,
        )

        runBlocking {
            whenever(entityQueryService.execute(any(), eq(workspaceId), any(), any(), any()))
                .thenReturn(queryResult)
        }

        val request = EntityQueryRequest(
            projection = QueryProjection(includeAttributes = listOf(attr1Id))
        )
        val response = service.queryEntities(workspaceId, entityTypeId, request)

        val resultPayload = response.entities.first().payload
        assertTrue(resultPayload.containsKey(attr1Id))
        assertEquals(1, resultPayload.size, "Only the requested attribute should be present")

        // Verify relationship hydration was never called
        verify(entityRelationshipService, never()).findRelatedEntities(any<Set<UUID>>(), any())
    }

    @Test
    fun `queryEntities with null projection returns full payload`() {
        val attr1Id = UUID.randomUUID()
        val attr2Id = UUID.randomUUID()
        val entity = Entity(
            id = UUID.randomUUID(),
            workspaceId = workspaceId,
            typeId = entityTypeId,
            payload = mapOf(
                attr1Id to EntityAttribute(payload = EntityAttributePrimitivePayload(value = "a", schemaType = SchemaType.TEXT)),
                attr2Id to EntityAttribute(payload = EntityAttributePrimitivePayload(value = "b", schemaType = SchemaType.TEXT)),
            ),
            icon = Icon(type = IconType.FILE, colour = IconColour.NEUTRAL),
            identifierKey = attr1Id,
        )
        val queryResult = EntityQueryResult(
            entities = listOf(entity),
            totalCount = 1,
            hasNextPage = false,
            projection = null,
        )

        runBlocking {
            whenever(entityQueryService.execute(any(), eq(workspaceId), any(), isNull(), any()))
                .thenReturn(queryResult)
        }
        whenever(entityRelationshipService.findRelatedEntities(any<Set<UUID>>(), eq(workspaceId)))
            .thenReturn(emptyMap())

        val request = EntityQueryRequest(projection = null)
        val response = service.queryEntities(workspaceId, entityTypeId, request)

        val resultPayload = response.entities.first().payload
        assertTrue(resultPayload.containsKey(attr1Id))
        assertTrue(resultPayload.containsKey(attr2Id))
    }

    // ------ Test Helpers ------

    private fun createTestEntity(id: UUID = UUID.randomUUID()): Entity {
        return Entity(
            id = id,
            workspaceId = workspaceId,
            typeId = entityTypeId,
            payload = mapOf(
                attributeId to EntityAttribute(
                    payload = EntityAttributePrimitivePayload(
                        value = "test",
                        schemaType = SchemaType.TEXT,
                    )
                )
            ),
            icon = Icon(type = IconType.FILE, colour = IconColour.NEUTRAL),
            identifierKey = attributeId,
        )
    }
}
