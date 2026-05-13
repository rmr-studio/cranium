package cranium.core.service.workflow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import cranium.core.service.workflow.state.WorkflowNodeTemplateParserService
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkflowNodeTemplateParserServiceTest {

    private val parser = WorkflowNodeTemplateParserService()

    // ========== Valid Template Tests ==========

    @Test
    fun `parse simple template with two segments`() {
        val result = parser.parse("{{ steps.fetch_leads }}")

        assertTrue(result.isTemplate)
        assertEquals(listOf("steps", "fetch_leads"), result.path)
        assertNull(result.rawValue)
    }

    @Test
    fun `parse template with nested property access`() {
        val result = parser.parse("{{ steps.fetch_leads.output }}")

        assertTrue(result.isTemplate)
        assertEquals(listOf("steps", "fetch_leads", "output"), result.path)
        assertNull(result.rawValue)
    }

    @Test
    fun `parse template with deep nesting`() {
        val result = parser.parse("{{ steps.fetch_leads.output.email.domain }}")

        assertTrue(result.isTemplate)
        assertEquals(listOf("steps", "fetch_leads", "output", "email", "domain"), result.path)
        assertNull(result.rawValue)
    }

    @Test
    fun `parse template with whitespace around path`() {
        val result = parser.parse("{{  steps.fetch_leads  }}")

        assertTrue(result.isTemplate)
        assertEquals(listOf("steps", "fetch_leads"), result.path)
        assertNull(result.rawValue)
    }

    @Test
    fun `parse template with underscores in segment names`() {
        val result = parser.parse("{{ steps.fetch_user_data.output_value }}")

        assertTrue(result.isTemplate)
        assertEquals(listOf("steps", "fetch_user_data", "output_value"), result.path)
        assertNull(result.rawValue)
    }

    @Test
    fun `parse template with numbers in segment names`() {
        val result = parser.parse("{{ steps.step1.output2 }}")

        assertTrue(result.isTemplate)
        assertEquals(listOf("steps", "step1", "output2"), result.path)
        assertNull(result.rawValue)
    }

    @Test
    fun `parse template with loop syntax for future support`() {
        val result = parser.parse("{{ loop.loop_name.item.field }}")

        assertTrue(result.isTemplate)
        assertEquals(listOf("loop", "loop_name", "item", "field"), result.path)
        assertNull(result.rawValue)
    }

    // ========== Static String Tests ==========

    @Test
    fun `parse static string returns rawValue`() {
        val result = parser.parse("static value")

        assertFalse(result.isTemplate)
        assertNull(result.path)
        assertEquals("static value", result.rawValue)
    }

    @Test
    fun `parse empty string returns rawValue`() {
        val result = parser.parse("")

        assertFalse(result.isTemplate)
        assertNull(result.path)
        assertEquals("", result.rawValue)
    }

    @Test
    fun `parse string with single curly brace returns rawValue`() {
        val result = parser.parse("{ not a template }")

        assertFalse(result.isTemplate)
        assertNull(result.path)
        assertEquals("{ not a template }", result.rawValue)
    }

    // ========== Error Cases ==========

    @Test
    fun `parse empty template throws error`() {
        val exception = assertThrows<IllegalArgumentException> {
            parser.parse("{{ }}")
        }
        assertTrue(exception.message!!.contains("Empty template"))
    }

    @Test
    fun `parse template with empty segment throws error`() {
        val exception = assertThrows<IllegalArgumentException> {
            parser.parse("{{ steps..output }}")
        }
        assertTrue(exception.message!!.contains("Empty path segment"))
    }

    @Test
    fun `parse template with invalid characters throws error`() {
        val exception = assertThrows<IllegalArgumentException> {
            parser.parse("{{ steps-name }}")
        }
        assertTrue(exception.message!!.contains("Invalid template syntax") || exception.message!!.contains("Invalid path segment"))
    }

    @Test
    fun `parse template with special characters throws error`() {
        val exception = assertThrows<IllegalArgumentException> {
            parser.parse("{{ steps@output }}")
        }
        assertTrue(exception.message!!.contains("Invalid template syntax") || exception.message!!.contains("Invalid path segment"))
    }

    @Test
    fun `parse multiple templates creates embedded template result`() {
        val result = parser.parse("Hello {{ user.name }}, you have {{ count }} messages")

        assertTrue(result.isTemplate)
        assertTrue(result.isEmbeddedTemplate)
        assertEquals(2, result.embeddedTemplates?.size)
        assertEquals(listOf("user", "name"), result.embeddedTemplates?.get(0)?.path)
        assertEquals(listOf("count"), result.embeddedTemplates?.get(1)?.path)
    }

    @Test
    fun `parse embedded template creates embedded template result`() {
        val result = parser.parse("prefix {{ steps.name }} suffix")

        assertTrue(result.isTemplate)
        assertTrue(result.isEmbeddedTemplate)
        assertEquals(1, result.embeddedTemplates?.size)
        assertEquals(listOf("steps", "name"), result.embeddedTemplates?.get(0)?.path)
        assertEquals("prefix {{ steps.name }} suffix", result.templateString)
    }

    // ========== isTemplate() Tests ==========

    @Test
    fun `isTemplate returns true for valid template`() {
        assertTrue(parser.isTemplate("{{ steps.fetch_leads }}"))
    }

    @Test
    fun `isTemplate returns false for static string`() {
        assertFalse(parser.isTemplate("static value"))
    }

    @Test
    fun `isTemplate returns true for template with whitespace`() {
        assertTrue(parser.isTemplate("{{  steps.name  }}"))
    }

    @Test
    fun `isTemplate returns false for single curly braces`() {
        assertFalse(parser.isTemplate("{ not a template }"))
    }
}
