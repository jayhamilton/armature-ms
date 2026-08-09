package com.addf.backend.armature.agent.agui;

public record TextMessageStart(String type, String messageId) implements AgUiEvent {
    public TextMessageStart(String messageId) {
        this("TEXT_MESSAGE_START", messageId);
    }
}
