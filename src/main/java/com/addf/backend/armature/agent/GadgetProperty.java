package com.addf.backend.armature.agent;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A single configurable property of a gadget, from library.json's propertyPages.")
@JsonIgnoreProperties(ignoreUnknown = true)
public record GadgetProperty(
        @Schema(description = "The property key, e.g. chartData.") String key,
        @Schema(description = "The library default value (string, number, or boolean), used as a "
                + "formatting example, not enforced.")
        Object value,
        @Schema(description = "Whether the property must be given a value.") Boolean required,
        @Schema(description = "JSON Schema fragment declaring this property's data shape, e.g. "
                + "{\"type\": \"boolean\"}. Absent for properties with no real value (e.g. section "
                + "headers), which is how this and the frontend's rendering both know to skip them.")
        Map<String, Object> schema
) {
}
