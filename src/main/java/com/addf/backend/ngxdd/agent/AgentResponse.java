package com.addf.backend.ngxdd.agent;

import java.util.List;

public record AgentResponse(String message, List<ToolCall> toolCalls) {
}
