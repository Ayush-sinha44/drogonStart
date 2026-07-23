package com.ayush.drogonStart.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for GET /api/v1/dependencies.
 * Groups available dependencies by category for easy frontend rendering.
 */
@Data
@Builder
public class DependencyResponse {

    /** Total number of available dependencies */
    private int totalCount;

    /** Dependencies grouped by category name (e.g. "DATABASE" -> [...]) */
    private Map<String, List<DependencyDefinition>> categories;
}
