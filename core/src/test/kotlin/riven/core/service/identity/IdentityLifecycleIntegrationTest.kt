package cranium.core.service.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import cranium.core.configuration.util.LoggerConfig
import cranium.core.models.response.entity.RelationshipResponse
import cranium.core.repository.identity.IdentityClusterMemberRepository
import cranium.core.repository.identity.IdentityClusterRepository
import cranium.core.repository.identity.MatchSuggestionRepository
import cranium.core.service.activity.ActivityService
import cranium.core.service.auth.AuthTokenService
import cranium.core.service.entity.EntityRelationshipService
import cranium.core.service.entity.EntityService
import cranium.core.service.notification.NotificationService
import java.time.ZonedDateTime
import java.time.temporal.TemporalAccessor
import java.util.Optional
import java.util.UUID

/**
 * Minimal Spring configuration for the identity lifecycle integration test.
 *
 * Loads JPA auto-configuration for identity and entity packages, all identity services
 * needed for the full lifecycle (pipeline + confirmation + cluster), and mocks for
 * non-identity dependencies (EntityRelationshipService, EntityService, NotificationService,
 * ActivityService, AuthTokenService).
 *
 * Security auto-configuration is excluded so that @PreAuthorize annotations on service
 * methods are not evaluated — AuthTokenService.getUserId() is mocked instead.
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
@EnableJpaRepositories(basePackages = ["cranium.core.repository.identity"])
@EntityScan(basePackages = ["cranium.core.entity.identity", "cranium.core.entity.entity"])
@EnableJpaAuditing(
    auditorAwareRef = "lifecycleAuditorProvider",
    dateTimeProviderRef = "lifecycleDateTimeProvider",
)
class IdentityLifecycleIntegrationTestConfig {

    @Bean
    fun lifecycleAuditorProvider(): AuditorAware<UUID> = AuditorAware { Optional.empty() }

    @Bean
    fun lifecycleDateTimeProvider(): DateTimeProvider =
        DateTimeProvider { Optional.of<TemporalAccessor>(ZonedDateTime.now()) }

    @Bean
    fun stubActivityService(): ActivityService = mock<ActivityService>()

    @Bean
    fun stubEntityRelationshipService(): EntityRelationshipService = mock<EntityRelationshipService>()

    @Bean
    fun stubEntityService(): EntityService = mock<EntityService>()

    @Bean
    fun stubNotificationService(): NotificationService = mock<NotificationService>()

    @Bean
    fun stubAuthTokenService(): AuthTokenService = mock<AuthTokenService>()
}

/**
 * End-to-end lifecycle integration test that proves the complete identity resolution flow
 * works against a real PostgreSQL instance:
 *
 * 1. Pipeline creates a PENDING match suggestion (candidate search -> scoring -> persistence).
 * 2. User confirms the suggestion -> cluster formed in the DB with both entities as members.
 * 3. Re-running the pipeline for the same entity produces 0 new suggestions because
 *    [IdentityMatchSuggestionService.inSameCluster] detects both entities share a cluster.
 *
 * This test bridges the gap between the pipeline integration test (which only tests
 * suggestion creation) and the infrastructure test (which only tests DB constraints).
 * It proves the services compose correctly end-to-end.
 *
 * Test data:
 * - Entity A (workspace 1): email="john@example.com", phone="555-1234" — trigger entity
 * - Entity B (workspace 1): email="john@exmple.com" (typo), phone="555-1234" — expected match
 */
