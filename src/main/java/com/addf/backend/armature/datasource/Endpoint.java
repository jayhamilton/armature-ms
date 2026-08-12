package com.addf.backend.armature.datasource;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

// The read shape returned to armature-ui - deliberately has no credential
// *value* field. See .work/specs/SPEC-73.md §1: a stored secret never
// round-trips back to the browser once written.
@Schema(description = "A REST data-source endpoint definition, as read back by the frontend.")
public record Endpoint(
        String id,
        String name,
        String address,
        String description,
        List<EndpointTag> tags,
        @Schema(description = "\"none\" | \"header\" | \"basic\"") String authType,
        @Schema(description = "Header name the credential is sent under when authType is \"header\".")
        String authHeaderName,
        @Schema(description = "Username when authType is \"basic\".") String credentialUser
) {
}
