package com.addf.backend.armature.agent;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A gadget type available in the frontend's library.json, sent with the chat "
        + "request so the assistant can ground its add_gadget suggestions in what's actually available.")
@JsonIgnoreProperties(ignoreUnknown = true)
public record GadgetLibraryEntry(
        @Schema(description = "The componentType key, e.g. PieChartComponent.") String componentType,
        @Schema(description = "Display title, e.g. Pie Chart.") String title,
        @Schema(description = "Short subtitle.") String subtitle,
        @Schema(description = "Longer description of what the gadget does.") String description,
        @Schema(description = "The gadget's configurable properties, used to derive a JSON Schema for "
                + "structured-output population of add_gadget's data.")
        List<GadgetPropertyPage> propertyPages
) {
}
