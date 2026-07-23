package com.ayush.drogonStart.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class ProjectRequest {

    @NotBlank(message = "Project name is required")
    @Pattern(regexp = "^[a-zA-Z0-9_-]{3,50}$",
            message = "Project name must be 3-50 characters, alphanumeric with hyphens/underscores only")
    private String name;

    @NotBlank(message = "Project type is required")
    @Pattern(regexp = "^(webapp|api|websocket)$",
            message = "Project type must be: webapp, api, or websocket")
    private String projectType;

    @Min(value = 1024, message = "Port must be between 1024 and 65535")
    @Max(value = 65535, message = "Port must be between 1024 and 65535")
    private Integer port = 8080;

    /**
     * C++ standard to set in CMakeLists.txt.
     * Valid values: "14", "17", "20", "23". Defaults to "17" if omitted.
     */
    @Pattern(regexp = "^(14|17|20|23)$",
            message = "C++ standard must be one of: 14, 17, 20, 23")
    private String cppStandard;

    /**
     * Drogon framework version tag to use for scaffolding.
     * Must match a pre-built Docker image tag (e.g., "v1.9.8", "v1.9.13").
     * Defaults to the newest stable version if omitted.
     *
     * Available versions can be fetched from GET /api/v1/dependencies/options.
     */
    @Pattern(regexp = "^v[0-9]+\\.[0-9]+\\.[0-9]+$",
            message = "Drogon version must be a valid tag (e.g., v1.9.8)")
    private String drogonVersion;

    /**
     * Optional list of dependency IDs to integrate into the generated project.
     * Example: ["postgresql", "redis", "nlohmann-json", "spdlog"]
     *
     * Available IDs can be fetched from GET /api/v1/dependencies.
     */
    @Size(max = 20, message = "Cannot select more than 20 dependencies")
    private List<@Pattern(regexp = "^[a-z0-9-]+$",
            message = "Dependency ID must be lowercase alphanumeric with hyphens") String> dependencies;
}
