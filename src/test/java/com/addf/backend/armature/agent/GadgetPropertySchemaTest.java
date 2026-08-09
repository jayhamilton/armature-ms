package com.addf.backend.armature.agent;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GadgetPropertySchemaTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void copiesEachPropertysSchemaThrough() throws Exception {
        GadgetLibraryEntry entry = new GadgetLibraryEntry(
                "BarChartComponent", "Bar Chart", "Vertical bar chart", "Add a vertical bar chart.",
                List.of(new GadgetPropertyPage(List.of(
                        new GadgetProperty("title", "Bar Chart", true, Map.of("type", "string")),
                        new GadgetProperty("chartShowXAxis", true, false, Map.of("type", "boolean")),
                        new GadgetProperty("chartMinRadius", 3, false, Map.of("type", "number")),
                        new GadgetProperty("tags", null, false, Map.of("type", "array", "items", Map.of("type", "string"))),
                        new GadgetProperty("chartData", "[{\"name\":\"Alpha\",\"value\":850}]", false, Map.of("type", "string"))
                )))
        );

        JsonNode schema = objectMapper.readTree(GadgetPropertySchema.forEntry(entry, objectMapper));

        assertThat(schema.path("type").asText()).isEqualTo("object");
        assertThat(schema.path("properties").path("title").path("type").asText()).isEqualTo("string");
        assertThat(schema.path("properties").path("chartShowXAxis").path("type").asText()).isEqualTo("boolean");
        assertThat(schema.path("properties").path("chartMinRadius").path("type").asText()).isEqualTo("number");
        assertThat(schema.path("properties").path("tags").path("type").asText()).isEqualTo("array");
        assertThat(schema.path("properties").path("tags").path("items").path("type").asText()).isEqualTo("string");
        assertThat(schema.path("properties").path("chartData").path("type").asText()).isEqualTo("string");
    }

    @Test
    void disallowsAdditionalPropertiesSoGenerationCanTerminate() throws Exception {
        // Without this, Ollama's grammar-constrained decoding has no forced stopping
        // point and can run away generating thousands of extra tokens - observed
        // directly against the live model before this was added.
        GadgetLibraryEntry entry = new GadgetLibraryEntry(
                "TextComponent", "Text", "Markdown text block", "Add a block of markdown text.",
                List.of(new GadgetPropertyPage(List.of(
                        new GadgetProperty("content", "", false, Map.of("type", "string"))))));

        JsonNode schema = objectMapper.readTree(GadgetPropertySchema.forEntry(entry, objectMapper));

        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
    }

    @Test
    void propertiesWithNoSchemaAreExcluded() throws Exception {
        // A property with no schema (e.g. a library.json "section" grouping
        // header, which carries no real value) shouldn't appear at all -
        // this is how exclusion works now, rather than checking controlType.
        GadgetLibraryEntry entry = new GadgetLibraryEntry(
                "BarChartComponent", "Bar Chart", "Vertical bar chart", "Add a vertical bar chart.",
                List.of(new GadgetPropertyPage(List.of(
                        new GadgetProperty("section-chart-props", "", false, null),
                        new GadgetProperty("title", "Bar Chart", true, Map.of("type", "string"))
                )))
        );

        JsonNode schema = objectMapper.readTree(GadgetPropertySchema.forEntry(entry, objectMapper));

        assertThat(schema.path("properties").has("section-chart-props")).isFalse();
        assertThat(schema.path("properties").has("title")).isTrue();
    }

    @Test
    void onlyRequiredPropertiesAppearInRequiredArray() throws Exception {
        GadgetLibraryEntry entry = new GadgetLibraryEntry(
                "BarChartComponent", "Bar Chart", "Vertical bar chart", "Add a vertical bar chart.",
                List.of(new GadgetPropertyPage(List.of(
                        new GadgetProperty("title", "Bar Chart", true, Map.of("type", "string")),
                        new GadgetProperty("subtitle", "", false, Map.of("type", "string"))
                )))
        );

        JsonNode schema = objectMapper.readTree(GadgetPropertySchema.forEntry(entry, objectMapper));

        List<String> required = objectMapper.convertValue(schema.path("required"), List.class);
        assertThat(required).containsExactly("title");
    }

    @Test
    void handlesNullPropertyPagesGracefully() {
        GadgetLibraryEntry entry = new GadgetLibraryEntry(
                "TextComponent", "Text", "Markdown text block", "Add a block of markdown text.", null);

        String schema = GadgetPropertySchema.forEntry(entry, objectMapper);

        assertThat(schema).contains("\"type\":\"object\"");
    }
}
