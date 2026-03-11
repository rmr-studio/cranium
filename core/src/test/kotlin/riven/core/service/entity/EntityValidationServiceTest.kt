package riven.core.service.entity

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.reset
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.entity.EntityEntity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataFormat
import riven.core.enums.core.DataType
import riven.core.enums.entity.validation.EntityTypeChangeType
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.models.common.validation.Schema
import riven.core.models.entity.EntityTypeSchema
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.models.entity.payload.EntityAttributeRelationPayloadReference
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.service.auth.AuthTokenService
import riven.core.service.schema.SchemaService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        EntityValidationServiceTest.TestConfig::class,
        EntityValidationService::class,
        SchemaService::class,
        ObjectMapper::class
    ]
)
@WithUserPersona(
    userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
    email = "test@test.com",
    displayName = "Test User",
    roles = [
        WorkspaceRole(
            workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
            role = WorkspaceRoles.OWNER
        )
    ]
)
class EntityValidationServiceTest : BaseServiceTest() {

    @Configuration
    class TestConfig

    @MockitoBean
    private lateinit var entityRelationshipRepository: EntityRelationshipRepository

    @Autowired
    private lateinit var entityValidationService: EntityValidationService

    @Autowired
    private lateinit var schemaService: SchemaService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var nameAttributeKey: UUID
    private lateinit var emailAttributeKey: UUID
    private lateinit var ageAttributeKey: UUID

    @BeforeEach
    fun setup() {
        // Reset all mocks
        reset(entityRelationshipRepository)

        // Initialize common attribute keys
        nameAttributeKey = UUID.randomUUID()
        emailAttributeKey = UUID.randomUUID()
        ageAttributeKey = UUID.randomUUID()
    }

    // ========== TEST CASE 1: Basic String Validation ==========

