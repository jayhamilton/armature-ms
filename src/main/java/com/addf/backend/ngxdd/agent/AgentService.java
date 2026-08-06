package com.addf.backend.ngxdd.agent;

import java.util.List;

public class AgentService {

    private final AgentToolRegistry toolRegistry = new AgentToolRegistry();

    public AgentResponse chat(AgentRequest request) {
        String message = request.message().toLowerCase();

        if (message.contains("add") || message.contains("create") || message.contains("chart")) {
            return new AgentResponse(
                    "I can add a chart-style gadget to the active dashboard.",
                    List.of(new ToolCall("add_gadget", "{\"type\":\"chart\"}"))
            );
        }

        if (message.contains("board") || message.contains("boards")) {
            return new AgentResponse(
                    "I can list the available dashboards and help you switch context.",
                    List.of(new ToolCall("list_boards", "{}"))
            );
        }

        return new AgentResponse(
                "I can help you inspect or modify the dashboard. Available tools: "
                        + toolRegistry.listTools().stream().map(ToolDefinition::name).toList(),
                List.of()
        );
    }
}
