package com.addf.backend.armature.agent.agui;

/**
 * A hand-rolled subset of AG-UI's event envelope (https://docs.ag-ui.com), covering run
 * lifecycle, text message streaming, tool call visibility, and the generic CUSTOM escape
 * hatch we use to carry {@link com.addf.backend.armature.agent.AgentUiPart} data. No AG-UI
 * Java SDK is resolvable from Maven Central under any known candidate groupId (verified
 * directly against the Central search API), so these are plain records matching the spec's
 * JSON shape rather than a dependency.
 */
public sealed interface AgUiEvent
        permits RunStarted, RunFinished, RunError, TextMessageStart, TextMessageContent, TextMessageEnd,
        ToolCallStart, ToolCallArgs, ToolCallEnd, CustomEvent {
}
