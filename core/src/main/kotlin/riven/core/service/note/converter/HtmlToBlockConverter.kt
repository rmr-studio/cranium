package riven.core.service.note.converter

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.safety.Safelist
import org.springframework.stereotype.Component
import java.util.*

/**
 * Converts sanitized HTML into BlockNote-compatible block structures.
 * Handles paragraphs, headings, ordered/unordered lists, inline styles (bold, italic),
 * links, and gracefully degrades unknown elements to plaintext.
 */
@Component
class HtmlToBlockConverter : NoteContentConverter {

    override fun convert(body: String): NoteConversionResult {
        val clean = Jsoup.clean(body, Safelist.relaxed())
        val document = Jsoup.parseBodyFragment(clean)
        val bodyElement = document.body()

        val blocks = mutableListOf<Map<String, Any>>()
        processBlockChildren(bodyElement, blocks)

        if (blocks.isEmpty()) {
            blocks.add(emptyParagraphBlock())
        }

        val plaintext = extractPlaintext(blocks)
        val title = plaintext.take(255)

        return NoteConversionResult(
            blocks = blocks,
            plaintext = plaintext,
            title = title,
        )
    }

    // ------ Block-level processing ------

    /**
     * Walks direct children of a container element and dispatches each to the
     * appropriate block converter. Top-level text nodes are wrapped in paragraphs.
     */
    private fun processBlockChildren(container: Element, blocks: MutableList<Map<String, Any>>) {
        for (child in container.childNodes()) {
            when (child) {
                is TextNode -> {
                    val text = child.wholeText.trim()
                    if (text.isNotEmpty()) {
                        blocks.add(paragraphBlock(listOf(textInline(text))))
                    }
                }
                is Element -> processBlockElement(child, blocks)
            }
        }
    }

    private fun processBlockElement(element: Element, blocks: MutableList<Map<String, Any>>) {
        when (element.tagName().lowercase()) {
            "p" -> blocks.add(paragraphBlock(parseInlineContent(element)))
            "h1" -> blocks.add(headingBlock(1, parseInlineContent(element)))
            "h2" -> blocks.add(headingBlock(2, parseInlineContent(element)))
            "h3" -> blocks.add(headingBlock(3, parseInlineContent(element)))
            "h4" -> blocks.add(headingBlock(4, parseInlineContent(element)))
            "h5" -> blocks.add(headingBlock(5, parseInlineContent(element)))
            "h6" -> blocks.add(headingBlock(6, parseInlineContent(element)))
            "ul" -> processListItems(element, "bulletListItem", blocks)
            "ol" -> processListItems(element, "numberedListItem", blocks)
            "br" -> { /* top-level <br> ignored */ }
            else -> {
                // Unknown block element: try to extract inline content as a paragraph
                val inlines = parseInlineContent(element)
                if (inlines.isNotEmpty()) {
                    blocks.add(paragraphBlock(inlines))
                }
            }
        }
    }

    private fun processListItems(listElement: Element, blockType: String, blocks: MutableList<Map<String, Any>>) {
        for (li in listElement.children()) {
            if (li.tagName().lowercase() == "li") {
                blocks.add(listItemBlock(blockType, parseInlineContent(li)))
            }
        }
    }

    // ------ Inline content processing ------

    /**
     * Parses the child nodes of a block-level element into a flat list of
     * BlockNote inline content objects (text, styled text, links).
     */
    private fun parseInlineContent(element: Element): List<Map<String, Any>> {
        val inlines = mutableListOf<Map<String, Any>>()
        walkInlineNodes(element.childNodes(), emptyMap(), inlines)
        return inlines
    }

    /**
     * Recursively walks inline nodes, accumulating style context as it descends
     * into nested formatting elements (e.g. bold inside italic).
     */
    private fun walkInlineNodes(
        nodes: List<Node>,
        inheritedStyles: Map<String, Any>,
        inlines: MutableList<Map<String, Any>>,
    ) {
        for (node in nodes) {
            when (node) {
                is TextNode -> {
                    val text = node.wholeText
                    if (text.isNotEmpty()) {
                        inlines.add(textInline(text, inheritedStyles))
                    }
                }
                is Element -> processInlineElement(node, inheritedStyles, inlines)
            }
        }
    }

    private fun processInlineElement(
        element: Element,
        inheritedStyles: Map<String, Any>,
        inlines: MutableList<Map<String, Any>>,
    ) {
        when (element.tagName().lowercase()) {
            "b", "strong" -> {
                val styles = inheritedStyles + ("bold" to true)
                walkInlineNodes(element.childNodes(), styles, inlines)
            }
            "i", "em" -> {
                val styles = inheritedStyles + ("italic" to true)
                walkInlineNodes(element.childNodes(), styles, inlines)
            }
            "a" -> {
                val href = element.attr("href")
                val linkContent = mutableListOf<Map<String, Any>>()
                walkInlineNodes(element.childNodes(), inheritedStyles, linkContent)
                inlines.add(linkInline(href, linkContent))
            }
            "br" -> {
                inlines.add(textInline("\n", inheritedStyles))
            }
            else -> {
                // Unknown inline element: strip tag, keep text content
                walkInlineNodes(element.childNodes(), inheritedStyles, inlines)
            }
        }
    }

    // ------ Block builders ------

    private fun paragraphBlock(content: List<Map<String, Any>>): Map<String, Any> = mapOf(
        "id" to randomId(),
        "type" to "paragraph",
        "props" to defaultProps(),
        "content" to content,
        "children" to emptyList<Any>(),
    )

    private fun headingBlock(level: Int, content: List<Map<String, Any>>): Map<String, Any> = mapOf(
        "id" to randomId(),
        "type" to "heading",
        "props" to defaultProps() + ("level" to level),
        "content" to content,
        "children" to emptyList<Any>(),
    )

    private fun listItemBlock(type: String, content: List<Map<String, Any>>): Map<String, Any> = mapOf(
        "id" to randomId(),
        "type" to type,
        "props" to defaultProps(),
        "content" to content,
        "children" to emptyList<Any>(),
    )

    private fun emptyParagraphBlock(): Map<String, Any> = paragraphBlock(emptyList())

    // ------ Inline builders ------

    private fun textInline(text: String, styles: Map<String, Any> = emptyMap()): Map<String, Any> = mapOf(
        "type" to "text",
        "text" to text,
        "styles" to styles,
    )

    private fun linkInline(href: String, content: List<Map<String, Any>>): Map<String, Any> = mapOf(
        "type" to "link",
        "href" to href,
        "content" to content,
    )

    // ------ Utilities ------

    private fun randomId(): String = UUID.randomUUID().toString().take(10)

    /**
     * Extracts plaintext from the already-built block list by recursively
     * collecting all "text" values from inline content.
     */
    private fun extractPlaintext(blocks: List<Map<String, Any>>): String {
        return blocks.joinToString("\n") { block ->
            extractTextFromContent(block["content"])
        }.trim()
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractTextFromContent(content: Any?): String {
        if (content == null) return ""
        val items = content as? List<Map<String, Any>> ?: return ""
        return items.joinToString("") { inline ->
            when (inline["type"]) {
                "text" -> inline["text"] as? String ?: ""
                "link" -> extractTextFromContent(inline["content"])
                else -> ""
            }
        }
    }
}
