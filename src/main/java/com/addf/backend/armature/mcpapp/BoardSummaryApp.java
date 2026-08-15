package com.addf.backend.armature.mcpapp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.addf.backend.armature.agent.BoardGadgetEntry;
import com.addf.backend.armature.agent.BoardSnapshot;
import com.addf.backend.armature.agent.BoardSnapshotStore;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

// Armature's first MCP App: a read-only tool that presents what's currently on the
// active board as an interactive UI, rather than the plain-text/JSON-intent replies
// AgentToolRegistry's board-mutation tools (add_gadget, move_gadget, etc.) return.
// Pairing an @McpTool with a ui:// @McpResource is the MCP Apps pattern (SEP-1865,
// final 2026-01-26); those tools are Spring AI @Tool methods on a ToolCallbackProvider
// bean instead, an older/separate registration path that composes with, but is not
// unified with, this one. The two mechanisms are demonstrated side by side
// deliberately - see MODEL_INTEGRATION.md's MCP Apps section.
@Component
public class BoardSummaryApp {

    static final String RESOURCE_URI = "ui://armature/board-summary.html";
    private static final String RESOURCE_CLASSPATH_PATH = "mcp-apps/board-summary.html";

    private final BoardSnapshotStore boardSnapshotStore;

    public BoardSummaryApp(BoardSnapshotStore boardSnapshotStore) {
        this.boardSnapshotStore = boardSnapshotStore;
    }

    @McpTool(
            name = "present_board_summary",
            title = "Present board summary",
            description = "Show an interactive summary of what's currently on the user's Armature "
                    + "dashboard - the board's title and the gadgets on it. This is presentational and "
                    + "read-only: it never adds, moves, or removes anything. Use this when the user asks "
                    + "what's on their board, wants to review it, or asks to see/show/display it, as "
                    + "opposed to asking to change it (which calls a different tool).",
            metaProvider = BoardSummaryUiMeta.class,
            // Overrides the annotation's own defaults (readOnlyHint=false,
            // destructiveHint=true), which are wrong for a tool that only ever reads
            // BoardSnapshotStore - hosts that surface these hints (e.g. a confirmation
            // prompt before calling) would otherwise flag a read-only tool as
            // destructive, undermining the read-vs-mutate distinction this tool exists
            // to demonstrate.
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false
            )
    )
    public CallToolResult presentBoardSummary() {
        Optional<BoardSnapshot> snapshot = boardSnapshotStore.latest();

        Map<String, Object> structuredContent = snapshot
                .map(s -> {
                    Map<String, Object> content = new LinkedHashMap<>();
                    content.put("boardTitle", s.boardTitle());
                    content.put("updatedAt", s.updatedAt().toString());
                    content.put("gadgets", toGadgetMaps(s.gadgets()));
                    return content;
                })
                .orElseGet(() -> {
                    Map<String, Object> empty = new LinkedHashMap<>();
                    empty.put("boardTitle", "");
                    empty.put("updatedAt", "");
                    empty.put("gadgets", List.of());
                    return empty;
                });

        String textSummary = snapshot
                .map(s -> "Showing " + s.gadgets().size() + " gadget(s) on \"" + s.boardTitle() + "\".")
                .orElse("No board has been synced yet - open Armature and send a chat message once, "
                        + "then ask again.");

        return CallToolResult.builder()
                .addTextContent(textSummary)
                .structuredContent(structuredContent)
                .build();
    }

    @McpResource(
            uri = RESOURCE_URI,
            name = "board-summary-app",
            title = "Board summary app",
            description = "Interactive HTML view rendering present_board_summary's result.",
            mimeType = "text/html;profile=mcp-app"
    )
    public String boardSummaryResource() throws IOException {
        return new ClassPathResource(RESOURCE_CLASSPATH_PATH).getContentAsString(StandardCharsets.UTF_8);
    }

    private static List<Map<String, Object>> toGadgetMaps(List<BoardGadgetEntry> gadgets) {
        return gadgets.stream()
                .map(gadget -> {
                    Map<String, Object> gadgetMap = new LinkedHashMap<>();
                    gadgetMap.put("instanceId", gadget.instanceId());
                    gadgetMap.put("title", gadget.title());
                    gadgetMap.put("componentType", gadget.componentType());
                    return gadgetMap;
                })
                .toList();
    }
}
