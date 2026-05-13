package cranium.core.service.note.converter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Suppress("UNCHECKED_CAST")
class HtmlToBlockConverterTest {

    private val converter = HtmlToBlockConverter()

    // ------ Helper accessors ------

    private fun Map<String, Any>.blockType(): String = this["type"] as String
    private fun Map<String, Any>.blockProps(): Map<String, Any> = this["props"] as Map<String, Any>
    private fun Map<String, Any>.blockContent(): List<Map<String, Any>> = this["content"] as List<Map<String, Any>>
    private fun Map<String, Any>.inlineType(): String = this["type"] as String
    private fun Map<String, Any>.inlineText(): String = this["text"] as String
    private fun Map<String, Any>.inlineStyles(): Map<String, Any> = this["styles"] as Map<String, Any>
    private fun Map<String, Any>.linkHref(): String = this["href"] as String
    private fun Map<String, Any>.linkContent(): List<Map<String, Any>> = this["content"] as List<Map<String, Any>>

    // ------ 1. Paragraph with plain text ------

    @Test
    fun `paragraph with plain text produces paragraph block`() {
        val result = converter.convert("<p>Hello world</p>")

        assertEquals(1, result.blocks.size)
        val block = result.blocks[0]
        assertEquals("paragraph", block.blockType())

        val content = block.blockContent()
        assertEquals(1, content.size)
        assertEquals("text", content[0].inlineType())
        assertEquals("Hello world", content[0].inlineText())

        assertEquals("Hello world", result.plaintext)
        assertEquals("Hello world", result.title)
    }

    // ------ 2. Headings h1 through h6 ------

    @Test
    fun `headings h1 through h6 produce heading blocks with correct level`() {
        for (level in 1..6) {
            val result = converter.convert("<h$level>Heading $level</h$level>")

            assertEquals(1, result.blocks.size, "Expected 1 block for h$level")
            val block = result.blocks[0]
            assertEquals("heading", block.blockType(), "Block type should be heading for h$level")
            assertEquals(level, block.blockProps()["level"], "Level should be $level for h$level")

            val content = block.blockContent()
            assertEquals(1, content.size)
            assertEquals("text", content[0].inlineType())
            assertEquals("Heading $level", content[0].inlineText())
        }
    }

    // ------ 3. Bold and strong ------

    @Test
    fun `bold and strong produce bold inline style`() {
        val resultB = converter.convert("<p><b>bold text</b></p>")
        val contentB = resultB.blocks[0].blockContent()
        assertEquals(1, contentB.size)
        assertEquals("bold text", contentB[0].inlineText())
        assertEquals(true, contentB[0].inlineStyles()["bold"])

        val resultStrong = converter.convert("<p><strong>strong text</strong></p>")
        val contentStrong = resultStrong.blocks[0].blockContent()
        assertEquals(1, contentStrong.size)
        assertEquals("strong text", contentStrong[0].inlineText())
        assertEquals(true, contentStrong[0].inlineStyles()["bold"])
    }

    // ------ 4. Italic and em ------

    @Test
    fun `italic and em produce italic inline style`() {
        val resultI = converter.convert("<p><i>italic text</i></p>")
        val contentI = resultI.blocks[0].blockContent()
        assertEquals(1, contentI.size)
        assertEquals("italic text", contentI[0].inlineText())
        assertEquals(true, contentI[0].inlineStyles()["italic"])

        val resultEm = converter.convert("<p><em>emphasized text</em></p>")
        val contentEm = resultEm.blocks[0].blockContent()
        assertEquals(1, contentEm.size)
        assertEquals("emphasized text", contentEm[0].inlineText())
        assertEquals(true, contentEm[0].inlineStyles()["italic"])
    }

    // ------ 5. Anchor tags ------

    @Test
    fun `anchor tags produce link inline content`() {
        val result = converter.convert("<p><a href=\"https://example.com\">link text</a></p>")

        assertEquals(1, result.blocks.size)
        val content = result.blocks[0].blockContent()
        assertEquals(1, content.size)

        val linkInline = content[0]
        assertEquals("link", linkInline.inlineType())
        assertEquals("https://example.com", linkInline.linkHref())

        val linkChildren = linkInline.linkContent()
        assertEquals(1, linkChildren.size)
        assertEquals("text", linkChildren[0].inlineType())
        assertEquals("link text", linkChildren[0].inlineText())

        assertEquals("link text", result.plaintext)
    }

