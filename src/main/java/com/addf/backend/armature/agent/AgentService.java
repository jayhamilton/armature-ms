package com.addf.backend.armature.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Service;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final ChatClient chatClient;
    private final ChatClient structuredOutputChatClient;
    private final AgentToolCallRecorder recorder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentService(ChatClient.Builder chatClientBuilder, ChatModel chatModel, AgentToolRegistry toolRegistry,
            AgentToolCallRecorder recorder) {
        this.chatClient = chatClientBuilder.defaultTools(toolRegistry).build();
        // Separate, tools-free client: Ollama's outputSchema constrains the whole
        // response to a schema, which doesn't mix with tool-calling. Built off the
        // ChatModel directly rather than reusing chatClientBuilder a second time,
        // since builders are mutated in place and might carry defaultTools over.
        this.structuredOutputChatClient = ChatClient.create(chatModel);
        this.recorder = recorder;
    }

    public AgentResponse chat(AgentRequest request) {
        String boardTitle = request.boardContext() != null && request.boardContext().boardTitle() != null
                ? request.boardContext().boardTitle()
                : "the active dashboard";
        String activeTab = request.boardContext() != null ? request.boardContext().activeTab() : null;

        String systemPrompt = """
                You are the conversational assistant embedded in the Armature dashboard app.
                The user is currently viewing "%s"%s.
                Use the list_boards, add_gadget, move_gadget, remove_gadget, add_row, and change_row_layout
                tools when the user's request implies inspecting boards, or adding, moving, removing,
                adding a row, or changing a row's layout. Otherwise just answer conversationally.
                You don't know how many rows the current board has or what's in each one. If the user
                doesn't say which row change_row_layout should target, ask them to clarify (e.g. "the first
                row" means rowIndex 0) rather than guessing.
                Keep replies short, at most two sentences.
                %s""".formatted(boardTitle, activeTab != null ? " (tab: " + activeTab + ")" : "",
                gadgetTypesSection(request.gadgetLibrary()));

        String reply = chatClient.prompt()
                .system(systemPrompt)
                .user(request.message())
                .call()
                .content();

        List<AgentUiPart> parts = new ArrayList<>(recorder.parts());
        enrichAddGadgetPartsWithPropertyValues(request, parts);

        return new AgentResponse(reply, recorder.toolCalls(), parts);
    }

    private String gadgetTypesSection(List<GadgetLibraryEntry> gadgetLibrary) {
        if (gadgetLibrary == null || gadgetLibrary.isEmpty()) {
            return """

                    No gadget library was provided with this request, so you do not know the real
                    componentType names. Do not guess or invent a componentType for add_gadget — ask the
                    user to specify the exact componentType instead.
                    """;
        }
        // The exact-value list and the descriptions are deliberately kept in two
        // separate sections rather than one "componentType: description" line each.
        // Putting them on the same line, in any punctuation/format tried so far
        // (with a title in parens, with a bare colon), has gotten echoed back
        // verbatim as the componentType argument more than once (e.g.
        // "BarChartComponent (Bar Chart)", "BarChartComponent: Add a vertical bar
        // chart..."), which then fails to match the library on both this call's
        // enrichment lookup and the frontend's findGadgetDefinition. A short, quoted,
        // comma-separated enum for the value the model must reproduce exactly, with
        // the longer descriptions clearly separated below as reference-only, is much
        // harder to accidentally concatenate into the value itself.
        String quotedTypes = gadgetLibrary.stream()
                .map(entry -> "\"%s\"".formatted(entry.componentType()))
                .collect(Collectors.joining(", "));
        String descriptions = gadgetLibrary.stream()
                .map(entry -> "- %s: %s".formatted(entry.componentType(), entry.description()))
                .collect(Collectors.joining("\n"));
        return """

                For add_gadget, componentType must be exactly one of these values, with no other text
                added: %s

                What each one is (for context only — do not include this text in componentType):
                %s
                """.formatted(quotedTypes, descriptions);
    }

    /**
     * Populates add_gadget suggestions with real property values (title, chart
     * data, etc.) via a second, schema-constrained model call, instead of
     * leaving them at library.json's static defaults. Any failure here (model
     * error, unparseable output, no matching library entry) is caught and
     * logged — the part's payload is simply left with just its componentType,
     * which is exactly today's behavior, so this only ever adds capability.
     */
    private void enrichAddGadgetPartsWithPropertyValues(AgentRequest request, List<AgentUiPart> parts) {
        List<GadgetLibraryEntry> library = request.gadgetLibrary();
        if (library == null || library.isEmpty()) {
            return;
        }

        for (int i = 0; i < parts.size(); i++) {
            AgentUiPart part = parts.get(i);
            if (!"gadget-suggestion".equals(part.componentType())) {
                continue;
            }
            try {
                JsonNode payloadNode = objectMapper.readTree(part.payload());
                String componentType = payloadNode.path("gadgetComponentType").asText(null);
                GadgetLibraryEntry entry = findEntry(library, componentType);
                if (entry == null || entry.propertyPages() == null || entry.propertyPages().isEmpty()) {
                    continue;
                }

                String schema = GadgetPropertySchema.forEntry(entry, objectMapper);
                String valuesJson = structuredOutputChatClient.prompt()
                        .user(propertyValuesPrompt(request.message(), entry))
                        // qwen3.5:4b is a "thinking" model (see its capabilities in `ollama list`) and
                        // defaults to extended chain-of-thought before answering — observed directly to
                        // run past 6000+ decoded tokens without disabling it, vs. ~2-3s with it disabled,
                        // for the exact same schema-constrained prompt.
                        .options(OllamaChatOptions.builder().outputSchema(schema).disableThinking())
                        .call()
                        .content();

                JsonNode propertyValues = objectMapper.readTree(valuesJson);
                Map<String, Object> enrichedPayload = new LinkedHashMap<>();
                enrichedPayload.put("gadgetComponentType", componentType);
                enrichedPayload.put("propertyValues", propertyValues);

                parts.set(i, new AgentUiPart(part.id(), part.type(), part.text(), part.componentType(),
                        objectMapper.writeValueAsString(enrichedPayload)));
            } catch (Exception e) {
                log.warn("Failed to populate structured gadget property values for part {}, "
                        + "falling back to library defaults", part.id(), e);
            }
        }
    }

    private GadgetLibraryEntry findEntry(List<GadgetLibraryEntry> library, String componentType) {
        if (componentType == null) {
            return null;
        }
        return library.stream()
                .filter(entry -> componentType.equals(entry.componentType()))
                .findFirst()
                .orElse(null);
    }

    private String propertyValuesPrompt(String userMessage, GadgetLibraryEntry entry) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("The user asked: \"").append(userMessage).append("\"\n");
        prompt.append("They are adding a ").append(entry.title()).append(" gadget (")
                .append(entry.componentType()).append(").\n");
        prompt.append("Generate values for its configurable properties that make the gadget relevant to what "
                + "the user asked, using your best knowledge. Output must match the given schema exactly.\n");

        List<GadgetPropertyPage> pages = entry.propertyPages() != null ? entry.propertyPages() : List.of();
        for (GadgetPropertyPage page : pages) {
            List<GadgetProperty> properties = page.properties() != null ? page.properties() : List.of();
            for (GadgetProperty property : properties) {
                // No schema means no real value (e.g. a section header) - skip it,
                // matching GadgetPropertySchema's own exclusion.
                if (property.key() == null || property.schema() == null) {
                    continue;
                }
                Object type = property.schema().get("type");
                prompt.append("- ").append(property.key()).append(" (").append(type).append(")");
                if (property.value() != null) {
                    // property.value() can now be a nested array/object (e.g. chartData), not just a
                    // primitive - Object's default toString() renders those as Java map/list syntax
                    // (e.g. "[{name=Alpha, value=850}]"), not valid JSON, which would actively mislead
                    // the model about the format it's supposed to produce.
                    prompt.append(", example default format: ").append(propertyValueAsJson(property.value()));
                }
                prompt.append("\n");
            }
        }
        return prompt.toString();
    }

    private String propertyValueAsJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
