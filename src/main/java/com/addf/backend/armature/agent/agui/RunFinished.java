package com.addf.backend.armature.agent.agui;

public record RunFinished(String type, String threadId, String runId) implements AgUiEvent {
    public RunFinished(String threadId, String runId) {
        this("RUN_FINISHED", threadId, runId);
    }
}
