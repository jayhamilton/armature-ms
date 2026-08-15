package com.addf.backend.armature.agent;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A structured piece of the assistant's UI-ready response.")
public record AgentUiPart(
        @Schema(description = "Part id, unique within the response.", example = "1") Integer id,
        @Schema(description = "Rendering kind.", example = "component",
                allowableValues = {"text", "component", "iframe", "mcp-app"})
        String type,
        @Schema(description = "Plain text content, present when type is 'text'.") String text,
        @Schema(description = "Card kind, present when type is 'component'.", example = "gadget-suggestion",
                allowableValues = {"gadget-suggestion", "board-list", "gadget-move", "gadget-remove", "row-add",
                        "row-layout", "a2ui-card"})
        String componentType,
        @Schema(description = "JSON-encoded payload. For 'component', shaped per componentType (e.g. "
                + "{\"gadgetComponentType\":\"BarChartComponent\"}). For 'mcp-app', "
                + "{\"toolName\":\"present_board_summary\"} - the frontend discovers the tool's ui:// "
                + "resource and fetches fresh data itself over MCP rather than having either embedded here.",
                example = "{\"gadgetComponentType\":\"BarChartComponent\"}")
        String payload
) {
}
