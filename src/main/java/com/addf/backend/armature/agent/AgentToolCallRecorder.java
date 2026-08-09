package com.addf.backend.armature.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.addf.backend.armature.agent.agui.AgUiEvent;
import com.addf.backend.armature.agent.agui.ToolCallArgs;
import com.addf.backend.armature.agent.agui.ToolCallEnd;
import com.addf.backend.armature.agent.agui.ToolCallStart;

/**
 * Collects the tool calls and UI parts produced while {@link AgentToolRegistry}'s
 * {@code @Tool} methods run during a single {@code /api/agent/chat} request.
 * Deliberately a plain object, not a Spring bean: {@link AgentService#chatStream}
 * creates one fresh instance per request and threads it through to the (singleton)
 * {@link AgentToolRegistry} via Spring AI's {@code ToolContext} rather than an
 * {@code @RequestScope} bean, since tool execution during real streaming can happen
 * on a Reactor worker thread with no servlet request bound to it - a
 * ThreadLocal-backed request scope fails there with {@code ScopeNotActiveException}.
 */
public class AgentToolCallRecorder {

    private final List<ToolCall> toolCalls = new ArrayList<>();
    private final List<AgentUiPart> parts = new ArrayList<>();
    private final AtomicInteger partIdSequence = new AtomicInteger(1);
    private final AtomicInteger toolCallIdSequence = new AtomicInteger(1);

    // Defaults to a no-op so tests and any caller that never streams still work.
    // AgentService sets this to push straight through the request's SseEmitter.
    private volatile Consumer<AgUiEvent> eventSink = event -> { };

    public void setEventSink(Consumer<AgUiEvent> eventSink) {
        this.eventSink = eventSink != null ? eventSink : event -> { };
    }

    /**
     * Records the tool call for the post-stream enrichment pass (unchanged) and
     * immediately fires the matching TOOL_CALL_START/ARGS/END events — this is
     * the real moment Spring AI invoked the tool mid-stream, not a simulation.
     */
    public void record(ToolCall toolCall, AgentUiPart part) {
        toolCalls.add(toolCall);
        parts.add(part);

        String toolCallId = "call-" + toolCallIdSequence.getAndIncrement();
        eventSink.accept(new ToolCallStart(toolCallId, toolCall.name()));
        eventSink.accept(new ToolCallArgs(toolCallId, toolCall.arguments()));
        eventSink.accept(new ToolCallEnd(toolCallId));
    }

    public int nextPartId() {
        return partIdSequence.getAndIncrement();
    }

    public List<ToolCall> toolCalls() {
        return List.copyOf(toolCalls);
    }

    public List<AgentUiPart> parts() {
        return List.copyOf(parts);
    }
}
