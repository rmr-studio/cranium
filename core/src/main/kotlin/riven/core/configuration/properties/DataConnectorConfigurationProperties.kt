package cranium.core.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the custom-source subsystem (Phase 2).
 *
 * @property enabled When `false` (the default) the custom-source beans
 *   ([cranium.core.service.connector.CredentialEncryptionService],
 *   [cranium.core.service.connector.DataConnectorConnectionService], and the
 *   controller) are not created, so deployments that do not opt in are not
 *   required to supply [credentialEncryptionKey].
 * @property credentialEncryptionKey Base64-encoded 32-byte (256-bit) AES key used by
 *   [cranium.core.service.connector.CredentialEncryptionService]. Supplied via the
 *   `RIVEN_CREDENTIAL_ENCRYPTION_KEY` environment variable; must be set when
 *   [enabled] is `true` or the encryption service fails fast at bean init.
 */
@ConfigurationProperties(prefix = "cranium.connector")
data class DataConnectorConfigurationProperties(
    val enabled: Boolean = false,
    val credentialEncryptionKey: String = "",
){
    // If enabled is true, the encryption key must be set and valid; if enabled is false, we ignore the key (even if set or malformed)
    init {
        if (enabled) {
            require(credentialEncryptionKey.isNotBlank()) {
                "RIVEN_CREDENTIAL_ENCRYPTION_KEY must be set (base64-encoded 32-byte key) when cranium.connector.enabled=true"
            }
            val decodedKey = try {
                java.util.Base64.getDecoder().decode(credentialEncryptionKey)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("RIVEN_CREDENTIAL_ENCRYPTION_KEY is not valid base64", e)
            }
            require(decodedKey.size == 32) {
                "RIVEN_CREDENTIAL_ENCRYPTION_KEY must decode to 32 bytes (256 bits), but decoded length was ${decodedKey.size}"
            }
        }
    }
}
