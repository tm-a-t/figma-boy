package me.tmat.figmaboy.mcp

import dev.vanutp.hack25.compare
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.ImageContent
import io.modelcontextprotocol.kotlin.sdk.PromptMessageContent
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.*
import kotlinx.serialization.json.jsonPrimitive
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import java.io.File
import kotlin.io.encoding.Base64

/** --- JSON Schema helpers (супер-короткие) --- */
private fun jStr() = buildJsonObject { put("type", JsonPrimitive("string")) }
private fun jNum() = buildJsonObject { put("type", JsonPrimitive("number")) }
private fun jBool() = buildJsonObject { put("type", JsonPrimitive("boolean")) }
private fun jEnum(vararg values: String) = buildJsonObject {
    put("type", JsonPrimitive("string"))
    put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
}
private fun jObj(props: JsonObject) = buildJsonObject {
    put("type", JsonPrimitive("object"))
    put("properties", props)
}
private fun jArr(items: JsonObject) = buildJsonObject {
    put("type", JsonPrimitive("array"))
    put("items", items)
}

/** --- Общие вложенные типы --- */
private val rgbaSchema = jObj(buildJsonObject {
    put("r", jNum()); put("g", jNum()); put("b", jNum()); put("a", jNum())
})

/** --- Описание MCP tools --- */
private val figmaTools = listOf(
    Tool(
        name = "get_document_info",
        title = "Get Document Info",
        description = "Get detailed information about the current Figma document",
        inputSchema = Tool.Input(), outputSchema = Tool.Output(), annotations = null
    ),
    Tool(
        name = "get_selection",
        title = "Get Selection",
        description = "Get information about the current selection in Figma",
        inputSchema = Tool.Input(), outputSchema = Tool.Output(), annotations = null
    ),
    Tool(
        name = "read_my_design",
        title = "Read My Design",
        description = "Get detailed information about the current selection, including all node details",
        inputSchema = Tool.Input(), outputSchema = Tool.Output(), annotations = null
    ),
    Tool(
        name = "get_node_info",
        title = "Get Node Info",
        description = "Get detailed information about a specific node in Figma",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeId", jStr())
        }),
        outputSchema = Tool.Output(), annotations = null
    ),
    Tool(
        name = "get_nodes_info",
        title = "Get Multiple Nodes Info",
        description = "Get detailed information about multiple nodes",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeIds", jArr(jStr()))
        }),
        outputSchema = Tool.Output(), annotations = null
    ),

    // Create rectangle
    Tool(
        name = "create_rectangle",
        title = "Create Rectangle",
        description = "Create a new rectangle in Figma",
        inputSchema = Tool.Input(buildJsonObject {
            put("x", jNum()); put("y", jNum())
            put("width", jNum()); put("height", jNum())
            put("name", jStr())
            put("parentId", jStr())
        }),
        outputSchema = Tool.Output(), annotations = null
    ),

    // Create frame
    Tool(
        name = "create_frame",
        title = "Create Frame",
        description = "Create a new frame in Figma (supports auto-layout props)",
        inputSchema = Tool.Input(buildJsonObject {
            put("x", jNum()); put("y", jNum())
            put("width", jNum()); put("height", jNum())
            put("name", jStr()); put("parentId", jStr())
            put("fillColor", rgbaSchema)
            put("strokeColor", rgbaSchema)
            put("strokeWeight", jNum())
            put("layoutMode", jEnum("NONE", "HORIZONTAL", "VERTICAL"))
            put("layoutWrap", jEnum("NO_WRAP", "WRAP"))
            put("paddingTop", jNum()); put("paddingRight", jNum()); put("paddingBottom", jNum()); put("paddingLeft", jNum())
            put("primaryAxisAlignItems", jEnum("MIN","MAX","CENTER","SPACE_BETWEEN"))
            put("counterAxisAlignItems", jEnum("MIN","MAX","CENTER","BASELINE"))
            put("layoutSizingHorizontal", jEnum("FIXED","HUG","FILL"))
            put("layoutSizingVertical", jEnum("FIXED","HUG","FILL"))
            put("itemSpacing", jNum())
        }),
        outputSchema = Tool.Output(), annotations = null
    ),

    // Create text
    Tool(
        name = "create_text",
        title = "Create Text",
        description = "Create a new text node",
        inputSchema = Tool.Input(buildJsonObject {
            put("x", jNum()); put("y", jNum())
            put("text", jStr())
            put("fontSize", jNum()); put("fontWeight", jNum())
            put("fontColor", rgbaSchema)
            put("name", jStr())
            put("parentId", jStr())
        }),
        outputSchema = Tool.Output(), annotations = null
    ),

    // Styling
    Tool(
        name = "set_fill_color",
        title = "Set Fill Color",
        description = "Set the fill color of a node",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeId", jStr())
            put("r", jNum()); put("g", jNum()); put("b", jNum()); put("a", jNum())
        }),
        outputSchema = Tool.Output(), annotations = null
    ),
    Tool(
        name = "set_stroke_color",
        title = "Set Stroke Color",
        description = "Set the stroke color (and optional weight) of a node",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeId", jStr())
            put("r", jNum()); put("g", jNum()); put("b", jNum()); put("a", jNum())
            put("weight", jNum())
        }),
        outputSchema = Tool.Output(), annotations = null
    ),

    // Transform
    Tool(
        name = "move_node",
        title = "Move Node",
        description = "Move a node to a new (x, y)",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeId", jStr()); put("x", jNum()); put("y", jNum())
        }),
        outputSchema = Tool.Output(), annotations = null
    ),
    Tool(
        name = "resize_node",
        title = "Resize Node",
        description = "Resize a node to (width, height)",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeId", jStr()); put("width", jNum()); put("height", jNum())
        }),
        outputSchema = Tool.Output(), annotations = null
    ),
    Tool(
        name = "clone_node",
        title = "Clone Node",
        description = "Clone an existing node (optional new position)",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeId", jStr()); put("x", jNum()); put("y", jNum())
        }),
        outputSchema = Tool.Output(), annotations = null
    ),

    // Delete
    Tool(
        name = "delete_node",
        title = "Delete Node",
        description = "Delete a single node",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeId", jStr())
        }),
        outputSchema = Tool.Output(), annotations = null
    ),
    Tool(
        name = "delete_multiple_nodes",
        title = "Delete Multiple Nodes",
        description = "Delete multiple nodes at once",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeIds", jArr(jStr()))
        }),
        outputSchema = Tool.Output(), annotations = null
    ),

    // Export
    Tool(
        name = "export_node_as_image",
        title = "Export Node as Image",
        description = "Export a node as PNG/JPG/SVG/PDF (base64 data)",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeId", jStr())
            put("format", jEnum("PNG","JPG","SVG","PDF"))
            put("scale", jNum())
        }),
        outputSchema = Tool.Output(), annotations = null
    ),

    // Text content
    Tool(
        name = "set_text_content",
        title = "Set Text Content",
        description = "Set the text content of a TEXT node",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeId", jStr()); put("text", jStr())
        }),
        outputSchema = Tool.Output(), annotations = null
    ),
    Tool(
        name = "set_multiple_text_contents",
        title = "Set Multiple Text Contents",
        description = "Replace text in many nodes (chunked)",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeId", jStr())
            put("text", jArr(jObj(buildJsonObject {
                put("nodeId", jStr()); put("text", jStr())
            })))
        }),
        outputSchema = Tool.Output(), annotations = null
    ),

    // Styles & components
    Tool(
        name = "get_styles",
        title = "Get Styles",
        description = "Get all local styles in the document",
        inputSchema = Tool.Input(), outputSchema = Tool.Output(), annotations = null
    ),
    Tool(
        name = "get_local_components",
        title = "Get Local Components",
        description = "Get all local components in the file",
        inputSchema = Tool.Input(), outputSchema = Tool.Output(), annotations = null
    ),
    Tool(
        name = "create_component_instance",
        title = "Create Component Instance",
        description = "Instantiate a library component by componentKey",
        inputSchema = Tool.Input(buildJsonObject {
            put("componentKey", jStr())
            put("x", jNum()); put("y", jNum())
        }),
        outputSchema = Tool.Output(), annotations = null
    ),
    Tool(
        name = "get_instance_overrides",
        title = "Get Instance Overrides",
        description = "Get override properties from a component instance",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeId", jStr())
        }),
        outputSchema = Tool.Output(), annotations = null
    ),
    Tool(
        name = "set_instance_overrides",
        title = "Set Instance Overrides",
        description = "Apply overrides from a source instance to target instances",
        inputSchema = Tool.Input(buildJsonObject {
            put("sourceInstanceId", jStr())
            put("targetNodeIds", jArr(jStr()))
        }),
        outputSchema = Tool.Output(), annotations = null
    ),

    // Corner radius
    Tool(
        name = "set_corner_radius",
        title = "Set Corner Radius",
        description = "Set uniform or per-corner radius",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeId", jStr())
            put("radius", jNum())
            put("corners", jArr(jBool())) // [topLeft, topRight, bottomRight, bottomLeft]
        }),
        outputSchema = Tool.Output(), annotations = null
    ),

    // Scanning
    Tool(
        name = "scan_text_nodes",
        title = "Scan Text Nodes",
        description = "Scan all text nodes under a node (chunked)",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeId", jStr())
        }),
        outputSchema = Tool.Output(), annotations = null
    ),
    Tool(
        name = "scan_nodes_by_types",
        title = "Scan Nodes by Types",
        description = "Find child nodes by types under a node",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeId", jStr())
            put("types", jArr(jStr()))
        }),
        outputSchema = Tool.Output(), annotations = null
    ),

    // Auto-layout controls
    Tool(
        name = "set_layout_mode",
        title = "Set Layout Mode",
        description = "Set layoutMode and layoutWrap for a frame",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeId", jStr())
            put("layoutMode", jEnum("NONE","HORIZONTAL","VERTICAL"))
            put("layoutWrap", jEnum("NO_WRAP","WRAP"))
        }),
        outputSchema = Tool.Output(), annotations = null
    ),
    Tool(
        name = "set_padding",
        title = "Set Padding",
        description = "Set padding for an auto-layout frame",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeId", jStr())
            put("paddingTop", jNum()); put("paddingRight", jNum())
            put("paddingBottom", jNum()); put("paddingLeft", jNum())
        }),
        outputSchema = Tool.Output(), annotations = null
    ),
    Tool(
        name = "set_axis_align",
        title = "Set Axis Align",
        description = "Set primary/counter axis alignment for an auto-layout frame",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeId", jStr())
            put("primaryAxisAlignItems", jEnum("MIN","MAX","CENTER","SPACE_BETWEEN"))
            put("counterAxisAlignItems", jEnum("MIN","MAX","CENTER","BASELINE"))
        }),
        outputSchema = Tool.Output(), annotations = null
    ),
    Tool(
        name = "set_layout_sizing",
        title = "Set Layout Sizing",
        description = "Set horizontal/vertical sizing modes",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeId", jStr())
            put("layoutSizingHorizontal", jEnum("FIXED","HUG","FILL"))
            put("layoutSizingVertical", jEnum("FIXED","HUG","FILL"))
        }),
        outputSchema = Tool.Output(), annotations = null
    ),
    Tool(
        name = "set_item_spacing",
        title = "Set Item Spacing",
        description = "Set spacing between children / wrapped rows/cols",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeId", jStr())
            put("itemSpacing", jNum())
            put("counterAxisSpacing", jNum())
        }),
        outputSchema = Tool.Output(), annotations = null
    ),

    // Reactions → connectors flow
    Tool(
        name = "get_reactions",
        title = "Get Reactions",
        description = "Get Figma prototype reactions from multiple nodes",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeIds", jArr(jStr()))
        }),
        outputSchema = Tool.Output(), annotations = null
    ),
    Tool(
        name = "set_default_connector",
        title = "Set Default Connector",
        description = "Set a copied connector as the default connector",
        inputSchema = Tool.Input(buildJsonObject {
            put("connectorId", jStr())
        }),
        outputSchema = Tool.Output(), annotations = null
    ),
    Tool(
        name = "create_connections",
        title = "Create Connections",
        description = "Create connections between nodes using the default connector style",
        inputSchema = Tool.Input(buildJsonObject {
            put("connections", jArr(
                jObj(buildJsonObject {
                    put("startNodeId", jStr())
                    put("endNodeId", jStr())
                    put("text", jStr())
                })
            ))
        }),
        outputSchema = Tool.Output(), annotations = null
    ),

    // Viewport focus/selection
    Tool(
        name = "set_focus",
        title = "Set Focus",
        description = "Select a node and scroll viewport to it",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeId", jStr())
        }),
        outputSchema = Tool.Output(), annotations = null
    ),
    Tool(
        name = "set_selections",
        title = "Set Selections",
        description = "Select multiple nodes and scroll viewport to show them",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeIds", jArr(jStr()))
        }),
        outputSchema = Tool.Output(), annotations = null
    ),

    // Annotations
    Tool(
        name = "get_annotations",
        title = "Get Annotations",
        description = "Get all annotations in the current document or specific node",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeId", jStr())
            put("includeCategories", jBool())
        }),
        outputSchema = Tool.Output(), annotations = null
    ),
    Tool(
        name = "set_annotation",
        title = "Set Annotation",
        description = "Create or update an annotation",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeId", jStr())
            put("annotationId", jStr())
            put("labelMarkdown", jStr())
            put("categoryId", jStr())
            put("properties", jArr(jObj(buildJsonObject { put("type", jStr()) })))
        }),
        outputSchema = Tool.Output(), annotations = null
    ),
    Tool(
        name = "set_multiple_annotations",
        title = "Set Multiple Annotations",
        description = "Set multiple annotations in parallel for elements of a node",
        inputSchema = Tool.Input(buildJsonObject {
            put("nodeId", jStr())
            put("annotations", jArr(
                jObj(buildJsonObject {
                    put("nodeId", jStr())
                    put("labelMarkdown", jStr())
                    put("categoryId", jStr())
                    put("annotationId", jStr())
                    put("properties", jArr(jObj(buildJsonObject { put("type", jStr()) })))
                })
            ))
        }),
        outputSchema = Tool.Output(), annotations = null
    )
)

