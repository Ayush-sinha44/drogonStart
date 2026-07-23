package com.ayush.drogonStart.dto;

import com.ayush.drogonStart.model.DependencyCategory;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Represents a single dependency available in the Drogon scaffolder catalog.
 *
 * Dependencies fall into two integration strategies:
 * - Built-in (builtIn=true): Drogon-native features configured via config.json
 *   (e.g., database drivers, Redis). No CMakeLists.txt changes needed.
 * - External (builtIn=false): Third-party C++ libraries added via CMake
 *   FetchContent. Requires CMakeLists.txt modifications.
 */
@Data
@Builder
public class DependencyDefinition {

    /** Unique identifier, e.g. "postgresql", "nlohmann-json" */
    private String id;

    /** Human-readable name, e.g. "PostgreSQL Driver" */
    private String name;

    /** Short description of what this dependency provides */
    private String description;

    /** Category for grouping in the UI */
    private DependencyCategory category;

    /** True if this is a Drogon built-in feature (config.json only) */
    private boolean builtIn;

    /**
     * CMake FetchContent block to insert into CMakeLists.txt.
     * Null for built-in dependencies.
     */
    private String cmakeFetchContent;

    /**
     * CMake link target name, e.g. "spdlog::spdlog".
     * Null for built-in dependencies.
     */
    private String cmakeLinkTarget;

    /**
     * JSON snippet to merge into config.json (db_clients or redis_clients).
     * Null for external library dependencies.
     */
    private String configJsonSnippet;

    /**
     * The config.json key this dependency writes to.
     * e.g. "db_clients" or "redis_clients". Null for external libs.
     */
    private String configJsonKey;

    /** System packages the user needs to install (informational) */
    private List<String> requiredSystemPackages;

    /** Example C++ code demonstrating usage of this dependency */
    private String exampleCode;

    /** Filename for the example code file (placed in controllers/) */
    private String exampleFileName;
}
