package com.addf.backend.armature.mcpapp;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

// One plotted series for ChartsApp#presentChart. Pie charts only ever read
// the first series in the list - labels double as slice names there instead
// of an x-axis.
public record ChartSeries(
        @JsonPropertyDescription("Series name shown in the chart legend.")
        String name,
        @JsonPropertyDescription("Numeric values, one per label, in the same order as the tool's labels argument.")
        List<Double> values
) {
}
