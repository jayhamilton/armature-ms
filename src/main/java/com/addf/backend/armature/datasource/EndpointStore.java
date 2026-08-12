package com.addf.backend.armature.datasource;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

// In-memory only - endpoint definitions (including stored credentials)
// don't survive a backend restart. This is the explicitly-agreed starting
// point for SPEC-73's persistence open question, not a design decision
// made here; a real datastore is a separate follow-up.
@Component
public class EndpointStore {

    private record StoredEndpoint(
            String id,
            String name,
            String address,
            String description,
            List<EndpointTag> tags,
            String authType,
            String authHeaderName,
            String credentialUser,
            String credentialValue
    ) {
        Endpoint toPublic() {
            return new Endpoint(id, name, address, description, tags, authType, authHeaderName, credentialUser);
        }
    }

    private final Map<String, StoredEndpoint> endpoints = new ConcurrentHashMap<>();

    public List<Endpoint> findAll() {
        return endpoints.values().stream()
                .map(StoredEndpoint::toPublic)
                .sorted(Comparator.comparing(Endpoint::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Endpoint create(EndpointWriteRequest request) {
        String id = UUID.randomUUID().toString();
        StoredEndpoint stored = new StoredEndpoint(
                id,
                request.name(),
                request.address(),
                request.description(),
                request.tags() != null ? request.tags() : List.of(),
                request.authType(),
                request.authHeaderName(),
                request.credentialUser(),
                request.credentialValue()
        );
        endpoints.put(id, stored);
        return stored.toPublic();
    }

    public Optional<Endpoint> update(String id, EndpointWriteRequest request) {
        StoredEndpoint existing = endpoints.get(id);
        if (existing == null) {
            return Optional.empty();
        }
        String credentialValue = (request.credentialValue() == null || request.credentialValue().isBlank())
                ? existing.credentialValue()
                : request.credentialValue();
        StoredEndpoint updated = new StoredEndpoint(
                id,
                request.name(),
                request.address(),
                request.description(),
                request.tags() != null ? request.tags() : List.of(),
                request.authType(),
                request.authHeaderName(),
                request.credentialUser(),
                credentialValue
        );
        endpoints.put(id, updated);
        return Optional.of(updated.toPublic());
    }

    public boolean delete(String id) {
        return endpoints.remove(id) != null;
    }
}
