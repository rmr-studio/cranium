package cranium.core.service.entity.type

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import cranium.core.configuration.auth.WorkspaceSecurity
import cranium.core.entity.entity.EntityTypeSequenceEntity
import cranium.core.entity.entity.EntityTypeSequenceId
import cranium.core.repository.entity.EntityTypeSequenceRepository
import cranium.core.service.auth.AuthTokenService
import cranium.core.service.util.BaseServiceTest
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        EntityTypeSequenceService::class,
    ]
)
class EntityTypeSequenceServiceTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var sequenceRepository: EntityTypeSequenceRepository

    @Autowired
    private lateinit var service: EntityTypeSequenceService

    private val entityTypeId = UUID.randomUUID()
    private val attributeId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        reset(sequenceRepository)
    }

    @Test
    fun `initializeSequence creates a new sequence row with value 0`() {
        whenever(sequenceRepository.save(any<EntityTypeSequenceEntity>())).thenAnswer { it.arguments[0] }

        service.initializeSequence(entityTypeId, attributeId)

        verify(sequenceRepository).save(argThat<EntityTypeSequenceEntity> {
            this.entityTypeId == this@EntityTypeSequenceServiceTest.entityTypeId &&
                this.attributeId == this@EntityTypeSequenceServiceTest.attributeId &&
                this.currentValue == 0L
        })
    }

    @Test
    fun `initializeSequence skips creation when sequence already exists`() {
        val id = EntityTypeSequenceId(entityTypeId, attributeId)
        whenever(sequenceRepository.existsById(id)).thenReturn(true)

        service.initializeSequence(entityTypeId, attributeId)

        verify(sequenceRepository, never()).save(any<EntityTypeSequenceEntity>())
    }

    @Test
    fun `nextValue increments and returns the new sequence value`() {
        whenever(sequenceRepository.incrementAndGet(entityTypeId, attributeId)).thenReturn(1L)

        val result = service.nextValue(entityTypeId, attributeId)

        assertEquals(1L, result)
        verify(sequenceRepository).incrementAndGet(entityTypeId, attributeId)
    }

    @Test
    fun `nextValue returns sequential values`() {
        whenever(sequenceRepository.incrementAndGet(entityTypeId, attributeId))
            .thenReturn(1L, 2L, 3L)

        assertEquals(1L, service.nextValue(entityTypeId, attributeId))
        assertEquals(2L, service.nextValue(entityTypeId, attributeId))
        assertEquals(3L, service.nextValue(entityTypeId, attributeId))
    }

    @Test
    fun `nextValue throws when sequence row is missing`() {
        whenever(sequenceRepository.incrementAndGet(entityTypeId, attributeId)).thenReturn(null)

        val exception = assertThrows<IllegalStateException> {
            service.nextValue(entityTypeId, attributeId)
        }

        assertTrue(exception.message!!.contains("Sequence row missing"))
    }

    @Test
    fun `formatId combines prefix with sequence number`() {
        val result = service.formatId("PKR", 42L)

        assertEquals("PKR-42", result)
    }

    @Test
    fun `formatId handles single digit`() {
        val result = service.formatId("TE", 1L)

        assertEquals("TE-1", result)
    }
}
