package com.addf.backend.armature.agent.agui;

public record RunError(String type, String message) implements AgUiEvent {
    public RunError(String message) {
        this("RUN_ERROR", message);
    }
}
