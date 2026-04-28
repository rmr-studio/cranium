package riven.core.service.connector

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import riven.core.configuration.properties.DataConnectorConfigurationProperties
import riven.core.exceptions.connector.CryptoException
import riven.core.exceptions.connector.DataCorruptionException
import java.util.Base64

/**
 * CONN-02: AES-256-GCM credential encryption — round-trip, fresh IV per call,
 * corruption detection, key-mismatch detection, fail-fast bean init validation.
 */
class CredentialEncryptionServiceTest {

    private val logger: KLogger = mock()
    private val validKey: String = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
    private val otherKey: String = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 1).toByte() })

    private fun service(key: String = validKey): CredentialEncryptionService =
        CredentialEncryptionService(logger, DataConnectorConfigurationProperties(credentialEncryptionKey = key))

    // ------ Round-trip ------

    @Test
    fun `encrypt then decrypt returns original plaintext`() {
        val svc = service()
        val encrypted = svc.encrypt("super-secret-password")
        assertEquals("super-secret-password", svc.decrypt(encrypted))
    }

    @Test
    fun `encrypt produces non-empty ciphertext and 12-byte IV with keyVersion 1`() {
        val svc = service()
        val encrypted = svc.encrypt("pw")
        assertFalse(encrypted.ciphertext.isEmpty(), "ciphertext should be non-empty")
        assertEquals(12, encrypted.iv.size, "GCM IV should be 12 bytes")
        assertEquals(1, encrypted.keyVersion)
    }

    @Test
    fun `two encrypt calls with same plaintext produce different ciphertexts and different IVs`() {
        val svc = service()
        val first = svc.encrypt("identical")
        val second = svc.encrypt("identical")

        assertFalse(
            first.iv.contentEquals(second.iv),
            "Fresh IV must be generated per encrypt call (SecureRandom)",
        )
        assertFalse(
            first.ciphertext.contentEquals(second.ciphertext),
            "Different IV must produce different ciphertext for the same plaintext",
        )
        // But both round-trip to the same plaintext
        assertEquals(svc.decrypt(first), svc.decrypt(second))
    }

    // ------ Failure modes ------

    @Test
    fun `decrypt with tampered ciphertext throws DataCorruptionException`() {
        val svc = service()
        val encrypted = svc.encrypt("payload")
        val tampered = encrypted.copy(
            ciphertext = encrypted.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 0xFF).toByte() },
        )
        assertThrows(DataCorruptionException::class.java) { svc.decrypt(tampered) }
    }

    @Test
    fun `decrypt with wrong key throws DataCorruptionException (GCM tag mismatch is indistinguishable from corruption)`() {
        val encrypter = service(validKey)
        val decrypter = service(otherKey)
        val encrypted = encrypter.encrypt("payload")
        assertThrows(DataCorruptionException::class.java) { decrypter.decrypt(encrypted) }
    }

    // ------ Bean init fail-fast ------

    @Test
    fun `bean init with blank key throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) { service(key = "") }
        assertThrows(IllegalArgumentException::class.java) { service(key = "   ") }
    }

    @Test
    fun `bean init with wrong-size key (not 32 bytes after base64 decode) throws IllegalArgumentException`() {
        val tooShort = Base64.getEncoder().encodeToString(ByteArray(16) { it.toByte() })
        assertThrows(IllegalArgumentException::class.java) { service(key = tooShort) }

        val tooLong = Base64.getEncoder().encodeToString(ByteArray(64) { it.toByte() })
        assertThrows(IllegalArgumentException::class.java) { service(key = tooLong) }
    }

    @Test
    fun `bean init with non-base64 key throws CryptoException`() {
        assertThrows(CryptoException::class.java) { service(key = "!!!not-valid-base64!!!") }
    }

    // ------ Value-class equality ------

    @Test
    fun `EncryptedCredentials equals and hashCode compare ByteArrays by content`() {
        val a = EncryptedCredentials(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6), 1)
        val b = EncryptedCredentials(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6), 1)
        val c = EncryptedCredentials(byteArrayOf(1, 2, 4), byteArrayOf(4, 5, 6), 1)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
        assertArrayEquals(a.ciphertext, b.ciphertext)
    }
}
