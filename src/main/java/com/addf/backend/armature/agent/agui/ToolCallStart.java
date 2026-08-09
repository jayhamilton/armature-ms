package com.addf.backend.armature.agent.agui;

public record ToolCallStart(String type, String toolCallId, String toolCallName) implements AgUiEvent {
    public ToolCallStart(String toolCallId, String toolCallName) {
        this("TOOL_CALL_START", toolCallId, toolCallName);
    }
}
