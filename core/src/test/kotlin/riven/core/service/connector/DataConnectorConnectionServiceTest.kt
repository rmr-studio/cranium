package riven.core.service.connector

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.connector.DataConnectorConnectionEntity
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.connector.SslMode
import riven.core.enums.integration.ConnectionStatus
import riven.core.enums.util.OperationType
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.exceptions.NotFoundException
import riven.core.exceptions.connector.CryptoException
import riven.core.exceptions.connector.DataCorruptionException
import riven.core.exceptions.connector.ReadOnlyVerificationException
import riven.core.exceptions.connector.SsrfRejectedException
import riven.core.models.connector.CredentialPayload
import riven.core.models.connector.request.CreateDataConnectorConnectionRequest
import riven.core.models.connector.request.UpdateDataConnectorConnectionRequest
import riven.core.repository.connector.CustomSourceConnectionRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.connector.pool.WorkspaceConnectionPoolManager
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import riven.core.service.util.factory.customsource.DataConnectorConnectionEntityFactory
import java.net.InetAddress
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for [DataConnectorConnectionService].
 *
 * Covers CONN-03 (gate orchestration in @Transactional), SEC-05 (gates run
 * before any persistence), and SEC-06 (CryptoException / DataCorruption
 * surface via ConnectionStatus — never as HTTP errors).
 */
@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SecurityTestConfig::class,
        DataConnectorConnectionServiceTest.TestConfig::class,
        DataConnectorConnectionService::class,
    ],
)
@TestPropertySource(properties = ["riven.connector.enabled=true"])
@WithUserPersona(
    userId = "11111111-1111-1111-1111-111111111111",
    email = "test@test.com",
    displayName = "Test User",
    roles = [
        WorkspaceRole(
            workspaceId = "22222222-2222-2222-2222-222222222222",
            role = WorkspaceRoles.ADMIN,
        ),
    ],
)
class DataConnectorConnectionServiceTest {

    @Configuration
    class TestConfig {
        @Bean
        fun objectMapper(): ObjectMapper = jacksonObjectMapper()
    }

    private val userId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val workspaceId: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val otherWorkspaceId: UUID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")

    @MockitoBean private lateinit var repository: CustomSourceConnectionRepository
    @MockitoBean private lateinit var encryptionService: CredentialEncryptionService
    @MockitoBean private lateinit var ssrfValidator: SsrfValidatorService
    @MockitoBean private lateinit var roVerifier: ReadOnlyRoleVerifierService
    @MockitoBean private lateinit var authTokenService: AuthTokenService
    @MockitoBean private lateinit var activityService: ActivityService
    @MockitoBean private lateinit var logger: KLogger
    @MockitoBean private lateinit var poolManager: WorkspaceConnectionPoolManager

    @Autowired private lateinit var service: DataConnectorConnectionService
    @Autowired private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setup() {
        reset(repository, encryptionService, ssrfValidator, roVerifier, activityService, authTokenService, poolManager)
        whenever(authTokenService.getUserId()).thenReturn(userId)
    }

    private fun validRequest() = CreateDataConnectorConnectionRequest(
        workspaceId = workspaceId,
        name = "prod-warehouse",
        host = "db.example.com",
        port = 5432,
        database = "analytics",
        user = "readonly",
        password = "hunter2",
        sslMode = SslMode.REQUIRE,
    )

    private fun entityWithId(
        id: UUID = UUID.randomUUID(),
        wsId: UUID = workspaceId,
        ciphertext: ByteArray = ByteArray(48) { it.toByte() },
        iv: ByteArray = ByteArray(12) { it.toByte() },
        status: ConnectionStatus = ConnectionStatus.CONNECTED,
    ): DataConnectorConnectionEntity {
        val e = DataConnectorConnectionEntityFactory.create(
            workspaceId = wsId,
            encryptedCredentials = ciphertext,
            iv = iv,
            connectionStatus = status,
        )
        // Reflection to set the generated id — the factory deliberately leaves it null
        // to preserve persist() semantics in integration tests; for unit tests we need
        // the post-save state where id is non-null.
        val idField = DataConnectorConnectionEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(e, id)
        return e
    }

    // ------ create() ------

