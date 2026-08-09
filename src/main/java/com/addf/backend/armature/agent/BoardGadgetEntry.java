package com.addf.backend.armature.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A gadget instance currently on the active board, sent with the chat request so "
        + "the assistant can ground move_gadget/remove_gadget's gadgetQuery in a real title instead of "
        + "guessing at or paraphrasing one.")
@JsonIgnoreProperties(ignoreUnknown = true)
public record BoardGadgetEntry(
        @Schema(description = "The gadget instance's id.") Long instanceId,
        @Schema(description = "Its current display title.", example = "Bar Chart") String title
) {
}
