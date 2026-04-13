package riven.core.service.connector

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KLogger
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.connector.DataConnectorConnectionEntity
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.connector.SslMode
import riven.core.enums.integration.ConnectionStatus
import riven.core.enums.util.OperationType
import riven.core.exceptions.connector.CryptoException
import riven.core.exceptions.connector.DataCorruptionException
import riven.core.exceptions.connector.ReadOnlyVerificationException
import riven.core.exceptions.connector.SsrfRejectedException
import riven.core.models.connector.ConnectorTestResult
import riven.core.models.connector.CredentialPayload
import riven.core.models.connector.DataConnectorConnectionModel
import riven.core.models.connector.request.CreateDataConnectorConnectionRequest
import riven.core.models.connector.request.DataConnectorConnectionTestRequest
import riven.core.models.connector.request.UpdateDataConnectorConnectionRequest
import riven.core.repository.connector.DataConnectorConnectionRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.connector.pool.WorkspaceConnectionPoolManager
import riven.core.util.ServiceUtil.findOrThrow
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Orchestrates the CRUD + gate-chain lifecycle for data-connector (ie. Postgres)
 * connections.
 * Gate chain on create / touched-update / test:
 *   1. [SsrfValidatorService.validateAndResolve] — resolve + blocklist
 *   2. [ReadOnlyRoleVerifierService.verify] — probe role read-only-ness
 *   3. [CredentialEncryptionService.encrypt] — AES-256-GCM seal (create/update only)
 *   4. [DataConnectorConnectionRepository.save] — persist (create/update only)
 *
 * Any gate failure rolls back the transaction. [CryptoException] and
 * [DataCorruptionException] at read-time transition the entity's
 * [ConnectionStatus] to FAILED with a user-safe message — they are NEVER
 * thrown to the HTTP layer (per locked Phase-2 decision).
 */
