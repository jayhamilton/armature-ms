package com.addf.backend.armature.mcpapp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.addf.backend.armature.mcpapp.ChartPresentationChoice.Presentation;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.StructuredElicitResult;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

// Armature's second MCP App (see BoardSummaryApp for the first, and its class
// comment for the tool+ui:// resource pattern this follows). Renders bar,
// area, and pie charts from data the caller supplies via Chart.js (MIT,
// vendored inline in charts.html for the same "no base URL" reason
// board-summary.html vendors the ext-apps SDK - see that file).
//
// Before rendering, this asks the calling client - via a real MCP elicitation
// (ui/elicit), not a hand-rolled prompt - whether the chart is meant for the
// Armature dashboard or just this conversation. What it deliberately does
// NOT do with a "dashboard gadget" answer: actually add a gadget to the
// board. AgentToolRegistry#addGadget only accepts a componentType (no chart
// data - "the backend has no access to real board/gadget state") and reads
// its AgentToolCallRecorder out of a ToolContext that AgentController wires
// up per chat request; calling it from here, via the ext-apps SDK's
// callServerTool, would hit a bare MCP tools/call with no recorder in
// context and NPE. Making that path safe is real scope (Phase 5 territory
// per MODEL_INTEGRATION.md), not a byproduct of adding a chart app. So the
// dashboard-gadget answer only changes the rendered chrome and the
// ui/request-display-mode hint sent to the host (pip instead of inline) -
// an honest reflection of what a display-mode request actually is, not a
// promise this can't keep.
@Component
public class ChartsApp {

    static final String RESOURCE_URI = "ui://armature/charts.html";
    private static final String RESOURCE_CLASSPATH_PATH = "mcp-apps/charts.html";

    private static final Set<String> VALID_CHART_TYPES = Set.of("bar", "area", "pie");

    @McpTool(
            name = "present_chart",
            title = "Present chart",
            description = "Render an interactive bar, area, or pie chart from data you supply - labels plus "
                    + "one or more numeric series. Before rendering, asks the user (via elicitation) whether "
                    + "the chart should be pinned to their Armature dashboard as a gadget or just shown in "
                    + "this conversation. Use this whenever the user asks to chart, plot, graph, or visualize "
                    + "data, as opposed to a tool that changes the board directly.",
            metaProvider = ChartsUiMeta.class,
            // Same override as present_board_summary and for the same reason: this
            // never mutates board state, only asks a display-preference question and
            // renders. Left at the annotation's defaults (readOnlyHint=false,
            // destructiveHint=true) a host would flag a chart render as destructive.
            annotations = @McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false
            )
    )
    public CallToolResult presentChart(
            @McpToolParam(required = true, description = "Chart type: \"bar\", \"area\", or \"pie\".")
            String chartType,
            @McpToolParam(required = true, description = "Chart title.")
            String title,
            @McpToolParam(required = true, description = "Category labels - the x-axis (bar/area) or the pie "
                    + "slice names.")
            List<String> labels,
            @McpToolParam(required = true, description = "One or more data series to plot, each with a name "
                    + "and one numeric value per label. Pie charts use only the first series.")
            List<ChartSeries> series,
            McpSyncRequestContext requestContext
    ) {
        String resolvedType = VALID_CHART_TYPES.contains(chartType) ? chartType : "bar";
        Presentation presentation = choosePresentation(requestContext, title);

        Map<String, Object> structuredContent = new LinkedHashMap<>();
        structuredContent.put("chartType", resolvedType);
        structuredContent.put("title", title);
        structuredContent.put("labels", labels);
        structuredContent.put("series", toSeriesMaps(series));
        structuredContent.put(
                "presentation",
                presentation == Presentation.DASHBOARD_GADGET ? "dashboard_gadget" : "chat_response"
        );

        String presentationLabel = presentation == Presentation.DASHBOARD_GADGET
                ? "as a dashboard gadget"
                : "in this conversation";
        String textSummary = "Rendered a " + resolvedType + " chart, \"" + title + "\", with " + series.size()
                + " series across " + labels.size() + " categories, presented " + presentationLabel + ".";

        return CallToolResult.builder()
                .addTextContent(textSummary)
                .structuredContent(structuredContent)
                .build();
    }

    // Defaults to CHAT_RESPONSE - the client not supporting elicitation, the user
    // declining/cancelling, or a malformed reply are all treated the same way: show
    // it here rather than assume the more consequential dashboard placement.
    private Presentation choosePresentation(McpSyncRequestContext requestContext, String title) {
        if (requestContext == null || !requestContext.elicitEnabled()) {
            return Presentation.CHAT_RESPONSE;
        }
        StructuredElicitResult<ChartPresentationChoice> result = requestContext.elicit(
                spec -> spec.message("Where should the \"" + title + "\" chart go - pinned to your Armature "
                        + "dashboard as a gadget, or just shown here in this conversation?"),
                ChartPresentationChoice.class
        );
        if (result.action() != ElicitResult.Action.ACCEPT || result.structuredContent() == null
                || result.structuredContent().presentation() == null) {
            return Presentation.CHAT_RESPONSE;
        }
        return result.structuredContent().presentation();
    }

    @McpResource(
            uri = RESOURCE_URI,
            name = "charts-app",
            title = "Charts app",
            description = "Interactive HTML view rendering present_chart's result as a bar, area, or pie chart.",
            mimeType = "text/html;profile=mcp-app"
    )
    public String chartsResource() throws IOException {
        return new ClassPathResource(RESOURCE_CLASSPATH_PATH).getContentAsString(StandardCharsets.UTF_8);
    }

    private static List<Map<String, Object>> toSeriesMaps(List<ChartSeries> series) {
        return series.stream()
                .map(s -> {
                    Map<String, Object> seriesMap = new LinkedHashMap<>();
                    seriesMap.put("name", s.name());
                    seriesMap.put("values", s.values());
                    return seriesMap;
                })
                .toList();
    }
}
