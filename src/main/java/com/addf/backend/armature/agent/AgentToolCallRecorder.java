package com.addf.backend.armature.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Collects the tool calls and UI parts produced while {@link AgentToolRegistry}'s
 * {@code @Tool} methods run during a single {@code /api/agent/chat} request.
 * Request-scoped because Spring AI's ChatClient auto-executes tools internally
 * and only returns the model's final text, not the intermediate calls — the
 * tool methods record what they did as a side effect instead.
 */
@Component
@RequestScope
public class AgentToolCallRecorder {

    private final List<ToolCall> toolCalls = new ArrayList<>();
    private final List<AgentUiPart> parts = new ArrayList<>();
    private final AtomicInteger partIdSequence = new AtomicInteger(1);

    public void record(ToolCall toolCall, AgentUiPart part) {
        toolCalls.add(toolCall);
        parts.add(part);
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
