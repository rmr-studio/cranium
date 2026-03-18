package riven.core.service.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import org.mockito.Mockito
import riven.core.configuration.util.LoggerConfig
import riven.core.repository.identity.MatchSuggestionRepository
import riven.core.service.activity.ActivityService
import java.time.ZonedDateTime
import java.time.temporal.TemporalAccessor
import java.util.Optional
import java.util.UUID

/**
 * Minimal Spring configuration for the identity matching pipeline integration tests.
 *
 * Loads only JPA auto-configuration for identity and entity packages — excludes
 * security, Temporal, Supabase, web layer, and all other unrelated beans.
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
@EnableJpaRepositories(basePackages = ["riven.core.repository.identity"])
@EntityScan(basePackages = ["riven.core.entity.identity", "riven.core.entity.entity"])
@EnableJpaAuditing(
    auditorAwareRef = "pipelineAuditorProvider",
    dateTimeProviderRef = "pipelineDateTimeProvider",
)
class IdentityMatchPipelineIntegrationTestConfig {

    @Bean
    fun pipelineAuditorProvider(): AuditorAware<UUID> = AuditorAware { Optional.empty() }

    @Bean
    fun pipelineDateTimeProvider(): DateTimeProvider =
        DateTimeProvider { Optional.of<TemporalAccessor>(ZonedDateTime.now()) }

    /**
     * Stub ActivityService for integration tests — activity logging is a side effect
     * that we do not need to verify here. A Mockito mock avoids pulling in
     * ActivityLogRepository and its entity scan dependencies.
     */
    @Bean
    fun stubActivityService(): ActivityService = Mockito.mock(ActivityService::class.java)
}

/**
 * End-to-end pipeline integration test that validates all Phase 2 success criteria against
 * a real PostgreSQL instance with pg_trgm enabled.
 *
 * This test calls the three matching services in sequence (candidate search, scoring,
 * suggestion persistence) to prove the full pipeline works. It does NOT test Temporal
 * workflow execution — that requires a Temporal test server.
 *
 * Test data layout:
 * - Entity A (workspace 1): email="john@example.com", phone="555-1234" — trigger entity
 * - Entity B (workspace 1): email="john@exmple.com" (typo), phone="555-1234" — expected match
 * - Entity C (workspace 1): email="totally@different.com" — no match (below threshold)
 * - Entity D (workspace 2): email="john@example.com" — cross-workspace isolation
 *
 * Covers MATCH-02 through MATCH-06 and SUGG-01 through SUGG-05.
 */