@SpringBootTest(
    classes = [
        IdentityLifecycleIntegrationTestConfig::class,
        LoggerConfig::class,
        // Pipeline services
        IdentityMatchCandidateService::class,
        IdentityNormalizationService::class,
        IdentityMatchScoringService::class,
        IdentityMatchSuggestionService::class,
        // Confirmation + cluster services
        IdentityConfirmationService::class,
        IdentityClusterService::class,
        IdentityReadService::class,
    ],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@ActiveProfiles("integration")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdentityLifecycleIntegrationTest {

    @Autowired
    private lateinit var candidateService: IdentityMatchCandidateService

    @Autowired
    private lateinit var scoringService: IdentityMatchScoringService

    @Autowired
    private lateinit var suggestionService: IdentityMatchSuggestionService

    @Autowired
    private lateinit var confirmationService: IdentityConfirmationService

    @Autowired
    private lateinit var suggestionRepository: MatchSuggestionRepository

    @Autowired
    private lateinit var clusterRepository: IdentityClusterRepository

    @Autowired
    private lateinit var clusterMemberRepository: IdentityClusterMemberRepository

    @Autowired
    private lateinit var authTokenService: AuthTokenService

    @Autowired
    private lateinit var entityRelationshipService: EntityRelationshipService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    companion object {
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
        )
            .withDatabaseName("cranium_test_lifecycle")
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

        val workspaceId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val userId: UUID = UUID.fromString("99000000-0000-0000-0000-000000000001")

        // Entity type ID
        val entityTypeId: UUID = UUID.fromString("10000000-0000-0000-0000-000000000001")

        // Entity IDs — canonical ordering: A < B
        val entityAId: UUID = UUID.fromString("30000000-0000-0000-0000-000000000001")
        val entityBId: UUID = UUID.fromString("30000000-0000-0000-0000-000000000002")

        // Attribute IDs for semantic metadata
        val attrEmailIdForTypeA: UUID = UUID.fromString("40000000-0000-0000-0000-000000000001")
        val attrPhoneIdForTypeA: UUID = UUID.fromString("40000000-0000-0000-0000-000000000002")

        // Individual attribute row IDs per entity
        val attrRowAEmail: UUID = UUID.fromString("50000000-0000-0000-0000-000000000001")
        val attrRowAPhone: UUID = UUID.fromString("50000000-0000-0000-0000-000000000002")
        val attrRowBEmail: UUID = UUID.fromString("50000000-0000-0000-0000-000000000003")
        val attrRowBPhone: UUID = UUID.fromString("50000000-0000-0000-0000-000000000004")
    }

    @org.junit.jupiter.api.BeforeAll
    fun installExtensionsAndSeedData() {
        // Install pg_trgm extension (not applied by Hibernate create-drop)
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm")

        // Apply identity constraints needed by the pipeline and cluster services
        jdbcTemplate.execute("DROP INDEX IF EXISTS uq_match_suggestions_pair")
        jdbcTemplate.execute(
            """CREATE UNIQUE INDEX IF NOT EXISTS uq_match_suggestions_pair
               ON match_suggestions (workspace_id, source_entity_id, target_entity_id)
               WHERE deleted = false"""
        )
        jdbcTemplate.execute(
            """ALTER TABLE match_suggestions
               DROP CONSTRAINT IF EXISTS chk_match_suggestions_canonical_order"""
        )
        jdbcTemplate.execute(
            """ALTER TABLE match_suggestions
               ADD CONSTRAINT chk_match_suggestions_canonical_order
               CHECK (source_entity_id < target_entity_id)"""
        )
        jdbcTemplate.execute("DROP INDEX IF EXISTS idx_identity_cluster_members_entity")
        jdbcTemplate.execute(
            """CREATE UNIQUE INDEX IF NOT EXISTS idx_identity_cluster_members_entity
               ON identity_cluster_members (entity_id)"""
        )

        // Seed semantic metadata: mark email and phone attribute IDs as IDENTIFIER
        insertSemanticMetadata(attrEmailIdForTypeA, "ATTRIBUTE", "IDENTIFIER")
        insertSemanticMetadata(attrPhoneIdForTypeA, "ATTRIBUTE", "IDENTIFIER")

        // Entity A: email="john@example.com", phone="555-1234" (trigger)
        insertAttribute(attrRowAEmail, entityAId, workspaceId, entityTypeId, attrEmailIdForTypeA, "EMAIL", "john@example.com")
        insertAttribute(attrRowAPhone, entityAId, workspaceId, entityTypeId, attrPhoneIdForTypeA, "PHONE", "555-1234")

        // Entity B: email="john@exmple.com" (typo), phone="555-1234" (exact) — expected to match A
        insertAttribute(attrRowBEmail, entityBId, workspaceId, entityTypeId, attrEmailIdForTypeA, "EMAIL", "john@exmple.com")
        insertAttribute(attrRowBPhone, entityBId, workspaceId, entityTypeId, attrPhoneIdForTypeA, "PHONE", "555-1234")
    }

    @BeforeEach
    fun cleanState() {
        // Use native SQL to bypass soft-delete filters and clean all rows between tests
        jdbcTemplate.execute("TRUNCATE TABLE identity_cluster_members CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE match_suggestions CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE identity_clusters CASCADE")
    }

    // ------ Helper methods ------

    private fun insertSemanticMetadata(targetId: UUID, targetType: String, classification: String) {
        jdbcTemplate.update(
            """INSERT INTO entity_type_semantic_metadata
               (id, workspace_id, entity_type_id, target_type, target_id, classification, tags,
                deleted, created_at, updated_at)
               VALUES (gen_random_uuid(), ?, ?, ?::text, ?, ?::text, '[]'::jsonb, false, NOW(), NOW())
               ON CONFLICT (entity_type_id, target_type, target_id) DO NOTHING""",
            workspaceId, entityTypeId, targetType, targetId, classification,
        )
    }

    private fun insertAttribute(
        id: UUID,
        entityId: UUID,
        workspaceId: UUID,
        typeId: UUID,
        attributeId: UUID,
        schemaType: String,
        value: String,
    ) {
        jdbcTemplate.update(
            """INSERT INTO entity_attributes
               (id, entity_id, workspace_id, type_id, attribute_id, schema_type, value,
                deleted, created_at, updated_at)
               VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, false, NOW(), NOW())
               ON CONFLICT (id) DO NOTHING""",
            id, entityId, workspaceId, typeId, attributeId, schemaType,
            """{"value": "$value"}""",
        )
    }

    // ------ Tests ------

    /**
     * Full lifecycle test: pipeline creates suggestion, user confirms, cluster formed, re-run skips.
     *
     * Proves the complete identity resolution lifecycle works end-to-end against real PostgreSQL:
     *
     * 1. Pipeline run: candidate search finds entity B as a match for entity A, scoring produces
     *    a composite score above threshold, and persistSuggestions writes a PENDING suggestion.
     * 2. Confirmation: confirmSuggestion transitions the suggestion to CONFIRMED, creates an
     *    identity cluster, and registers both entities as cluster members.
     * 3. Re-run: a second pipeline run for entity A produces 0 new suggestions because
     *    inSameCluster() detects both entities already share the same cluster.
     */
    @Test
    fun `full lifecycle - pipeline creates suggestion, user confirms, cluster formed, re-run skips`() {
        // -- Step 1: Run the pipeline to create a PENDING suggestion --

        val candidates = candidateService.findCandidates(entityAId, workspaceId)
        assertTrue(candidates.isNotEmpty(), "Expected at least one candidate for entity A")

        val triggerAttributes = candidateService.getTriggerAttributes(entityAId, workspaceId)
        val scored = scoringService.scoreCandidates(entityAId, triggerAttributes, candidates)
        assertTrue(scored.isNotEmpty(), "Expected at least one scored candidate above threshold")

        val persistedCount = suggestionService.persistSuggestions(workspaceId, scored, null)
        assertEquals(1, persistedCount, "Expected exactly one suggestion persisted for the A-B pair")

        // Assert 1 suggestion created with PENDING status
        val suggestions = jdbcTemplate.queryForList(
            "SELECT * FROM match_suggestions WHERE workspace_id = ? AND deleted = false",
            workspaceId,
        )
        assertEquals(1, suggestions.size, "Expected exactly one PENDING suggestion in DB")
        assertEquals("PENDING", suggestions[0]["status"], "Expected suggestion status to be PENDING")

        // -- Step 2: Confirm the suggestion --

        val suggestionId = requireNotNull(suggestions[0]["id"] as UUID) { "Suggestion ID must not be null" }

        // Mock authTokenService to return a fixed userId for the confirmation call
        whenever(authTokenService.getUserId()).thenReturn(userId)

        // Mock entityRelationshipService.addRelationship to return a mock response
        whenever(entityRelationshipService.addRelationship(any(), any(), any())).thenReturn(
            mock<RelationshipResponse>()
        )

        confirmationService.confirmSuggestion(workspaceId, suggestionId)

        // -- Step 3: Verify DB state after confirmation --

        // Suggestion should now be CONFIRMED
        val confirmedSuggestions = jdbcTemplate.queryForList(
            "SELECT * FROM match_suggestions WHERE workspace_id = ? AND id = ?",
            workspaceId, suggestionId,
        )
        assertEquals(1, confirmedSuggestions.size, "Expected the suggestion row to still exist")
        assertEquals("CONFIRMED", confirmedSuggestions[0]["status"], "Expected suggestion status to be CONFIRMED")

        // Identity clusters table should have exactly 1 cluster
        val clusterCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM identity_clusters WHERE workspace_id = ? AND deleted = false",
            Int::class.java,
            workspaceId,
        )
        assertEquals(1, requireNotNull(clusterCount), "Expected exactly 1 identity cluster")

        // Identity cluster members should have 2 rows (entity A + entity B)
        val memberCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM identity_cluster_members",
            Int::class.java,
        )
        assertEquals(2, requireNotNull(memberCount), "Expected 2 cluster members (entity A and entity B)")

        // Verify the correct entities are in the cluster
        val memberEntityIds = jdbcTemplate.queryForList(
            "SELECT entity_id FROM identity_cluster_members ORDER BY entity_id",
            UUID::class.java,
        )
        assertTrue(memberEntityIds.contains(entityAId), "Expected entity A to be a cluster member")
        assertTrue(memberEntityIds.contains(entityBId), "Expected entity B to be a cluster member")

        // -- Step 4: Re-run the pipeline — should produce 0 new suggestions --

        val rerunCandidates = candidateService.findCandidates(entityAId, workspaceId)
        val rerunTriggerAttributes = candidateService.getTriggerAttributes(entityAId, workspaceId)
        val rerunScored = if (rerunCandidates.isEmpty()) {
            emptyList()
        } else {
            scoringService.scoreCandidates(entityAId, rerunTriggerAttributes, rerunCandidates)
        }

        val rerunCount = suggestionService.persistSuggestions(workspaceId, rerunScored, null)
        assertEquals(0, rerunCount, "Expected 0 new suggestions on re-run (inSameCluster should skip)")

        // Total active suggestions should still be 1 (the CONFIRMED one from earlier)
        val totalActiveSuggestions = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM match_suggestions WHERE workspace_id = ? AND deleted = false",
            Int::class.java,
            workspaceId,
        )
        assertEquals(1, requireNotNull(totalActiveSuggestions), "Expected exactly 1 suggestion total after re-run")
    }
}
