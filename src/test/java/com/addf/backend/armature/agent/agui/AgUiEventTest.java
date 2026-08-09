package com.addf.backend.armature.agent.agui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms each AgUiEvent record serializes with the exact AG-UI spec "type" string,
 * since callers never pass it themselves - it's fixed by each record's canonical
 * constructor and would silently drift if a constructor argument order changed.
 */
class AgUiEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void runStartedSerializesWithSpecType() throws Exception {
        assertType(new RunStarted("thread-1", "run-1"), "RUN_STARTED");
    }

    @Test
    void runFinishedSerializesWithSpecType() throws Exception {
        assertType(new RunFinished("thread-1", "run-1"), "RUN_FINISHED");
    }

    @Test
    void runErrorSerializesWithSpecType() throws Exception {
        assertType(new RunError("boom"), "RUN_ERROR");
    }

    @Test
    void textMessageStartSerializesWithSpecType() throws Exception {
        assertType(new TextMessageStart("msg-1"), "TEXT_MESSAGE_START");
    }

    @Test
    void textMessageContentSerializesWithSpecType() throws Exception {
        assertType(new TextMessageContent("msg-1", "hello"), "TEXT_MESSAGE_CONTENT");
    }

    @Test
    void textMessageEndSerializesWithSpecType() throws Exception {
        assertType(new TextMessageEnd("msg-1"), "TEXT_MESSAGE_END");
    }

    @Test
    void toolCallStartSerializesWithSpecType() throws Exception {
        assertType(new ToolCallStart("call-1", "add_gadget"), "TOOL_CALL_START");
    }

    @Test
    void toolCallArgsSerializesWithSpecType() throws Exception {
        assertType(new ToolCallArgs("call-1", "{}"), "TOOL_CALL_ARGS");
    }

    @Test
    void toolCallEndSerializesWithSpecType() throws Exception {
        assertType(new ToolCallEnd("call-1"), "TOOL_CALL_END");
    }

    @Test
    void customEventSerializesWithSpecType() throws Exception {
        assertType(new CustomEvent("ui-part", "{}"), "CUSTOM");
    }

    private void assertType(AgUiEvent event, String expectedType) throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(event));
        assertThat(json.path("type").asText()).isEqualTo(expectedType);
    }
}