    @Test
    fun `validateEntity - validates required string attribute successfully`() {
        // Given: EntityType with required string attribute
        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    nameAttributeKey to Schema(
                        key = SchemaType.TEXT,
                        label = "Name",
                        type = DataType.STRING,
                        required = true
                    )
                )
            )
        )

        // And: Entity with valid string payload
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = mapOf(
                nameAttributeKey to EntityAttributePrimitivePayload(
                    value = "John Doe",
                    schemaType = SchemaType.TEXT
                )
            )
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: No validation errors
        assertTrue(errors.isEmpty(), "Should have no validation errors for valid string")
    }

    @Test
    fun `validateEntity - fails validation when required string attribute is missing`() {
        // Given: EntityType with required string attribute
        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    nameAttributeKey to Schema(
                        key = SchemaType.TEXT,
                        label = "Name",
                        type = DataType.STRING,
                        required = true
                    )
                )
            )
        )

        // And: Entity with empty payload (missing required field)
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = emptyMap()
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: Should have validation error for missing required field
        assertFalse(errors.isEmpty(), "Should have validation errors for missing required field")
        assertTrue(
            errors.any { it.contains("required") || it.contains(nameAttributeKey.toString()) },
            "Error should mention required field: $errors"
        )
    }

    @Test
    fun `validateEntity - validates optional string attribute when missing`() {
        // Given: EntityType with optional string attribute
        val descriptionKey = UUID.randomUUID()
        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    descriptionKey to Schema(
                        key = SchemaType.TEXT,
                        label = "Description",
                        type = DataType.STRING,
                        required = false
                    )
                )
            )
        )

        // And: Entity with empty payload
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = emptyMap()
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: No validation errors (optional field can be missing)
        assertTrue(errors.isEmpty(), "Should have no validation errors for missing optional field")
    }

    // ========== TEST CASE 2: String Format Validation ==========

    @Test
    fun `validateEntity - validates email format successfully`() {
        // Given: EntityType with email attribute
        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    emailAttributeKey to Schema(
                        key = SchemaType.EMAIL,
                        label = "Email",
                        type = DataType.STRING,
                        format = DataFormat.EMAIL,
                        required = true
                    )
                )
            )
        )

        // And: Entity with valid email
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = mapOf(
                emailAttributeKey to EntityAttributePrimitivePayload(
                    value = "john.doe@example.com",
                    schemaType = SchemaType.EMAIL
                )
            )
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: No validation errors
        assertTrue(errors.isEmpty(), "Should have no validation errors for valid email")
    }

    @Test
    fun `validateEntity - fails validation for invalid email format`() {
        // Given: EntityType with email attribute
        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    emailAttributeKey to Schema(
                        key = SchemaType.EMAIL,
                        label = "Email",
                        type = DataType.STRING,
                        format = DataFormat.EMAIL,
                        required = true
                    )
                )
            )
        )

        // And: Entity with invalid email
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = mapOf(
                emailAttributeKey to EntityAttributePrimitivePayload(
                    value = "not-an-email",
                    schemaType = SchemaType.EMAIL
                )
            )
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: Should have validation error for invalid email
        assertFalse(errors.isEmpty(), "Should have validation errors for invalid email format")
        assertTrue(
            errors.any { it.contains("email") || it.contains("format") },
            "Error should mention email format: $errors"
        )
    }

    @Test
    fun `validateEntity - validates phone format successfully`() {
        // Given: EntityType with phone attribute
        val phoneKey = UUID.randomUUID()
        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    phoneKey to Schema(
                        key = SchemaType.PHONE,
                        label = "Phone",
                        type = DataType.STRING,
                        format = DataFormat.PHONE,
                        required = true
                    )
                )
            )
        )

        // And: Entity with valid phone number
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = mapOf(
                phoneKey to EntityAttributePrimitivePayload(
                    value = "+12345678901",
                    schemaType = SchemaType.PHONE
                )
            )
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: No validation errors
        assertTrue(errors.isEmpty(), "Should have no validation errors for valid phone: $errors")
    }

    @Test
    fun `validateEntity - validates URL format successfully`() {
        // Given: EntityType with URL attribute
        val urlKey = UUID.randomUUID()
        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    urlKey to Schema(
                        key = SchemaType.URL,
                        label = "Website",
                        type = DataType.STRING,
                        format = DataFormat.URL,
                        required = true
                    )
                )
            )
        )

        // And: Entity with valid URL
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = mapOf(
                urlKey to EntityAttributePrimitivePayload(
                    value = "https://example.com",
                    schemaType = SchemaType.URL
                )
            )
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: No validation errors
        assertTrue(errors.isEmpty(), "Should have no validation errors for valid URL")
    }

    // ========== TEST CASE 3: Number Validation ==========

    @Test
    fun `validateEntity - validates number attribute successfully`() {
        // Given: EntityType with number attribute
        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    ageAttributeKey to Schema(
                        key = SchemaType.NUMBER,
                        label = "Age",
                        type = DataType.NUMBER,
                        required = true
                    )
                )
            )
        )

        // And: Entity with valid number
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = mapOf(
                ageAttributeKey to EntityAttributePrimitivePayload(
                    value = 25,
                    schemaType = SchemaType.NUMBER
                )
            )
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: No validation errors
        assertTrue(errors.isEmpty(), "Should have no validation errors for valid number")
    }

    @Test
    fun `validateEntity - validates number with minimum constraint`() {
        // Given: EntityType with number attribute having minimum constraint
        val priceKey = UUID.randomUUID()
        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    priceKey to Schema(
                        key = SchemaType.NUMBER,
                        label = "Price",
                        type = DataType.NUMBER,
                        required = true,
                        options = Schema.SchemaOptions(
                            minimum = 0.0
                        )
                    )
                )
            )
        )

        // And: Entity with number below minimum
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = mapOf(
                priceKey to EntityAttributePrimitivePayload(
                    value = -10,
                    schemaType = SchemaType.NUMBER
                )
            )
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: Should have validation error
        assertFalse(errors.isEmpty(), "Should have validation errors for number below minimum")
        assertTrue(
            errors.any { it.contains("minimum") },
            "Error should mention minimum constraint: $errors"
        )
    }

    @Test
    fun `validateEntity - validates number with maximum constraint`() {
        // Given: EntityType with number attribute having maximum constraint
        val percentageKey = UUID.randomUUID()
        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    percentageKey to Schema(
                        key = SchemaType.NUMBER,
                        label = "Percentage",
                        type = DataType.NUMBER,
                        required = true,
                        options = Schema.SchemaOptions(
                            minimum = 0.0,
                            maximum = 100.0
                        )
                    )
                )
            )
        )

        // And: Entity with number above maximum
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = mapOf(
                percentageKey to EntityAttributePrimitivePayload(
                    value = 150,
                    schemaType = SchemaType.NUMBER
                )
            )
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: Should have validation error
        assertFalse(errors.isEmpty(), "Should have validation errors for number above maximum")
        assertTrue(
            errors.any { it.contains("maximum") },
            "Error should mention maximum constraint: $errors"
        )
    }

    // ========== TEST CASE 4: Boolean Validation ==========

    @Test
    fun `validateEntity - validates boolean attribute successfully`() {
        // Given: EntityType with boolean attribute
        val isActiveKey = UUID.randomUUID()
        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    isActiveKey to Schema(
                        key = SchemaType.CHECKBOX,
                        label = "Is Active",
                        type = DataType.BOOLEAN,
                        required = true
                    )
                )
            )
        )

        // And: Entity with valid boolean
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = mapOf(
                isActiveKey to EntityAttributePrimitivePayload(
                    value = true,
                    schemaType = SchemaType.CHECKBOX
                )
            )
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: No validation errors
        assertTrue(errors.isEmpty(), "Should have no validation errors for valid boolean")
    }

    @Test
    fun `validateEntity - fails validation when boolean attribute has wrong type`() {
        // Given: EntityType with boolean attribute
        val isActiveKey = UUID.randomUUID()
        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    isActiveKey to Schema(
                        key = SchemaType.CHECKBOX,
                        label = "Is Active",
                        type = DataType.BOOLEAN,
                        required = true
                    )
                )
            )
        )

        // And: Entity with string instead of boolean
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = mapOf(
                isActiveKey to EntityAttributePrimitivePayload(
                    value = "yes",
                    schemaType = SchemaType.CHECKBOX
                )
            )
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: Should have validation error
        assertFalse(errors.isEmpty(), "Should have validation errors for wrong type")
        assertTrue(
            errors.any { it.contains("boolean") || it.contains("type") },
            "Error should mention type mismatch: $errors"
        )
    }

    // ========== TEST CASE 5: String Length Constraints ==========

    @Test
    fun `validateEntity - validates string minLength constraint`() {
        // Given: EntityType with string attribute having minLength
        val passwordKey = UUID.randomUUID()
        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    passwordKey to Schema(
                        key = SchemaType.TEXT,
                        label = "Password",
                        type = DataType.STRING,
                        required = true,
                        options = Schema.SchemaOptions(
                            minLength = 8
                        )
                    )
                )
            )
        )

        // And: Entity with string shorter than minLength
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = mapOf(
                passwordKey to EntityAttributePrimitivePayload(
                    value = "short",
                    schemaType = SchemaType.TEXT
                )
            )
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: Should have validation error
        assertFalse(errors.isEmpty(), "Should have validation errors for string below minLength")
        assertTrue(
            errors.any { it.contains("minLength") || it.contains("minimum") || it.contains("at least") || it.contains("too short") },
            "Error should mention minLength constraint: $errors"
        )
    }

    @Test
    fun `validateEntity - validates string maxLength constraint`() {
        // Given: EntityType with string attribute having maxLength
        val bioKey = UUID.randomUUID()
        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    bioKey to Schema(
                        key = SchemaType.TEXT,
                        label = "Bio",
                        type = DataType.STRING,
                        required = true,
                        options = Schema.SchemaOptions(
                            maxLength = 100
                        )
                    )
                )
            )
        )

        // And: Entity with string longer than maxLength
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = mapOf(
                bioKey to EntityAttributePrimitivePayload(
                    value = "a".repeat(150),
                    schemaType = SchemaType.TEXT
                )
            )
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: Should have validation error
        assertFalse(errors.isEmpty(), "Should have validation errors for string above maxLength")
        assertTrue(
            errors.any { it.contains("maxLength") || it.contains("maximum") || it.contains("at most") || it.contains("too long") },
            "Error should mention maxLength constraint: $errors"
        )
    }

    // ========== TEST CASE 6: Enum Validation ==========

    @Test
    fun `validateEntity - validates enum constraint successfully`() {
        // Given: EntityType with enum attribute
        val statusKey = UUID.randomUUID()
        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    statusKey to Schema(
                        key = SchemaType.SELECT,
                        label = "Status",
                        type = DataType.STRING,
                        required = true,
                        options = Schema.SchemaOptions(
                            enum = listOf("ACTIVE", "INACTIVE", "PENDING")
                        )
                    )
                )
            )
        )

        // And: Entity with valid enum value
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = mapOf(
                statusKey to EntityAttributePrimitivePayload(
                    value = "ACTIVE",
                    schemaType = SchemaType.SELECT
                )
            )
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: No validation errors
        assertTrue(errors.isEmpty(), "Should have no validation errors for valid enum value")
    }

    @Test
    fun `validateEntity - fails validation for invalid enum value`() {
        // Given: EntityType with enum attribute
        val statusKey = UUID.randomUUID()
        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    statusKey to Schema(
                        key = SchemaType.SELECT,
                        label = "Status",
                        type = DataType.STRING,
                        required = true,
                        options = Schema.SchemaOptions(
                            enum = listOf("ACTIVE", "INACTIVE", "PENDING")
                        )
                    )
                )
            )
        )

        // And: Entity with invalid enum value
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = mapOf(
                statusKey to EntityAttributePrimitivePayload(
                    value = "UNKNOWN",
                    schemaType = SchemaType.SELECT
                )
            )
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: Should have validation error
        assertFalse(errors.isEmpty(), "Should have validation errors for invalid enum value")
        assertTrue(
            errors.any { it.contains("enum") || it.contains("UNKNOWN") },
            "Error should mention enum constraint: $errors"
        )
    }

    // ========== TEST CASE 7: Regex Pattern Validation ==========

    @Test
    fun `validateEntity - validates regex pattern successfully`() {
        // Given: EntityType with regex pattern attribute
        val codeKey = UUID.randomUUID()
        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    codeKey to Schema(
                        key = SchemaType.TEXT,
                        label = "Product Code",
                        type = DataType.STRING,
                        required = true,
                        options = Schema.SchemaOptions(
                            regex = "^[A-Z]{3}-\\d{4}$"
                        )
                    )
                )
            )
        )

        // And: Entity with value matching pattern
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = mapOf(
                codeKey to EntityAttributePrimitivePayload(
                    value = "ABC-1234",
                    schemaType = SchemaType.TEXT
                )
            )
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: No validation errors
        assertTrue(errors.isEmpty(), "Should have no validation errors for value matching regex pattern")
    }

    @Test
    fun `validateEntity - fails validation for regex pattern mismatch`() {
        // Given: EntityType with regex pattern attribute
        val codeKey = UUID.randomUUID()
        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    codeKey to Schema(
                        key = SchemaType.TEXT,
                        label = "Product Code",
                        type = DataType.STRING,
                        required = true,
                        options = Schema.SchemaOptions(
                            regex = "^[A-Z]{3}-\\d{4}$"
                        )
                    )
                )
            )
        )

        // And: Entity with value not matching pattern
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = mapOf(
                codeKey to EntityAttributePrimitivePayload(
                    value = "invalid-code",
                    schemaType = SchemaType.TEXT
                )
            )
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: Should have validation error
        assertFalse(errors.isEmpty(), "Should have validation errors for regex pattern mismatch")
        assertTrue(
            errors.any { it.contains("pattern") },
            "Error should mention pattern constraint: $errors"
        )
    }

    // ========== TEST CASE 8: Array Validation ==========

