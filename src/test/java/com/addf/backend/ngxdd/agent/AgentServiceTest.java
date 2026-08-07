package com.addf.backend.ngxdd.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentServiceTest {

    private final AgentService agentService = new AgentService(new AgentToolRegistry());

    @Test
    void shouldReturnListBoardsToolForBoardQueries() {
        AgentResponse response = agentService.chat(new AgentRequest("list my boards", new AgentBoardContext(1L, "Main Board", "overview")));

        assertThat(response.message()).contains("boards");
        assertThat(response.toolCalls()).hasSize(1);
        assertThat(response.toolCalls().get(0).name()).isEqualTo("list_boards");
        assertThat(response.parts()).anyMatch(part -> "board-list".equals(part.componentType()));
    }

    @Test
    void shouldReturnAddGadgetToolForCreateRequests() {
        AgentResponse response = agentService.chat(new AgentRequest("add a chart to the dashboard", new AgentBoardContext(1L, "Main Board", "overview")));

        assertThat(response.message()).contains("chart");
        assertThat(response.toolCalls()).hasSize(1);
        assertThat(response.toolCalls().get(0).name()).isEqualTo("add_gadget");
        assertThat(response.parts())
                .anyMatch(part -> "gadget-suggestion".equals(part.componentType())
                        && part.payload().contains("BarChartComponent"));
    }

    @Test
    void shouldReturnMoveGadgetToolForMoveRequests() {
        AgentResponse response = agentService.chat(new AgentRequest("move the bar chart to the right", new AgentBoardContext(1L, "Main Board", "overview")));

        assertThat(response.toolCalls()).hasSize(1);
        assertThat(response.toolCalls().get(0).name()).isEqualTo("move_gadget");
        assertThat(response.parts())
                .anyMatch(part -> "gadget-move".equals(part.componentType())
                        && part.payload().contains("\"direction\":\"right\"")
                        && part.payload().contains("bar chart"));
    }

    @Test
    void shouldNotMisreadMoveRequestAsAddGadget() {
        AgentResponse response = agentService.chat(new AgentRequest("move the bar chart to the right", new AgentBoardContext(1L, "Main Board", "overview")));

        assertThat(response.toolCalls().get(0).name()).isNotEqualTo("add_gadget");
    }
}