val kekotool = RegisteredTool(
    Tool(
        name = "compare_screenshot_to_web_page",
        title = "Compare Screenshot to Web Page",
        description = "Compare a reference screenshot to a live web page and highlight differences",
        inputSchema = Tool.Input(
            buildJsonObject {
                put("referencePath", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Path to the reference screenshot image file"))
                })
                put("url", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("URL of the web page to capture and compare"))
                })
            }
        ),
        outputSchema = Tool.Output(),
        annotations = null,
    )
) { request ->
    val chromeBinaryPath = System.getenv("CHROME_BINARY")
    val chromeOptions = ChromeOptions()
//    chromeOptions.addArguments("--headless")
    if (chromeBinaryPath != null) {
        chromeOptions.setBinary(chromeBinaryPath)
    }
    val driver = ChromeDriver(chromeOptions)
    try {
        val referencePath = request.arguments["referencePath"]?.jsonPrimitive?.content
            ?: return@RegisteredTool CallToolResult(listOf(TextContent("Missing 'referencePath' argument")), isError = true)
        if (!File(referencePath).exists()) {
            return@RegisteredTool CallToolResult(listOf(TextContent("Reference file does not exist: $referencePath")), isError = true)
        }
        val url = request.arguments["url"]?.jsonPrimitive?.content
            ?: return@RegisteredTool CallToolResult(listOf(TextContent("Missing 'url' argument")), isError = true)
        val (imageBytes, diffPercentage) = compare(driver, referencePath, url)
        CallToolResult(
            listOf(
                ImageContent(
                    data = Base64.encode(imageBytes),
                    mimeType = "image/png",
                ),
                TextContent("Difference: %.2f%%".format(diffPercentage))
            ),
        )
    } finally {
        driver.quit()
    }
}

fun registerTools(hub: PluginHub) =
    figmaTools.map { tool ->
        RegisteredTool(tool) { request ->
            assert(hub.isConnected())

            val figmaPluginResponse = hub.callToolThroughPlugin(tool.name, request.arguments)
            if (figmaPluginResponse.ok) {
                CallToolResult(
                    listOf(TextContent("Executed the command. Result: ${json.encodeToString(figmaPluginResponse.result)}")),
                    buildJsonObject {
                        put("result", figmaPluginResponse.result ?: JsonNull)
                    }
                )
            } else {
                CallToolResult(
                    listOf(TextContent("Couldn't execute the command")),
                    isError = true,
                )
            }
        }
    } + listOf(kekotool)