@Service
@ConditionalOnProperty(prefix = "riven.connector", name = ["enabled"], havingValue = "true")
class DataConnectorConnectionService(
    private val logger: KLogger,
    private val repository: DataConnectorConnectionRepository,
    private val encryptionService: CredentialEncryptionService,
    private val ssrfValidator: SsrfValidatorService,
    private val roVerifier: ReadOnlyRoleVerifierService,
    private val authTokenService: AuthTokenService,
    private val activityService: ActivityService,
    private val objectMapper: ObjectMapper,
    private val poolManager: WorkspaceConnectionPoolManager,
) {

    // ------ Public mutations ------

    /**
     * Create a new connection: run SSRF + RO gates, encrypt credentials, persist.
     * Any gate failure rolls back (no partial persist).
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#request.workspaceId)")
    fun create(request: CreateDataConnectorConnectionRequest): DataConnectorConnectionModel {
        val userId = authTokenService.getUserId()
        runGateChain(
            request.host, request.port, request.database,
            request.user, request.password, request.sslMode,
        )
        val encrypted = encryptPayload(toPayload(request))
        val entity = DataConnectorConnectionEntity(
            workspaceId = request.workspaceId,
            name = request.name,
            connectionStatus = ConnectionStatus.CONNECTED,
            encryptedCredentials = encrypted.ciphertext,
            iv = encrypted.iv,
            keyVersion = encrypted.keyVersion,
            lastVerifiedAt = ZonedDateTime.now(),
        )
        val saved = repository.save(entity)
        logActivity(
            operation = OperationType.CREATE,
            userId = userId,
            workspaceId = request.workspaceId,
            entityId = requireNotNull(saved.id) { "saved entity must have id after save" },
            details = mapOf("name" to request.name, "host" to request.host, "port" to request.port),
        )
        return saved.toModel(
            request.host, request.port, request.database,
            request.user, request.sslMode.value,
        )
    }

    /**
     * Dry-run gate validation — no encrypt, no persist. Aggregate pass/fail
     * with a category indicator for the caller to render.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#request.workspaceId)")
    fun test(request: DataConnectorConnectionTestRequest): ConnectorTestResult {
        return try {
            runGateChain(
                request.host, request.port, request.database,
                request.user, request.password, request.sslMode,
            )
            ConnectorTestResult(pass = true, category = null, message = "All gates passed")
        } catch (e: SsrfRejectedException) {
            ConnectorTestResult(pass = false, category = "SSRF", message = e.message ?: "SSRF rejected")
        } catch (e: ReadOnlyVerificationException) {
            ConnectorTestResult(
                pass = false,
                category = "RO_VERIFY",
                message = e.message ?: "Role verification failed",
            )
        }
    }

    /**
     * PATCH semantics: if any credential-touching field is present, re-run the
     * full gate chain against the merged payload and re-encrypt. Otherwise
     * apply cosmetic changes only (name).
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun update(
        workspaceId: UUID,
        id: UUID,
        request: UpdateDataConnectorConnectionRequest,
    ): DataConnectorConnectionModel {
        val userId = authTokenService.getUserId()
        val entity = findOrThrow { repository.findByIdAndWorkspaceId(id, workspaceId) }

        val updatedModel: DataConnectorConnectionModel = if (request.touchesCredentials()) {
            applyCredentialUpdate(entity, request)
        } else {
            applyCosmeticUpdate(entity, request)
        }

        logActivity(
            operation = OperationType.UPDATE,
            userId = userId,
            workspaceId = workspaceId,
            entityId = id,
            details = mapOf(
                "touchedCredentials" to request.touchesCredentials(),
                "nameChanged" to (request.name != null),
            ),
        )
        return updatedModel
    }

    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun softDelete(workspaceId: UUID, id: UUID) {
        val userId = authTokenService.getUserId()
        val entity = findOrThrow { repository.findByIdAndWorkspaceId(id, workspaceId) }
        entity.deleted = true
        entity.deletedAt = ZonedDateTime.now()
        repository.save(entity)
        // Evict any cached pool for this connection so adapter calls fail fast
        // (the soft-deleted connection is not supposed to serve traffic). Idempotent.
        poolManager.evict(id)
        logActivity(
            operation = OperationType.DELETE,
            userId = userId,
            workspaceId = workspaceId,
            entityId = id,
            details = emptyMap(),
        )
    }

    // ------ Public reads ------

    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getById(workspaceId: UUID, id: UUID): DataConnectorConnectionModel {
        val entity = findOrThrow { repository.findByIdAndWorkspaceId(id, workspaceId) }
        return decryptToModel(entity)
    }

    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun listByWorkspace(workspaceId: UUID): List<DataConnectorConnectionModel> =
        repository.findByWorkspaceId(workspaceId).map { decryptToModel(it) }

    // ------ Private helpers ------

    private fun runGateChain(
        host: String,
        port: Int,
        database: String,
        user: String,
        password: String,
        sslMode: SslMode,
    ) {
        val resolved = ssrfValidator.validateAndResolve(host)
        val primary = resolved.first()
        roVerifier.verify(host, primary, port, database, user, password, sslMode.value)
    }

    private fun encryptPayload(payload: CredentialPayload): EncryptedCredentials =
        encryptionService.encrypt(objectMapper.writeValueAsString(payload))

    /**
     * Decrypt the entity's credentials and materialise the response model.
     * [CryptoException] and [DataCorruptionException] transition the status
     * to FAILED with a user-safe message — they NEVER propagate to HTTP.
     */
    private fun decryptToModel(entity: DataConnectorConnectionEntity): DataConnectorConnectionModel {
        return try {
            val json = encryptionService.decrypt(
                EncryptedCredentials(entity.encryptedCredentials, entity.iv, entity.keyVersion),
            )
            val payload: CredentialPayload = objectMapper.readValue(json)
            entity.toModel(payload.host, payload.port, payload.database, payload.user, payload.sslMode.value)
        } catch (_: DataCorruptionException) {
            val msg = "Stored credentials are unreadable — please re-enter the password."
            transitionToFailed(entity, msg)
            buildFailedModel(entity, reason = msg)
        } catch (_: CryptoException) {
            val msg = "Configuration error — contact support."
            transitionToFailed(entity, msg)
            buildFailedModel(entity, reason = msg)
        }
    }

    private fun applyCredentialUpdate(
        entity: DataConnectorConnectionEntity,
        request: UpdateDataConnectorConnectionRequest,
    ): DataConnectorConnectionModel {
        val currentJson = encryptionService.decrypt(
            EncryptedCredentials(entity.encryptedCredentials, entity.iv, entity.keyVersion),
        )
        val current: CredentialPayload = objectMapper.readValue(currentJson)
        val merged = current.copy(
            host = request.host ?: current.host,
            port = request.port ?: current.port,
            database = request.database ?: current.database,
            user = request.user ?: current.user,
            password = request.password ?: current.password,
            sslMode = request.sslMode ?: current.sslMode,
        )

        runGateChain(merged.host, merged.port, merged.database, merged.user, merged.password, merged.sslMode)

        val reEncrypted = encryptPayload(merged)
        entity.encryptedCredentials = reEncrypted.ciphertext
        entity.iv = reEncrypted.iv
        entity.keyVersion = reEncrypted.keyVersion
        entity.lastVerifiedAt = ZonedDateTime.now()
        if (entity.connectionStatus.canTransitionTo(ConnectionStatus.CONNECTED)) {
            entity.connectionStatus = ConnectionStatus.CONNECTED
            entity.lastFailureReason = null
        }
        request.name?.let { entity.name = it }
        val saved = repository.save(entity)
        // Evict the cached pool so the next adapter call rebuilds with the
        // fresh credentials. No-op if the pool was never built.
        poolManager.evict(requireNotNull(saved.id) { "saved entity must have id after save" })
        return saved.toModel(
            merged.host, merged.port, merged.database, merged.user, merged.sslMode.value,
        )
    }

    private fun applyCosmeticUpdate(
        entity: DataConnectorConnectionEntity,
        request: UpdateDataConnectorConnectionRequest,
    ): DataConnectorConnectionModel {
        request.name?.let { entity.name = it }
        val saved = repository.save(entity)
        return decryptToModel(saved)
    }

    private fun transitionToFailed(entity: DataConnectorConnectionEntity, reason: String) {
        if (entity.connectionStatus.canTransitionTo(ConnectionStatus.FAILED)) {
            entity.connectionStatus = ConnectionStatus.FAILED
            entity.lastFailureReason = reason
            repository.save(entity)
        }
        logger.warn { "Connection ${entity.id} transitioned to FAILED: $reason" }
    }

    private fun buildFailedModel(
        entity: DataConnectorConnectionEntity,
        reason: String,
    ): DataConnectorConnectionModel = DataConnectorConnectionModel(
        id = requireNotNull(entity.id) { "entity.id must not be null for persisted row" },
        workspaceId = entity.workspaceId,
        name = entity.name,
        host = UNAVAILABLE,
        port = 0,
        database = UNAVAILABLE,
        user = UNAVAILABLE,
        sslMode = UNAVAILABLE,
        connectionStatus = ConnectionStatus.FAILED,
        lastVerifiedAt = entity.lastVerifiedAt,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    ).also { logger.debug { "Built FAILED model for ${entity.id}: $reason" } }

    private fun toPayload(r: CreateDataConnectorConnectionRequest) = CredentialPayload(
        host = r.host,
        port = r.port,
        database = r.database,
        user = r.user,
        password = r.password,
        sslMode = r.sslMode,
    )

    private fun logActivity(
        operation: OperationType,
        userId: UUID,
        workspaceId: UUID,
        entityId: UUID,
        details: Map<String, Any>,
    ) {
        activityService.logActivity(
            activity = Activity.DATA_CONNECTOR_CONNECTION,
            operation = operation,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.DATA_CONNECTOR_CONNECTION,
            entityId = entityId,
            details = details,
        )
    }

    companion object {
        private const val UNAVAILABLE = "[unavailable]"
    }
}

