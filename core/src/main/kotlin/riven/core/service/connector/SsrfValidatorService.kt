package riven.core.service.connector

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import riven.core.exceptions.connector.SsrfRejectedException
import java.math.BigInteger
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Seam over [InetAddress.getAllByName] so tests can simulate DNS rebinding
 * without hitting real DNS. Production uses [DefaultNameResolver].
 */
interface NameResolver {
    @Throws(UnknownHostException::class)
    fun resolve(host: String): Array<InetAddress>
}

@Component
class DefaultNameResolver : NameResolver {
    override fun resolve(host: String): Array<InetAddress> = InetAddress.getAllByName(host)
}

/**
 * Resolve-once SSRF validator for custom-source connection hosts.
 *
 * Implements SEC-01 (blocklist) and SEC-02 (DNS-rebinding defense): the caller
 * is expected to open its JDBC connection against one of the returned IPs, not
 * the original hostname, so a malicious resolver cannot swap to a private IP
 * between this check and the connect call.
 *
 * Blocklist covers, exhaustively (see 02-CONTEXT.md):
 * - IPv4: 127.0.0.0/8, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16,
 *         169.254.0.0/16, 0.0.0.0/8, 100.64.0.0/10, 224.0.0.0/4,
 *         255.255.255.255
 * - IPv6: ::1, fe80::/10, fc00::/7, ::ffff:0:0/96 (unwrapped + re-checked)
 *
 * Error copy is intentionally generic — it never discloses which specific
 * CIDR or category matched, because the caller may be user-controlled input.
 */
@Service
class SsrfValidatorService(
    private val logger: KLogger,
    private val resolver: NameResolver,
) {

    /**
     * Resolve [host] and reject if any resolved address is blocked.
     *
     * @return list of resolved [InetAddress]es the caller should connect to
     *   by IP literal (DNS-rebinding-safe).
     * @throws SsrfRejectedException if any address is blocked, if resolution
     *   fails, or if the host produces zero addresses. Message is always
     *   [GENERIC_MESSAGE] — no CIDR disclosure.
     */
    fun validateAndResolve(host: String): List<InetAddress> {
        val addresses = try {
            resolver.resolve(host).toList()
        } catch (e: UnknownHostException) {
            logger.warn { "SSRF reject: host=$host could not be resolved" }
            throw SsrfRejectedException(GENERIC_MESSAGE, e)
        }
        if (addresses.isEmpty()) {
            logger.warn { "SSRF reject: host=$host resolved to empty set" }
            throw SsrfRejectedException(GENERIC_MESSAGE)
        }
        for (addr in addresses) {
            checkAddress(addr, host)
        }
        return addresses
    }

    private fun checkAddress(addr: InetAddress, host: String) {
        val category = categorise(addr)
        if (category != null) {
            logger.warn { "SSRF reject: host=$host ip=${addr.hostAddress} category=$category" }
            throw SsrfRejectedException(GENERIC_MESSAGE)
        }
    }

    private fun categorise(addr: InetAddress): String? {
        // JVM-builtin categories first — cheap and cover most private space.
        when {
            addr.isLoopbackAddress -> return "loopback"
            addr.isLinkLocalAddress -> return "link-local"
            addr.isSiteLocalAddress -> return "site-local"
            addr.isMulticastAddress -> return "multicast"
            addr.isAnyLocalAddress -> return "any-local"
        }
        return when (addr) {
            is Inet4Address -> ipv4ExplicitBlock(addr)
            is Inet6Address -> ipv6ExplicitBlock(addr)
        }
    }

    private fun ipv4ExplicitBlock(addr: Inet4Address): String? {
        val ip = BigInteger(1, addr.address).toLong()
        // 100.64.0.0/10 CGNAT
        if (inCidr(ip, 0x64400000L, 10)) return "cgnat"
        // 224.0.0.0/4 multicast (isMulticast covers this; kept for defence-in-depth)
        if (inCidr(ip, 0xE0000000L, 4)) return "multicast"
        // 255.255.255.255 broadcast
        if (ip == 0xFFFFFFFFL) return "broadcast"
        // 0.0.0.0/8 reserved — isAnyLocalAddress covers 0.0.0.0 exactly; this
        // covers 0.x.x.x as a whole.
        if (inCidr(ip, 0x00000000L, 8)) return "reserved-zero"
        return null
    }

    private fun ipv6ExplicitBlock(addr: Inet6Address): String? {
        val bytes = addr.address
        // IPv4-mapped ::ffff:0:0/96 — unwrap and re-check as IPv4.
        if (isIpv4Mapped(bytes)) {
            val v4 = Inet4Address.getByAddress(bytes.sliceArray(12..15))
            val v4Category = when {
                v4.isLoopbackAddress -> "mapped-loopback"
                v4.isLinkLocalAddress -> "mapped-link-local"
                v4.isSiteLocalAddress -> "mapped-site-local"
                v4.isAnyLocalAddress -> "mapped-any-local"
                v4.isMulticastAddress -> "mapped-multicast"
                else -> ipv4ExplicitBlock(v4 as Inet4Address)?.let { "mapped-$it" }
            }
            return v4Category
        }
        // fc00::/7 Unique Local Address
        if ((bytes[0].toInt() and 0xFE) == 0xFC) return "ula"
        return null
    }

    private fun isIpv4Mapped(bytes: ByteArray): Boolean {
        if (bytes.size != 16) return false
        for (i in 0..9) if (bytes[i] != 0.toByte()) return false
        return bytes[10] == 0xFF.toByte() && bytes[11] == 0xFF.toByte()
    }

    private fun inCidr(ip: Long, network: Long, prefixBits: Int): Boolean {
        val mask = if (prefixBits == 0) 0L else (-1L shl (32 - prefixBits)) and 0xFFFFFFFFL
        return (ip and mask) == (network and mask)
    }

    companion object {
        const val GENERIC_MESSAGE: String =
            "Host not reachable: this address is blocked for security reasons (private/loopback/metadata)."
    }
}
