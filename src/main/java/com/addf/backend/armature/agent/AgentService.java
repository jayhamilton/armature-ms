package com.addf.backend.armature.agent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.addf.backend.armature.agent.agui.AgUiEvent;
import com.addf.backend.armature.agent.agui.CustomEvent;
import com.addf.backend.armature.agent.agui.RunError;
import com.addf.backend.armature.agent.agui.RunFinished;
import com.addf.backend.armature.agent.agui.RunStarted;
import com.addf.backend.armature.agent.agui.TextMessageContent;
import com.addf.backend.armature.agent.agui.TextMessageEnd;
import com.addf.backend.armature.agent.agui.TextMessageStart;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final ChatClient chatClient;
    private final ChatClient structuredOutputChatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Which provider is currently backing the primary ChatModel bean (see
    // application.properties' spring.ai.model.chat) - the schema-constrained
    // structured-output call below is Ollama-specific and checks this before running.
    @Value("${spring.ai.model.chat:ollama}")
    private String activeChatModel;

    public AgentService(ChatClient.Builder chatClientBuilder, ChatModel chatModel, AgentToolRegistry toolRegistry) {
        this.chatClient = chatClientBuilder.defaultTools(toolRegistry).build();
        // Separate, tools-free client: Ollama's outputSchema constrains the whole
        // response to a schema, which doesn't mix with tool-calling. Built off the
        // ChatModel directly rather than reusing chatClientBuilder a second time,
        // since builders are mutated in place and might carry defaultTools over.
        this.structuredOutputChatClient = ChatClient.create(chatModel);
    }

    /**
     * Streams the reply as a hand-rolled AG-UI event sequence over {@code emitter}:
     * RUN_STARTED, then TEXT_MESSAGE_START, TEXT_MESSAGE_CONTENT (real token deltas
     * from Ollama, not simulated), and TEXT_MESSAGE_END, then one
     * CUSTOM("ui-part", ...) event per resolved
     * {@link AgentUiPart} once the same enrichment pass used previously runs, then
     * RUN_FINISHED. TOOL_CALL_START/ARGS/END events are interleaved by
     * {@link AgentToolCallRecorder} as tools actually execute mid-stream.
     */
    public void chatStream(AgentRequest request, SseEmitter emitter) {
        String threadId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        String messageId = UUID.randomUUID().toString();

        // Fresh per request, not an injected @RequestScope bean - see AgentToolCallRecorder's
        // javadoc for why. Threaded into AgentToolRegistry's tool methods via ToolContext,
        // which works regardless of which thread ends up invoking a given tool.
        AgentToolCallRecorder recorder = new AgentToolCallRecorder();

        send(emitter, new RunStarted(threadId, runId));
        recorder.setEventSink(event -> send(emitter, event));

        String boardTitle = request.boardContext() != null && request.boardContext().boardTitle() != null
                ? request.boardContext().boardTitle()
                : "the active dashboard";
        String activeTab = request.boardContext() != null ? request.boardContext().activeTab() : null;

        String systemPrompt = """
                You are the conversational assistant embedded in the Armature dashboard app.
                The user is currently viewing "%s"%s.
                Use the list_boards, add_gadget, move_gadget, remove_gadget, add_row, change_row_layout, or
                show_board_summary tools when the user's request implies inspecting boards, or adding,
                moving, removing, adding a row, changing a row's layout, or reviewing the current board's
                gadgets. Otherwise just answer conversationally.
                Use show_board_summary when the user asks what's on THIS board, or to see/show/review its
                gadgets (e.g. "what's on my board", "show my board", "what gadgets do I have") - it renders
                an interactive view and never changes anything. This is different from list_boards, which
                is only for the separate list of board names to switch between, not this board's contents.
                Do not use show_board_summary for a request to add, move, remove, or otherwise change the
                board either; that is always one of the other tools, never this one.
                Only call a tool that directly corresponds to something the user's message explicitly
                asked for in this turn. Never call a different, unrelated tool alongside it "while you're
                at it," to be helpful, or because it seems related - e.g. removing a gadget the user asked
                to remove does not also call move_gadget, add_row, or list_boards unless the user asked for
                that too, in the same message.
                You don't know how many rows the current board has or what's in each one. If the user
                doesn't say which row change_row_layout should target, ask them to clarify (e.g. "the first
                row" means rowIndex 0) rather than guessing.
                Call add_gadget, move_gadget, or remove_gadget at most once each per distinct thing the
                user actually asked for - even if you're unsure of the exact componentType or title, commit
                to your single best match rather than calling the same tool again with a different guess.
                move_gadget and remove_gadget only record what you asked for - whether a matching gadget
                is actually found and acted on happens afterward, outside this conversation, so phrase
                your reply tentatively (e.g. "Moving the bar chart up" rather than "I've moved it") rather
                than stating with certainty that it happened.
                Keep replies short, at most two sentences.
                %s
                %s""".formatted(boardTitle, activeTab != null ? " (tab: " + activeTab + ")" : "",
                gadgetTypesSection(request.gadgetLibrary()), boardGadgetsSection(request.boardGadgets()));

        // TEXT_MESSAGE_START is deferred until the first real delta (or, if there
        // never is one, until the stream completes) rather than sent up front here.
        // Sending it immediately made the frontend tear down its typing indicator
        // and show an empty assistant bubble for the entire real generation time
        // (including tool-call reasoning), instead of only once text actually starts.
        AtomicBoolean textStarted = new AtomicBoolean(false);

        chatClient.prompt()
                .system(systemPrompt)
                .user(request.message())
                .toolContext(Map.of(AgentToolRegistry.RECORDER_KEY, recorder))
                // Tried .options(OllamaChatOptions.builder().disableThinking()) here, on the
                // theory that it might reduce spurious extra tool calls the same way it fixed
                // structured output's runaway generation below - measured empirically instead
                // (8/8 repeated "remove the bar chart" requests spuriously also called
                // list_boards or add_row, vs ~2/5 with thinking left on), so it made this worse,
                // not better. Left enabled here; the spurious-tool-call problem needs a different
                // fix (see AgentToolRegistry's per-request call cap, tracked separately).
                .stream()
                .content()
                .subscribe(
                        delta -> {
                            if (textStarted.compareAndSet(false, true)) {
                                send(emitter, new TextMessageStart(messageId));
                            }
                            send(emitter, new TextMessageContent(messageId, delta));
                        },
                        error -> {
                            log.warn("Chat stream failed", error);
                            send(emitter, new RunError(
                                    error.getMessage() != null ? error.getMessage() : "The assistant hit an error."));
                            emitter.completeWithError(error);
                        },
                        // Runs on whatever scheduler the upstream Flux completes on (a Reactor
                        // Netty event-loop thread, since Ollama streaming goes through WebClient)
                        // - the enrichment call below is blocking, so it's dispatched onto
                        // boundedElastic rather than running directly on that thread.
                        () -> {
                            if (textStarted.compareAndSet(false, true)) {
                                send(emitter, new TextMessageStart(messageId));
                            }
                            Mono.fromRunnable(() -> finishRun(emitter, request, recorder, threadId, runId, messageId))
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .subscribe();
                        }
                );
    }

    private void finishRun(SseEmitter emitter, AgentRequest request, AgentToolCallRecorder recorder, String threadId,
            String runId, String messageId) {
        send(emitter, new TextMessageEnd(messageId));

        List<AgentUiPart> parts = new ArrayList<>(recorder.parts());
        enrichAddGadgetPartsWithPropertyValues(request, parts);
        for (AgentUiPart part : parts) {
            send(emitter, new CustomEvent("ui-part", part));
        }

        send(emitter, new RunFinished(threadId, runId));
        emitter.complete();
    }

    private void send(SseEmitter emitter, AgUiEvent event) {
        try {
            emitter.send(SseEmitter.event().data(event));
        } catch (IOException e) {
            log.warn("Failed to send AG-UI event {}", event, e);
        }
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

    private String boardGadgetsSection(List<BoardGadgetEntry> boardGadgets) {
        if (boardGadgets == null || boardGadgets.isEmpty()) {
            return """

                    There are no gadgets on the board right now (or none were reported), so you do not
                    know any real titles. Do not guess or invent one for move_gadget/remove_gadget — say
                    there's nothing to move or remove, or ask the user to add a gadget first.
                    """;
        }
        // Same reasoning as gadgetTypesSection's exact-value list: gadgetQuery must be copied
        // verbatim from a real title, not paraphrased from the user's own words - the frontend
        // matches it as a literal substring of the actual gadget title, so an embellished or
        // shortened version (e.g. "the 2023 starting five knicks bar chart" for a gadget
        // actually titled "Bar Chart") fails to match anything on the board.
        String quotedTitles = boardGadgets.stream()
                .map(entry -> "\"%s\"".formatted(entry.title()))
                .collect(Collectors.joining(", "));
        return """

                The gadgets currently on the board have these exact titles: %s
                For move_gadget and remove_gadget, gadgetQuery must be copied exactly from one of these
                titles, with no other text added. The user will rarely say a title word-for-word - match
                their description to whichever title it most plausibly refers to (if there's only one
                gadget, that's almost always the one they mean, even if their wording doesn't overlap with
                its title much) rather than requiring an exact or near-exact match. Only ask for
                clarification when two or more titles are genuinely plausible matches.
                """.formatted(quotedTitles);
    }

    /**
     * Populates add_gadget suggestions with real property values (title, chart
     * data, etc.) via a second, schema-constrained model call, instead of
     * leaving them at library.json's static defaults. Any failure here (model
     * error, unparseable output, no matching library entry) is caught and
     * logged — the part's payload is simply left with just its componentType,
     * which is exactly today's behavior, so this only ever adds capability.
     *
     * <p>The schema constraint itself ({@code OllamaChatOptions.outputSchema}) is
     * Ollama-specific grammar-decoding, with no equivalent wired up for other
     * providers - skips itself entirely when Ollama isn't the active chat model
     * (see {@link #activeChatModel}), degrading to the same library-defaults
     * fallback as any other failure here rather than sending Ollama-only options
     * to whatever provider is actually active.
     */
    private void enrichAddGadgetPartsWithPropertyValues(AgentRequest request, List<AgentUiPart> parts) {
        List<GadgetLibraryEntry> library = request.gadgetLibrary();
        if (library == null || library.isEmpty()) {
            return;
        }
        if (!"ollama".equals(activeChatModel)) {
            log.debug("Skipping structured property-value enrichment - active chat model is '{}', not ollama",
                    activeChatModel);
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
