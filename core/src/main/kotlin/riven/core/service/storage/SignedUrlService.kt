package riven.core.service.storage

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import riven.core.configuration.storage.StorageConfigurationProperties
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Generates and validates HMAC-SHA256 signed tokens for secure file download URLs.
 *
 * Tokens encode a storage key and expiry timestamp, signed with a configurable secret.
 * Validation uses constant-time comparison to prevent timing attacks.
 */
@Service
class SignedUrlService(
    private val logger: KLogger,
    private val config: StorageConfigurationProperties
) {

    private val algorithm = "HmacSHA256"

    // ------ Token Generation ------

    /**
     * Generate a signed token containing the storage key and expiry timestamp.
     *
     * @param storageKey the file's storage key
     * @param expiresIn duration until the token expires (clamped to max if exceeded)
     * @return Base64URL-encoded signed token
     */
    fun generateToken(storageKey: String, expiresIn: Duration): String {
        val clampedExpiry = clampExpiry(expiresIn)
        val expiresAt = Instant.now().plus(clampedExpiry).epochSecond
        val payload = "$storageKey:$expiresAt"
        val signature = computeHmac(payload)

        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$payload:$signature".toByteArray())
    }

    // ------ Token Validation ------

    /**
     * Validate a signed token and return the storage key and expiry if valid.
     *
     * Uses constant-time comparison via MessageDigest.isEqual() to prevent timing attacks.
     *
     * @param token Base64URL-encoded signed token
     * @return Pair of (storageKey, expiresAtEpochSecond) or null if invalid/expired
     */
    fun validateToken(token: String): Pair<String, Long>? {
        if (token.isBlank()) return null

        val decoded = try {
            String(Base64.getUrlDecoder().decode(token))
        } catch (e: IllegalArgumentException) {
            logger.debug { "Token decode failed: ${e.message}" }
            return null
        }

        // Split on last colon to separate payload from signature
        val lastColonIndex = decoded.lastIndexOf(':')
        if (lastColonIndex <= 0) return null

        val payload = decoded.substring(0, lastColonIndex)
        val providedSignature = decoded.substring(lastColonIndex + 1)

        // Recompute HMAC and compare with constant-time comparison
        val expectedSignature = computeHmac(payload)
        if (!MessageDigest.isEqual(
                providedSignature.toByteArray(),
                expectedSignature.toByteArray()
            )
        ) {
            logger.debug { "Token signature mismatch" }
            return null
        }

        // Parse storageKey and expiresAt from payload
        val payloadColonIndex = payload.lastIndexOf(':')
        if (payloadColonIndex <= 0) return null

        val storageKey = payload.substring(0, payloadColonIndex)
        val expiresAt = try {
            payload.substring(payloadColonIndex + 1).toLong()
        } catch (e: NumberFormatException) {
            logger.debug { "Token expiry parse failed: ${e.message}" }
            return null
        }

        // Check expiry
        if (Instant.now().epochSecond > expiresAt) {
            logger.debug { "Token expired at $expiresAt" }
            return null
        }

        return Pair(storageKey, expiresAt)
    }

    // ------ URL Generation ------

    /**
     * Generate a download URL containing a signed token.
     *
     * @param storageKey the file's storage key
     * @param expiresIn duration until the URL expires
     * @return download URL path in the format "/api/v1/storage/download/{token}"
     */
    fun generateDownloadUrl(storageKey: String, expiresIn: Duration): String {
        val token = generateToken(storageKey, expiresIn)
        return "/api/v1/storage/download/$token"
    }

    // ------ Default Expiry ------

    /**
     * Get the configured default expiry duration.
     */
    fun getDefaultExpiry(): Duration =
        Duration.ofSeconds(config.signedUrl.defaultExpirySeconds)

    // ------ Private Helpers ------

    private fun computeHmac(payload: String): String {
        val mac = Mac.getInstance(algorithm)
        val keySpec = SecretKeySpec(config.signedUrl.secret.toByteArray(), algorithm)
        mac.init(keySpec)
        val hmacBytes = mac.doFinal(payload.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes)
    }

    private fun clampExpiry(expiresIn: Duration): Duration {
        val maxExpiry = Duration.ofSeconds(config.signedUrl.maxExpirySeconds)
        return if (expiresIn > maxExpiry) maxExpiry else expiresIn
    }
}
