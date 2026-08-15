package com.addf.backend.armature.agent;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@CrossOrigin
@Tag(name = "Assistant", description = "Conversational dashboard assistant (AG-UI event-stream chat contract)")
public class AgentController {

    private final AgentService agentService;
    private final BoardSnapshotStore boardSnapshotStore;

    public AgentController(AgentService agentService, BoardSnapshotStore boardSnapshotStore) {
        this.agentService = agentService;
        this.boardSnapshotStore = boardSnapshotStore;
    }

    @Operation(
            summary = "Send a chat message to the dashboard assistant",
            description = "Streams a hand-rolled AG-UI event sequence (RUN_STARTED, TEXT_MESSAGE_*, "
                    + "TOOL_CALL_*, CUSTOM ui-part events, RUN_FINISHED) as text/event-stream, replacing the "
                    + "single up-front JSON response this endpoint used to return."
    )
    @PostMapping(value = "/api/agent/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody AgentRequest request) {
        boardSnapshotStore.update(request.boardContext(), request.boardGadgets());
        SseEmitter emitter = new SseEmitter(0L);
        agentService.chatStream(request, emitter);
        return emitter;
    }
}
