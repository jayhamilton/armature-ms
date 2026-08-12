package com.addf.backend.armature.datasource;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A label matched against a gadget's own tags to decide which endpoints it can use.")
public record EndpointTag(String facet, String name) {
}
