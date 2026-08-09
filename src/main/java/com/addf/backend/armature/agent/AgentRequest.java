package com.addf.backend.armature.agent;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A chat message sent to the dashboard assistant.")
public record AgentRequest(
        @Schema(description = "The user's message.", example = "Add a chart to the dashboard")
        String message,
        @Schema(description = "The dashboard the user currently has open, if any.")
        AgentBoardContext boardContext,
        @Schema(description = "The frontend's gadget library (from library.json), so the assistant can "
                + "ground add_gadget suggestions in what's actually available. Omitted or empty for "
                + "non-UI callers.")
        List<GadgetLibraryEntry> gadgetLibrary
) {
}
