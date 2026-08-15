package com.addf.backend.armature.mcpapp;

import java.util.Map;

import org.springframework.ai.mcp.annotation.context.MetaProvider;

// Links present_board_summary's tool definition to the ui:// resource it renders.
// See BoardSummaryApp - the tool and the resource it points at live together in
// that one class, so this only needs to hardcode the resource URI once.
public class BoardSummaryUiMeta implements MetaProvider {

    @Override
    public Map<String, Object> getMeta() {
        return Map.of("ui", Map.of("resourceUri", BoardSummaryApp.RESOURCE_URI));
    }
}
