package cranium.core.service.catalog

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.core.io.support.ResourcePatternResolver
import cranium.core.configuration.properties.ManifestConfigurationProperties
import cranium.core.enums.catalog.ManifestType
import java.net.URI

class ManifestScannerServiceTest {

    private lateinit var resourcePatternResolver: ResourcePatternResolver
    private lateinit var objectMapper: ObjectMapper
    private lateinit var logger: KLogger
    private lateinit var service: ManifestScannerService

    private val validIntegrationJson = """
    {
      "manifestVersion": "1.0",
      "key": "hubspot",
      "name": "HubSpot",
      "category": "CRM",
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
        objectMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
        logger = mock()
        service = ManifestScannerService(resourcePatternResolver, objectMapper, ManifestConfigurationProperties(), logger)
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
