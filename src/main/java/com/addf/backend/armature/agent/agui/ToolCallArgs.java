package com.addf.backend.armature.agent.agui;

public record ToolCallArgs(String type, String toolCallId, String delta) implements AgUiEvent {
    public ToolCallArgs(String toolCallId, String delta) {
        this("TOOL_CALL_ARGS", toolCallId, delta);
    }
}
