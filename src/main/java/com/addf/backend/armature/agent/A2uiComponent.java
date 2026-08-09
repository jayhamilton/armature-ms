package com.addf.backend.armature.agent;

import java.util.List;
import java.util.Map;

/**
 * A generic A2UI component-catalog node: a component name, its props, and nested
 * children. Deliberately not a sealed hierarchy per component kind — nothing on the
 * Java side recurses into this beyond building it once, so a generic node is enough.
 * The catalog is small and grows as new component kinds are actually rendered by the
 * frontend; today: "Card", "Text", "Button".
 */
public record A2uiComponent(String component, Map<String, Object> props, List<A2uiComponent> children) {
}
