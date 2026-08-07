package com.addf.backend.ngxdd.agent;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

@Service
public class AgentService {

    private final AgentToolRegistry toolRegistry;

    public AgentService(AgentToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public AgentResponse chat(AgentRequest request) {
        String message = request.message().toLowerCase();
        String boardTitle = request.boardContext() != null && request.boardContext().boardTitle() != null
                ? request.boardContext().boardTitle()
                : "the active dashboard";

        // Checked before the add/create/chart branch below, since a message
        // like "move the bar chart right" contains "chart" and would
        // otherwise be misread as an add_gadget request.
        if (message.contains("move")) {
            Optional<String> direction = toolRegistry.resolveMoveDirection(message);
            if (direction.isPresent()) {
                String gadgetQuery = toolRegistry.extractMoveGadgetQuery(request.message());
                String payload = "{\"direction\":\"" + direction.get() + "\",\"gadgetQuery\":\"" + gadgetQuery + "\"}";
                return new AgentResponse(
                        "I can move " + (gadgetQuery.isBlank() ? "that gadget" : gadgetQuery) + " " + direction.get()
                                + " on " + boardTitle + ".",
                        List.of(new ToolCall("move_gadget", payload)),
                        List.of(
                                new AgentUiPart(1, "text", "I can move a gadget on the current board.", null, null),
                                new AgentUiPart(2, "component", null, "gadget-move", payload)
                        )
                );
            }
        }

        if (message.contains("add") || message.contains("create") || message.contains("chart")) {
            String gadgetComponentType = toolRegistry.resolveGadgetComponentType(message);
            return new AgentResponse(
                    "I can add a chart-style gadget to " + boardTitle + ".",
                    List.of(new ToolCall("add_gadget", "{\"type\":\"chart\"}")),
                    List.of(
                            new AgentUiPart(1, "text", "I can add a chart-style gadget to the current board.", null, null),
                            new AgentUiPart(2, "component", null, "gadget-suggestion",
                                    "{\"gadgetComponentType\":\"" + gadgetComponentType + "\"}")
                    )
            );
        }

        if (message.contains("board") || message.contains("boards")) {
            return new AgentResponse(
                    "I can list the available dashboards and help you switch context.",
                    List.of(new ToolCall("list_boards", "{}")),
                    List.of(
                            new AgentUiPart(1, "text", "I can help you inspect or switch the current dashboard.", null, null),
                            new AgentUiPart(2, "component", null, "board-list", "{}")
                    )
            );
        }

        return new AgentResponse(
                "I can help you inspect or modify the dashboard. Available tools: "
                        + toolRegistry.listTools().stream().map(ToolDefinition::name).toList(),
                List.of(),
                List.of(
                        new AgentUiPart(1, "text", "Ask me to create a board, add a gadget, or explain the current view.", null, null),
                        new AgentUiPart(2, "component", null, "a2ui-card", "{\"title\":\"Assistant ready\",\"summary\":\"The REST + A2UI chat flow is active.\"}")
                )
        );
    }
}
