package com.addf.backend.armature.agent;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A property page grouping from library.json's propertyPages.")
@JsonIgnoreProperties(ignoreUnknown = true)
public record GadgetPropertyPage(
        @Schema(description = "The properties in this page.") List<GadgetProperty> properties
) {
}
