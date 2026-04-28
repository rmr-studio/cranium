package riven.core.service.connector

/**
 * Encrypted-credential value class: the AEAD ciphertext (including GCM auth tag),
 * the per-record IV, and the key version used.
 *
 * `equals`/`hashCode` are explicitly overridden because Kotlin's generated data-class
 * implementations compare `ByteArray` by reference, which would make round-trip
 * equality checks unreliable in tests and persistence layers.
 */
data class EncryptedCredentials(
    val ciphertext: ByteArray,
    val iv: ByteArray,
    val keyVersion: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedCredentials) return false
        return ciphertext.contentEquals(other.ciphertext) &&
            iv.contentEquals(other.iv) &&
            keyVersion == other.keyVersion
    }

    override fun hashCode(): Int =
        31 * (31 * ciphertext.contentHashCode() + iv.contentHashCode()) + keyVersion
}
