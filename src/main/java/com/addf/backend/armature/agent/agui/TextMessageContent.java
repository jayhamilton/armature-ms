package com.addf.backend.armature.agent.agui;

public record TextMessageContent(String type, String messageId, String delta) implements AgUiEvent {
    public TextMessageContent(String messageId, String delta) {
        this("TEXT_MESSAGE_CONTENT", messageId, delta);
    }
}
