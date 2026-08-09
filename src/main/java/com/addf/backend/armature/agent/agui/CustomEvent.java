package com.addf.backend.armature.agent.agui;

/**
 * AG-UI's generic escape hatch for app-specific payloads. We use it with
 * {@code name = "ui-part"} and {@code value} set to an
 * {@link com.addf.backend.armature.agent.AgentUiPart}, replacing what used to be
 * delivered as the {@code parts} array in a single up-front JSON response.
 */
public record CustomEvent(String type, String name, Object value) implements AgUiEvent {
    public CustomEvent(String name, Object value) {
        this("CUSTOM", name, value);
    }
}
