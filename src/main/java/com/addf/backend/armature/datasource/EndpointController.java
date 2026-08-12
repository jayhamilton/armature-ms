package com.addf.backend.armature.datasource;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// CRUD for endpoint definitions - see .work/specs/SPEC-73.md §1. This is
// deliberately scoped to CRUD only; the actual data-fetch proxy
// (POST /api/datasource/fetch, §3) and its required SSRF mitigations (§4)
// are a separate follow-up, not added here.
@RestController
@CrossOrigin
@Tag(name = "Endpoints", description = "REST data-source endpoint definitions")
@RequestMapping("/api/endpoints")
public class EndpointController {

    private final EndpointStore store;

    public EndpointController(EndpointStore store) {
        this.store = store;
    }

    @Operation(summary = "List all configured endpoints")
    @GetMapping
    public List<Endpoint> list() {
        return store.findAll();
    }

    @Operation(summary = "Create a new endpoint")
    @PostMapping
    public Endpoint create(@RequestBody EndpointWriteRequest request) {
        return store.create(request);
    }

    @Operation(summary = "Update an existing endpoint")
    @PutMapping("/{id}")
    public ResponseEntity<Endpoint> update(@PathVariable String id, @RequestBody EndpointWriteRequest request) {
        return store.update(id, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Delete an endpoint")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return store.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
