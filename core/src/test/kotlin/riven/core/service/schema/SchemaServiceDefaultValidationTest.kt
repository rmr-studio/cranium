package cranium.core.service.schema

import tools.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import cranium.core.configuration.auth.WorkspaceSecurity
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.core.DataFormat
import cranium.core.enums.core.DataType
import cranium.core.models.common.validation.Schema
import cranium.core.models.common.validation.SchemaOptions
import cranium.core.service.auth.AuthTokenService
import cranium.core.service.util.BaseServiceTest
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        SchemaService::class,
        ObjectMapper::class,
    ]
)
class SchemaServiceDefaultValidationTest : BaseServiceTest() {

    @Autowired
    private lateinit var schemaService: SchemaService

    // ------ Valid Defaults ------

    @Test
    fun `validateDefault accepts valid string default for TEXT`() {
        val schema = Schema<UUID>(key = SchemaType.TEXT, type = DataType.STRING)
        val errors = schemaService.validateDefault(schema, "hello")
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `validateDefault accepts valid number default for NUMBER`() {
        val schema = Schema<UUID>(key = SchemaType.NUMBER, type = DataType.NUMBER)
        val errors = schemaService.validateDefault(schema, 42)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `validateDefault accepts valid boolean default for CHECKBOX`() {
        val schema = Schema<UUID>(key = SchemaType.CHECKBOX, type = DataType.BOOLEAN)
        val errors = schemaService.validateDefault(schema, true)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `validateDefault accepts valid enum value for SELECT`() {
        val schema = Schema<UUID>(
            key = SchemaType.SELECT, type = DataType.STRING,
            options = SchemaOptions(enum = listOf("draft", "active", "done")),
        )
        val errors = schemaService.validateDefault(schema, "draft")
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `validateDefault accepts valid email default`() {
        val schema = Schema<UUID>(key = SchemaType.EMAIL, type = DataType.STRING, format = DataFormat.EMAIL)
        val errors = schemaService.validateDefault(schema, "user@example.com")
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `validateDefault accepts number within min max range`() {
        val schema = Schema<UUID>(
            key = SchemaType.NUMBER, type = DataType.NUMBER,
            options = SchemaOptions(minimum = 0.0, maximum = 100.0),
        )
        val errors = schemaService.validateDefault(schema, 50)
        assertTrue(errors.isEmpty())
    }

    // ------ Invalid Defaults ------

    @Test
    fun `validateDefault rejects number default for TEXT field`() {
        val schema = Schema<UUID>(key = SchemaType.TEXT, type = DataType.STRING)
        val errors = schemaService.validateDefault(schema, 42)
        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun `validateDefault rejects string default for NUMBER field`() {
        val schema = Schema<UUID>(key = SchemaType.NUMBER, type = DataType.NUMBER)
        val errors = schemaService.validateDefault(schema, "not-a-number")
        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun `validateDefault rejects invalid enum value for SELECT`() {
        val schema = Schema<UUID>(
            key = SchemaType.SELECT, type = DataType.STRING,
            options = SchemaOptions(enum = listOf("draft", "active", "done")),
        )
        val errors = schemaService.validateDefault(schema, "invalid_status")
        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun `validateDefault rejects invalid email format`() {
        val schema = Schema<UUID>(key = SchemaType.EMAIL, type = DataType.STRING, format = DataFormat.EMAIL)
        val errors = schemaService.validateDefault(schema, "not-an-email")
        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun `validateDefault rejects number below minimum`() {
        val schema = Schema<UUID>(
            key = SchemaType.NUMBER, type = DataType.NUMBER,
            options = SchemaOptions(minimum = 0.0, maximum = 100.0),
        )
        val errors = schemaService.validateDefault(schema, -5)
        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun `validateDefault rejects string shorter than minLength`() {
        val schema = Schema<UUID>(
            key = SchemaType.TEXT, type = DataType.STRING,
            options = SchemaOptions(minLength = 5),
        )
        val errors = schemaService.validateDefault(schema, "hi")
        assertTrue(errors.isNotEmpty())
    }

    // ------ ID SchemaType ------

    @Test
    fun `validateDefault rejects any default value for ID type`() {
        val schema = Schema<UUID>(
            key = SchemaType.ID, type = DataType.STRING,
            options = SchemaOptions(prefix = "PKR"),
        )
        val errors = schemaService.validateDefault(schema, "PKR-1")
        assertTrue(errors.isNotEmpty(), "ID attributes should not accept default values since they are auto-generated")
    }

    @Test
    fun `validateDefault accepts null default for ID type`() {
        val schema = Schema<UUID>(
            key = SchemaType.ID, type = DataType.STRING,
            options = SchemaOptions(prefix = "PKR"),
        )
        val errors = schemaService.validateDefault(schema, null)
        assertTrue(errors.isEmpty())
    }
}
