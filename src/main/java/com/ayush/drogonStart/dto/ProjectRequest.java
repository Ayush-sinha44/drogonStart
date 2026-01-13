package com.ayush.drogonStart.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

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
}
