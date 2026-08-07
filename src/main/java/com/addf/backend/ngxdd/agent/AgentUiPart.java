package com.addf.backend.ngxdd.agent;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A structured piece of the assistant's UI-ready response.")
public record AgentUiPart(
        @Schema(description = "Part id, unique within the response.", example = "1") Integer id,
        @Schema(description = "Rendering kind.", example = "component", allowableValues = {"text", "component", "iframe"})
        String type,
        @Schema(description = "Plain text content, present when type is 'text'.") String text,
        @Schema(description = "Card kind, present when type is 'component'.", example = "gadget-suggestion",
                allowableValues = {"gadget-suggestion", "board-list", "a2ui-card"})
        String componentType,
        @Schema(description = "JSON-encoded payload for the given componentType.",
                example = "{\"gadgetComponentType\":\"BarChartComponent\"}")
        String payload
) {
}
