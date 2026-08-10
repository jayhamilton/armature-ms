package com.addf.backend.armature.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The dashboard assistant's tools, callable by whatever ChatModel is
 * configured and, once registered as a bean, visible over MCP. The backend
 * has no access to real board/gadget state — that lives in the browser's
 * localStorage — so each tool just records the intent (componentType,
 * direction, gadget query) as a {@link ToolCall}/{@link AgentUiPart} pair for
 * the frontend to resolve against the real board.
 *
 * <p>Each tool method takes a {@link ToolContext} to reach the current
 * request's {@link AgentToolCallRecorder}, rather than having it injected as
 * a constructor field. This is a singleton bean, and since
 * {@link AgentService#chatStream} streams the reply via {@code ChatClient}'s
 * reactive {@code .stream()}, Spring AI can invoke these methods on a Reactor
 * worker thread that has no servlet request bound to it - an
 * {@code @RequestScope}-backed recorder (ThreadLocal-based) would fail with
 * {@code ScopeNotActiveException} there. {@code ToolContext} carries a plain
 * object reference through Spring AI's own call stack instead, so it works
 * regardless of which thread ends up invoking the tool.
 *
 * <p>Every method checks {@link AgentToolCallRecorder#canRecord()} first and
 * bails out with {@link #ALREADY_ACTED_MESSAGE} if another tool already acted
 * this request - see that method's javadoc for why.
 */
@Component
public class AgentToolRegistry {

    public static final String RECORDER_KEY = "agentToolCallRecorder";

    private static final String ALREADY_ACTED_MESSAGE = "Not doing this - only one board change is allowed "
            + "per user message, and one was already made this turn. Tell the user to ask again in a "
            + "separate message if they want this too.";

    private static final Set<String> VALID_DIRECTIONS = Set.of("left", "right", "up", "down");

    private static final Set<String> VALID_LAYOUT_STRUCTURES = Set.of(
            "one_col", "one_col_full", "two_col_equal", "two_col_narrow_wide", "two_col_wide_narrow", "three_col_equal"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool(name = "list_boards", description = "List the available dashboards so the user can inspect or switch between them.")
    public String listBoards(ToolContext toolContext) {
        AgentToolCallRecorder recorder = recorder(toolContext);
        if (!recorder.canRecord()) {
            return ALREADY_ACTED_MESSAGE;
        }
        recorder.record(
                new ToolCall("list_boards", "{}"),
                new AgentUiPart(recorder.nextPartId(), "component", null, "board-list", "{}")
        );
        return "Showing the list of available boards.";
    }

    @Tool(name = "add_gadget", description = "Add a gadget to the current board.")
    public String addGadget(
            @ToolParam(description = "The gadget component type to add, chosen from the available gadget "
                    + "types listed in the system prompt.")
            String componentType,
            ToolContext toolContext
    ) {
        AgentToolCallRecorder recorder = recorder(toolContext);
        if (!recorder.canRecord()) {
            return ALREADY_ACTED_MESSAGE;
        }
        String payload = toJson(Map.of("gadgetComponentType", componentType));
        recorder.record(
                new ToolCall("add_gadget", toJson(Map.of("type", componentType))),
                new AgentUiPart(recorder.nextPartId(), "component", null, "gadget-suggestion", payload)
        );
        return "Suggested adding a " + componentType + " gadget to the board.";
    }

    @Tool(name = "move_gadget", description = "Move a named gadget left, right, up, or down on the current board.")
    public String moveGadget(
            @ToolParam(description = "The gadget's name or approximate title, as the user referred to it.")
            String gadgetQuery,
            @ToolParam(description = "The direction to move the gadget. Must be one of: left, right, up, down.")
            String direction,
            ToolContext toolContext
    ) {
        AgentToolCallRecorder recorder = recorder(toolContext);
        if (!recorder.canRecord()) {
            return ALREADY_ACTED_MESSAGE;
        }
        String resolvedDirection = VALID_DIRECTIONS.contains(direction) ? direction : "right";
        Map<String, Object> payloadValues = new LinkedHashMap<>();
        payloadValues.put("direction", resolvedDirection);
        payloadValues.put("gadgetQuery", gadgetQuery);
        String payload = toJson(payloadValues);
        recorder.record(
                new ToolCall("move_gadget", payload),
                new AgentUiPart(recorder.nextPartId(), "component", null, "gadget-move", payload)
        );
        return "Moving " + gadgetQuery + " " + resolvedDirection + ".";
    }

    @Tool(name = "remove_gadget", description = "Remove a named gadget from the current board.")
    public String removeGadget(
            @ToolParam(description = "The gadget's name or approximate title, as the user referred to it.")
            String gadgetQuery,
            ToolContext toolContext
    ) {
        AgentToolCallRecorder recorder = recorder(toolContext);
        if (!recorder.canRecord()) {
            return ALREADY_ACTED_MESSAGE;
        }
        String payload = toJson(Map.of("gadgetQuery", gadgetQuery));
        recorder.record(
                new ToolCall("remove_gadget", payload),
                new AgentUiPart(recorder.nextPartId(), "component", null, "gadget-remove", payload)
        );
        return "Removing " + gadgetQuery + ".";
    }

    @Tool(name = "add_row", description = "Add a new empty row to the current board, so gadgets can be placed into it.")
    public String addRow(ToolContext toolContext) {
        AgentToolCallRecorder recorder = recorder(toolContext);
        if (!recorder.canRecord()) {
            return ALREADY_ACTED_MESSAGE;
        }
        recorder.record(
                new ToolCall("add_row", "{}"),
                new AgentUiPart(recorder.nextPartId(), "component", null, "row-add", "{}")
        );
        return "Adding a new row to the board.";
    }

    @Tool(name = "change_row_layout", description = "Change the column layout of a specific row on the current board.")
    public String changeRowLayout(
            @ToolParam(description = "The zero-based index of the row to change (0 is the first row).")
            Integer rowIndex,
            @ToolParam(description = "The column layout. Must be one of: one_col, one_col_full, two_col_equal, "
                    + "two_col_narrow_wide, two_col_wide_narrow, three_col_equal.")
            String structure,
            ToolContext toolContext
    ) {
        AgentToolCallRecorder recorder = recorder(toolContext);
        if (!recorder.canRecord()) {
            return ALREADY_ACTED_MESSAGE;
        }
        String resolvedStructure = VALID_LAYOUT_STRUCTURES.contains(structure) ? structure : "two_col_equal";
        Map<String, Object> payloadValues = new LinkedHashMap<>();
        payloadValues.put("rowIndex", rowIndex);
        payloadValues.put("structure", resolvedStructure);
        String payload = toJson(payloadValues);
        recorder.record(
                new ToolCall("change_row_layout", payload),
                new AgentUiPart(recorder.nextPartId(), "component", null, "row-layout", payload)
        );
        return "Changing row " + rowIndex + " to " + resolvedStructure + ".";
    }

    private AgentToolCallRecorder recorder(ToolContext toolContext) {
        return (AgentToolCallRecorder) toolContext.getContext().get(RECORDER_KEY);
    }

    private String toJson(Map<String, Object> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode tool payload " + values, e);
        }
    }
}
