package com.ayush.drogonStart.registry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Registry of valid build configuration options for Drogon project scaffolding.
 *
 * This is intentionally separate from DependencyRegistry — C++ standard and
 * Drogon version are orthogonal build-level configuration, not FetchContent
 * library dependencies.
 */
@Component
@Slf4j
public class BuildOptionsRegistry {

    /** Allowed C++ standard values */
    public static final List<String> VALID_CPP_STANDARDS = List.of("14", "17", "20", "23");

    /** Default C++ standard when not specified */
    public static final String DEFAULT_CPP_STANDARD = "17";

    /**
     * Allowed Drogon version tags — each must correspond to a pre-built Docker
     * image (e.g., drogon-scaffold:v1.9.8). These are real release tags from
     * https://github.com/drogonframework/drogon/releases
     */
    public static final List<String> VALID_DROGON_VERSIONS = List.of(
            "v1.9.7",
            "v1.9.8",
            "v1.9.12",
            "v1.9.13"
    );

    /** Default Drogon version when not specified (newest stable) */
    public static final String DEFAULT_DROGON_VERSION = "v1.9.13";

    /**
     * Validate a C++ standard value.
     *
     * @return error message if invalid, null if valid
     */
    public String validateCppStandard(String cppStandard) {
        if (cppStandard == null) {
            return null; // will be defaulted
        }
        if (!VALID_CPP_STANDARDS.contains(cppStandard)) {
            return "Invalid C++ standard: '" + cppStandard
                    + "'. Valid options: " + String.join(", ", VALID_CPP_STANDARDS);
        }
        return null;
    }

    /**
     * Validate a Drogon version value.
     *
     * @return error message if invalid, null if valid
     */
    public String validateDrogonVersion(String drogonVersion) {
        if (drogonVersion == null) {
            return null; // will be defaulted
        }
        if (!VALID_DROGON_VERSIONS.contains(drogonVersion)) {
            return "Invalid Drogon version: '" + drogonVersion
                    + "'. Valid options: " + String.join(", ", VALID_DROGON_VERSIONS);
        }
        return null;
    }

    /**
     * Returns a map suitable for the GET /api/v1/dependencies/options response.
     */
    public Map<String, Object> getOptionsMap() {
        return Map.of(
                "cppStandards", VALID_CPP_STANDARDS,
                "defaultCppStandard", DEFAULT_CPP_STANDARD,
                "drogonVersions", VALID_DROGON_VERSIONS,
                "defaultDrogonVersion", DEFAULT_DROGON_VERSION
        );
    }
}
