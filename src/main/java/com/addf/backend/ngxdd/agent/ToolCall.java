package com.addf.backend.ngxdd.agent;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A tool the assistant is invoking, e.g. add_gadget or list_boards.")
public record ToolCall(
        @Schema(description = "The tool's name.", example = "add_gadget") String name,
        @Schema(description = "JSON-encoded arguments for the tool.", example = "{\"type\":\"chart\"}") String arguments
) {
}
