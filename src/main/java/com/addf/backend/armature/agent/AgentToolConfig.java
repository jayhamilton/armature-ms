package com.addf.backend.armature.agent;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes {@link AgentToolRegistry}'s {@code @Tool} methods as a
 * {@link ToolCallbackProvider} bean, which is what the MCP server
 * autoconfiguration (ToolCallbackConverterAutoConfiguration) actually scans
 * for. A plain {@code @Component} with {@code @Tool} methods isn't picked up
 * on its own — without this bean the MCP server logs "No tool methods found
 * in the provided tool objects: []" at boot even though the methods exist.
 */
@Configuration
public class AgentToolConfig {

    @Bean
    public ToolCallbackProvider agentToolCallbackProvider(AgentToolRegistry toolRegistry) {
        return MethodToolCallbackProvider.builder().toolObjects(toolRegistry).build();
    }
}
