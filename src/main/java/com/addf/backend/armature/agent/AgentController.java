package com.addf.backend.armature.agent;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin
@Tag(name = "Assistant", description = "Conversational dashboard assistant (REST + A2UI chat contract)")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @Operation(
            summary = "Send a chat message to the dashboard assistant",
            description = "Returns a reply plus structured UI parts (text/component/iframe) and any tool "
                    + "calls (e.g. add_gadget, list_boards) implied by the message."
    )
    @PostMapping("/api/agent/chat")
    public AgentResponse chat(@RequestBody AgentRequest request) {
        return agentService.chat(request);
    }
}
