package com.addf.backend.ngxdd.agent;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin
public class AgentController {

    private final AgentService agentService = new AgentService();

    @PostMapping("/api/agent/chat")
    public AgentResponse chat(@RequestBody AgentRequest request) {
        return agentService.chat(request);
    }
}
