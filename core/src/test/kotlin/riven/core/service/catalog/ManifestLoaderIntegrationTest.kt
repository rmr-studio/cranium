package riven.core.service.catalog

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import riven.core.enums.catalog.ManifestType
import riven.core.repository.catalog.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Integration tests for the full manifest loader pipeline.
 *
 * Exercises scan -> resolve -> upsert against a real PostgreSQL database via Testcontainers.
 * Tests verify startup load, idempotent reload, and manifest removal reconciliation.
 */
@SpringBootTest(
    classes = [ManifestLoaderIntegrationTestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@ActiveProfiles("integration")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ManifestLoaderIntegrationTest {

    @Autowired
    private lateinit var loaderService: ManifestLoaderService

    @Autowired
    private lateinit var manifestCatalogRepository: ManifestCatalogRepository

    @Autowired
    private lateinit var catalogEntityTypeRepository: CatalogEntityTypeRepository

    @Autowired
    private lateinit var catalogRelationshipRepository: CatalogRelationshipRepository

    @Autowired
    private lateinit var catalogRelationshipTargetRuleRepository: CatalogRelationshipTargetRuleRepository

    @Autowired
    private lateinit var catalogSemanticMetadataRepository: CatalogSemanticMetadataRepository

    @Autowired
    private lateinit var catalogFieldMappingRepository: CatalogFieldMappingRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    companion object {
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
        )
            .withDatabaseName("riven_test")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        val tempDir: Path = Files.createTempDirectory("manifest-loader-test")

        init {
            postgres.start()
            copyFixturesToTempDir()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("riven.manifests.base-path") { "file:${tempDir.toAbsolutePath()}" }
        }

        private fun copyFixturesToTempDir() {
            copyClasspathFile("manifests/models/customer.json", "models/customer.json")
            copyClasspathFile("manifests/templates/saas-starter/manifest.json", "templates/saas-starter/manifest.json")
            copyClasspathFile("manifests/integrations/hubspot/manifest.json", "integrations/hubspot/manifest.json")
        }

        private fun copyClasspathFile(classpathPath: String, relativePath: String) {
            val resource = ClassPathResource(classpathPath)
            val targetPath = tempDir.resolve(relativePath)
            Files.createDirectories(targetPath.parent)
            resource.inputStream.use { input ->
                Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    @BeforeEach
    fun truncateCatalogTables() {
        jdbcTemplate.execute("TRUNCATE TABLE catalog_semantic_metadata CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE catalog_relationship_target_rules CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE catalog_relationships CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE catalog_field_mappings CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE catalog_entity_types CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE manifest_catalog CASCADE")
    }

    // ------ TEST-06: Full Load Cycle ------

    @Test
    @Order(1)
    fun `fullLoadCycle verifies all fixtures loaded into catalog`() {
        loaderService.loadAllManifests()

        // 3 manifests: customer (model), saas-starter (template), hubspot (integration)
        val manifestCount = manifestCatalogRepository.count()
        assertTrue(manifestCount >= 3, "Expected at least 3 manifest catalog entries, got $manifestCount")

        // Verify customer model exists
        val customerModel = manifestCatalogRepository.findByKeyAndManifestType("customer", ManifestType.MODEL)
        assertNotNull(customerModel, "Customer model manifest should exist")
        assertEquals("Customer", customerModel!!.name)
        assertFalse(customerModel.stale, "Customer model should not be stale")

        // Verify saas-starter template exists
        val saasTemplate = manifestCatalogRepository.findByKeyAndManifestType("saas-starter", ManifestType.TEMPLATE)
        assertNotNull(saasTemplate, "SaaS Starter template should exist")
        assertEquals("SaaS Starter", saasTemplate!!.name)

        // Verify hubspot integration exists
        val hubspotIntegration = manifestCatalogRepository.findByKeyAndManifestType("hubspot", ManifestType.INTEGRATION)
        assertNotNull(hubspotIntegration, "HubSpot integration should exist")
        hubspotIntegration!!

        // Verify entity types were created
        val entityTypeCount = catalogEntityTypeRepository.count()
        assertTrue(entityTypeCount > 0, "Expected entity types to be created, got $entityTypeCount")

        // Verify template entity types: customer (via $ref) + deal (inline) = 2 for saas-starter
        val templateEntityTypes = catalogEntityTypeRepository.findByManifestId(saasTemplate.id!!)
        assertTrue(templateEntityTypes.size >= 2, "SaaS template should have at least 2 entity types")

        // Verify integration entity types: hubspot-contact + hubspot-deal = 2
        val integrationEntityTypes = catalogEntityTypeRepository.findByManifestId(hubspotIntegration.id!!)
        assertEquals(2, integrationEntityTypes.size, "HubSpot should have 2 entity types")

        // Verify relationships exist for template
        val templateRelationships = catalogRelationshipRepository.findByManifestId(saasTemplate.id!!)
        assertTrue(templateRelationships.isNotEmpty(), "SaaS template should have relationships")

        // Verify field mappings exist for integration
        val fieldMappings = catalogFieldMappingRepository.findByManifestId(hubspotIntegration.id!!)
        assertTrue(fieldMappings.isNotEmpty(), "HubSpot should have field mappings")

        // Verify no stale entries after fresh load
        val staleEntries = manifestCatalogRepository.findByStaleTrue()
        assertTrue(staleEntries.isEmpty(), "No entries should be stale after fresh load")
    }

    // ------ TEST-07: Idempotent Reload ------

    @Test
    @Order(2)
    fun `idempotentReload produces identical catalog state with stable child IDs`() {
        // First load
        loaderService.loadAllManifests()
        val firstCounts = countAllTables()
        val customerManifest = manifestCatalogRepository.findByKeyAndManifestType("customer", ManifestType.MODEL)
        val firstCustomerId = customerManifest!!.id

        // Capture child IDs after first load
        val firstEntityTypeIds = catalogEntityTypeRepository.findByManifestId(firstCustomerId!!)
            .map { it.id }.toSet()

        // Second load
        loaderService.loadAllManifests()
        val secondCounts = countAllTables()
        val customerManifestAfter = manifestCatalogRepository.findByKeyAndManifestType("customer", ManifestType.MODEL)
        val secondCustomerId = customerManifestAfter!!.id

        // Capture child IDs after second load
        val secondEntityTypeIds = catalogEntityTypeRepository.findByManifestId(secondCustomerId!!)
            .map { it.id }.toSet()

        // Assert counts are identical
        assertEquals(firstCounts, secondCounts, "Table counts should be identical after idempotent reload")

        // Assert same entity persisted (not duplicated)
        assertEquals(firstCustomerId, secondCustomerId, "Customer manifest ID should be unchanged after reload")

        // Assert child IDs are stable (content hash skip)
        assertEquals(firstEntityTypeIds, secondEntityTypeIds, "Child entity type IDs should be stable across reloads")

        // Verify no stale entries
        val staleEntries = manifestCatalogRepository.findByStaleTrue()
        assertTrue(staleEntries.isEmpty(), "No entries should be stale after idempotent reload")
    }

    // ------ TEST-08: Manifest Removal Reconciliation ------

    @Test
    @Order(3)
    fun `manifestRemoval marks entry as stale`() {
        // Initial load with all fixtures
        loaderService.loadAllManifests()
        val initialCount = manifestCatalogRepository.count()
        assertTrue(initialCount >= 3, "Should have at least 3 manifests before removal")

        // Delete customer model file from temp dir
        val customerModelPath = tempDir.resolve("models/customer.json")
        assertTrue(Files.exists(customerModelPath), "Customer model file should exist before deletion")
        Files.delete(customerModelPath)

        // Reload -- customer should become stale
        loaderService.loadAllManifests()

        // The customer model catalog entry should now be stale
        val customerModel = manifestCatalogRepository.findByKeyAndManifestType("customer", ManifestType.MODEL)
        assertNotNull(customerModel, "Customer model catalog entry should still exist")
        assertTrue(customerModel!!.stale, "Customer model should be marked stale after file removal")

        // The saas-starter template should also be stale because it references the customer model via ${'$'}ref
        // (resolverService returns stale=true when the ref cannot be resolved)
        val saasTemplate = manifestCatalogRepository.findByKeyAndManifestType("saas-starter", ManifestType.TEMPLATE)
        assertNotNull(saasTemplate)
        assertTrue(saasTemplate!!.stale, "SaaS template should be stale (unresolved ref to customer model)")

        // HubSpot integration should still be non-stale
        val hubspotIntegration = manifestCatalogRepository.findByKeyAndManifestType("hubspot", ManifestType.INTEGRATION)
        assertNotNull(hubspotIntegration, "HubSpot integration should still exist")
        assertFalse(hubspotIntegration!!.stale, "HubSpot integration should not be stale")

        // Restore the file for test cleanup
        val resource = ClassPathResource("manifests/models/customer.json")
        resource.inputStream.use { input ->
            Files.copy(input, customerModelPath, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    // ------ Helpers ------

    private fun countAllTables(): Map<String, Long> = mapOf(
        "manifest_catalog" to manifestCatalogRepository.count(),
        "catalog_entity_types" to catalogEntityTypeRepository.count(),
        "catalog_relationships" to catalogRelationshipRepository.count(),
        "catalog_relationship_target_rules" to catalogRelationshipTargetRuleRepository.count(),
        "catalog_semantic_metadata" to catalogSemanticMetadataRepository.count(),
        "catalog_field_mappings" to catalogFieldMappingRepository.count(),
    )
}
