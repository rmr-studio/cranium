package cranium.core.entity.block

import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import cranium.core.enums.block.layout.RenderType
import cranium.core.enums.block.node.NodeType
import cranium.core.models.block.layout.RenderContent
import cranium.core.models.block.layout.TreeLayout
import cranium.core.models.block.layout.Widget
import cranium.core.models.block.layout.options.DraggableOptions
import cranium.core.models.block.layout.options.ResizableOptions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

class TreeLayoutSerializationTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    /**
     * Test deserialization of the example payload from Gridstack.save()
     * This validates that our TreeLayout data model correctly captures all Gridstack metadata
     */
    @Test
    fun `should deserialize nested block layout from Gridstack save payload`() {
        // This is the exact payload example provided by the user
        val gridstackPayload = """
        {
            "resizable": {
                "handles": "se, sw"
            },
            "draggable": {
                "cancel": ".block-no-drag"
            },
            "margin": 8,
            "acceptWidgets": true,
            "children": [
                {
                    "id": "6f8c09fa-1b7e-4c41-aaec-4538f3ff34a6",
                    "x": 0,
                    "y": 0,
                    "w": 12,
                    "h": 4,
                    "content": "{\"id\":\"6f8c09fa-1b7e-4c41-aaec-4538f3ff34a6\",\"key\":\"content_block_list\",\"renderType\":\"LIST\",\"blockType\":\"CONTENT\"}"
                },
                {
                    "id": "7885d99e-0ef7-4077-9b83-f1fb75cda3ab",
                    "x": 0,
                    "y": 4,
                    "w": 12,
                    "h": 4,
                    "subGridOpts": {
                        "resizable": {
                            "handles": "se, sw"
                        },
                        "draggable": {
                            "cancel": ".block-no-drag",
                            "pause": 200
                        },
                        "acceptWidgets": true,
                        "alwaysShowResizeHandle": false,
                        "layout": "list",
                        "class": "grid-stack-subgrid",
                        "margin": 8,
                        "cellHeight": 40,
                        "children": [
                            {
                                "id": "0b720c22-dfe0-46ad-b440-fc2ed39be364",
                                "x": 0,
                                "y": 0,
                                "w": 12,
                                "h": 4,
                                "content": "{\"id\":\"0b720c22-dfe0-46ad-b440-fc2ed39be364\",\"key\":\"note\",\"renderType\":\"COMPONENT\",\"blockType\":\"CONTENT\"}"
                            },
                            {
                                "id": "373bc593-7e21-4877-a34f-e967085062a1",
                                "x": 0,
                                "y": 4,
                                "w": 12,
                                "h": 4,
                                "content": "{\"id\":\"373bc593-7e21-4877-a34f-e967085062a1\",\"key\":\"note\",\"renderType\":\"COMPONENT\",\"blockType\":\"CONTENT\"}"
                            }
                        ]
                    },
                    "content": "{\"id\":\"7885d99e-0ef7-4077-9b83-f1fb75cda3ab\",\"key\":\"layout_container\",\"renderType\":\"CONTAINER\",\"blockType\":\"CONTENT\"}"
                }
            ]
        }
        """.trimIndent()

        // Deserialize the payload into our TreeLayout model
        val treeLayout: TreeLayout = objectMapper.readValue(gridstackPayload)

        // Validate top-level grid options
        assertNotNull(treeLayout.resizable)
        assertEquals("se, sw", treeLayout.resizable?.handles)

        assertNotNull(treeLayout.draggable)
        assertEquals(".block-no-drag", treeLayout.draggable?.cancel)

        assertEquals(8, treeLayout.margin)
        assertEquals(true, treeLayout.acceptWidgets)

        // Validate children
        assertNotNull(treeLayout.children)
        assertEquals(2, treeLayout.children?.size)

        // Validate first child (simple block list)
        val firstChild = treeLayout.children?.get(0)
        assertNotNull(firstChild)
        assertEquals("6f8c09fa-1b7e-4c41-aaec-4538f3ff34a6", firstChild?.id)
        assertEquals(0, firstChild?.x)
        assertEquals(0, firstChild?.y)
        assertEquals(12, firstChild?.w)
        assertEquals(4, firstChild?.h)
        assertTrue(firstChild?.content?.key.equals("content_block_list"))
        assertNull(firstChild?.subGridOpts) // No nested grid

        // Validate second child (nested container with sub-grid)
        val secondChild = treeLayout.children?.get(1)
        assertNotNull(secondChild)
        assertEquals("7885d99e-0ef7-4077-9b83-f1fb75cda3ab", secondChild?.id)
        assertEquals(0, secondChild?.x)
        assertEquals(4, secondChild?.y)
        assertEquals(12, secondChild?.w)
        assertEquals(4, secondChild?.h)
        assertTrue(secondChild?.content?.key.equals("layout_container"))

        // Validate nested sub-grid options
        val subGrid = secondChild?.subGridOpts
        assertNotNull(subGrid)
        assertEquals("se, sw", subGrid?.resizable?.handles)
        assertEquals(".block-no-drag", subGrid?.draggable?.cancel)
        assertEquals(200, subGrid?.draggable?.pause)
        assertEquals(true, subGrid?.acceptWidgets)
        assertEquals(false, subGrid?.alwaysShowResizeHandle)
        assertEquals("list", subGrid?.layout)
        assertEquals("grid-stack-subgrid", subGrid?.`class`)
        assertEquals(8, subGrid?.margin)
        assertEquals(40, subGrid?.cellHeight)

        // Validate nested children
        val nestedChildren = subGrid?.children
        assertNotNull(nestedChildren)
        assertEquals(2, nestedChildren?.size)

        // Validate first nested child
        val firstNestedChild = nestedChildren?.get(0)
        assertEquals("0b720c22-dfe0-46ad-b440-fc2ed39be364", firstNestedChild?.id)
        assertEquals(0, firstNestedChild?.x)
        assertEquals(0, firstNestedChild?.y)
        assertEquals(12, firstNestedChild?.w)
        assertEquals(4, firstNestedChild?.h)

        // Validate second nested child
        val secondNestedChild = nestedChildren?.get(1)
        assertEquals("373bc593-7e21-4877-a34f-e967085062a1", secondNestedChild?.id)
        assertEquals(0, secondNestedChild?.x)
        assertEquals(4, secondNestedChild?.y)
        assertEquals(12, secondNestedChild?.w)
        assertEquals(4, secondNestedChild?.h)
    }

    /**
     * Test serialization to ensure round-trip compatibility
     * This ensures that TreeLayout can be saved to DB and loaded back correctly
     */
    @Test
    fun `should serialize and deserialize TreeLayout maintaining data integrity`() {
        // Create a complex TreeLayout programmatically
        val widget1Id = UUID.randomUUID().toString()
        val widget2Id = UUID.randomUUID().toString()
        val nestedWidgetId = UUID.randomUUID().toString()

        val originalLayout = TreeLayout(
            resizable = ResizableOptions(handles = "se, sw", autoHide = true),
            draggable = DraggableOptions(cancel = ".no-drag", pause = 100),
            margin = 10,
            acceptWidgets = true,
            cellHeight = 50,
            layout = "list",
            children = listOf(
                Widget(
                    id = widget1Id,
                    x = 0,
                    y = 0,
                    w = 6,
                    h = 3,
                    content = RenderContent(
                        id = widget1Id,
                        key = "widget_content_1",
                        renderType = RenderType.COMPONENT,
                        blockType = NodeType.CONTENT
                    ),
                    locked = false
                ),
                Widget(
                    id = widget2Id,
                    x = 6,
                    y = 0,
                    w = 6,
                    h = 3,
                    content = RenderContent(
                        id = widget2Id,
                        key = "widget_content_2",
                        renderType = RenderType.CONTAINER,
                        blockType = NodeType.CONTENT
                    ),
                    subGridOpts = TreeLayout(
                        margin = 5,
                        children = listOf(
                            Widget(
                                id = nestedWidgetId,
                                x = 0,
                                y = 0,
                                w = 12,
                                h = 2,
                                content = RenderContent(
                                    id = nestedWidgetId,
                                    key = "nested_widget_1",
                                    renderType = RenderType.COMPONENT,
                                    blockType = NodeType.CONTENT
                                )
                            )
                        )
                    )
                )
            )
        )

        // Serialize to JSON (simulating save to database)
        val json = objectMapper.writeValueAsString(originalLayout)
        assertNotNull(json)
        assertTrue(json.contains("widget_content_1"))
        assertTrue(json.contains("nested_widget_1"))

        // Deserialize back (simulating load from database)
        val deserializedLayout: TreeLayout = objectMapper.readValue(json)

        // Verify data integrity
        assertEquals(originalLayout.margin, deserializedLayout.margin)
        assertEquals(originalLayout.acceptWidgets, deserializedLayout.acceptWidgets)
        assertEquals(originalLayout.cellHeight, deserializedLayout.cellHeight)
        assertEquals(originalLayout.layout, deserializedLayout.layout)

        assertEquals(originalLayout.resizable?.handles, deserializedLayout.resizable?.handles)
        assertEquals(originalLayout.draggable?.cancel, deserializedLayout.draggable?.cancel)

        assertEquals(2, deserializedLayout.children?.size)

        val firstChild = deserializedLayout.children?.get(0)
        assertEquals(widget1Id, firstChild?.id)
        assertEquals(6, firstChild?.w)
        assertEquals("widget_content_1", firstChild?.content?.key)

        val secondChild = deserializedLayout.children?.get(1)
        assertEquals(widget2Id, secondChild?.id)
        assertNotNull(secondChild?.subGridOpts)
        assertEquals(1, secondChild?.subGridOpts?.children?.size)
        assertEquals(nestedWidgetId, secondChild?.subGridOpts?.children?.get(0)?.id)
        assertEquals("nested_widget_1", secondChild?.subGridOpts?.children?.get(0)?.content?.key)
    }

    /**
     * Test that null values are properly handled (important for optional Gridstack options)
     */
    @Test
    fun `should handle minimal layout with only required fields`() {
        val minimalPayload = """
        {
            "children": [
                {
                    "id": "simple-widget",
                    "x": 0,
                    "y": 0,
                    "w": 12,
                    "h": 4
                }
            ]
        }
        """.trimIndent()

        val layout: TreeLayout = objectMapper.readValue(minimalPayload)

        assertNull(layout.resizable)
        assertNull(layout.draggable)
        assertNull(layout.margin)
        assertNotNull(layout.children)
        assertEquals(1, layout.children?.size)
        assertEquals("simple-widget", layout.children?.get(0)?.id)
    }

    /**
     * Test edge case: deeply nested layouts (3+ levels)
     */
    @Test
    fun `should handle deeply nested sub-grids`() {
        val deeplyNestedPayload = """
        {
            "children": [
                {
                    "id": "level-1",
                    "x": 0,
                    "y": 0,
                    "w": 12,
                    "h": 10,
                    "subGridOpts": {
                        "children": [
                            {
                                "id": "level-2",
                                "x": 0,
                                "y": 0,
                                "w": 12,
                                "h": 8,
                                "subGridOpts": {
                                    "children": [
                                        {
                                            "id": "level-3",
                                            "x": 0,
                                            "y": 0,
                                            "w": 12,
                                            "h": 4
                                        }
                                    ]
                                }
                            }
                        ]
                    }
                }
            ]
        }
        """.trimIndent()

        val layout: TreeLayout = objectMapper.readValue(deeplyNestedPayload)

        val level1 = layout.children?.get(0)
        assertNotNull(level1)
        assertEquals("level-1", level1?.id)

        val level2 = level1?.subGridOpts?.children?.get(0)
        assertNotNull(level2)
        assertEquals("level-2", level2?.id)

        val level3 = level2?.subGridOpts?.children?.get(0)
        assertNotNull(level3)
        assertEquals("level-3", level3?.id)
    }
}
