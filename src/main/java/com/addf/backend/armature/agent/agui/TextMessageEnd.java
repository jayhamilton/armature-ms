package com.addf.backend.armature.agent.agui;

public record TextMessageEnd(String type, String messageId) implements AgUiEvent {
    public TextMessageEnd(String messageId) {
        this("TEXT_MESSAGE_END", messageId);
    }
}
