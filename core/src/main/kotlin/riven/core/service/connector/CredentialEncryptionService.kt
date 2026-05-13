package cranium.core.service.connector

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import cranium.core.configuration.properties.DataConnectorConfigurationProperties
import cranium.core.exceptions.connector.CryptoException
import cranium.core.exceptions.connector.DataCorruptionException
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM credential encryption service. Per-record 12-byte IV generated via
 * [SecureRandom] on every [encrypt] call. 128-bit authentication tag.
 *
 * The KLogger is injected for future operational logging; the plaintext credential
 * material is NEVER logged at any level. See Phase 2 SEC-05/SEC-06.
 *
 * Fail-fast on malformed keys at bean initialisation.
 */
@Service
@ConditionalOnProperty(prefix = "cranium.connector", name = ["enabled"], havingValue = "true")
class CredentialEncryptionService(
    @Suppress("unused") private val logger: KLogger,
    props: DataConnectorConfigurationProperties,
) {
    private val secretKeySpec: SecretKeySpec = initKey(props.credentialEncryptionKey)

    /**
     * Encrypts [plaintext] under the configured AES-256 key with a fresh 12-byte IV.
     *
     * @return [EncryptedCredentials] with `keyVersion = 1` (v1 wire format).
     */
    fun encrypt(plaintext: String): EncryptedCredentials {
        val iv = ByteArray(IV_LEN_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedCredentials(ciphertext, iv, keyVersion = KEY_VERSION)
    }

    /**
     * Decrypts [encrypted]. On GCM tag mismatch (corruption OR wrong key), throws
     * [DataCorruptionException]; on any other crypto failure, throws [CryptoException].
     */
    fun decrypt(encrypted: EncryptedCredentials): String {
        return try {
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, GCMParameterSpec(GCM_TAG_BITS, encrypted.iv))
            String(cipher.doFinal(encrypted.ciphertext), Charsets.UTF_8)
        } catch (e: AEADBadTagException) {
            throw DataCorruptionException("Credential ciphertext is corrupted or key mismatch", e)
        } catch (e: GeneralSecurityException) {
            throw CryptoException("Credential decryption failed", e)
        }
    }

    private fun initKey(base64Key: String): SecretKeySpec {
        require(base64Key.isNotBlank()) {
            "RIVEN_CREDENTIAL_ENCRYPTION_KEY must be set (base64-encoded 32-byte key)"
        }
        val keyBytes = try {
            Base64.getDecoder().decode(base64Key)
        } catch (e: IllegalArgumentException) {
            throw CryptoException("RIVEN_CREDENTIAL_ENCRYPTION_KEY is not valid Base64", e)
        }
        require(keyBytes.size == AES_256_KEY_BYTES) {
            "RIVEN_CREDENTIAL_ENCRYPTION_KEY must decode to $AES_256_KEY_BYTES bytes (256-bit); got ${keyBytes.size}"
        }
        return SecretKeySpec(keyBytes, "AES")
    }

    companion object {
        private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
        private const val AES_256_KEY_BYTES = 32
        private const val IV_LEN_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val KEY_VERSION = 1
    }
}
