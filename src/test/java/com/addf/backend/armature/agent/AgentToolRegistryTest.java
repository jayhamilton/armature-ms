package com.addf.backend.armature.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolRegistryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AgentToolCallRecorder recorder;
    private AgentToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        recorder = new AgentToolCallRecorder();
        toolRegistry = new AgentToolRegistry(recorder);
    }

    @Test
    void listBoardsRecordsBoardListPart() {
        toolRegistry.listBoards();

        assertThat(recorder.toolCalls()).hasSize(1);
        assertThat(recorder.toolCalls().get(0).name()).isEqualTo("list_boards");
        assertThat(recorder.parts()).hasSize(1);
        assertThat(recorder.parts().get(0).componentType()).isEqualTo("board-list");
    }

    @Test
    void addGadgetRecordsGadgetSuggestionForValidComponentType() throws Exception {
        toolRegistry.addGadget("TableComponent");

        ToolCall call = recorder.toolCalls().get(0);
        assertThat(call.name()).isEqualTo("add_gadget");

        AgentUiPart part = recorder.parts().get(0);
        assertThat(part.componentType()).isEqualTo("gadget-suggestion");
        JsonNode payload = objectMapper.readTree(part.payload());
        assertThat(payload.get("gadgetComponentType").asText()).isEqualTo("TableComponent");
    }

    @Test
    void addGadgetPassesComponentTypeThroughVerbatim() throws Exception {
        toolRegistry.addGadget("PieChartComponent");

        AgentUiPart part = recorder.parts().get(0);
        JsonNode payload = objectMapper.readTree(part.payload());
        assertThat(payload.get("gadgetComponentType").asText()).isEqualTo("PieChartComponent");
    }

    @Test
    void moveGadgetRecordsDirectionAndQuery() throws Exception {
        toolRegistry.moveGadget("bar chart", "left");

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
        toolRegistry.moveGadget("the \"sales\" chart", "up");

        AgentUiPart part = recorder.parts().get(0);
        JsonNode payload = objectMapper.readTree(part.payload());
        assertThat(payload.get("gadgetQuery").asText()).isEqualTo("the \"sales\" chart");
    }

    @Test
    void removeGadgetRecordsGadgetRemovePart() throws Exception {
        toolRegistry.removeGadget("bar chart");

        ToolCall call = recorder.toolCalls().get(0);
        assertThat(call.name()).isEqualTo("remove_gadget");

        AgentUiPart part = recorder.parts().get(0);
        assertThat(part.componentType()).isEqualTo("gadget-remove");
        JsonNode payload = objectMapper.readTree(part.payload());
        assertThat(payload.get("gadgetQuery").asText()).isEqualTo("bar chart");
    }

    @Test
    void addRowRecordsRowAddPart() {
        toolRegistry.addRow();

        assertThat(recorder.toolCalls()).hasSize(1);
        assertThat(recorder.toolCalls().get(0).name()).isEqualTo("add_row");
        assertThat(recorder.parts()).hasSize(1);
        assertThat(recorder.parts().get(0).componentType()).isEqualTo("row-add");
    }

    @Test
    void changeRowLayoutRecordsRowIndexAndStructure() throws Exception {
        toolRegistry.changeRowLayout(1, "three_col_equal");

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
        toolRegistry.changeRowLayout(0, "five_col_rainbow");

        AgentUiPart part = recorder.parts().get(0);
        JsonNode payload = objectMapper.readTree(part.payload());
        assertThat(payload.get("structure").asText()).isEqualTo("two_col_equal");
    }
}
