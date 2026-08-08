package com.addf.backend.armature.agent;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The assistant's reply to a chat message.")
public record AgentResponse(
        @Schema(description = "Plain-text reply shown as the assistant's chat bubble.")
        String message,
        @Schema(description = "Tool invocations implied by the message, e.g. add_gadget or list_boards.")
        List<ToolCall> toolCalls,
        @Schema(description = "Structured UI parts (text/component/iframe) the frontend renders as cards.")
        List<AgentUiPart> parts
) {
}