//    @Test
//    fun `validateEntity - validates array of strings successfully`() {
//        // Given: EntityType with array attribute
//        val tagsKey = UUID.randomUUID()
//        val entityType = createEntityType(
//            schema = Schema(
//                key = SchemaType.OBJECT,
//                type = DataType.OBJECT,
//                properties = mapOf(
//                    tagsKey to Schema(
//                        key = SchemaType.,
//                        label = "Tags",
//                        type = DataType.ARRAY,
//                        required = true,
//                        items = Schema(
//                            key = SchemaType.TEXT,
//                            type = DataType.STRING
//                        )
//                    )
//                )
//            )
//        )
//
//        // And: Entity with valid array
//        val (entity, attributes) = createEntity(
//            typeId = entityType.id!!,
//            payload = mapOf(
//                tagsKey to EntityAttributePrimitivePayload(
//                    value = listOf("kotlin", "spring", "testing"),
//                    schemaType = SchemaType.ARRAY
//                )
//            )
//        )
//
//        // When: Validating entity
//        val errors = entityValidationService.validateEntity(entity, entityType, attributes)
//
//        // Then: No validation errors
//        assertTrue(errors.isEmpty(), "Should have no validation errors for valid array")
//    }

//    @Test
//    fun `validateEntity - validates empty array when not required`() {
//        // Given: EntityType with optional array attribute
//        val tagsKey = UUID.randomUUID()
//        val entityType = createEntityType(
//            schema = Schema(
//                key = SchemaType.OBJECT,
//                type = DataType.OBJECT,
//                properties = mapOf(
//                    tagsKey to Schema(
//                        key = SchemaType.ARRAY,
//                        label = "Tags",
//                        type = DataType.ARRAY,
//                        required = false,
//                        items = Schema(
//                            key = SchemaType.TEXT,
//                            type = DataType.STRING
//                        )
//                    )
//                )
//            )
//        )
//
//        // And: Entity with empty array
//        val (entity, attributes) = createEntity(
//            typeId = entityType.id!!,
//            payload = mapOf(
//                tagsKey to EntityAttributePrimitivePayload(
//                    value = emptyList<String>(),
//                    schemaType = SchemaType.ARRAY
//                )
//            )
//        )
//
//        // When: Validating entity
//        val errors = entityValidationService.validateEntity(entity, entityType, attributes)
//
//        // Then: No validation errors
//        assertTrue(errors.isEmpty(), "Should have no validation errors for empty optional array")
//    }

    // ========== TEST CASE 9: Nested Object Validation ==========

    @Test
    fun `validateEntity - validates nested object successfully`() {
        // Given: EntityType with nested object attribute
        val addressKey = UUID.randomUUID()
        val streetKey = UUID.randomUUID()
        val cityKey = UUID.randomUUID()

        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    addressKey to Schema(
                        key = SchemaType.OBJECT,
                        label = "Address",
                        type = DataType.OBJECT,
                        required = true,
                        properties = mapOf(
                            streetKey to Schema(
                                key = SchemaType.TEXT,
                                label = "Street",
                                type = DataType.STRING,
                                required = true
                            ),
                            cityKey to Schema(
                                key = SchemaType.TEXT,
                                label = "City",
                                type = DataType.STRING,
                                required = true
                            )
                        )
                    )
                )
            )
        )

        // And: Entity with valid nested object
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = mapOf(
                addressKey to EntityAttributePrimitivePayload(
                    value = mapOf(
                        streetKey.toString() to "123 Main St",
                        cityKey.toString() to "Springfield"
                    ),
                    schemaType = SchemaType.OBJECT
                )
            )
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: No validation errors
        assertTrue(errors.isEmpty(), "Should have no validation errors for valid nested object")
    }

    @Test
    fun `validateEntity - fails validation when nested object is missing required field`() {
        // Given: EntityType with nested object attribute
        val addressKey = UUID.randomUUID()
        val streetKey = UUID.randomUUID()
        val cityKey = UUID.randomUUID()

        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    addressKey to Schema(
                        key = SchemaType.OBJECT,
                        label = "Address",
                        type = DataType.OBJECT,
                        required = true,
                        properties = mapOf(
                            streetKey to Schema(
                                key = SchemaType.TEXT,
                                label = "Street",
                                type = DataType.STRING,
                                required = true
                            ),
                            cityKey to Schema(
                                key = SchemaType.TEXT,
                                label = "City",
                                type = DataType.STRING,
                                required = true
                            )
                        )
                    )
                )
            )
        )

        // And: Entity with nested object missing required field
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = mapOf(
                addressKey to EntityAttributePrimitivePayload(
                    value = mapOf(
                        streetKey.toString() to "123 Main St"
                        // Missing cityKey
                    ),
                    schemaType = SchemaType.OBJECT
                )
            )
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: Should have validation error
        assertFalse(errors.isEmpty(), "Should have validation errors for missing required nested field")
        assertTrue(
            errors.any { it.contains("required") || it.contains(cityKey.toString()) },
            "Error should mention missing required field: $errors"
        )
    }

    // ========== TEST CASE 10: Multiple Attributes Validation ==========

    @Test
    fun `validateEntity - validates multiple attributes with mixed types`() {
        // Given: EntityType with multiple attributes
        val isActiveKey = UUID.randomUUID()
        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    nameAttributeKey to Schema(
                        key = SchemaType.TEXT,
                        label = "Name",
                        type = DataType.STRING,
                        required = true
                    ),
                    emailAttributeKey to Schema(
                        key = SchemaType.EMAIL,
                        label = "Email",
                        type = DataType.STRING,
                        format = DataFormat.EMAIL,
                        required = true
                    ),
                    ageAttributeKey to Schema(
                        key = SchemaType.NUMBER,
                        label = "Age",
                        type = DataType.NUMBER,
                        required = true
                    ),
                    isActiveKey to Schema(
                        key = SchemaType.CHECKBOX,
                        label = "Is Active",
                        type = DataType.BOOLEAN,
                        required = true
                    )
                )
            )
        )

        // And: Entity with all valid attributes
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = mapOf(
                nameAttributeKey to EntityAttributePrimitivePayload(
                    value = "John Doe",
                    schemaType = SchemaType.TEXT
                ),
                emailAttributeKey to EntityAttributePrimitivePayload(
                    value = "john@example.com",
                    schemaType = SchemaType.EMAIL
                ),
                ageAttributeKey to EntityAttributePrimitivePayload(
                    value = 30,
                    schemaType = SchemaType.NUMBER
                ),
                isActiveKey to EntityAttributePrimitivePayload(
                    value = true,
                    schemaType = SchemaType.CHECKBOX
                )
            )
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: No validation errors
        assertTrue(errors.isEmpty(), "Should have no validation errors for all valid attributes")
    }

    // ========== TEST CASE 11: Relationship Payload Exclusion ==========

    @Test
    fun `validateEntity - excludes relationship payloads from attribute validation`() {
        // Given: EntityType with only attribute schema (no relationships in schema)
        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    nameAttributeKey to Schema(
                        key = SchemaType.TEXT,
                        label = "Name",
                        type = DataType.STRING,
                        required = true
                    )
                )
            )
        )

        // And: Entity with both attribute and relationship payloads
        val relationshipKey = UUID.randomUUID()
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = mapOf(
                nameAttributeKey to EntityAttributePrimitivePayload(
                    value = "John Doe",
                    schemaType = SchemaType.TEXT
                ),
                relationshipKey to EntityAttributeRelationPayloadReference(
                    relations = listOf(UUID.randomUUID(), UUID.randomUUID())
                )
            )
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: No validation errors (relationship payload is excluded)
        assertTrue(
            errors.isEmpty(),
            "Should have no validation errors as relationship payloads are excluded from attribute validation"
        )
    }

    @Test
    fun `validateEntity - validates only attribute payloads when mixed with relationships`() {
        // Given: EntityType with attribute schema
        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    nameAttributeKey to Schema(
                        key = SchemaType.TEXT,
                        label = "Name",
                        type = DataType.STRING,
                        required = true
                    ),
                    emailAttributeKey to Schema(
                        key = SchemaType.EMAIL,
                        label = "Email",
                        type = DataType.STRING,
                        format = DataFormat.EMAIL,
                        required = true
                    )
                )
            )
        )

        // And: Entity with valid attribute, invalid email, and relationships
        val relationshipKey = UUID.randomUUID()
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = mapOf(
                nameAttributeKey to EntityAttributePrimitivePayload(
                    value = "John Doe",
                    schemaType = SchemaType.TEXT
                ),
                emailAttributeKey to EntityAttributePrimitivePayload(
                    value = "invalid-email",
                    schemaType = SchemaType.EMAIL
                ),
                relationshipKey to EntityAttributeRelationPayloadReference(
                    relations = listOf(UUID.randomUUID())
                )
            )
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: Should only have error for invalid email attribute
        assertFalse(errors.isEmpty(), "Should have validation errors for invalid email")
        assertTrue(
            errors.any { it.contains("email") || it.contains("format") },
            "Error should be about email format, not relationships: $errors"
        )
    }

    // ========== TEST CASE 12: Breaking Schema Changes Detection ==========

    @Test
    fun `detectSchemaBreakingChanges - detects removed required field as breaking`() {
        // Given: Old schema with required field
        val oldSchema = Schema<UUID>(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = mapOf(
                nameAttributeKey to Schema(
                    key = SchemaType.TEXT,
                    label = "Name",
                    type = DataType.STRING,
                    required = true
                )
            )
        )

        // And: New schema without that field
        val newSchema = Schema<UUID>(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = emptyMap()
        )

        // When: Detecting breaking changes
        val changes = entityValidationService.detectSchemaBreakingChanges(oldSchema, newSchema)

        // Then: Should detect as breaking change
        val removedChange = changes.find { it.type == EntityTypeChangeType.FIELD_REMOVED }
        assertNotNull(removedChange, "Should detect field removal")
        assertTrue(removedChange!!.breaking, "Removing required field should be breaking")
        assertEquals(nameAttributeKey.toString(), removedChange.path)
    }

    @Test
    fun `detectSchemaBreakingChanges - detects removed optional field as non-breaking`() {
        // Given: Old schema with optional field
        val oldSchema = Schema<UUID>(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = mapOf(
                nameAttributeKey to Schema(
                    key = SchemaType.TEXT,
                    label = "Name",
                    type = DataType.STRING,
                    required = false
                )
            )
        )

        // And: New schema without that field
        val newSchema = Schema<UUID>(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = emptyMap()
        )

        // When: Detecting breaking changes
        val changes = entityValidationService.detectSchemaBreakingChanges(oldSchema, newSchema)

        // Then: Should detect as non-breaking change
        val removedChange = changes.find { it.type == EntityTypeChangeType.FIELD_REMOVED }
        assertNotNull(removedChange, "Should detect field removal")
        assertFalse(removedChange!!.breaking, "Removing optional field should not be breaking")
    }

    @Test
    fun `detectSchemaBreakingChanges - detects added required field as breaking`() {
        // Given: Old schema without field
        val oldSchema = Schema<UUID>(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = emptyMap()
        )

        // And: New schema with required field
        val newSchema = Schema<UUID>(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = mapOf(
                emailAttributeKey to Schema(
                    key = SchemaType.EMAIL,
                    label = "Email",
                    type = DataType.STRING,
                    format = DataFormat.EMAIL,
                    required = true
                )
            )
        )

        // When: Detecting breaking changes
        val changes = entityValidationService.detectSchemaBreakingChanges(oldSchema, newSchema)

        // Then: Should detect as breaking change
        val addedChange = changes.find { it.type == EntityTypeChangeType.FIELD_REQUIRED_ADDED }
        assertNotNull(addedChange, "Should detect required field addition")
        assertTrue(addedChange!!.breaking, "Adding required field should be breaking")
        assertEquals(emailAttributeKey.toString(), addedChange.path)
    }

    @Test
    fun `detectSchemaBreakingChanges - detects type change as breaking`() {
        // Given: Old schema with string field
        val oldSchema = Schema<UUID>(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = mapOf(
                ageAttributeKey to Schema(
                    key = SchemaType.TEXT,
                    label = "Age",
                    type = DataType.STRING,
                    required = true
                )
            )
        )

        // And: New schema with same field as number
        val newSchema = Schema<UUID>(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = mapOf(
                ageAttributeKey to Schema(
                    key = SchemaType.NUMBER,
                    label = "Age",
                    type = DataType.NUMBER,
                    required = true
                )
            )
        )

        // When: Detecting breaking changes
        val changes = entityValidationService.detectSchemaBreakingChanges(oldSchema, newSchema)

        // Then: Should detect as breaking change
        val typeChange = changes.find { it.type == EntityTypeChangeType.FIELD_TYPE_CHANGED }
        assertNotNull(typeChange, "Should detect type change")
        assertTrue(typeChange!!.breaking, "Type change should be breaking")
        assertEquals(ageAttributeKey.toString(), typeChange.path)
        assertTrue(
            typeChange.description.contains("STRING") && typeChange.description.contains("NUMBER"),
            "Description should mention both types"
        )
    }

    @Test
    fun `detectSchemaBreakingChanges - detects optional to required change as breaking`() {
        // Given: Old schema with optional field
        val oldSchema = Schema<UUID>(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = mapOf(
                nameAttributeKey to Schema(
                    key = SchemaType.TEXT,
                    label = "Name",
                    type = DataType.STRING,
                    required = false
                )
            )
        )

        // And: New schema with same field as required
        val newSchema = Schema<UUID>(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = mapOf(
                nameAttributeKey to Schema(
                    key = SchemaType.TEXT,
                    label = "Name",
                    type = DataType.STRING,
                    required = true
                )
            )
        )

        // When: Detecting breaking changes
        val changes = entityValidationService.detectSchemaBreakingChanges(oldSchema, newSchema)

        // Then: Should detect as breaking change
        val requiredChange = changes.find { it.type == EntityTypeChangeType.FIELD_REQUIRED_ADDED }
        assertNotNull(requiredChange, "Should detect required flag change")
        assertTrue(requiredChange!!.breaking, "Optional to required change should be breaking")
        assertEquals(nameAttributeKey.toString(), requiredChange.path)
    }

    @Test
    fun `detectSchemaBreakingChanges - returns empty list when no changes`() {
        // Given: Identical schemas
        val schema = Schema<UUID>(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = mapOf(
                nameAttributeKey to Schema(
                    key = SchemaType.TEXT,
                    label = "Name",
                    type = DataType.STRING,
                    required = true
                )
            )
        )

        // When: Detecting breaking changes
        val changes = entityValidationService.detectSchemaBreakingChanges(schema, schema)

        // Then: Should have no changes
        assertTrue(changes.isEmpty(), "Should have no changes for identical schemas")
    }

    // ========== TEST CASE 13: Validate Existing Entities Against New Schema ==========
    // NOTE: These tests currently fail due to a bug in EntityValidationService.validateExistingEntitiesAgainstNewSchema
    // The method passes full EntityAttributePayload objects to the validator instead of extracting the .value field
    // like validateEntity does (line 172 vs line 37-39). Once this bug is fixed, uncomment these tests.

    @Test
    fun `validateExistingEntitiesAgainstNewSchema - returns all valid when entities match new schema`() {
        // Given: New schema
        val newSchema = Schema<UUID>(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = mapOf(
                nameAttributeKey to Schema(
                    key = SchemaType.TEXT,
                    label = "Name",
                    type = DataType.STRING,
                    required = true
                )
            )
        )

        // And: Entities that match the schema
        val pairs = listOf(
            createEntity(
                typeId = UUID.randomUUID(),
                payload = mapOf(
                    nameAttributeKey to EntityAttributePrimitivePayload(
                        value = "Entity 1",
                        schemaType = SchemaType.TEXT
                    )
                )
            ),
            createEntity(
                typeId = UUID.randomUUID(),
                payload = mapOf(
                    nameAttributeKey to EntityAttributePrimitivePayload(
                        value = "Entity 2",
                        schemaType = SchemaType.TEXT
                    )
                )
            )
        )
        val entities = pairs.map { it.first }
        val attributesByEntityId = pairs.associate { it.first.id!! to it.second }

        // When: Validating entities against new schema
        val summary = entityValidationService.validateExistingEntitiesAgainstNewSchema(entities, newSchema, attributesByEntityId)

        // Then: All entities should be valid
        assertEquals(2, summary.totalEntities)
        assertEquals(2, summary.validCount)
        assertEquals(0, summary.invalidCount)
        assertTrue(summary.sampleErrors.isEmpty())
    }

    @Test
    fun `validateExistingEntitiesAgainstNewSchema - identifies invalid entities`() {
        // Given: New schema with required email
        val newSchema = Schema<UUID>(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = mapOf(
                emailAttributeKey to Schema(
                    key = SchemaType.EMAIL,
                    label = "Email",
                    type = DataType.STRING,
                    format = DataFormat.EMAIL,
                    required = true
                )
            )
        )

        // And: Entities with missing or invalid email
        val pairs = listOf(
            createEntity(
                typeId = UUID.randomUUID(),
                payload = mapOf(
                    emailAttributeKey to EntityAttributePrimitivePayload(
                        value = "valid@example.com",
                        schemaType = SchemaType.EMAIL
                    )
                )
            ),
            createEntity(
                typeId = UUID.randomUUID(),
                payload = emptyMap() // Missing required email
            ),
            createEntity(
                typeId = UUID.randomUUID(),
                payload = mapOf(
                    emailAttributeKey to EntityAttributePrimitivePayload(
                        value = "invalid-email",
                        schemaType = SchemaType.EMAIL
                    )
                )
            )
        )
        val entities = pairs.map { it.first }
        val attributesByEntityId = pairs.associate { it.first.id!! to it.second }

        // When: Validating entities against new schema
        val summary = entityValidationService.validateExistingEntitiesAgainstNewSchema(entities, newSchema, attributesByEntityId)

        // Then: Should identify invalid entities
        assertEquals(3, summary.totalEntities)
        assertEquals(1, summary.validCount)
        assertEquals(2, summary.invalidCount)
        assertEquals(2, summary.sampleErrors.size)
    }

    @Test
    fun `validateExistingEntitiesAgainstNewSchema - limits sample errors to 10`() {
        // Given: New schema with required field
        val newSchema = Schema<UUID>(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = mapOf(
                nameAttributeKey to Schema(
                    key = SchemaType.TEXT,
                    label = "Name",
                    type = DataType.STRING,
                    required = true
                )
            )
        )

        // And: 15 entities all missing required field
        val pairs = (1..15).map {
            createEntity(
                typeId = UUID.randomUUID(),
                payload = emptyMap()
            )
        }
        val entities = pairs.map { it.first }
        val attributesByEntityId = pairs.associate { it.first.id!! to it.second }

        // When: Validating entities against new schema
        val summary = entityValidationService.validateExistingEntitiesAgainstNewSchema(entities, newSchema, attributesByEntityId)

        // Then: Should limit sample errors to 10
        assertEquals(15, summary.totalEntities)
        assertEquals(0, summary.validCount)
        assertEquals(15, summary.invalidCount)
        assertEquals(10, summary.sampleErrors.size, "Should limit sample errors to 10")
    }

    // ========== TEST CASE 14: Date and DateTime Format Validation ==========

    @Test
    fun `validateEntity - validates date format successfully`() {
        // Given: EntityType with date attribute
        val birthDateKey = UUID.randomUUID()
        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    birthDateKey to Schema(
                        key = SchemaType.DATE,
                        label = "Birth Date",
                        type = DataType.STRING,
                        format = DataFormat.DATE,
                        required = true
                    )
                )
            )
        )

        // And: Entity with valid date (ISO 8601 format)
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = mapOf(
                birthDateKey to EntityAttributePrimitivePayload(
                    value = "2000-01-01",
                    schemaType = SchemaType.DATE
                )
            )
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: No validation errors
        assertTrue(errors.isEmpty(), "Should have no validation errors for valid date format")
    }

    @Test
    fun `validateEntity - validates datetime format successfully`() {
        // Given: EntityType with datetime attribute
        val createdAtKey = UUID.randomUUID()
        val entityType = createEntityType(
            schema = Schema(
                key = SchemaType.OBJECT,
                type = DataType.OBJECT,
                properties = mapOf(
                    createdAtKey to Schema(
                        key = SchemaType.DATE,
                        label = "Created At",
                        type = DataType.STRING,
                        format = DataFormat.DATETIME,
                        required = true
                    )
                )
            )
        )

        // And: Entity with valid datetime (ISO 8601 format)
        val (entity, attributes) = createEntity(
            typeId = entityType.id!!,
            payload = mapOf(
                createdAtKey to EntityAttributePrimitivePayload(
                    value = "2024-01-01T12:00:00Z",
                    schemaType = SchemaType.DATE
                )
            )
        )

        // When: Validating entity
        val errors = entityValidationService.validateEntity(entity, entityType, attributes)

        // Then: No validation errors
        assertTrue(errors.isEmpty(), "Should have no validation errors for valid datetime format")
    }

    // ========== TEST CASE: ID Read-Only Enforcement ==========

    @Test
    fun `validateEntity rejects modification of ID attribute on update`() {
        val idAttrKey = UUID.randomUUID()
        val schema = Schema<UUID>(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = mapOf(
                nameAttributeKey to Schema(
                    key = SchemaType.TEXT, type = DataType.STRING, label = "Name", required = true
                ),
                idAttrKey to Schema(
                    key = SchemaType.ID, type = DataType.STRING, label = "Reference",
                    options = Schema.SchemaOptions(prefix = "TSK"),
                ),
            ),
        )

        val entityType = EntityTypeEntity(
            id = UUID.randomUUID(),
            key = "task",
            displayNameSingular = "Task",
            displayNamePlural = "Tasks",
            identifierKey = nameAttributeKey,
            workspaceId = workspaceId,
            schema = schema,
        )

        val entity = EntityEntity(
            id = UUID.randomUUID(),
            workspaceId = workspaceId,
            typeId = entityType.id!!,
            typeKey = "task",
            identifierKey = nameAttributeKey,
        )

        val attributes = mapOf(
            nameAttributeKey to EntityAttributePrimitivePayload(value = "My Task", schemaType = SchemaType.TEXT),
            idAttrKey to EntityAttributePrimitivePayload(value = "TSK-999", schemaType = SchemaType.ID),
        )

        val errors = entityValidationService.validateEntity(
            entity = entity,
            entityType = entityType,
            attributes = attributes,
            isUpdate = true,
            previousAttributes = mapOf(
                idAttrKey to EntityAttributePrimitivePayload(value = "TSK-1", schemaType = SchemaType.ID),
            ),
        )

        assertTrue(errors.any { it.contains("cannot be modified") })
    }

    // ========== Helper Methods ==========

    private fun createEntityType(
        schema: EntityTypeSchema,
        key: String = "test-entity-type",
        workspaceId: UUID = this.workspaceId
    ): EntityTypeEntity {
        return EntityTypeEntity(
            id = UUID.randomUUID(),
            key = key,
            displayNameSingular = "Test Entity",
            displayNamePlural = "Test Entities",
            workspaceId = workspaceId,
            schema = schema,
            identifierKey = nameAttributeKey,
            iconType = IconType.CIRCLE_DASHED,
            iconColour = IconColour.NEUTRAL
        )
    }

    private fun createEntity(
        typeId: UUID,
        payload: Map<UUID, riven.core.models.entity.payload.EntityAttributePayload>,
        workspaceId: UUID = this.workspaceId
    ): Pair<EntityEntity, Map<UUID, EntityAttributePrimitivePayload>> {
        val entity = EntityEntity(
            id = UUID.randomUUID(),
            workspaceId = workspaceId,
            typeId = typeId,
            identifierKey = nameAttributeKey,
            iconType = IconType.FILE,
            iconColour = IconColour.NEUTRAL,
            typeKey = "test-entity-type"
        )
        val attributes = payload
            .filterValues { it is EntityAttributePrimitivePayload }
            .mapValues { it.value as EntityAttributePrimitivePayload }
        return entity to attributes
    }
}
