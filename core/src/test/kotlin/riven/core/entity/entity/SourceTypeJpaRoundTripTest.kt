package cranium.core.entity.entity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import cranium.core.enums.integration.SourceType
import cranium.core.repository.entity.EntityTypeRepository
import cranium.core.service.util.factory.entity.EntityFactory
import java.time.ZonedDateTime
import java.time.temporal.TemporalAccessor
import java.util.Optional
import java.util.UUID

/**
 * Verifies [SourceType.CONNECTOR] round-trips through JPA on
 * [EntityTypeEntity.sourceType] without loss.
 *
 * Uses the `integration` profile + Testcontainers PostgreSQL, mirroring the
 * project convention (core/CLAUDE.md Testing Rules). H2 cannot emulate
 * PostgreSQL-specific types (`jsonb`, `enum`, reserved-word columns like
 * `key`, `value`) used by `EntityTypeEntity`, so the round-trip must execute
 * against a real Postgres.
 *
 * No migration is required: `source_type` is VARCHAR(50) with `@Enumerated(STRING)`;
 * Hibernate `ddl-auto: create-drop` generates the schema from the entity metadata,
 * which already accommodates the new enum value.
 */
@Configuration
@EnableAutoConfiguration(
    exclude = [
        SecurityAutoConfiguration::class,
        UserDetailsServiceAutoConfiguration::class,
        OAuth2ResourceServerAutoConfiguration::class,
    ],
    excludeName = [
        "io.temporal.spring.boot.autoconfigure.ServiceStubsAutoConfiguration",
        "io.temporal.spring.boot.autoconfigure.RootNamespaceAutoConfiguration",
        "io.temporal.spring.boot.autoconfigure.NonRootNamespaceAutoConfiguration",
        "io.temporal.spring.boot.autoconfigure.MetricsScopeAutoConfiguration",
        "io.temporal.spring.boot.autoconfigure.OpenTracingAutoConfiguration",
        "io.temporal.spring.boot.autoconfigure.TestServerAutoConfiguration",
    ],
)
@EnableJpaRepositories(basePackages = ["cranium.core.repository.entity"])
@EntityScan("cranium.core.entity.entity")
@EnableJpaAuditing(
    auditorAwareRef = "sourceTypeRoundTripAuditorProvider",
    dateTimeProviderRef = "sourceTypeRoundTripDateTimeProvider",
)
class SourceTypeJpaRoundTripTestConfig {

    @Bean
    fun sourceTypeRoundTripAuditorProvider(): AuditorAware<UUID> =
        AuditorAware { Optional.empty() }

    @Bean
    fun sourceTypeRoundTripDateTimeProvider(): DateTimeProvider =
        DateTimeProvider { Optional.of<TemporalAccessor>(ZonedDateTime.now()) }
}

@SpringBootTest(
    classes = [SourceTypeJpaRoundTripTestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@ActiveProfiles("integration")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SourceTypeJpaRoundTripTest {

    companion object {
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
        )
            .withDatabaseName("cranium_test")
            .withUsername("test")
            .withPassword("test")

        init {
            postgres.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }

    @Autowired
    private lateinit var entityTypeRepository: EntityTypeRepository

    @Test
    @Transactional
    fun `EntityTypeEntity with CONNECTOR round-trips through JPA`() {
        val unique = UUID.randomUUID().toString().take(8)
        val entity = EntityFactory.createEntityType(
            key = "connector_roundtrip_$unique",
            sourceType = SourceType.CONNECTOR,
        ).copy(id = null) // strip factory-generated id so JPA calls persist() not merge()

        val saved = entityTypeRepository.saveAndFlush(entity)
        val persistedId = requireNotNull(saved.id) {
            "id should be assigned after saveAndFlush"
        }

        val reloaded = entityTypeRepository.findById(persistedId).orElseThrow()

        assertThat(reloaded.sourceType).isEqualTo(SourceType.CONNECTOR)
    }
}
