package com.addf.backend.armature.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.addf.backend.armature.agent.agui.AgUiEvent;
import com.addf.backend.armature.agent.agui.ToolCallArgs;
import com.addf.backend.armature.agent.agui.ToolCallEnd;
import com.addf.backend.armature.agent.agui.ToolCallStart;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolRegistryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AgentToolCallRecorder recorder;
    private AgentToolRegistry toolRegistry;
    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        recorder = new AgentToolCallRecorder();
        toolRegistry = new AgentToolRegistry();
        toolContext = new ToolContext(Map.of(AgentToolRegistry.RECORDER_KEY, recorder));
    }

    @Test
    void listBoardsRecordsBoardListPart() {
        toolRegistry.listBoards(toolContext);

        assertThat(recorder.toolCalls()).hasSize(1);
        assertThat(recorder.toolCalls().get(0).name()).isEqualTo("list_boards");
        assertThat(recorder.parts()).hasSize(1);
        assertThat(recorder.parts().get(0).componentType()).isEqualTo("board-list");
    }

    @Test
    void addGadgetRecordsGadgetSuggestionForValidComponentType() throws Exception {
        toolRegistry.addGadget("TableComponent", toolContext);

        ToolCall call = recorder.toolCalls().get(0);
        assertThat(call.name()).isEqualTo("add_gadget");

        AgentUiPart part = recorder.parts().get(0);
        assertThat(part.componentType()).isEqualTo("gadget-suggestion");
        JsonNode payload = objectMapper.readTree(part.payload());
        assertThat(payload.get("gadgetComponentType").asText()).isEqualTo("TableComponent");
    }

    @Test
    void addGadgetPassesComponentTypeThroughVerbatim() throws Exception {
        toolRegistry.addGadget("PieChartComponent", toolContext);

        AgentUiPart part = recorder.parts().get(0);
        JsonNode payload = objectMapper.readTree(part.payload());
        assertThat(payload.get("gadgetComponentType").asText()).isEqualTo("PieChartComponent");
    }

    @Test
    void addGadgetFiresToolCallEventsThroughEventSink() {
        List<AgUiEvent> events = new ArrayList<>();
        recorder.setEventSink(events::add);

        toolRegistry.addGadget("TableComponent", toolContext);

        assertThat(events).hasSize(3);
        assertThat(events.get(0)).isInstanceOf(ToolCallStart.class);
        assertThat(events.get(1)).isInstanceOf(ToolCallArgs.class);
        assertThat(events.get(2)).isInstanceOf(ToolCallEnd.class);
    }

    @Test
    void moveGadgetRecordsDirectionAndQuery() throws Exception {
        toolRegistry.moveGadget("bar chart", "left", toolContext);

        ToolCall call = recorder.toolCalls().get(0);
        assertThat(call.name()).isEqualTo("move_gadget");

        AgentUiPart part = recorder.parts().get(0);
        assertThat(part.componentType()).isEqualTo("gadget-move");
        JsonNode payload = objectMapper.readTree(part.payload());
        assertThat(payload.get("direction").asText()).isEqualTo("left");
        assertThat(payload.get("gadgetQuery").asText()).isEqualTo("bar chart");
    }

    @Test
    void moveGadgetPreservesQuotesInGadgetQueryWithoutBreakingJson() throws Exception {
        toolRegistry.moveGadget("the \"sales\" chart", "up", toolContext);

        AgentUiPart part = recorder.parts().get(0);
        JsonNode payload = objectMapper.readTree(part.payload());
        assertThat(payload.get("gadgetQuery").asText()).isEqualTo("the \"sales\" chart");
    }

    @Test
    void removeGadgetRecordsGadgetRemovePart() throws Exception {
        toolRegistry.removeGadget("bar chart", toolContext);

        ToolCall call = recorder.toolCalls().get(0);
        assertThat(call.name()).isEqualTo("remove_gadget");

        AgentUiPart part = recorder.parts().get(0);
        assertThat(part.componentType()).isEqualTo("gadget-remove");
        JsonNode payload = objectMapper.readTree(part.payload());
        assertThat(payload.get("gadgetQuery").asText()).isEqualTo("bar chart");
    }

    @Test
    void addRowRecordsRowAddPart() {
        toolRegistry.addRow(toolContext);

        assertThat(recorder.toolCalls()).hasSize(1);
        assertThat(recorder.toolCalls().get(0).name()).isEqualTo("add_row");
        assertThat(recorder.parts()).hasSize(1);
        assertThat(recorder.parts().get(0).componentType()).isEqualTo("row-add");
    }

    @Test
    void changeRowLayoutRecordsRowIndexAndStructure() throws Exception {
        toolRegistry.changeRowLayout(1, "three_col_equal", toolContext);

        ToolCall call = recorder.toolCalls().get(0);
        assertThat(call.name()).isEqualTo("change_row_layout");

        AgentUiPart part = recorder.parts().get(0);
        assertThat(part.componentType()).isEqualTo("row-layout");
        JsonNode payload = objectMapper.readTree(part.payload());
        assertThat(payload.get("rowIndex").asInt()).isEqualTo(1);
        assertThat(payload.get("structure").asText()).isEqualTo("three_col_equal");
    }

    @Test
    void changeRowLayoutFallsBackToTwoColEqualForUnknownStructure() throws Exception {
        toolRegistry.changeRowLayout(0, "five_col_rainbow", toolContext);

        AgentUiPart part = recorder.parts().get(0);
        JsonNode payload = objectMapper.readTree(part.payload());
        assertThat(payload.get("structure").asText()).isEqualTo("two_col_equal");
    }
}
