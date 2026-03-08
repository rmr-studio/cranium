package riven.core.service.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import riven.core.configuration.storage.StorageConfigurationProperties
import io.github.oshai.kotlinlogging.KLogger
import org.mockito.kotlin.mock
import java.time.Duration

/**
 * Unit tests for SignedUrlService.
 *
 * Tests HMAC-SHA256 token generation and validation including:
 * - Valid token round-trip (generate -> validate)
 * - Expired token rejection
 * - Tampered signature rejection
 * - Tampered storage key rejection
 * - Malformed token rejection
 * - Download URL format
 * - Expiry clamping to max bounds
 */
class SignedUrlServiceTest {

    private lateinit var signedUrlService: SignedUrlService
    private val logger: KLogger = mock()

    private val config = StorageConfigurationProperties(
        signedUrl = StorageConfigurationProperties.SignedUrl(
            secret = "test-hmac-secret-for-unit-tests-must-be-long-enough",
            defaultExpirySeconds = 3600,
            maxExpirySeconds = 86400
        )
    )

    @BeforeEach
    fun setUp() {
        signedUrlService = SignedUrlService(logger, config)
    }

    // ------ Token Generation and Validation ------

    @Test
    fun `generateToken and validateToken round-trip succeeds`() {
        val storageKey = "workspace-id/avatar/some-uuid.png"
        val token = signedUrlService.generateToken(storageKey, Duration.ofHours(1))

        val result = signedUrlService.validateToken(token)

        assertNotNull(result)
        assertEquals(storageKey, result!!.first)
        assertTrue(result.second > System.currentTimeMillis() / 1000)
    }

    @Test
    fun `validateToken returns null for expired token`() {
        val storageKey = "workspace-id/avatar/some-uuid.png"
        // Generate token with negative duration to simulate already-expired
        val token = signedUrlService.generateToken(storageKey, Duration.ofSeconds(-10))

        val result = signedUrlService.validateToken(token)

        assertNull(result)
    }

    @Test
    fun `validateToken returns null for tampered signature`() {
        val storageKey = "workspace-id/avatar/some-uuid.png"
        val token = signedUrlService.generateToken(storageKey, Duration.ofHours(1))

        // Decode, replace the signature entirely, re-encode
        val decoded = String(java.util.Base64.getUrlDecoder().decode(token))
        val lastColonIndex = decoded.lastIndexOf(':')
        val payload = decoded.substring(0, lastColonIndex)
        val tamperedToken = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$payload:invalidsignature".toByteArray())

        val result = signedUrlService.validateToken(tamperedToken)

        assertNull(result)
    }

    @Test
    fun `validateToken returns null for tampered storage key`() {
        val storageKey = "workspace-id/avatar/some-uuid.png"
        val token = signedUrlService.generateToken(storageKey, Duration.ofHours(1))

        // Decode, tamper with the key, re-encode (but signature won't match)
        val decoded = java.util.Base64.getUrlDecoder().decode(token)
        val decodedStr = String(decoded)
        val tamperedStr = decodedStr.replaceFirst("workspace-id", "other-workspace")
        val tamperedToken = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(tamperedStr.toByteArray())

        val result = signedUrlService.validateToken(tamperedToken)

        assertNull(result)
    }

    @Test
    fun `validateToken returns null for malformed token - not Base64`() {
        val result = signedUrlService.validateToken("not-valid-base64!!!")

        assertNull(result)
    }

    @Test
    fun `validateToken returns null for malformed token - wrong format`() {
        val malformed = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("no-colons-here".toByteArray())

        val result = signedUrlService.validateToken(malformed)

        assertNull(result)
    }

    @Test
    fun `validateToken returns null for empty token`() {
        val result = signedUrlService.validateToken("")

        assertNull(result)
    }

    // ------ Download URL Format ------

    @Test
    fun `generateDownloadUrl returns correct URL format`() {
        val storageKey = "workspace-id/avatar/some-uuid.png"
        val url = signedUrlService.generateDownloadUrl(storageKey, Duration.ofHours(1))

        assertTrue(url.startsWith("/api/v1/storage/download/"))
        // Extract the token part and verify it validates
        val token = url.removePrefix("/api/v1/storage/download/")
        val result = signedUrlService.validateToken(token)
        assertNotNull(result)
        assertEquals(storageKey, result!!.first)
    }

    // ------ Expiry Bounds ------

    @Test
    fun `custom expiry within bounds is honored`() {
        val storageKey = "workspace-id/avatar/some-uuid.png"
        val customExpiry = Duration.ofMinutes(30)
        val beforeGenerate = System.currentTimeMillis() / 1000

        val token = signedUrlService.generateToken(storageKey, customExpiry)
        val result = signedUrlService.validateToken(token)

        assertNotNull(result)
        val expiresAt = result!!.second
        // Should expire roughly 30 minutes from now (within 5 seconds tolerance)
        val expectedExpiry = beforeGenerate + customExpiry.seconds
        assertTrue(expiresAt in (expectedExpiry - 5)..(expectedExpiry + 5))
    }

    @Test
    fun `custom expiry exceeding max is clamped to max`() {
        val storageKey = "workspace-id/avatar/some-uuid.png"
        val excessiveExpiry = Duration.ofDays(30) // Way beyond 86400 seconds max
        val beforeGenerate = System.currentTimeMillis() / 1000

        val token = signedUrlService.generateToken(storageKey, excessiveExpiry)
        val result = signedUrlService.validateToken(token)

        assertNotNull(result)
        val expiresAt = result!!.second
        // Should be clamped to maxExpirySeconds (86400) not 30 days
        val expectedMax = beforeGenerate + config.signedUrl.maxExpirySeconds
        assertTrue(expiresAt in (expectedMax - 5)..(expectedMax + 5))
    }

    // ------ Default Expiry ------

    @Test
    fun `getDefaultExpiry returns configured default`() {
        val expiry = signedUrlService.getDefaultExpiry()

        assertEquals(Duration.ofSeconds(3600), expiry)
    }
}
