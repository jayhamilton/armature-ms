package com.addf.backend.armature.agent;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * Keyword -> canonical move direction. The frontend does the real gadget
     * lookup against the current board, so this only needs to recognize intent.
     */
    private static final Map<String, String> KEYWORD_TO_DIRECTION = new LinkedHashMap<>();
    static {
        KEYWORD_TO_DIRECTION.put("left", "left");
        KEYWORD_TO_DIRECTION.put("right", "right");
        KEYWORD_TO_DIRECTION.put("up", "up");
        KEYWORD_TO_DIRECTION.put("higher", "up");
        KEYWORD_TO_DIRECTION.put("top", "up");
        KEYWORD_TO_DIRECTION.put("down", "down");
        KEYWORD_TO_DIRECTION.put("lower", "down");
        KEYWORD_TO_DIRECTION.put("bottom", "down");
    }

    /**
     * Stripped out of a "move" message to leave (approximately) the gadget's
     * name behind — the frontend matches whatever remains against the real
     * board's gadget titles, so this only needs to be roughly right.
     */
    private static final Set<String> MOVE_QUERY_STOPWORDS = new LinkedHashSet<>(Arrays.asList(
            "move", "the", "to", "please", "gadget", "widget", "a", "an",
            "left", "right", "up", "down", "higher", "lower", "top", "bottom"
    ));

    public List<ToolDefinition> listTools() {
        return List.of(
                new ToolDefinition("list_boards", "List the available dashboards."),
                new ToolDefinition("add_gadget", "Add a gadget such as a chart or statistic to the current board."),
                new ToolDefinition("move_gadget", "Move a named gadget left, right, up, or down on the current board.")
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

    public Optional<String> resolveMoveDirection(String lowerCaseMessage) {
        return KEYWORD_TO_DIRECTION.entrySet().stream()
                .filter(entry -> lowerCaseMessage.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    /**
     * Approximates the gadget's name by stripping move/direction stopwords
     * out of the original message, e.g. "move the bar chart to the right"
     * -> "bar chart".
     */
    public String extractMoveGadgetQuery(String message) {
        return Arrays.stream(message.split("\\W+"))
                .filter(word -> !word.isBlank() && !MOVE_QUERY_STOPWORDS.contains(word.toLowerCase()))
                .collect(Collectors.joining(" "));
    }
}
