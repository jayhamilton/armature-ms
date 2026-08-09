package com.addf.backend.armature.agent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real /api/agent/chat path against the locally running Ollama
 * instance. Skips (rather than fails) when Ollama isn't reachable, since this
 * needs an actual model call, not a mock.
 *
 * The endpoint now streams a hand-rolled AG-UI event sequence rather than returning
 * one JSON blob, but since this test uses a real HTTP client against a real embedded
 * server (not MockMvc), a blocking exchange().expectBody(String.class) still waits for
 * the whole response - it just now contains multiple "data: {...}" SSE frames instead
 * of a single JSON object. {@link #parseSseResponse(String)} reconstructs the same
 * (message, toolCalls, parts) shape the old AgentResponse used to hand back directly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentServiceTest {

    @LocalServerPort
    private int port;

    private RestTestClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void ollamaMustBeRunning() {
        Assumptions.assumeTrue(isOllamaReachable(), "Ollama is not running on localhost:11434 - skipping live model test");
    }

    @BeforeEach
    void setUp() {
        // Default read timeout is too tight for a local model, especially now that
        // add_gadget requests can involve a second structured-output model call.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(60));
        client = RestTestClient.bindToServer(requestFactory).baseUrl("http://localhost:" + port).build();
    }

    private static boolean isOllamaReachable() {
        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:11434/api/tags"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private String postAndGetSseBody(AgentRequest request) {
        return client.post()
                .uri("/api/agent/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }

    private record ParsedToolCall(String name, String arguments) {
    }

    private record ParsedRun(String message, List<ParsedToolCall> toolCalls, List<AgentUiPart> parts) {
    }

    /** Reassembles the AG-UI event stream into the same shape the old single-JSON AgentResponse had. */
    private ParsedRun parseSseResponse(String sseBody) throws Exception {
        StringBuilder message = new StringBuilder();
        Map<String, String> toolCallNamesById = new LinkedHashMap<>();
        List<ParsedToolCall> toolCalls = new ArrayList<>();
        List<AgentUiPart> parts = new ArrayList<>();

        for (String frame : sseBody.split("\n\n")) {
            String line = frame.strip();
            if (!line.startsWith("data:")) {
                continue;
            }
            JsonNode event = objectMapper.readTree(line.substring("data:".length()).strip());
            switch (event.path("type").asText()) {
                case "TEXT_MESSAGE_CONTENT" -> message.append(event.path("delta").asText());
                case "TOOL_CALL_START" -> toolCallNamesById.put(
                        event.path("toolCallId").asText(), event.path("toolCallName").asText());
                case "TOOL_CALL_ARGS" -> toolCalls.add(new ParsedToolCall(
                        toolCallNamesById.get(event.path("toolCallId").asText()), event.path("delta").asText()));
                case "CUSTOM" -> {
                    if ("ui-part".equals(event.path("name").asText())) {
                        parts.add(objectMapper.treeToValue(event.path("value"), AgentUiPart.class));
                    }
                }
                default -> { }
            }
        }
        return new ParsedRun(message.toString(), toolCalls, parts);
    }

    @Test
    void addGadgetRequestProducesGadgetSuggestion() throws Exception {
        // Grounded with a gadgetLibrary, matching what armature-ui always sends in practice.
        // An ungrounded request is intentionally non-deterministic here (see
        // AgentService's "don't guess, ask" system prompt instruction) — that behavior
        // is what addGadgetRequestGroundsInProvidedGadgetLibrary exists to confirm works
        // with a library, this test's purpose is just exercising the add_gadget path.
        AgentRequest request = new AgentRequest(
                "Add a bar chart to the dashboard",
                new AgentBoardContext(1L, "Main Board", "overview"),
                List.of(new GadgetLibraryEntry("BarChartComponent", "Bar Chart", "Vertical bar chart",
                        "Add a vertical bar chart to visualize categorical data.", null)),
                List.of()
        );

        ParsedRun run = parseSseResponse(postAndGetSseBody(request));

        assertThat(run.message()).isNotBlank();
        assertThat(run.toolCalls()).anyMatch(call -> "add_gadget".equals(call.name()));
        assertThat(run.parts()).anyMatch(part -> "gadget-suggestion".equals(part.componentType()));
    }

    @Test
    void addGadgetRequestGroundsInProvidedGadgetLibrary() throws Exception {
        AgentRequest request = new AgentRequest(
                "Add a pie chart to the dashboard",
                new AgentBoardContext(1L, "Main Board", "overview"),
                List.of(
                        new GadgetLibraryEntry("BarChartComponent", "Bar Chart", "Vertical bar chart",
                                "Add a vertical bar chart to visualize categorical data.", null),
                        new GadgetLibraryEntry("PieChartComponent", "Pie Chart", "Proportional data chart",
                                "Add a pie chart to show proportional data.", null),
                        new GadgetLibraryEntry("TableComponent", "Table", "Tabular data",
                                "Add a table to display rows of tabular data.", null)
                ),
                List.of()
        );

        ParsedRun run = parseSseResponse(postAndGetSseBody(request));

        assertThat(run.toolCalls())
                .anyMatch(call -> "add_gadget".equals(call.name()) && call.arguments().contains("PieChartComponent"));
        assertThat(run.parts())
                .anyMatch(part -> "gadget-suggestion".equals(part.componentType())
                        && part.payload().contains("PieChartComponent"));
    }

    @Test
    void listBoardsRequestProducesBoardList() throws Exception {
        AgentRequest request = new AgentRequest(
                "What boards do I have?",
                new AgentBoardContext(1L, "Main Board", "overview"),
                List.of(),
                List.of()
        );

        ParsedRun run = parseSseResponse(postAndGetSseBody(request));

        assertThat(run.toolCalls()).anyMatch(call -> "list_boards".equals(call.name()));
        assertThat(run.parts()).anyMatch(part -> "board-list".equals(part.componentType()));
    }

    @Test
    void removeGadgetRequestProducesGadgetRemoval() throws Exception {
        AgentRequest request = new AgentRequest(
                "Remove the bar chart from the dashboard",
                new AgentBoardContext(1L, "Main Board", "overview"),
                List.of(),
                List.of()
        );

        ParsedRun run = parseSseResponse(postAndGetSseBody(request));

        assertThat(run.toolCalls()).anyMatch(call -> "remove_gadget".equals(call.name()));
        assertThat(run.parts()).anyMatch(part -> "gadget-remove".equals(part.componentType()));
    }

    @Test
    void removeGadgetRequestGroundsQueryInProvidedBoardGadgetTitle() throws Exception {
        // The model has no access to real board state at all, so without boardGadgets it can
        // only guess at or paraphrase a title from the user's own words - which then fails the
        // frontend's title-substring match against the real gadget. Grounding gadgetQuery in an
        // exact, real title (the same fix already applied to add_gadget via gadgetLibrary) is
        // what this test confirms actually happens end-to-end against the live model.
        AgentRequest request = new AgentRequest(
                "Remove the 2023 starting five knicks bar chart from the dashboard",
                new AgentBoardContext(1L, "Main Board", "overview"),
                List.of(),
                List.of(new BoardGadgetEntry(42L, "Bar Chart"))
        );

        ParsedRun run = parseSseResponse(postAndGetSseBody(request));

        assertThat(run.toolCalls())
                .anyMatch(call -> "remove_gadget".equals(call.name()) && call.arguments().contains("Bar Chart"));
    }

    @Test
    void addRowRequestProducesRowAdd() throws Exception {
        AgentRequest request = new AgentRequest(
                "Add a new row to the board",
                new AgentBoardContext(1L, "Main Board", "overview"),
                List.of(),
                List.of()
        );

        ParsedRun run = parseSseResponse(postAndGetSseBody(request));

        assertThat(run.toolCalls()).anyMatch(call -> "add_row".equals(call.name()));
        assertThat(run.parts()).anyMatch(part -> "row-add".equals(part.componentType()));
    }

    @Test
    void changeRowLayoutRequestProducesRowLayout() throws Exception {
        AgentRequest request = new AgentRequest(
                "Change the first row to a three column layout",
                new AgentBoardContext(1L, "Main Board", "overview"),
                List.of(),
                List.of()
        );

        ParsedRun run = parseSseResponse(postAndGetSseBody(request));

        assertThat(run.toolCalls())
                .anyMatch(call -> "change_row_layout".equals(call.name())
                        && call.arguments().contains("\"rowIndex\":0")
                        && call.arguments().contains("three_col_equal"));
        assertThat(run.parts()).anyMatch(part -> "row-layout".equals(part.componentType()));
    }

    @Test
    void addGadgetRequestPopulatesStructuredPropertyValues() throws Exception {
        AgentRequest request = new AgentRequest(
                "Create a bar chart of average points per game for the starting five",
                new AgentBoardContext(1L, "Main Board", "overview"),
                List.of(barChartLibraryEntry()),
                List.of()
        );

        ParsedRun run = parseSseResponse(postAndGetSseBody(request));

        AgentUiPart suggestion = run.parts().stream()
                .filter(part -> "gadget-suggestion".equals(part.componentType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No gadget-suggestion part in response: " + run.parts()));

        assertThat(suggestion.payload()).contains("propertyValues");

        JsonNode payload = objectMapper.readTree(suggestion.payload());
        JsonNode propertyValues = payload.path("propertyValues");
        assertThat(propertyValues.has("chartData")).isTrue();

        // chartData is now a real nested array in the schema (not a string) - confirmed
        // enforced end-to-end through the actual application code and live model, not
        // just the curl experiments that established this schema shape gets enforced.
        JsonNode chartData = propertyValues.path("chartData");
        assertThat(chartData.isArray()).isTrue();
        assertThat(chartData.size()).isGreaterThan(0);
        assertThat(chartData.get(0).has("name")).isTrue();
        assertThat(chartData.get(0).has("value")).isTrue();
    }

    private static GadgetLibraryEntry barChartLibraryEntry() {
        Map<String, Object> stringSchema = Map.of("type", "string");
        Map<String, Object> booleanSchema = Map.of("type", "boolean");
        Map<String, Object> chartDataSchema = Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "object",
                        "properties", Map.of("name", Map.of("type", "string"), "value", Map.of("type", "number")),
                        "required", List.of("name", "value"),
                        "additionalProperties", false
                )
        );
        List<GadgetProperty> configProperties = List.of(
                new GadgetProperty("title", "Bar Chart", true, stringSchema),
                new GadgetProperty("subtitle", "", false, stringSchema),
                new GadgetProperty("chartShowXAxis", true, false, booleanSchema),
                new GadgetProperty("chartXAxisLabel", "Category", false, stringSchema),
                new GadgetProperty("chartLegend", true, false, booleanSchema),
                new GadgetProperty("chartShowYAxis", true, false, booleanSchema),
                new GadgetProperty("chartYAxisLabel", "Value", false, stringSchema)
        );
        List<GadgetProperty> dataProperties = List.of(
                new GadgetProperty("chartData",
                        List.of(Map.of("name", "Alpha", "value", 850), Map.of("name", "Beta", "value", 750)),
                        false, chartDataSchema)
        );
        return new GadgetLibraryEntry(
                "BarChartComponent", "Bar Chart", "Vertical bar chart",
                "Add a vertical bar chart to visualize categorical data.",
                List.of(new GadgetPropertyPage(configProperties), new GadgetPropertyPage(dataProperties))
        );
    }
}
