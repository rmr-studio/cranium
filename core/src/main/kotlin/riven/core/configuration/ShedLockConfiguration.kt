package cranium.core.configuration

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import javax.sql.DataSource

/**
 * Configuration for ShedLock distributed locking.
 *
 * Enables distributed scheduler locking across multiple application instances
 * using PostgreSQL as the lock store. This ensures that scheduled tasks
 * (like queue processing) only execute on one instance at a time.
 *
 * Key features:
 * - Uses database time (.usingDbTime()) to avoid clock skew issues
 * - Default lock duration of 5 minutes prevents stuck locks
 * - Minimum lock time of 10 seconds prevents rapid re-execution
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "5m")
class ShedLockConfiguration(
    private val dataSource: DataSource
) {

    /**
     * Creates the JDBC-based lock provider for ShedLock.
     *
     * Uses database server time rather than application time to ensure
     * consistent lock timing across all instances regardless of clock drift.
     *
     * @return LockProvider configured for PostgreSQL
     */
    @Bean
    fun lockProvider(): LockProvider {
        return JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(org.springframework.jdbc.core.JdbcTemplate(dataSource))
            .usingDbTime()
            .build()
            .let { JdbcTemplateLockProvider(it) }
    }
}
