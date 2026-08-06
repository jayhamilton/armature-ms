package com.addf.backend.ngxdd.agent;

import java.util.List;

public class AgentToolRegistry {

    public List<ToolDefinition> listTools() {
        return List.of(
                new ToolDefinition("list_boards", "List the available dashboards."),
                new ToolDefinition("add_gadget", "Add a gadget such as a chart or statistic to the current board.")
        );
    }
}