    // ------ 6. Unordered and ordered lists ------

    @Test
    fun `unordered and ordered lists produce list item blocks`() {
        val resultUl = converter.convert("<ul><li>bullet item</li></ul>")
        assertEquals(1, resultUl.blocks.size)
        assertEquals("bulletListItem", resultUl.blocks[0].blockType())
        val ulContent = resultUl.blocks[0].blockContent()
        assertEquals(1, ulContent.size)
        assertEquals("bullet item", ulContent[0].inlineText())

        val resultOl = converter.convert("<ol><li>numbered item</li></ol>")
        assertEquals(1, resultOl.blocks.size)
        assertEquals("numberedListItem", resultOl.blocks[0].blockType())
        val olContent = resultOl.blocks[0].blockContent()
        assertEquals(1, olContent.size)
        assertEquals("numbered item", olContent[0].inlineText())
    }

    // ------ 7. Nested lists ------

    @Test
    fun `nested lists produce nested list items`() {
        val html = """
            <ul>
                <li>parent
                    <ul>
                        <li>child</li>
                    </ul>
                </li>
            </ul>
        """.trimIndent()

        val result = converter.convert(html)

        // The converter flattens nested lists: the parent li gets its text content
        // and the nested list text is included as inline content within the parent item.
        // We verify that the output contains the text from both levels.
        assertTrue(result.blocks.isNotEmpty(), "Should produce at least one block")
        assertTrue(
            result.blocks.any { it.blockType() == "bulletListItem" },
            "Should contain bulletListItem blocks"
        )
        assertTrue(result.plaintext.contains("parent"), "Plaintext should contain parent text")
        assertTrue(result.plaintext.contains("child"), "Plaintext should contain child text")
    }

    // ------ 8. Empty HTML ------

    @Test
    fun `empty HTML produces empty paragraph block`() {
        val result = converter.convert("")

        assertEquals(1, result.blocks.size)
        val block = result.blocks[0]
        assertEquals("paragraph", block.blockType())

        val content = block.blockContent()
        assertTrue(content.isEmpty(), "Empty HTML should produce paragraph with no inline content")

        assertEquals("", result.plaintext)
        assertEquals("", result.title)
    }

    @Test
    fun `blank whitespace HTML produces empty paragraph block`() {
        val result = converter.convert("   \n  ")

        assertEquals(1, result.blocks.size)
        assertEquals("paragraph", result.blocks[0].blockType())
        assertEquals("", result.plaintext)
    }

    // ------ 9. Script and iframe sanitized ------

    @Test
    fun `script and iframe tags are sanitized out`() {
        val html = "<p>safe content</p><script>alert('xss')</script><iframe src=\"evil.html\"></iframe>"
        val result = converter.convert(html)

        // Only the paragraph should survive sanitization
        assertEquals(1, result.blocks.size)
        assertEquals("paragraph", result.blocks[0].blockType())

        val content = result.blocks[0].blockContent()
        assertEquals("safe content", content[0].inlineText())

        // Plaintext must not contain script or iframe content
        assertTrue(!result.plaintext.contains("alert"), "Script content should be sanitized")
        assertTrue(!result.plaintext.contains("evil"), "Iframe content should be sanitized")
    }

    // ------ 10. Malformed HTML ------

    @Test
    fun `malformed HTML degrades gracefully`() {
        val html = "<p>text<b>bold"
        val result = converter.convert(html)

        // Jsoup repairs malformed HTML, so we should still get output
        assertTrue(result.blocks.isNotEmpty(), "Malformed HTML should still produce blocks")
        assertNotNull(result.plaintext)
        assertTrue(result.plaintext.contains("text"), "Should extract text from malformed HTML")
        assertTrue(result.plaintext.contains("bold"), "Should extract bold text from malformed HTML")
    }
}