    @Test
    fun `create rolls back when SSRF rejects`() {
        whenever(ssrfValidator.validateAndResolve(any()))
            .thenThrow(SsrfRejectedException("Host not reachable: blocked"))

        assertThrows<SsrfRejectedException> { service.create(validRequest()) }

        verify(repository, never()).save(any<DataConnectorConnectionEntity>())
        verify(encryptionService, never()).encrypt(any())
        verify(roVerifier, never()).verify(any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `create rolls back when RO verify rejects`() {
        whenever(ssrfValidator.validateAndResolve(any()))
            .thenReturn(listOf(InetAddress.getByName("8.8.8.8")))
        whenever(roVerifier.verify(any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(ReadOnlyVerificationException("Role has write privileges"))

        assertThrows<ReadOnlyVerificationException> { service.create(validRequest()) }

        verify(repository, never()).save(any<DataConnectorConnectionEntity>())
        verify(encryptionService, never()).encrypt(any())
    }

    @Test
    fun `create encrypts credentials and saves on success`() {
        whenever(ssrfValidator.validateAndResolve(any()))
            .thenReturn(listOf(InetAddress.getByName("8.8.8.8")))
        whenever(encryptionService.encrypt(any()))
            .thenReturn(EncryptedCredentials(ByteArray(64) { 1 }, ByteArray(12) { 2 }, 1))
        whenever(repository.save(any<DataConnectorConnectionEntity>()))
            .thenAnswer { inv ->
                val e = inv.arguments[0] as DataConnectorConnectionEntity
                val idField = DataConnectorConnectionEntity::class.java.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(e, UUID.randomUUID())
                e
            }

        val result = service.create(validRequest())

        assertEquals(ConnectionStatus.CONNECTED, result.connectionStatus)
        assertEquals("db.example.com", result.host)
        verify(repository).save(argThat<DataConnectorConnectionEntity> { e ->
            e.encryptedCredentials.isNotEmpty() && e.iv.isNotEmpty() && e.keyVersion == 1
        })
        verify(activityService).logActivity(
            activity = eq(Activity.DATA_CONNECTOR_CONNECTION),
            operation = eq(OperationType.CREATE),
            userId = eq(userId),
            workspaceId = eq(workspaceId),
            entityType = eq(ApplicationEntityType.DATA_CONNECTOR_CONNECTION),
            entityId = anyOrNull(),
            timestamp = any(),
            details = any(),
        )
    }

    @Test
    fun `create blocks cross-workspace access via @PreAuthorize`() {
        val foreign = validRequest().copy(workspaceId = otherWorkspaceId)
        assertThrows<AccessDeniedException> { service.create(foreign) }
        verify(repository, never()).save(any<DataConnectorConnectionEntity>())
    }

    // ------ getById / decrypt failure modes ------

    @Test
    fun `CryptoException during decrypt transitions status to FAILED with config error message`() {
        val id = UUID.randomUUID()
        val entity = entityWithId(id = id)
        whenever(repository.findByIdAndWorkspaceId(id, workspaceId)).thenReturn(Optional.of(entity))
        whenever(encryptionService.decrypt(any())).thenThrow(CryptoException("bad key"))
        whenever(repository.save(any<DataConnectorConnectionEntity>())).thenAnswer { it.arguments[0] }

        val model = service.getById(workspaceId, id)

        assertEquals(ConnectionStatus.FAILED, model.connectionStatus)
        verify(repository).save(argThat<DataConnectorConnectionEntity> { e ->
            e.connectionStatus == ConnectionStatus.FAILED &&
                (e.lastFailureReason?.contains("Configuration error") == true)
        })
    }

    @Test
    fun `DataCorruptionException during decrypt transitions status to FAILED with re-enter message`() {
        val id = UUID.randomUUID()
        val entity = entityWithId(id = id)
        whenever(repository.findByIdAndWorkspaceId(id, workspaceId)).thenReturn(Optional.of(entity))
        whenever(encryptionService.decrypt(any())).thenThrow(DataCorruptionException("tag mismatch"))
        whenever(repository.save(any<DataConnectorConnectionEntity>())).thenAnswer { it.arguments[0] }

        val model = service.getById(workspaceId, id)

        assertEquals(ConnectionStatus.FAILED, model.connectionStatus)
        verify(repository).save(argThat<DataConnectorConnectionEntity> { e ->
            (e.lastFailureReason?.contains("re-enter the password") == true)
        })
    }

    // ------ listByWorkspace ------

    @Test
    fun `listByWorkspace isolates per-row decrypt failures`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val good = entityWithId(id = id1, ciphertext = ByteArray(32) { 1 }, iv = ByteArray(12) { 1 })
        val bad = entityWithId(id = id2, ciphertext = ByteArray(32) { 2 }, iv = ByteArray(12) { 2 })

        whenever(repository.findByWorkspaceId(workspaceId)).thenReturn(listOf(good, bad))

        val goodPayload = CredentialPayload(
            "ok.example.com", 5432, "d", "u", "p", SslMode.REQUIRE,
        )
        // doAnswer so per-invocation routing is clean regardless of arg identity
        whenever(encryptionService.decrypt(any())).doAnswer { inv ->
            val ec = inv.arguments[0] as EncryptedCredentials
            when (ec.ciphertext[0]) {
                1.toByte() -> objectMapper.writeValueAsString(goodPayload)
                else -> throw DataCorruptionException("corrupt")
            }
        }
        whenever(repository.save(any<DataConnectorConnectionEntity>())).thenAnswer { it.arguments[0] }

        val result = service.listByWorkspace(workspaceId)

        assertEquals(2, result.size)
        val byId = result.associateBy { it.id }
        assertEquals(ConnectionStatus.CONNECTED, byId[id1]!!.connectionStatus)
        assertEquals(ConnectionStatus.FAILED, byId[id2]!!.connectionStatus)
    }

    // ------ update() ------

    @Test
    fun `update with name only skips the gate chain`() {
        val id = UUID.randomUUID()
        val entity = entityWithId(id = id)
        whenever(repository.findByIdAndWorkspaceId(id, workspaceId)).thenReturn(Optional.of(entity))
        whenever(repository.save(any<DataConnectorConnectionEntity>())).thenAnswer { it.arguments[0] }
        val goodPayload = CredentialPayload(
            "db.example.com", 5432, "d", "u", "pw", SslMode.REQUIRE,
        )
        whenever(encryptionService.decrypt(any())).thenReturn(objectMapper.writeValueAsString(goodPayload))

        service.update(workspaceId, id, UpdateDataConnectorConnectionRequest(name = "renamed"))

        verify(ssrfValidator, never()).validateAndResolve(any())
        verify(roVerifier, never()).verify(any(), any(), any(), any(), any(), any(), any())
        verify(encryptionService, never()).encrypt(any())
        verify(repository).save(argThat<DataConnectorConnectionEntity> { e -> e.name == "renamed" })
    }

    @Test
    fun `update with host change re-runs gate chain and re-encrypts with merged payload`() {
        val id = UUID.randomUUID()
        val oldCiphertext = ByteArray(32) { 9 }
        val entity = entityWithId(id = id, ciphertext = oldCiphertext)
        whenever(repository.findByIdAndWorkspaceId(id, workspaceId)).thenReturn(Optional.of(entity))

        val currentPayload = CredentialPayload(
            "old.example.com", 5432, "olddb", "olduser", "oldpw", SslMode.REQUIRE,
        )
        whenever(encryptionService.decrypt(any())).thenReturn(objectMapper.writeValueAsString(currentPayload))
        whenever(ssrfValidator.validateAndResolve(any()))
            .thenReturn(listOf(InetAddress.getByName("8.8.8.8")))

        var capturedPlaintext: String? = null
        whenever(encryptionService.encrypt(any())).doAnswer { inv ->
            capturedPlaintext = inv.arguments[0] as String
            EncryptedCredentials(ByteArray(64) { 5 }, ByteArray(12) { 6 }, 1)
        }
        whenever(repository.save(any<DataConnectorConnectionEntity>())).thenAnswer { it.arguments[0] }

        service.update(
            workspaceId, id,
            UpdateDataConnectorConnectionRequest(host = "new.example.com"),
        )

        verify(ssrfValidator).validateAndResolve(eq("new.example.com"))
        verify(roVerifier).verify(eq("new.example.com"), any(), any(), any(), any(), any(), any())
        val merged: CredentialPayload = objectMapper.readValue(capturedPlaintext!!)
        assertEquals("new.example.com", merged.host)
        assertEquals("oldpw", merged.password, "password preserved when not in PATCH")
        assertEquals("olduser", merged.user)
    }

    @Test
    fun `update with password change re-runs gates and rotates ciphertext`() {
        val id = UUID.randomUUID()
        val oldCiphertext = ByteArray(32) { 9 }
        val entity = entityWithId(id = id, ciphertext = oldCiphertext)
        whenever(repository.findByIdAndWorkspaceId(id, workspaceId)).thenReturn(Optional.of(entity))

        val current = CredentialPayload("h", 5432, "d", "u", "old", SslMode.REQUIRE)
        whenever(encryptionService.decrypt(any())).thenReturn(objectMapper.writeValueAsString(current))
        whenever(ssrfValidator.validateAndResolve(any()))
            .thenReturn(listOf(InetAddress.getByName("8.8.8.8")))

        val newCiphertext = ByteArray(64) { 7 }
        whenever(encryptionService.encrypt(any()))
            .thenReturn(EncryptedCredentials(newCiphertext, ByteArray(12) { 8 }, 1))
        whenever(repository.save(any<DataConnectorConnectionEntity>())).thenAnswer { it.arguments[0] }

        service.update(
            workspaceId, id,
            UpdateDataConnectorConnectionRequest(password = "new-pw"),
        )

        verify(ssrfValidator).validateAndResolve(any())
        verify(roVerifier).verify(any(), any(), any(), any(), any(), any(), any())
        verify(repository).save(argThat<DataConnectorConnectionEntity> { e ->
            e.encryptedCredentials.contentEquals(newCiphertext) &&
                !e.encryptedCredentials.contentEquals(oldCiphertext)
        })
    }

    @Test
    fun `update on missing entity throws NotFoundException`() {
        val id = UUID.randomUUID()
        whenever(repository.findByIdAndWorkspaceId(id, workspaceId)).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            service.update(workspaceId, id, UpdateDataConnectorConnectionRequest(name = "x"))
        }
    }

    // ------ softDelete ------

    @Test
    fun `softDelete sets deleted flag and logs activity`() {
        val id = UUID.randomUUID()
        val entity = entityWithId(id = id)
        whenever(repository.findByIdAndWorkspaceId(id, workspaceId)).thenReturn(Optional.of(entity))
        whenever(repository.save(any<DataConnectorConnectionEntity>())).thenAnswer { it.arguments[0] }

        service.softDelete(workspaceId, id)

        verify(repository).save(argThat<DataConnectorConnectionEntity> { e -> e.deleted && e.deletedAt != null })
        verify(activityService).logActivity(
            activity = eq(Activity.DATA_CONNECTOR_CONNECTION),
            operation = eq(OperationType.DELETE),
            userId = eq(userId),
            workspaceId = eq(workspaceId),
            entityType = eq(ApplicationEntityType.DATA_CONNECTOR_CONNECTION),
            entityId = eq(id),
            timestamp = any(),
            details = any(),
        )
    }

    @Test
    fun `getById blocks cross-workspace via @PreAuthorize`() {
        assertThrows<AccessDeniedException> {
            service.getById(otherWorkspaceId, UUID.randomUUID())
        }
    }

    // ------ pool eviction wiring (Phase 3 03-02) ------

    @Test
    fun `update with credential change evicts pool`() {
        val id = UUID.randomUUID()
        val entity = entityWithId(id = id)
        whenever(repository.findByIdAndWorkspaceId(id, workspaceId)).thenReturn(Optional.of(entity))

        val current = CredentialPayload("h", 5432, "d", "u", "old", SslMode.REQUIRE)
        whenever(encryptionService.decrypt(any())).thenReturn(objectMapper.writeValueAsString(current))
        whenever(ssrfValidator.validateAndResolve(any()))
            .thenReturn(listOf(InetAddress.getByName("8.8.8.8")))
        whenever(encryptionService.encrypt(any()))
            .thenReturn(EncryptedCredentials(ByteArray(64) { 1 }, ByteArray(12) { 2 }, 1))
        whenever(repository.save(any<DataConnectorConnectionEntity>())).thenAnswer { it.arguments[0] }

        service.update(workspaceId, id, UpdateDataConnectorConnectionRequest(password = "new-pw"))

        verify(poolManager).evict(eq(id))
    }

    @Test
    fun `update with cosmetic change only does not evict pool`() {
        val id = UUID.randomUUID()
        val entity = entityWithId(id = id)
        whenever(repository.findByIdAndWorkspaceId(id, workspaceId)).thenReturn(Optional.of(entity))
        whenever(repository.save(any<DataConnectorConnectionEntity>())).thenAnswer { it.arguments[0] }
        val goodPayload = CredentialPayload("h", 5432, "d", "u", "pw", SslMode.REQUIRE)
        whenever(encryptionService.decrypt(any())).thenReturn(objectMapper.writeValueAsString(goodPayload))

        service.update(workspaceId, id, UpdateDataConnectorConnectionRequest(name = "renamed"))

        verify(poolManager, never()).evict(any())
    }

    @Test
    fun `softDelete evicts pool`() {
        val id = UUID.randomUUID()
        val entity = entityWithId(id = id)
        whenever(repository.findByIdAndWorkspaceId(id, workspaceId)).thenReturn(Optional.of(entity))
        whenever(repository.save(any<DataConnectorConnectionEntity>())).thenAnswer { it.arguments[0] }

        service.softDelete(workspaceId, id)

        verify(poolManager).evict(eq(id))
    }

}
