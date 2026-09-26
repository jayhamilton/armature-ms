package com.addf.backend.armature.mcpapp;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

// The shape ChartsApp#presentChart elicits from the calling client before
// rendering - see that method for why this is asked via MCP elicitation
// (ui/elicit) rather than baked into the tool's own input schema.
public record ChartPresentationChoice(
        @JsonPropertyDescription("Where the chart should be presented: DASHBOARD_GADGET to pin it to the "
                + "Armature board as a gadget, or CHAT_RESPONSE to show it only in this conversation.")
        Presentation presentation
) {
    public enum Presentation {
        DASHBOARD_GADGET,
        CHAT_RESPONSE
    }
}
