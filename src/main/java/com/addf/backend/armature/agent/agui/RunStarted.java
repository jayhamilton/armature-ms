package com.addf.backend.armature.agent.agui;

public record RunStarted(String type, String threadId, String runId) implements AgUiEvent {
    public RunStarted(String threadId, String runId) {
        this("RUN_STARTED", threadId, runId);
    }
}
