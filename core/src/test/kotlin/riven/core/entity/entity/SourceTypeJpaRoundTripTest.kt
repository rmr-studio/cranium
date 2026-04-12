package riven.core.entity.entity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
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
import riven.core.enums.integration.SourceType
import riven.core.repository.entity.EntityTypeRepository
import riven.core.service.util.factory.entity.EntityFactory
import java.time.ZonedDateTime
import java.time.temporal.TemporalAccessor
import java.util.Optional
import java.util.UUID

/**
 * Verifies [SourceType.CUSTOM_SOURCE] round-trips through JPA on
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
@EnableJpaRepositories(basePackages = ["riven.core.repository.entity"])
@EntityScan("riven.core.entity.entity")
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
            .withDatabaseName("riven_test")
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
    fun `EntityTypeEntity with CUSTOM_SOURCE round-trips through JPA`() {
        val unique = UUID.randomUUID().toString().take(8)
        val entity = EntityFactory.createEntityType(
            key = "custom_source_roundtrip_$unique",
            sourceType = SourceType.CUSTOM_SOURCE,
        ).copy(id = null) // strip factory-generated id so JPA calls persist() not merge()

        val saved = entityTypeRepository.saveAndFlush(entity)
        val persistedId = requireNotNull(saved.id) {
            "id should be assigned after saveAndFlush"
        }

        val reloaded = entityTypeRepository.findById(persistedId).orElseThrow()

        assertThat(reloaded.sourceType).isEqualTo(SourceType.CUSTOM_SOURCE)
    }
}