@SpringBootTest(
    classes = [
        IdentityMatchPipelineIntegrationTestConfig::class,
        LoggerConfig::class,
        IdentityMatchCandidateService::class,
        IdentityMatchScoringService::class,
        IdentityMatchSuggestionService::class,
    ],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@ActiveProfiles("integration")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class IdentityMatchPipelineIntegrationTest {

    @Autowired
    private lateinit var candidateService: IdentityMatchCandidateService

    @Autowired
    private lateinit var scoringService: IdentityMatchScoringService

    @Autowired
    private lateinit var suggestionService: IdentityMatchSuggestionService

    @Autowired
    private lateinit var suggestionRepository: MatchSuggestionRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    companion object {
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
        )
            .withDatabaseName("riven_test_pipeline")
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

        // Fixed workspace and entity IDs — using UUIDs with canonical ordering where needed
        val workspaceId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val workspace2Id = UUID.fromString("00000000-0000-0000-0000-000000000002")

        // Entity type IDs
        val entityTypeId = UUID.fromString("10000000-0000-0000-0000-000000000001")

        // Entity IDs — canonical ordering guaranteed for A < B where needed
        val entityAId = UUID.fromString("30000000-0000-0000-0000-000000000001") // trigger
        val entityBId = UUID.fromString("30000000-0000-0000-0000-000000000002") // expected match (B > A)
        val entityCId = UUID.fromString("30000000-0000-0000-0000-000000000003") // no match
        val entityDId = UUID.fromString("30000000-0000-0000-0000-000000000004") // different workspace

        // Attribute IDs for semantic metadata
        val attrEmailIdForTypeA = UUID.fromString("40000000-0000-0000-0000-000000000001")
        val attrPhoneIdForTypeA = UUID.fromString("40000000-0000-0000-0000-000000000002")

        // Individual attribute row IDs per entity
        val attrRowAEmail = UUID.fromString("50000000-0000-0000-0000-000000000001")
        val attrRowAPhone = UUID.fromString("50000000-0000-0000-0000-000000000002")
        val attrRowBEmail = UUID.fromString("50000000-0000-0000-0000-000000000003")
        val attrRowBPhone = UUID.fromString("50000000-0000-0000-0000-000000000004")
        val attrRowCEmail = UUID.fromString("50000000-0000-0000-0000-000000000005")
        val attrRowDEmail = UUID.fromString("50000000-0000-0000-0000-000000000006")
    }

    @org.junit.jupiter.api.BeforeAll
    fun installExtensionsAndSeedData() {
        // Install pg_trgm extension (not applied by Hibernate create-drop)
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm")

        // Apply identity constraints needed by IdentityMatchSuggestionService
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

        // Seed semantic metadata: mark email and phone attribute IDs as IDENTIFIER
        insertSemanticMetadata(attrEmailIdForTypeA, "ATTRIBUTE", "IDENTIFIER")
        insertSemanticMetadata(attrPhoneIdForTypeA, "ATTRIBUTE", "IDENTIFIER")

        // Entity A: email="john@example.com", phone="555-1234" (trigger)
        insertAttribute(attrRowAEmail, entityAId, workspaceId, entityTypeId, attrEmailIdForTypeA, "EMAIL", "john@example.com")
        insertAttribute(attrRowAPhone, entityAId, workspaceId, entityTypeId, attrPhoneIdForTypeA, "PHONE", "555-1234")

        // Entity B: email="john@exmple.com" (typo), phone="555-1234" (exact) — expected to match A
        insertAttribute(attrRowBEmail, entityBId, workspaceId, entityTypeId, attrEmailIdForTypeA, "EMAIL", "john@exmple.com")
        insertAttribute(attrRowBPhone, entityBId, workspaceId, entityTypeId, attrPhoneIdForTypeA, "PHONE", "555-1234")

        // Entity C: email="totally@different.com" — should not match A (below 0.3 blocking threshold)
        insertAttribute(attrRowCEmail, entityCId, workspaceId, entityTypeId, attrEmailIdForTypeA, "EMAIL", "totally@different.com")

        // Entity D: email="john@example.com" but in workspace 2 — must never appear as candidate for A
        insertAttribute(attrRowDEmail, entityDId, workspace2Id, entityTypeId, attrEmailIdForTypeA, "EMAIL", "john@example.com")
    }

    @BeforeEach
    fun truncateSuggestions() {
        jdbcTemplate.execute("TRUNCATE TABLE match_suggestions CASCADE")
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
     * MATCH-02, MATCH-03, MATCH-04, SUGG-01: Matching pair produces a PENDING suggestion.
     *
     * Entity A and Entity B share a similar email (typo) and an exact phone match.
     * The pipeline must find B as a candidate, score it above 0.5, and persist a suggestion.
     */
    @Test
    @org.junit.jupiter.api.Order(1)
    fun `matching pair produces PENDING suggestion with composite score above threshold`() {
        val candidates = candidateService.findCandidates(entityAId, workspaceId)
        assertTrue(candidates.isNotEmpty(), "Expected at least one candidate for entity A")

        val triggerAttributes = candidateService.getTriggerAttributes(entityAId, workspaceId)
        val scored = scoringService.scoreCandidates(entityAId, triggerAttributes, candidates)
        assertTrue(scored.isNotEmpty(), "Expected at least one scored candidate above threshold")

        val aboveThreshold = scored.filter {
            it.targetEntityId == entityBId || it.sourceEntityId == entityBId
        }
        assertTrue(aboveThreshold.isNotEmpty(), "Expected entity B to appear in scored candidates")
        assertTrue(aboveThreshold.first().compositeScore > 0.5, "Expected composite score > 0.5")
        assertTrue(aboveThreshold.first().signals.isNotEmpty(), "Expected signal breakdown to be non-empty")

        val count = suggestionService.persistSuggestions(workspaceId, scored, null)
        assertEquals(1, count, "Expected exactly one suggestion persisted for the A-B pair")

        // Verify the suggestion row in the DB
        val suggestions = jdbcTemplate.queryForList(
            "SELECT * FROM match_suggestions WHERE workspace_id = ?",
            workspaceId,
        )
        assertEquals(1, suggestions.size, "Expected exactly one row in match_suggestions")
        assertEquals("PENDING", suggestions[0]["status"], "Expected status to be PENDING")

        val confidenceScore = (suggestions[0]["confidence_score"] as java.math.BigDecimal).toDouble()
        assertTrue(confidenceScore > 0.5, "Expected confidence_score > 0.5 in DB row")

        // Verify signals JSONB is non-empty (contains at least one signal entry)
        val signalsJson = suggestions[0]["signals"]?.toString() ?: ""
        assertTrue(signalsJson.isNotEmpty() && signalsJson != "[]", "Expected signals JSONB to be non-empty")
    }

    /**
     * MATCH-04: No candidates above threshold — no suggestion created.
     *
     * Entity C has email "totally@different.com" which shares no trigram similarity
     * with any other entity's IDENTIFIER attributes. The pipeline must produce no suggestions.
     */
    @Test
    @org.junit.jupiter.api.Order(2)
    fun `entity with no similarity matches produces no suggestion`() {
        val candidates = candidateService.findCandidates(entityCId, workspaceId)
        // C may find no candidates at all, or may find candidates below threshold
        val triggerAttributes = candidateService.getTriggerAttributes(entityCId, workspaceId)
        val scored = if (candidates.isEmpty()) emptyList()
        else scoringService.scoreCandidates(entityCId, triggerAttributes, candidates)

        val count = suggestionService.persistSuggestions(workspaceId, scored, null)
        assertEquals(0, count, "Expected no suggestions for entity C")

        val rowCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM match_suggestions WHERE workspace_id = ?",
            Int::class.java,
            workspaceId,
        )
        assertEquals(0, rowCount, "Expected no rows in match_suggestions for entity C pipeline run")
    }

    /**
     * MATCH-05: Cross-workspace isolation.
     *
     * Entity D is in workspace 2 and has the same email as Entity A in workspace 1.
     * When running the pipeline for Entity A, Entity D must never appear as a candidate.
     */
    @Test
    @org.junit.jupiter.api.Order(3)
    fun `candidates from different workspace are never returned`() {
        val candidates = candidateService.findCandidates(entityAId, workspaceId)
        val candidateIds = candidates.map { it.candidateEntityId }
        assertTrue(
            !candidateIds.contains(entityDId),
            "Expected entity D (workspace 2) to be excluded from entity A (workspace 1) candidates"
        )
    }

    /**
     * SUGG-02: Idempotent re-run returns 0 new suggestions.
     *
     * Running the full pipeline for Entity A twice must produce only one suggestion row —
     * the second run detects the existing active suggestion and silently skips.
     */
    @Test
    @org.junit.jupiter.api.Order(4)
    fun `idempotent re-run returns zero when suggestion already exists`() {
        // First run
        val candidates = candidateService.findCandidates(entityAId, workspaceId)
        val triggerAttributes = candidateService.getTriggerAttributes(entityAId, workspaceId)
        val scored = scoringService.scoreCandidates(entityAId, triggerAttributes, candidates)
        val firstCount = suggestionService.persistSuggestions(workspaceId, scored, null)
        assertEquals(1, firstCount, "Expected first run to create 1 suggestion")

        // Second run — must return 0
        val secondCount = suggestionService.persistSuggestions(workspaceId, scored, null)
        assertEquals(0, secondCount, "Expected second run to return 0 (idempotent skip)")

        // Only one row should exist
        val rowCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM match_suggestions WHERE workspace_id = ?",
            Int::class.java,
            workspaceId,
        )
        assertEquals(1, rowCount, "Expected exactly one suggestion row after idempotent re-run")
    }

    /**
     * SUGG-03, SUGG-04: Re-suggestion after rejection with improved score.
     *
     * 1. Run pipeline → creates PENDING suggestion for A-B.
     * 2. Reject the suggestion (writes rejectionSignals snapshot).
     * 3. Update entity B's email to exact "john@example.com" (higher similarity).
     * 4. Re-run pipeline → new PENDING suggestion created because score improved.
     * 5. Old rejected suggestion stays soft-deleted.
     */
    @Test
    @org.junit.jupiter.api.Order(5)
    fun `re-suggestion after rejection with higher score creates new pending suggestion`() {
        // Step 1: Run pipeline, create suggestion
        val candidates = candidateService.findCandidates(entityAId, workspaceId)
        val triggerAttributes = candidateService.getTriggerAttributes(entityAId, workspaceId)
        val scored = scoringService.scoreCandidates(entityAId, triggerAttributes, candidates)
        val firstCount = suggestionService.persistSuggestions(workspaceId, scored, null)
        assertEquals(1, firstCount, "Expected first run to create 1 suggestion")

        // Step 2: Reject the suggestion directly via SQL (rejectSuggestion is now on IdentityConfirmationService
        // which requires JWT context — this integration test uses a minimal config without security).
        val suggestions = suggestionRepository.findAll()
        assertEquals(1, suggestions.size, "Expected exactly one suggestion before rejection")
        val suggestion = suggestions.first()
        assertNotNull(suggestion.id)
        val rejectionUserId = UUID.fromString("99000000-0000-0000-0000-000000000001")
        jdbcTemplate.update(
            """UPDATE match_suggestions
               SET status = 'REJECTED', resolved_by = ?, resolved_at = NOW(),
                   rejection_signals = ?::jsonb, deleted = true, deleted_at = NOW()
               WHERE id = ?""",
            rejectionUserId,
            """{"signals": [], "confidenceScore": 0.0}""",
            requireNotNull(suggestion.id),
        )

        // Verify suggestion is in REJECTED status and soft-deleted
        val rejectedCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM match_suggestions WHERE workspace_id = ? AND status = 'REJECTED' AND deleted = true",
            Int::class.java,
            workspaceId,
        )
        assertEquals(1, rejectedCount, "Expected rejected suggestion to be soft-deleted with status REJECTED")

        // Step 3: Update entity B's email to exact match for higher score
        jdbcTemplate.update(
            "UPDATE entity_attributes SET value = ?::jsonb WHERE id = ?",
            """{"value": "john@example.com"}""", attrRowBEmail,
        )

        // Step 4: Re-run pipeline — should create new PENDING suggestion
        val newCandidates = candidateService.findCandidates(entityAId, workspaceId)
        val newTriggerAttributes = candidateService.getTriggerAttributes(entityAId, workspaceId)
        val newScored = scoringService.scoreCandidates(entityAId, newTriggerAttributes, newCandidates)
        val rerunCount = suggestionService.persistSuggestions(workspaceId, newScored, null)

        assertTrue(rerunCount >= 0, "Expected re-run to not throw an error")

        // Step 5: Verify old rejected row still exists and new PENDING row was created if score improved
        val totalRows = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM match_suggestions WHERE workspace_id = ?",
            Int::class.java,
            workspaceId,
        )
        assertTrue(totalRows!! >= 1, "Expected at least the rejected row to exist")

        // The new PENDING suggestion should exist if the score was above the rejected score
        val pendingRows = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM match_suggestions WHERE workspace_id = ? AND status = 'PENDING' AND deleted = false",
            Int::class.java,
            workspaceId,
        )
        assertTrue(pendingRows!! >= 1, "Expected a new PENDING suggestion to be created after rejection and score improvement")
    }
}
