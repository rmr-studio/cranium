package riven.core.filter.integration

import io.github.oshai.kotlinlogging.KLogger
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import riven.core.configuration.properties.NangoConfigurationProperties
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Servlet filter that verifies the HMAC-SHA256 signature of incoming Nango webhook requests.
 *
 * Nango signs each webhook with the secretKey using HMAC-SHA256 and includes the hex-encoded
 * signature in the X-Nango-Hmac-Sha256 header. This filter reads the raw request body,
 * computes the expected signature, and compares using constant-time comparison to prevent
 * timing attacks.
 *
 * On success: wraps the request in a CachedBodyHttpServletRequest so downstream handlers
 * can re-read the body (servlet input stream can only be read once).
 *
 * On failure: responds 401 and does NOT call filterChain.doFilter.
 */
class NangoWebhookHmacFilter(
    private val nangoProperties: NangoConfigurationProperties,
    private val logger: KLogger
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val contentLength = request.contentLengthLong
        if (contentLength > nangoProperties.maxWebhookBodySize) {
            logger.warn { "Nango webhook body exceeds size limit: $contentLength > ${nangoProperties.maxWebhookBodySize}" }
            response.sendError(413, "Payload too large")
            return
        }

        val bodyBytes = request.inputStream.readNBytes(nangoProperties.maxWebhookBodySize)
        val signatureHeader = request.getHeader(HMAC_HEADER)

        if (signatureHeader.isNullOrBlank()) {
            logger.warn { "Nango webhook request missing ${HMAC_HEADER} header" }
            response.sendError(401, "Missing webhook signature")
            return
        }

        val expectedSignature = computeHmac(nangoProperties.secretKey, bodyBytes)

        if (!MessageDigest.isEqual(expectedSignature.toByteArray(), signatureHeader.toByteArray())) {
            logger.warn { "Nango webhook request has invalid HMAC signature" }
            response.sendError(401, "Invalid webhook signature")
            return
        }

        val wrappedRequest = CachedBodyHttpServletRequest(request, bodyBytes)
        filterChain.doFilter(wrappedRequest, response)
    }

    // ------ Private Helpers ------

    private fun computeHmac(secretKey: String, body: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey.toByteArray(), "HmacSHA256"))
        return mac.doFinal(body).joinToString("") { "%02x".format(it) }
    }

    // ------ Inner Classes ------

    /**
     * HttpServletRequest wrapper that caches the request body bytes so the input stream
     * can be read multiple times (once by this filter, once by the downstream handler).
     */
    class CachedBodyHttpServletRequest(
        request: HttpServletRequest,
        private val bodyBytes: ByteArray
    ) : HttpServletRequestWrapper(request) {

        override fun getInputStream(): ServletInputStream {
            val byteArrayInputStream = ByteArrayInputStream(bodyBytes)
            return object : ServletInputStream() {
                override fun read(): Int = byteArrayInputStream.read()
                override fun isFinished(): Boolean = byteArrayInputStream.available() == 0
                override fun isReady(): Boolean = true
                override fun setReadListener(readListener: ReadListener) {
                    // No-op for synchronous filter use
                }
            }
        }

        override fun getReader(): BufferedReader =
            BufferedReader(InputStreamReader(getInputStream()))
    }

    companion object {
        const val HMAC_HEADER = "X-Nango-Hmac-Sha256"
    }
}
