package com.addf.backend.ngxdd.agent;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A chat message sent to the dashboard assistant.")
public record AgentRequest(
        @Schema(description = "The user's message.", example = "Add a chart to the dashboard")
        String message,
        @Schema(description = "The dashboard the user currently has open, if any.")
        AgentBoardContext boardContext
) {
}
