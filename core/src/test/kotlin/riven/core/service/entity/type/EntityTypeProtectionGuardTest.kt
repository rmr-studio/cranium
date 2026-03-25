package riven.core.service.entity.type

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.models.common.validation.Schema
import riven.core.service.util.factory.entity.EntityFactory
import java.util.*

class EntityTypeProtectionGuardTest {

    private lateinit var guard: EntityTypeProtectionGuard

    // Shared attribute IDs for schema construction
    private val protectedAttributeId = UUID.randomUUID()
    private val unprotectedAttributeId = UUID.randomUUID()
    private val nonExistentAttributeId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        guard = EntityTypeProtectionGuard()
    }

    // ------ Schema Helpers ------

    private fun schemaWithProtectedAndUnprotectedAttributes(): Schema<UUID> {
        return EntityFactory.createSimpleSchema(
            properties = mapOf(
                protectedAttributeId to Schema(
                    key = SchemaType.TEXT,
                    type = DataType.STRING,
                    required = true,
                    protected = true,
                ),
                unprotectedAttributeId to Schema(
                    key = SchemaType.TEXT,
                    type = DataType.STRING,
                    required = false,
                    protected = false,
                ),
            )
        )
    }

    // ------ assertCanDelete ------

    @Nested
    inner class AssertCanDelete {

        @Test
        fun `throws for readonly entity type`() {
            val type = EntityFactory.createEntityType(readonly = true)

            val exception = assertThrows<IllegalArgumentException> {
                guard.assertCanDelete(type)
            }
            assert(exception.message!!.contains("readonly"))
        }

        @Test
        fun `throws for protected entity type`() {
            val type = EntityFactory.createEntityType(protected = true)

            val exception = assertThrows<IllegalArgumentException> {
                guard.assertCanDelete(type)
            }
            assert(exception.message!!.contains("protected"))
        }

        @Test
        fun `does not throw for user entity type`() {
            val type = EntityFactory.createEntityType(readonly = false, protected = false)

            assertDoesNotThrow {
                guard.assertCanDelete(type)
            }
        }
    }

    // ------ assertCanModifySchema ------

    @Nested
    inner class AssertCanModifySchema {

        @Test
        fun `throws for readonly entity type`() {
            val type = EntityFactory.createEntityType(readonly = true)

            val exception = assertThrows<IllegalArgumentException> {
                guard.assertCanModifySchema(type)
            }
            assert(exception.message!!.contains("readonly"))
        }

        @Test
        fun `does not throw for protected entity type`() {
            val type = EntityFactory.createEntityType(protected = true)

            assertDoesNotThrow {
                guard.assertCanModifySchema(type)
            }
        }

        @Test
        fun `does not throw for user entity type`() {
            val type = EntityFactory.createEntityType(readonly = false, protected = false)

            assertDoesNotThrow {
                guard.assertCanModifySchema(type)
            }
        }
    }

    // ------ assertCanModifyAttribute ------

    @Nested
    inner class AssertCanModifyAttribute {

        @Test
        fun `throws for readonly entity type regardless of attribute`() {
            val schema = schemaWithProtectedAndUnprotectedAttributes()
            val type = EntityFactory.createEntityType(
                readonly = true,
                schema = schema,
                identifierKey = unprotectedAttributeId,
            )

            assertThrows<IllegalArgumentException> {
                guard.assertCanModifyAttribute(type, unprotectedAttributeId)
            }

            assertThrows<IllegalArgumentException> {
                guard.assertCanModifyAttribute(type, protectedAttributeId)
            }
        }

        @Test
        fun `throws for protected attribute on non-readonly type`() {
            val schema = schemaWithProtectedAndUnprotectedAttributes()
            val type = EntityFactory.createEntityType(
                readonly = false,
                schema = schema,
                identifierKey = unprotectedAttributeId,
            )

            val exception = assertThrows<IllegalArgumentException> {
                guard.assertCanModifyAttribute(type, protectedAttributeId)
            }
            assert(exception.message!!.contains("protected attribute"))
        }

        @Test
        fun `does not throw for unprotected attribute on non-readonly type`() {
            val schema = schemaWithProtectedAndUnprotectedAttributes()
            val type = EntityFactory.createEntityType(
                readonly = false,
                schema = schema,
                identifierKey = unprotectedAttributeId,
            )

            assertDoesNotThrow {
                guard.assertCanModifyAttribute(type, unprotectedAttributeId)
            }
        }

        @Test
        fun `does not throw for attribute not found in schema`() {
            val schema = schemaWithProtectedAndUnprotectedAttributes()
            val type = EntityFactory.createEntityType(
                readonly = false,
                schema = schema,
                identifierKey = unprotectedAttributeId,
            )

            assertDoesNotThrow {
                guard.assertCanModifyAttribute(type, nonExistentAttributeId)
            }
        }

        @Test
        fun `throws for protected attribute on protected entity type`() {
            val schema = schemaWithProtectedAndUnprotectedAttributes()
            val type = EntityFactory.createEntityType(
                readonly = false,
                protected = true,
                schema = schema,
                identifierKey = unprotectedAttributeId,
            )

            assertThrows<IllegalArgumentException> {
                guard.assertCanModifyAttribute(type, protectedAttributeId)
            }
        }

        @Test
        fun `does not throw for unprotected attribute on protected entity type`() {
            val schema = schemaWithProtectedAndUnprotectedAttributes()
            val type = EntityFactory.createEntityType(
                readonly = false,
                protected = true,
                schema = schema,
                identifierKey = unprotectedAttributeId,
            )

            assertDoesNotThrow {
                guard.assertCanModifyAttribute(type, unprotectedAttributeId)
            }
        }
    }

    // ------ assertCanRemoveAttribute ------

    @Nested
    inner class AssertCanRemoveAttribute {

        @Test
        fun `throws for readonly entity type`() {
            val schema = schemaWithProtectedAndUnprotectedAttributes()
            val type = EntityFactory.createEntityType(
                readonly = true,
                schema = schema,
                identifierKey = unprotectedAttributeId,
            )

            assertThrows<IllegalArgumentException> {
                guard.assertCanRemoveAttribute(type, unprotectedAttributeId)
            }
        }

        @Test
        fun `throws for protected attribute on non-readonly type`() {
            val schema = schemaWithProtectedAndUnprotectedAttributes()
            val type = EntityFactory.createEntityType(
                readonly = false,
                schema = schema,
                identifierKey = unprotectedAttributeId,
            )

            val exception = assertThrows<IllegalArgumentException> {
                guard.assertCanRemoveAttribute(type, protectedAttributeId)
            }
            assert(exception.message!!.contains("protected attribute"))
        }

        @Test
        fun `does not throw for unprotected attribute on non-readonly type`() {
            val schema = schemaWithProtectedAndUnprotectedAttributes()
            val type = EntityFactory.createEntityType(
                readonly = false,
                schema = schema,
                identifierKey = unprotectedAttributeId,
            )

            assertDoesNotThrow {
                guard.assertCanRemoveAttribute(type, unprotectedAttributeId)
            }
        }

        @Test
        fun `does not throw for attribute not found in schema`() {
            val schema = schemaWithProtectedAndUnprotectedAttributes()
            val type = EntityFactory.createEntityType(
                readonly = false,
                schema = schema,
                identifierKey = unprotectedAttributeId,
            )

            assertDoesNotThrow {
                guard.assertCanRemoveAttribute(type, nonExistentAttributeId)
            }
        }

        @Test
        fun `throws for protected attribute on protected entity type`() {
            val schema = schemaWithProtectedAndUnprotectedAttributes()
            val type = EntityFactory.createEntityType(
                readonly = false,
                protected = true,
                schema = schema,
                identifierKey = unprotectedAttributeId,
            )

            assertThrows<IllegalArgumentException> {
                guard.assertCanRemoveAttribute(type, protectedAttributeId)
            }
        }
    }

    // ------ assertCanUpdateConfiguration ------

    @Nested
    inner class AssertCanUpdateConfiguration {

        @Test
        fun `throws for readonly entity type`() {
            val type = EntityFactory.createEntityType(readonly = true)

            assertThrows<IllegalArgumentException> {
                guard.assertCanUpdateConfiguration(type, changingSemanticGroup = false, changingLifecycleDomain = false)
            }
        }

        @Test
        fun `throws for protected type changing semantic group`() {
            val type = EntityFactory.createEntityType(protected = true)

            val exception = assertThrows<IllegalArgumentException> {
                guard.assertCanUpdateConfiguration(type, changingSemanticGroup = true, changingLifecycleDomain = false)
            }
            assert(exception.message!!.contains("semantic group"))
        }

        @Test
        fun `throws for protected type changing lifecycle domain`() {
            val type = EntityFactory.createEntityType(protected = true)

            val exception = assertThrows<IllegalArgumentException> {
                guard.assertCanUpdateConfiguration(type, changingSemanticGroup = false, changingLifecycleDomain = true)
            }
            assert(exception.message!!.contains("lifecycle domain"))
        }

        @Test
        fun `throws for protected type changing both semantic group and lifecycle domain`() {
            val type = EntityFactory.createEntityType(protected = true)

            assertThrows<IllegalArgumentException> {
                guard.assertCanUpdateConfiguration(type, changingSemanticGroup = true, changingLifecycleDomain = true)
            }
        }

        @Test
        fun `does not throw for protected type with cosmetic-only changes`() {
            val type = EntityFactory.createEntityType(protected = true)

            assertDoesNotThrow {
                guard.assertCanUpdateConfiguration(type, changingSemanticGroup = false, changingLifecycleDomain = false)
            }
        }

        @Test
        fun `does not throw for user type changing semantic group`() {
            val type = EntityFactory.createEntityType(readonly = false, protected = false)

            assertDoesNotThrow {
                guard.assertCanUpdateConfiguration(type, changingSemanticGroup = true, changingLifecycleDomain = false)
            }
        }

        @Test
        fun `does not throw for user type changing lifecycle domain`() {
            val type = EntityFactory.createEntityType(readonly = false, protected = false)

            assertDoesNotThrow {
                guard.assertCanUpdateConfiguration(type, changingSemanticGroup = false, changingLifecycleDomain = true)
            }
        }

        @Test
        fun `does not throw for user type changing everything`() {
            val type = EntityFactory.createEntityType(readonly = false, protected = false)

            assertDoesNotThrow {
                guard.assertCanUpdateConfiguration(type, changingSemanticGroup = true, changingLifecycleDomain = true)
            }
        }
    }
}
