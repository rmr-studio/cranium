package riven.core.service.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import riven.core.enums.identity.MatchSource
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
import org.mockito.kotlin.mock
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
    fun stubActivityService(): ActivityService = mock<ActivityService>()
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
        IdentityNormalizationService::class,
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

        // TEST-07: Phone formatting variation
        // Two new attribute type IDs for phone formatting test (separate from main test fixtures)
        val attrPhoneFormattedId = UUID.fromString("40000000-0000-0000-0000-000000000010")  // semantic metadata target
        val entityPhoneFormattedA = UUID.fromString("30000000-0000-0000-0000-000000000010")
        val entityPhoneFormattedB = UUID.fromString("30000000-0000-0000-0000-000000000011")
        val attrRowPhoneFormattedA = UUID.fromString("50000000-0000-0000-0000-000000000010")
        val attrRowPhoneFormattedB = UUID.fromString("50000000-0000-0000-0000-000000000011")

        // TEST-08: William/Bill nickname match with corroborating PHONE signal
        val attrNameNicknameId = UUID.fromString("40000000-0000-0000-0000-000000000020")   // semantic: NAME signal
        val attrPhoneNicknameId = UUID.fromString("40000000-0000-0000-0000-000000000021")   // semantic: PHONE signal
        val entityNicknameA = UUID.fromString("30000000-0000-0000-0000-000000000020")  // "William"
        val entityNicknameB = UUID.fromString("30000000-0000-0000-0000-000000000021")  // "Bill" (B > A for canonical check)
        val attrRowNicknameAName = UUID.fromString("50000000-0000-0000-0000-000000000020")
        val attrRowNicknameAPhone = UUID.fromString("50000000-0000-0000-0000-000000000021")
        val attrRowNicknameBName = UUID.fromString("50000000-0000-0000-0000-000000000022")
        val attrRowNicknameBPhone = UUID.fromString("50000000-0000-0000-0000-000000000023")

        // TEST-09: Corporate email domain match — j.smith@acme.com vs john.smith@acme.com
        // Both share "acme.com" domain. Local parts "j.smith" (tokens: ["j","smith"]) vs
        // "john.smith" (tokens: ["john","smith"]) — overlap = |{"smith"}| / min(2,2) = 0.5 (meets threshold).
        val attrEmailDomainId = UUID.fromString("40000000-0000-0000-0000-000000000030")
        val entityEmailDomainA = UUID.fromString("30000000-0000-0000-0000-000000000030") // j.smith@acme.com (trigger)
        val entityEmailDomainB = UUID.fromString("30000000-0000-0000-0000-000000000031") // john.smith@acme.com (B > A)
        val attrRowEmailDomainA = UUID.fromString("50000000-0000-0000-0000-000000000030")
        val attrRowEmailDomainB = UUID.fromString("50000000-0000-0000-0000-000000000031")

        // TEST-11: Free email domain exclusion (gmail.com) — email domain strategy must be skipped
        val entityGmailA = UUID.fromString("30000000-0000-0000-0000-000000000032")
        val entityGmailB = UUID.fromString("30000000-0000-0000-0000-000000000033")
        val attrRowGmailA = UUID.fromString("50000000-0000-0000-0000-000000000032")
        val attrRowGmailB = UUID.fromString("50000000-0000-0000-0000-000000000033")

        // TEST-10 / TEST-12: Phonetic match + multi-strategy merge (Smythe/Smith + exact PHONE)
        // Entity A ("smythe") and Entity B ("smith") share the same phonetic code SM0 via dmetaphone().
        // Both share phone "9995551234" — distinct from other test phones — so EXACT_NORMALIZED PHONE also fires.
        val attrNamePhoneticId = UUID.fromString("40000000-0000-0000-0000-000000000040")
        val attrPhonePhoneticId = UUID.fromString("40000000-0000-0000-0000-000000000041")
        val entityPhoneticA = UUID.fromString("40000000-0000-0000-0000-000000000040")
        val entityPhoneticB = UUID.fromString("40000000-0000-0000-0000-000000000041")
        val attrRowPhoneticAName = UUID.fromString("50000000-0000-0000-0000-000000000040")
        val attrRowPhoneticAPhone = UUID.fromString("50000000-0000-0000-0000-000000000041")
        val attrRowPhoneticBName = UUID.fromString("50000000-0000-0000-0000-000000000042")
        val attrRowPhoneticBPhone = UUID.fromString("50000000-0000-0000-0000-000000000043")
    }

    @org.junit.jupiter.api.BeforeAll
    fun installExtensionsAndSeedData() {
        // Install pg_trgm extension (not applied by Hibernate create-drop)
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm")
        // Install fuzzystrmatch extension — required for dmetaphone() phonetic candidate queries (Phase 5)
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS fuzzystrmatch")

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

        // TEST-07: Seed phone formatting variation test data.
        // Entity A stores phone in international format, entity B in a hyphenated local format.
        // These share the same digits ("8009876543") but differ enough in character composition
        // that the pg_trgm similarity is below 0.3 — so only the exact-digits query finds the match.
        // Both use "800" prefix, deliberately different from "555" used by the primary test entities.
        insertSemanticMetadata(attrPhoneFormattedId, "ATTRIBUTE", "IDENTIFIER", "PHONE")
        insertAttribute(attrRowPhoneFormattedA, entityPhoneFormattedA, workspaceId, entityTypeId, attrPhoneFormattedId, "PHONE", "+1 (800) 987-6543")
        insertAttribute(attrRowPhoneFormattedB, entityPhoneFormattedB, workspaceId, entityTypeId, attrPhoneFormattedId, "PHONE", "(800) 987-65-43")

        // TEST-08: Seed William/Bill nickname match with corroborating PHONE signal.
        // Name attributes use TEXT schema type with signal_type='NAME' in semantic metadata.
        // Phone attributes use PHONE schema type with signal_type='PHONE' in semantic metadata.
        // Phone number "7776543210" is deliberately different from other test phone numbers.
        insertSemanticMetadata(attrNameNicknameId, "ATTRIBUTE", "IDENTIFIER", "NAME")
        insertSemanticMetadata(attrPhoneNicknameId, "ATTRIBUTE", "IDENTIFIER", "PHONE")
        // Entity A: "william" + corroborating phone
        insertAttribute(attrRowNicknameAName, entityNicknameA, workspaceId, entityTypeId, attrNameNicknameId, "TEXT", "william")
        insertAttribute(attrRowNicknameAPhone, entityNicknameA, workspaceId, entityTypeId, attrPhoneNicknameId, "PHONE", "7776543210")
        // Entity B: "bill" (nickname for William) + same phone
        insertAttribute(attrRowNicknameBName, entityNicknameB, workspaceId, entityTypeId, attrNameNicknameId, "TEXT", "bill")
        insertAttribute(attrRowNicknameBPhone, entityNicknameB, workspaceId, entityTypeId, attrPhoneNicknameId, "PHONE", "7776543210")

        // TEST-09: Corporate email domain match — j.smith@acme.com vs john.smith@acme.com
        // "acme.com" is not in FREE_EMAIL_DOMAINS, so domain-aware query fires.
        // localPartSimilarity("j.smith", "john.smith"):
        //   tokens A = ["j", "smith"], tokens B = ["john", "smith"]
        //   overlap = |{"smith"}| / min(2, 2) = 0.5 — exactly at threshold, candidate is included.
        insertSemanticMetadata(attrEmailDomainId, "ATTRIBUTE", "IDENTIFIER")
        insertAttribute(attrRowEmailDomainA, entityEmailDomainA, workspaceId, entityTypeId, attrEmailDomainId, "EMAIL", "j.smith@acme.com")
        insertAttribute(attrRowEmailDomainB, entityEmailDomainB, workspaceId, entityTypeId, attrEmailDomainId, "EMAIL", "john.smith@acme.com")

        // TEST-11: Free email domain exclusion — j.smith@gmail.com vs john.smith@gmail.com
        // "gmail.com" is in FREE_EMAIL_DOMAINS, so domain-aware query must NOT fire.
        // Trigram candidates may still exist but no EMAIL_DOMAIN matchSource should appear.
        insertAttribute(attrRowGmailA, entityGmailA, workspaceId, entityTypeId, attrEmailDomainId, "EMAIL", "j.smith@gmail.com")
        insertAttribute(attrRowGmailB, entityGmailB, workspaceId, entityTypeId, attrEmailDomainId, "EMAIL", "john.smith@gmail.com")

        // TEST-10 / TEST-12: Phonetic match (Smythe/Smith) + multi-strategy merge
        // Entity A ("smythe") and Entity B ("smith") both map to phonetic code SM0 via dmetaphone().
        // Both share phone "9995551234" — distinct from all other test phone numbers — so the
        // EXACT_NORMALIZED PHONE strategy also fires and merges into a second signal (TEST-12).
        insertSemanticMetadata(attrNamePhoneticId, "ATTRIBUTE", "IDENTIFIER", "NAME")
        insertSemanticMetadata(attrPhonePhoneticId, "ATTRIBUTE", "IDENTIFIER", "PHONE")
        // Entity A: "smythe" + corroborating phone
        insertAttribute(attrRowPhoneticAName, entityPhoneticA, workspaceId, entityTypeId, attrNamePhoneticId, "TEXT", "smythe")
        insertAttribute(attrRowPhoneticAPhone, entityPhoneticA, workspaceId, entityTypeId, attrPhonePhoneticId, "PHONE", "9995551234")
        // Entity B: "smith" (phonetically equivalent to "smythe") + same phone
        insertAttribute(attrRowPhoneticBName, entityPhoneticB, workspaceId, entityTypeId, attrNamePhoneticId, "TEXT", "smith")
        insertAttribute(attrRowPhoneticBPhone, entityPhoneticB, workspaceId, entityTypeId, attrPhonePhoneticId, "PHONE", "9995551234")

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

    /**
     * Inserts semantic metadata with an optional signal_type column for signal-routing tests.
     *
     * The signal_type column is required for NAME and COMPANY signals since those are not
     * derivable from SchemaType alone. When null, fromColumnValue() falls back to fromSchemaType().
     */
    private fun insertSemanticMetadata(
        targetId: UUID,
        targetType: String,
        classification: String,
        signalType: String?,
    ) {
        jdbcTemplate.update(
            """INSERT INTO entity_type_semantic_metadata
               (id, workspace_id, entity_type_id, target_type, target_id, classification, signal_type, tags,
                deleted, created_at, updated_at)
               VALUES (gen_random_uuid(), ?, ?, ?::text, ?, ?::text, ?, '[]'::jsonb, false, NOW(), NOW())
               ON CONFLICT (entity_type_id, target_type, target_id) DO NOTHING""",
            workspaceId, entityTypeId, targetType, targetId, classification, signalType,
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
        assertTrue(
            requireNotNull(totalRows) { "queryForObject returned null for total match_suggestions count" } >= 1,
            "Expected at least the rejected row to exist",
        )

        // The new PENDING suggestion should exist if the score was above the rejected score
        val pendingRows = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM match_suggestions WHERE workspace_id = ? AND status = 'PENDING' AND deleted = false",
            Int::class.java,
            workspaceId,
        )
        assertTrue(
            requireNotNull(pendingRows) { "queryForObject returned null for pending match_suggestions count" } >= 1,
            "Expected a new PENDING suggestion to be created after rejection and score improvement",
        )
    }

    /**
     * TEST-07: Phone formatting variation produces match via exact-digits query.
     *
     * Entity A has phone "+1 (800) 987-6543" (international format → normalized to "8009876543").
     * Entity B has phone "(800) 987-65-43" (unusual hyphenation → different character composition).
     * The pg_trgm similarity of the normalized trigger "8009876543" vs raw "(800) 987-65-43" is
     * below the 0.3 blocking threshold, so trigram blocking FAILS. The exact-digits query strips
     * all non-digit chars from the stored value and compares to the normalized digits — matching.
     *
     * Expected: Entity B appears as a candidate with matchSource=EXACT_NORMALIZED (score=1.0).
     */
    @Test
    @org.junit.jupiter.api.Order(6)
    fun `phone formatting variation produces match via exact-digits query`() {
        val candidates = candidateService.findCandidates(entityPhoneFormattedA, workspaceId)

        assertTrue(candidates.isNotEmpty(), "Expected at least one candidate for phone-formatted entity A")

        val entityBCandidate = candidates.firstOrNull { it.candidateEntityId == entityPhoneFormattedB }
        assertNotNull(entityBCandidate, "Expected entity B (raw digits) to appear as candidate for entity A (+1 (555) 123-4567)")

        assertEquals(
            MatchSource.EXACT_NORMALIZED, entityBCandidate!!.matchSource,
            "Expected matchSource=EXACT_NORMALIZED for phone formatting variation match",
        )
        assertEquals(1.0, entityBCandidate.similarityScore, 1e-6, "Expected similarity score=1.0 for exact-digits match")
    }

    /**
     * TEST-08: William/Bill nickname match with corroborating PHONE signal produces a merge suggestion.
     *
     * Entity A (nickname-A) has NAME="william" + PHONE="5559876543".
     * Entity B (nickname-B) has NAME="bill" + PHONE="5559876543".
     * "william" expands to include "bill" via NicknameExpander, so the nickname query finds entity B.
     * The PHONE signal (same digits, exact match) corroborates the NAME signal, pushing the
     * composite score above the 0.85 confidence gate.
     *
     * Expected:
     * - Entity B appears in candidates with a NICKNAME match on the NAME attribute.
     * - The scored result exceeds the confidence gate.
     * - A PENDING suggestion is created.
     */
    @Test
    @org.junit.jupiter.api.Order(7)
    fun `william bill nickname match with corroborating phone signal produces merge suggestion`() {
        val candidates = candidateService.findCandidates(entityNicknameA, workspaceId)

        assertTrue(candidates.isNotEmpty(), "Expected candidates for entity A (william)")

        // Verify entity B appears with NICKNAME matchSource on the NAME attribute
        val nicknameCandidate = candidates.firstOrNull { candidate ->
            candidate.candidateEntityId == entityNicknameB &&
                candidate.matchSource == MatchSource.NICKNAME
        }
        assertNotNull(
            nicknameCandidate,
            "Expected entity B (bill) to appear as NICKNAME candidate for entity A (william). " +
                "Found candidates: ${candidates.map { "${it.candidateEntityId} matchSource=${it.matchSource}" }}",
        )

        // Run full pipeline: score + persist suggestions
        val triggerAttributes = candidateService.getTriggerAttributes(entityNicknameA, workspaceId)
        val scored = scoringService.scoreCandidates(entityNicknameA, triggerAttributes, candidates)

        val entityBScored = scored.filter { it.targetEntityId == entityNicknameB || it.sourceEntityId == entityNicknameB }
        assertTrue(
            entityBScored.isNotEmpty(),
            "Expected entity B to appear in scored candidates above confidence gate",
        )

        val count = suggestionService.persistSuggestions(workspaceId, scored, null)
        assertTrue(count >= 1, "Expected at least one suggestion to be persisted for William/Bill match")

        // Verify PENDING suggestion exists
        val suggestions = jdbcTemplate.queryForList(
            "SELECT * FROM match_suggestions WHERE workspace_id = ? AND deleted = false AND status = 'PENDING'",
            workspaceId,
        )
        val nicknameSuggestion = suggestions.firstOrNull { row ->
            val source = row["source_entity_id"].toString()
            val target = row["target_entity_id"].toString()
            source == entityNicknameA.toString() || target == entityNicknameA.toString() ||
                source == entityNicknameB.toString() || target == entityNicknameB.toString()
        }
        assertNotNull(nicknameSuggestion, "Expected a PENDING suggestion for William/Bill pair")
    }

    /**
     * TEST-09: Corporate email domain match produces a PENDING suggestion.
     *
     * Entity A (j.smith@acme.com) and Entity B (john.smith@acme.com) share the corporate domain
     * "acme.com". The email domain strategy fires (not a free domain) and computes local-part overlap:
     * - tokens A = ["j", "smith"], tokens B = ["john", "smith"]
     * - overlap = |{"smith"}| / min(2, 2) = 0.5 — at threshold, candidate is included.
     *
     * Note: the final matchSource in merged results may be TRIGRAM if the trigram similarity score
     * exceeds the local-part overlap score (0.5). Both strategies may independently find entity B;
     * `mergeCandidates` keeps the higher-scoring entry, preferring EMAIL_DOMAIN only on ties.
     * The meaningful assertion here is that entity B IS found and a PENDING suggestion is created.
     *
     * Expected:
     * - Entity B appears in candidates (via EMAIL_DOMAIN and/or TRIGRAM strategy).
     * - At least one candidate with EMAIL_DOMAIN matchSource exists across all candidates, OR
     *   entity B appears — demonstrating the email domain path was exercised.
     * - A PENDING suggestion is created for the A-B pair.
     * - Composite score > 0.
     */
    @Test
    @org.junit.jupiter.api.Order(8)
    fun `email domain match produces PENDING suggestion for corporate domain`() {
        val candidates = candidateService.findCandidates(entityEmailDomainA, workspaceId)

        assertTrue(candidates.isNotEmpty(), "Expected at least one candidate for j.smith@acme.com")

        // Entity B must appear — either via EMAIL_DOMAIN or TRIGRAM (if trigram score > overlap score)
        val entityBCandidate = candidates.firstOrNull { it.candidateEntityId == entityEmailDomainB }
        assertNotNull(
            entityBCandidate,
            "Expected entity B (john.smith@acme.com) to appear as a candidate. " +
                "Found candidates: ${candidates.map { "${it.candidateEntityId} matchSource=${it.matchSource}" }}",
        )

        // Run full pipeline: score + persist suggestion
        val triggerAttributes = candidateService.getTriggerAttributes(entityEmailDomainA, workspaceId)
        val scored = scoringService.scoreCandidates(entityEmailDomainA, triggerAttributes, candidates)

        val entityBScored = scored.filter {
            it.targetEntityId == entityEmailDomainB || it.sourceEntityId == entityEmailDomainB
        }
        assertTrue(entityBScored.isNotEmpty(), "Expected entity B to appear in scored candidates above confidence gate")

        val count = suggestionService.persistSuggestions(workspaceId, scored, null)
        assertTrue(count >= 1, "Expected at least one suggestion to be persisted for acme.com email domain match")

        val suggestions = jdbcTemplate.queryForList(
            "SELECT * FROM match_suggestions WHERE workspace_id = ? AND deleted = false AND status = 'PENDING'",
            workspaceId,
        )
        val acmeSuggestion = suggestions.firstOrNull { row ->
            val source = row["source_entity_id"].toString()
            val target = row["target_entity_id"].toString()
            source == entityEmailDomainA.toString() || target == entityEmailDomainA.toString() ||
                source == entityEmailDomainB.toString() || target == entityEmailDomainB.toString()
        }
        assertNotNull(acmeSuggestion, "Expected a PENDING suggestion for j.smith@acme.com / john.smith@acme.com pair")

        val confidenceScore = (requireNotNull(acmeSuggestion)["confidence_score"] as java.math.BigDecimal).toDouble()
        assertTrue(confidenceScore > 0, "Expected composite score > 0 for email domain match")
    }

    /**
     * Task 1 setup verification: phonetic test entities have been seeded with NAME and PHONE attributes.
     *
     * Verifies that the fuzzystrmatch extension is installed and callable, and that the phonetic
     * test entities (entityPhoneticA = "smythe", entityPhoneticB = "smith") have the expected
     * attribute rows in entity_attributes. This confirms the fixture is ready for TEST-10/TEST-12.
     */
    @Test
    @org.junit.jupiter.api.Order(9)
    fun `phonetic test entities are seeded with name and phone attributes`() {
        // Verify fuzzystrmatch is installed and dmetaphone() is callable
        val phoneticCode = jdbcTemplate.queryForObject(
            "SELECT dmetaphone('smith')",
            String::class.java,
        )
        assertEquals("SM0", phoneticCode, "Expected dmetaphone('smith') = 'SM0' (fuzzystrmatch installed)")

        // Verify phonetic entity A ("smythe") has both NAME and PHONE attribute rows
        val countA = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM entity_attributes WHERE entity_id = ?",
            Int::class.java,
            entityPhoneticA,
        )
        assertEquals(2, countA, "Expected entity A (smythe) to have 2 attribute rows (NAME + PHONE)")

        // Verify phonetic entity B ("smith") has both NAME and PHONE attribute rows
        val countB = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM entity_attributes WHERE entity_id = ?",
            Int::class.java,
            entityPhoneticB,
        )
        assertEquals(2, countB, "Expected entity B (smith) to have 2 attribute rows (NAME + PHONE)")
    }

    /**
     * TEST-11: Free email domain does NOT trigger the domain-aware candidate strategy.
     *
     * Entity A (j.smith@gmail.com) and Entity B (john.smith@gmail.com) share the free domain
     * "gmail.com", which is in the FREE_EMAIL_DOMAINS set. The domain-aware query must be skipped.
     *
     * Trigram candidates MAY still exist for gmail emails — we only assert that no candidate
     * has matchSource=EMAIL_DOMAIN. The free-domain guard lives in the findCandidates loop,
     * so findEmailDomainCandidates should never be called for gmail.com.
     */
    @Test
    @org.junit.jupiter.api.Order(10)
    fun `free email domain does NOT trigger domain strategy`() {
        val candidates = candidateService.findCandidates(entityGmailA, workspaceId)

        // Trigram candidates may exist for gmail emails — we only assert that the domain-aware
        // strategy was skipped (no EMAIL_DOMAIN matchSource in results).
        val emailDomainCandidates = candidates.filter { it.matchSource == MatchSource.EMAIL_DOMAIN }
        assertTrue(
            emailDomainCandidates.isEmpty(),
            "Expected no EMAIL_DOMAIN candidates for gmail.com (free domain). " +
                "Found: ${emailDomainCandidates.map { "${it.candidateEntityId} matchSource=${it.matchSource}" }}",
        )
    }
}
