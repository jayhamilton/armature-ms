package com.addf.backend.armature.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Builds a JSON Schema for a gadget's configurable properties, so Ollama's
 * structured output can be constrained to a valid shape for that specific
 * gadget type. Each property's own schema fragment is authored at the
 * source (library.json, via GadgetProperty.schema) and just gets copied
 * through here - no type inference/guessing. Pure/deterministic, no model
 * call involved.
 */
public final class GadgetPropertySchema {

    private GadgetPropertySchema() {
    }

    public static String forEntry(GadgetLibraryEntry entry, ObjectMapper objectMapper) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        List<GadgetPropertyPage> pages = entry.propertyPages() != null ? entry.propertyPages() : List.of();
        for (GadgetPropertyPage page : pages) {
            List<GadgetProperty> pageProperties = page.properties() != null ? page.properties() : List.of();
            for (GadgetProperty property : pageProperties) {
                // No schema means no real value (e.g. a section header) - skip it.
                if (property.key() == null || property.schema() == null) {
                    continue;
                }
                properties.put(property.key(), property.schema());
                if (Boolean.TRUE.equals(property.required())) {
                    required.add(property.key());
                }
            }
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "object");
        root.put("properties", properties);
        // Without this, Ollama's grammar-constrained decoding has no forced stopping
        // point after the known properties are satisfied - additional properties stay
        // grammatically valid, and generation can run away for thousands of tokens
        // instead of closing the object. Observed directly: a schema without this
        // decoded 6800+ tokens and counting before being killed.
        root.put("additionalProperties", false);
        if (!required.isEmpty()) {
            root.put("required", required);
        }

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode schema for " + entry.componentType(), e);
        }
    }
}
