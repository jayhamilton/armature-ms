package com.addf.backend.ngxdd.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class AgentToolRegistry {

    /**
     * Keyword -> real componentType from the frontend's GADGET_REGISTRY /
     * library.json, so add_gadget suggests a gadget the UI can actually render.
     */
    private static final Map<String, String> KEYWORD_TO_GADGET_COMPONENT_TYPE = new LinkedHashMap<>();
    static {
        KEYWORD_TO_GADGET_COMPONENT_TYPE.put("chart", "BarChartComponent");
        KEYWORD_TO_GADGET_COMPONENT_TYPE.put("table", "TableComponent");
        KEYWORD_TO_GADGET_COMPONENT_TYPE.put("number", "NumberCardComponent");
        KEYWORD_TO_GADGET_COMPONENT_TYPE.put("stat", "StatisticComponent");
        KEYWORD_TO_GADGET_COMPONENT_TYPE.put("text", "TextComponent");
    }

    public List<ToolDefinition> listTools() {
        return List.of(
                new ToolDefinition("list_boards", "List the available dashboards."),
                new ToolDefinition("add_gadget", "Add a gadget such as a chart or statistic to the current board.")
        );
    }

    /**
     * Maps a lowercased user message to a real gadget componentType, falling
     * back to a chart when a keyword match isn't found but a gadget was implied.
     */
    public String resolveGadgetComponentType(String lowerCaseMessage) {
        return KEYWORD_TO_GADGET_COMPONENT_TYPE.entrySet().stream()
                .filter(entry -> lowerCaseMessage.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("BarChartComponent");
    }
}
