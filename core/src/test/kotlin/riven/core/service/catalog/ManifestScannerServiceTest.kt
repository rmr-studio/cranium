package riven.core.service.catalog

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.core.io.support.ResourcePatternResolver
import riven.core.enums.catalog.ManifestType
import java.net.URI

class ManifestScannerServiceTest {

    private lateinit var resourcePatternResolver: ResourcePatternResolver
    private lateinit var objectMapper: ObjectMapper
    private lateinit var logger: KLogger
    private lateinit var service: ManifestScannerService

    private val validModelJson = """
    {
      "manifestVersion": "1.0",
      "key": "customer",
      "name": "Customer",
      "displayName": { "singular": "Customer", "plural": "Customers" },
      "attributes": {
        "name": { "key": "TEXT", "type": "string" },
        "email": { "key": "EMAIL", "type": "string" }
      }
    }
    """.trimIndent()

    private val validTemplateJson = """
    {
      "manifestVersion": "1.0",
      "key": "saas-starter",
      "name": "SaaS Starter",
      "entityTypes": [
        {
          "key": "deal",
          "name": "Deal",
          "displayName": { "singular": "Deal", "plural": "Deals" },
          "attributes": {
            "title": { "key": "TEXT", "type": "string" }
          }
        }
      ]
    }
    """.trimIndent()

    private val validIntegrationJson = """
    {
      "manifestVersion": "1.0",
      "key": "hubspot",
      "name": "HubSpot",
      "entityTypes": [
        {
          "key": "hubspot-contact",
          "name": "HubSpot Contact",
          "displayName": { "singular": "HubSpot Contact", "plural": "HubSpot Contacts" },
          "attributes": {
            "email": { "key": "EMAIL", "type": "string" }
          }
        }
      ]
    }
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        resourcePatternResolver = mock()
        objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        logger = mock()
        service = ManifestScannerService(resourcePatternResolver, objectMapper, logger)
    }

    // ------ scanModels ------

    @Test
    fun `scanModels returns valid model manifest`() {
        val resource = createResourceWithContent("customer.json", validModelJson)
        whenever(resourcePatternResolver.getResources("classpath:manifests/models/*.json"))
            .thenReturn(arrayOf(resource))
        mockSchemaResource("manifests/schemas/model.schema.json")

        val result = service.scanModels()

        assertEquals(1, result.size)
        assertEquals("customer", result[0].key)
        assertEquals(ManifestType.MODEL, result[0].type)
    }

    @Test
    fun `scanModels skips invalid manifest and logs WARN`() {
        val invalidJson = """
        {
          "manifestVersion": "1.0",
          "key": "bad-model",
          "name": "Bad Model"
        }
        """.trimIndent()

        val resource = createResourceWithContent("bad-model.json", invalidJson)
        whenever(resourcePatternResolver.getResources("classpath:manifests/models/*.json"))
            .thenReturn(arrayOf(resource))
        mockSchemaResource("manifests/schemas/model.schema.json")

        val result = service.scanModels()

        assertEquals(0, result.size)
        verify(logger).warn(any<() -> Any?>())
    }

    @Test
    fun `scanModels handles unparseable JSON`() {
        val resource = createResourceWithContent("garbage.json", "this is not json {{{")
        whenever(resourcePatternResolver.getResources("classpath:manifests/models/*.json"))
            .thenReturn(arrayOf(resource))

        val result = service.scanModels()

        assertEquals(0, result.size)
        verify(logger).warn(any<Throwable>(), any<() -> Any?>())
    }

    @Test
    fun `scanModels handles empty directory`() {
        whenever(resourcePatternResolver.getResources("classpath:manifests/models/*.json"))
            .thenReturn(emptyArray())

        val result = service.scanModels()

        assertEquals(0, result.size)
    }

    // ------ scanTemplates ------

    @Test
    fun `scanTemplates extracts key from directory name`() {
        val resource = createResourceWithUrl(
            "file:/app/manifests/templates/saas-starter/manifest.json",
            validTemplateJson
        )
        whenever(resourcePatternResolver.getResources("classpath:manifests/templates/*/manifest.json"))
            .thenReturn(arrayOf(resource))
        mockSchemaResource("manifests/schemas/template.schema.json")

        val result = service.scanTemplates()

        assertEquals(1, result.size)
        assertEquals("saas-starter", result[0].key)
        assertEquals(ManifestType.TEMPLATE, result[0].type)
    }

    // ------ scanIntegrations ------

    @Test
    fun `scanIntegrations extracts key from directory name`() {
        val resource = createResourceWithUrl(
            "file:/app/manifests/integrations/hubspot/manifest.json",
            validIntegrationJson
        )
        whenever(resourcePatternResolver.getResources("classpath:manifests/integrations/*/manifest.json"))
            .thenReturn(arrayOf(resource))
        mockSchemaResource("manifests/schemas/integration.schema.json")

        val result = service.scanIntegrations()

        assertEquals(1, result.size)
        assertEquals("hubspot", result[0].key)
        assertEquals(ManifestType.INTEGRATION, result[0].type)
    }

    // ------ Helpers ------

    private fun createResourceWithContent(filename: String, content: String): Resource {
        return mock {
            on { this.filename } doReturn filename
            on { getInputStream() } doAnswer { content.byteInputStream() }
        }
    }

    private fun createResourceWithUrl(urlString: String, content: String): Resource {
        return mock {
            on { url } doReturn URI(urlString).toURL()
            on { getInputStream() } doAnswer { content.byteInputStream() }
        }
    }

    private fun mockSchemaResource(schemaPath: String) {
        val realSchemaResource = ClassPathResource(schemaPath)
        whenever(resourcePatternResolver.getResource("classpath:$schemaPath"))
            .thenReturn(realSchemaResource)
    }
}
