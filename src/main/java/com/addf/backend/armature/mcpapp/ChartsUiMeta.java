package com.addf.backend.armature.mcpapp;

import java.util.Map;

import org.springframework.ai.mcp.annotation.context.MetaProvider;

// Links present_chart's tool definition to the ui:// resource it renders.
// See BoardSummaryUiMeta - same pattern, one resource URI per app.
public class ChartsUiMeta implements MetaProvider {

    @Override
    public Map<String, Object> getMeta() {
        return Map.of("ui", Map.of("resourceUri", ChartsApp.RESOURCE_URI));
    }
}
