package riven.core.configuration.connector

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.sql.SQLException

/**
 * CONN-04 / SEC-05-06: TurboFilter-based credential redaction.
 *
 * The filter is registered via [LogRedactionConfiguration]'s `@PostConstruct`
 * method in production; here we invoke it directly (no Spring context needed)
 * so tests stay fast and isolated.
 *
 * Attaches a [ListAppender] to the root logger to capture formatted events,
 * asserts JDBC URLs and `password=` values are masked. The third-party
 * `logger.error(msg, sqlException)` path is explicitly exercised to prove the
 * TurboFilter covers log calls that bypass any custom pattern layout — this is
 * the locked decision from CONTEXT.md: "Applied globally so third-party code is covered."
 */
class LogRedactionTest {

    private lateinit var loggerContext: LoggerContext
    private lateinit var rootLogger: Logger
    private lateinit var captureAppender: ListAppender<ILoggingEvent>
    private lateinit var registeredFilter: CredentialRedactionTurboFilter

    @BeforeEach
    fun setUp() {
        loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
        rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME)

        captureAppender = ListAppender<ILoggingEvent>().apply {
            context = loggerContext
            start()
        }
        rootLogger.addAppender(captureAppender)

        // Register the filter via the production configuration class to exercise
        // the exact same wiring code (including idempotency guard).
        LogRedactionConfiguration().registerRedactionFilter()
        registeredFilter = loggerContext.turboFilterList
            .filterIsInstance<CredentialRedactionTurboFilter>()
            .first()
    }

    @AfterEach
    fun tearDown() {
        rootLogger.detachAppender(captureAppender)
        // Remove our filter so other tests are not impacted.
        loggerContext.turboFilterList.remove(registeredFilter)
    }

    // ------ Core redaction ------

    @Test
    fun `scrubs jdbc postgresql URL in info message`() {
        val log = LoggerFactory.getLogger("test.direct")
        log.info("connecting to jdbc:postgresql://user:pw@db.example.com:5432/mydb")

        val msgs = captureAppender.list.map { it.formattedMessage }
        assertTrue(msgs.any { it.contains("[REDACTED_JDBC_URL]") }, "Expected redaction token; got: $msgs")
        assertTrue(msgs.none { it.contains("pw@db.example.com") }, "Raw URL must not appear; got: $msgs")
    }

    @Test
    fun `scrubs bare postgresql URL (no jdbc prefix)`() {
        val log = LoggerFactory.getLogger("test.bare")
        log.info("connection string postgresql://admin:hunter2@pg.internal:5432/db")

        val msgs = captureAppender.list.map { it.formattedMessage }
        assertTrue(msgs.any { it.contains("[REDACTED_JDBC_URL]") })
        assertTrue(msgs.none { it.contains("hunter2") })
        assertTrue(msgs.none { it.contains("pg.internal") })
    }

    @Test
    fun `scrubs password query param`() {
        val log = LoggerFactory.getLogger("test.pwd")
        log.info("Error with password=hunter2 in payload")

        val msgs = captureAppender.list.map { it.formattedMessage }
        assertTrue(msgs.any { it.contains("password=[REDACTED]") })
        assertTrue(msgs.none { it.contains("hunter2") })
    }

    @Test
    fun `scrubs credentials passed via SLF4J format params`() {
        val log = LoggerFactory.getLogger("test.fmt")
        log.info("url={} failed", "jdbc:postgresql://u:p@h:5432/db")

        val msgs = captureAppender.list.map { it.formattedMessage }
        assertTrue(msgs.any { it.contains("[REDACTED_JDBC_URL]") })
        assertTrue(msgs.none { it.contains("u:p@h") })
    }

    // ------ Third-party SQLException path (locked-decision assertion) ------

    @Test
    fun `scrubs JDBC URL embedded in SQLException message (third-party path)`() {
        val sqlEx = SQLException("connection to jdbc:postgresql://leak:5432/db refused")
        // Simulate arbitrary third-party library code that doesn't go through any
        // custom PatternLayout — the TurboFilter must still redact.
        val driverLog = LoggerFactory.getLogger("org.postgresql.Driver")
        driverLog.error("JDBC connect failed", sqlEx)

        val msgs = captureAppender.list.map { it.formattedMessage }
        assertTrue(
            msgs.any { it.contains("[REDACTED_JDBC_URL]") },
            "Expected at least one captured message to contain the redaction token; got: $msgs",
        )
        assertTrue(
            msgs.none { it.contains("jdbc:postgresql://leak") },
            "Captured messages must not contain the raw JDBC URL; got: $msgs",
        )
    }

    @Test
    fun `scrubs credentials embedded in nested exception cause chain`() {
        val root = RuntimeException("wrapper")
        val causeWithSecret = SQLException("auth failed for postgresql://root:toor@pg:5432/x")
        root.initCause(causeWithSecret)

        LoggerFactory.getLogger("third.party.wrapper").error("boom", root)

        val msgs = captureAppender.list.map { it.formattedMessage }
        assertTrue(msgs.any { it.contains("[REDACTED_JDBC_URL]") })
        assertTrue(msgs.none { it.contains("root:toor") })
    }

    // ------ Non-sensitive messages pass through unchanged ------

    @Test
    fun `leaves non-credential messages unchanged`() {
        LoggerFactory.getLogger("test.clean").info("normal operation completed")

        val formatted = captureAppender.list.map { it.formattedMessage }
        assertEquals(1, formatted.size, "Non-sensitive message should pass through once, not be re-logged")
        assertTrue(formatted.any { it == "normal operation completed" })
    }

    // ------ Idempotent registration ------

    @Test
    fun `registerRedactionFilter is idempotent — a second call does not add another filter`() {
        val countBefore = loggerContext.turboFilterList.filterIsInstance<CredentialRedactionTurboFilter>().size
        LogRedactionConfiguration().registerRedactionFilter()
        val countAfter = loggerContext.turboFilterList.filterIsInstance<CredentialRedactionTurboFilter>().size
        assertEquals(countBefore, countAfter)
    }
}
