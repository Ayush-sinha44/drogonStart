package com.ayush.drogonStart.controller;

import com.ayush.drogonStart.dto.DependencyDefinition;
import com.ayush.drogonStart.dto.DependencyResponse;
import com.ayush.drogonStart.registry.BuildOptionsRegistry;
import com.ayush.drogonStart.registry.DependencyRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller exposing the dependency catalog.
 *
 * A frontend can call GET /api/v1/dependencies to render dependency
 * checkboxes grouped by category (similar to Spring Initializr).
 */
@RestController
@RequestMapping("/api/v1/dependencies")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
@Slf4j
public class DependencyController {

    private final DependencyRegistry dependencyRegistry;
    private final BuildOptionsRegistry buildOptionsRegistry;

    /**
     * List all available dependencies grouped by category.
     * GET /api/v1/dependencies
     */
    @GetMapping
    public ResponseEntity<DependencyResponse> getAllDependencies() {
        log.info("Fetching all available dependencies");

        Map<String, List<DependencyDefinition>> grouped = dependencyRegistry.getAllGroupedByCategory();
        int totalCount = grouped.values().stream()
                .mapToInt(List::size)
                .sum();

        DependencyResponse response = DependencyResponse.builder()
                .totalCount(totalCount)
                .categories(grouped)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Get a single dependency by ID.
     * GET /api/v1/dependencies/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<DependencyDefinition> getDependencyById(@PathVariable String id) {
        log.info("Fetching dependency: {}", id);

        Optional<DependencyDefinition> dep = dependencyRegistry.getById(id);
        return dep.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get available build configuration options (C++ standards, Drogon versions).
     * GET /api/v1/dependencies/options
     *
     * Frontend uses this to populate dropdowns for C++ standard and Drogon version.
     */
    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> getBuildOptions() {
        log.info("Fetching build configuration options");
        return ResponseEntity.ok(buildOptionsRegistry.getOptionsMap());
    }
}
