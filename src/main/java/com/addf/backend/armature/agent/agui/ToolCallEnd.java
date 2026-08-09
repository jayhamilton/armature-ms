package com.addf.backend.armature.agent.agui;

public record ToolCallEnd(String type, String toolCallId) implements AgUiEvent {
    public ToolCallEnd(String toolCallId) {
        this("TOOL_CALL_END", toolCallId);
    }
}
