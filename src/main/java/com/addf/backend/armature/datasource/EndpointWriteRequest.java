package com.addf.backend.armature.datasource;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

// Create/update request shape - the only place a credential *value* ever
// appears on the wire. On update, a blank credentialValue means "keep the
// existing one" (see EndpointStore.update()), since the frontend never has
// the real value to send back.
@Schema(description = "Create/update payload for an endpoint definition.")
public record EndpointWriteRequest(
        String name,
        String address,
        String description,
        List<EndpointTag> tags,
        String authType,
        String authHeaderName,
        String credentialUser,
        @Schema(description = "API key or Basic auth password. Blank on update leaves the stored value unchanged.")
        String credentialValue
) {
}
