package cranium.core.configuration.connector

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.turbo.TurboFilter
import ch.qos.logback.core.spi.FilterReply
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import org.springframework.context.annotation.Configuration

private val JDBC_URL_PATTERN = Regex("(jdbc:)?postgresql://[^\\s,;\"'<>]+")
private val PASSWORD_PATTERN = Regex("(?i)password=[^\\s&;,<>\"']+")

/**
 * Redaction helper — public for ease of reuse from other configuration-package
 * utilities but the regexes are an implementation detail of this filter.
 */
internal fun scrubCredentials(input: String?): String? {
    if (input == null) return null
    return input
        .replace(JDBC_URL_PATTERN, "[REDACTED_JDBC_URL]")
        .replace(PASSWORD_PATTERN, "password=[REDACTED]")
}

/**
 * Global TurboFilter that rewrites log format strings, parameters, and throwable
 * messages to scrub credential material (Postgres JDBC URLs, `password=...` query
 * params). Registered on the root [LoggerContext] so every logger in the JVM —
 * including the PostgreSQL JDBC driver, HikariCP, and Spring internals — is
 * covered without per-appender configuration.
 *
 * ### Rewrite pattern
 *
 * The SLF4J TurboFilter API does not permit in-place mutation of the format
 * string the caller handed in. To still redact, we:
 *
 * 1. Render the caller's `(format, params, t)` into a single [String] via
 *    [org.slf4j.helpers.MessageFormatter] and append any throwable messages.
 * 2. Scrub the rendered form.
 * 3. If scrubbing changed nothing → return [FilterReply.NEUTRAL] (pass-through).
 * 4. Otherwise re-log the scrubbed string at the same level through the same
 *    logger, guarded by a thread-local re-entry flag, and return
 *    [FilterReply.DENY] to suppress the original unscrubbed event.
 *
 * ### Tradeoff
 *
 * Stack traces are NOT preserved on the re-logged event because we replay only
 * a [String] message. For Phase 2 this is acceptable — our own connection
 * service sanitises exception chains before logging (primary defence), and the
 * TurboFilter is belt-and-suspenders for third-party code paths where we do
 * not control the caller. If a future phase needs stack-preserving redaction,
 * a `ThrowableConverter` in `logback-spring.xml` can be layered on as a
 * supplement.
 */
class CredentialRedactionTurboFilter : TurboFilter() {
    private val inFilter = ThreadLocal.withInitial { false }

    override fun decide(
        marker: Marker?,
        logger: Logger,
        level: Level,
        format: String?,
        params: Array<out Any?>?,
        t: Throwable?,
    ): FilterReply {
        if (inFilter.get()) return FilterReply.NEUTRAL
        if (format == null && t == null) return FilterReply.NEUTRAL

        val rendered = renderForInspection(format, params, t)
        val scrubbed = scrubCredentials(rendered)
        if (rendered == scrubbed) {
            return FilterReply.NEUTRAL
        }

        inFilter.set(true)
        try {
            val msg = scrubbed ?: return FilterReply.NEUTRAL
            when (level.toInt()) {
                Level.ERROR_INT -> logger.error(msg)
                Level.WARN_INT -> logger.warn(msg)
                Level.INFO_INT -> logger.info(msg)
                Level.DEBUG_INT -> logger.debug(msg)
                Level.TRACE_INT -> logger.trace(msg)
            }
        } finally {
            inFilter.set(false)
        }
        return FilterReply.DENY
    }

    private fun renderForInspection(format: String?, params: Array<out Any?>?, t: Throwable?): String {
        val sb = StringBuilder()
        if (format != null) {
            sb.append(org.slf4j.helpers.MessageFormatter.arrayFormat(format, params).message)
        }
        if (t != null) {
            var cur: Throwable? = t
            while (cur != null) {
                sb.append(' ').append(cur.message ?: "")
                cur = cur.cause
            }
        }
        return sb.toString()
    }
}

/**
 * Registers [CredentialRedactionTurboFilter] on the root Logback [LoggerContext]
 * at Spring startup. Idempotent — guards against test context reloads that would
 * otherwise stack duplicate filters.
 */
@Configuration
class LogRedactionConfiguration {

    @PostConstruct
    fun registerRedactionFilter() {
        val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
        if (loggerContext.turboFilterList.none { it is CredentialRedactionTurboFilter }) {
            loggerContext.addTurboFilter(CredentialRedactionTurboFilter())
        }
    }
}
