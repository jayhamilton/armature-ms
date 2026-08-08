package com.addf.backend.armature.agent;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Identifies which dashboard the user is currently viewing.")
public record AgentBoardContext(
        @Schema(description = "The board's id.", example = "1") Long boardId,
        @Schema(description = "The board's display title.", example = "Production Overview") String boardTitle,
        @Schema(description = "The active tab within the board, if any.") String activeTab
) {
}
