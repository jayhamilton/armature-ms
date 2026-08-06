package com.addf.backend.ngxdd.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentServiceTest {

    private final AgentService agentService = new AgentService();

    @Test
    void shouldReturnListBoardsToolForBoardQueries() {
        AgentResponse response = agentService.chat(new AgentRequest("list my boards"));

        assertThat(response.message()).contains("boards");
        assertThat(response.toolCalls()).hasSize(1);
        assertThat(response.toolCalls().get(0).name()).isEqualTo("list_boards");
    }

    @Test
    void shouldReturnAddGadgetToolForCreateRequests() {
        AgentResponse response = agentService.chat(new AgentRequest("add a chart to the dashboard"));

        assertThat(response.message()).contains("chart");
        assertThat(response.toolCalls()).hasSize(1);
        assertThat(response.toolCalls().get(0).name()).isEqualTo("add_gadget");
    }
}
