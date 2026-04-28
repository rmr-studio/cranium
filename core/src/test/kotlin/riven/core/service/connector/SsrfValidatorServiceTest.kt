package riven.core.service.connector

import io.github.oshai.kotlinlogging.KLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import riven.core.exceptions.connector.SsrfRejectedException
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Unit tests for [SsrfValidatorService]. Covers SEC-01 (blocklist) and
 * SEC-02 (DNS-rebinding-safe resolved-IP check).
 *
 * A [NameResolver] seam is injected so DNS rebinding scenarios can be
 * simulated without real DNS lookups — production wiring uses
 * [DefaultNameResolver] which delegates to `InetAddress.getAllByName`.
 */
class SsrfValidatorServiceTest {

    private val logger: KLogger = mock()

    private fun serviceFor(vararg hostToIps: Pair<String, Array<InetAddress>>): SsrfValidatorService {
        val resolver = mock<NameResolver>()
        for ((host, ips) in hostToIps) {
            whenever(resolver.resolve(host)).thenReturn(ips)
        }
        return SsrfValidatorService(logger, resolver)
    }

    // --------- Blocklist coverage (ip-literal hosts resolve to themselves) ---------

    @ParameterizedTest(name = "rejects {0}")
    @ValueSource(strings = [
        "127.0.0.1",        // loopback
        "10.0.0.1",         // rfc1918
        "172.16.0.1",       // rfc1918
        "192.168.1.1",      // rfc1918
        "169.254.169.254",  // link-local (AWS/GCP metadata)
        "100.64.0.1",       // CGNAT
        "224.0.0.1",        // multicast
        "255.255.255.255",  // broadcast
        "0.0.0.0",          // any-local
        "0.0.0.1",          // reserved-zero 0.0.0.0/8
    ])
    fun `rejects blocked ipv4 literal`(ipLiteral: String) {
        val addr = InetAddress.getByName(ipLiteral)
        val service = serviceFor(ipLiteral to arrayOf(addr))

        val ex = assertThrows<SsrfRejectedException> { service.validateAndResolve(ipLiteral) }
        assertThat(ex.message).isEqualTo(SsrfValidatorService.GENERIC_MESSAGE)
    }

    @ParameterizedTest(name = "rejects {0}")
    @ValueSource(strings = [
        "::1",                 // ipv6 loopback
        "fe80::1",             // ipv6 link-local
        "fc00::1",             // ipv6 ULA
        "::ffff:127.0.0.1",    // ipv4-mapped loopback — must unwrap and re-check
        "::ffff:10.0.0.1",     // ipv4-mapped rfc1918
    ])
    fun `rejects blocked ipv6 literal`(ipLiteral: String) {
        val addr = InetAddress.getByName(ipLiteral)
        val service = serviceFor(ipLiteral to arrayOf(addr))

        val ex = assertThrows<SsrfRejectedException> { service.validateAndResolve(ipLiteral) }
        assertThat(ex.message).isEqualTo(SsrfValidatorService.GENERIC_MESSAGE)
    }

    @Test
    fun `accepts public 8 8 8 8 and returns the resolved addresses`() {
        val addr = InetAddress.getByName("8.8.8.8")
        val service = serviceFor("8.8.8.8" to arrayOf(addr))

        val resolved = service.validateAndResolve("8.8.8.8")

        assertThat(resolved).containsExactly(addr)
    }

    @Test
    fun `DNS rebinding defense - hostname resolving to blocked ip is rejected`() {
        val blocked = InetAddress.getByName("10.0.0.1")
        val service = serviceFor("evil.example.com" to arrayOf(blocked))

        val ex = assertThrows<SsrfRejectedException> { service.validateAndResolve("evil.example.com") }
        assertThat(ex.message).isEqualTo(SsrfValidatorService.GENERIC_MESSAGE)
    }

    @Test
    fun `DNS rebinding defense - any blocked ip in multi-ip response is rejected`() {
        val public = InetAddress.getByName("8.8.8.8")
        val blocked = InetAddress.getByName("169.254.169.254")
        val service = serviceFor("sneaky.example.com" to arrayOf(public, blocked))

        assertThrows<SsrfRejectedException> { service.validateAndResolve("sneaky.example.com") }
    }

    @Test
    fun `UnknownHostException is translated to SsrfRejectedException with generic message`() {
        val resolver = mock<NameResolver>()
        whenever(resolver.resolve("does-not-exist.invalid"))
            .thenThrow(UnknownHostException("no such host"))
        val service = SsrfValidatorService(logger, resolver)

        val ex = assertThrows<SsrfRejectedException> { service.validateAndResolve("does-not-exist.invalid") }
        assertThat(ex.message).isEqualTo(SsrfValidatorService.GENERIC_MESSAGE)
    }

    @Test
    fun `error message does not leak specific CIDR that matched`() {
        val addr = InetAddress.getByName("10.0.0.1")
        val service = serviceFor("10.0.0.1" to arrayOf(addr))

        val ex = assertThrows<SsrfRejectedException> { service.validateAndResolve("10.0.0.1") }
        assertThat(ex.message)
            .doesNotContain("10.0.0.0/8")
            .doesNotContain("rfc1918")
            .doesNotContain("site-local")
            .isEqualTo(SsrfValidatorService.GENERIC_MESSAGE)
    }
}
