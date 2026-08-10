package com.ayush.drogonStart.service;

import com.ayush.drogonStart.dto.DependencyDefinition;
import com.ayush.drogonStart.registry.DependencyRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Post-generation customizer that modifies the scaffolded Drogon project
 * to integrate user-selected dependencies.
 *
 * This service operates on the generated project files AFTER drogon_ctl
 * has created the base project structure, and BEFORE the project is zipped.
 *
 * Two integration strategies are used:
 * - Built-in deps (databases, Redis): modify config.json
 * - External libs (nlohmann-json, spdlog, etc.): modify CMakeLists.txt
 *
 * Optionally, example C++ source files are placed in the controllers/ directory.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectCustomizer {

    private final DependencyRegistry dependencyRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Apply all selected dependencies and build options to the generated project.
     *
     * @param projectPath Root directory of the generated Drogon project
     * @param dependencyIds List of selected dependency IDs (e.g., ["postgresql", "spdlog"])
     * @param port The port to configure in config.json
     * @param cppStandard The C++ standard to set in CMakeLists.txt (e.g., "17", "20")
     * @param drogonVersion The Drogon version to pin in find_package (e.g., "v1.9.8")
     */
    public void customize(Path projectPath, List<String> dependencyIds, Integer port,
                          String cppStandard, String drogonVersion) {
        // 0. Always apply C++ standard (it always has a value after defaulting)
        if (cppStandard != null) {
            updateCppStandard(projectPath, cppStandard);
        }

        // 0b. Pin Drogon version in find_package
        if (drogonVersion != null) {
            patchDrogonVersion(projectPath, drogonVersion);
        }

        if (dependencyIds == null || dependencyIds.isEmpty()) {
            log.info("No dependencies selected, skipping dependency customization");
            updatePort(projectPath, port);
            return;
        }

        List<DependencyDefinition> selectedDeps = dependencyRegistry.getByIds(dependencyIds);

        if (selectedDeps.isEmpty()) {
            log.warn("No valid dependencies found for IDs: {}", dependencyIds);
            updatePort(projectPath, port);
            return;
        }

        log.info("Customizing project at {} with {} dependencies: {}",
                projectPath, selectedDeps.size(),
                selectedDeps.stream().map(DependencyDefinition::getId).collect(Collectors.joining(", ")));

        // Separate built-in and external deps
        List<DependencyDefinition> builtInDeps = selectedDeps.stream()
                .filter(DependencyDefinition::isBuiltIn)
                .collect(Collectors.toList());

        List<DependencyDefinition> externalDeps = selectedDeps.stream()
                .filter(dep -> !dep.isBuiltIn())
                .collect(Collectors.toList());

        // 1. Modify config.json for built-in deps (databases, Redis)
        if (!builtInDeps.isEmpty()) {
            modifyConfigJson(projectPath, builtInDeps, port);
        } else {
            updatePort(projectPath, port);
        }

        // 2. Modify CMakeLists.txt for external deps
        if (!externalDeps.isEmpty()) {
            modifyCMakeLists(projectPath, externalDeps);
        }

        // 3. Add example source files for all selected deps
        addExampleFiles(projectPath, selectedDeps);

        log.info("Project customization completed successfully");
    }

    // ==================== DROGON VERSION PINNING ====================

    /**
     * Pin the Drogon version in CMakeLists.txt's find_package directive.
     * Replaces: find_package(Drogon CONFIG REQUIRED)
     * With:     find_package(Drogon 1.9.8 CONFIG REQUIRED)
     *
     * This was previously in ContainerManager.patchCMakeLists() but is now
     * centralized here alongside all other post-generation patching.
     */
    private void patchDrogonVersion(Path projectPath, String drogonVersion) {
        Path cmakePath = projectPath.resolve("CMakeLists.txt");

        if (!Files.exists(cmakePath)) {
            log.warn("CMakeLists.txt not found at {}, skipping Drogon version pinning", cmakePath);
            return;
        }

        try {
            String content = Files.readString(cmakePath, StandardCharsets.UTF_8);

            String numericVersion = stripLeadingV(drogonVersion);
            content = content.replace(
                    "find_package(Drogon CONFIG REQUIRED)",
                    "find_package(Drogon " + numericVersion + " CONFIG REQUIRED)"
            );

            Files.writeString(cmakePath, content, StandardCharsets.UTF_8);
            log.info("Pinned Drogon version to {} in CMakeLists.txt", numericVersion);

        } catch (IOException e) {
            log.error("Failed to pin Drogon version in CMakeLists.txt: {}", e.getMessage(), e);
        }
    }

    /**
     * Strip the leading 'v' from a version tag (e.g., "v1.9.8" → "1.9.8").
     */
    private String stripLeadingV(String version) {
        return (version != null && version.startsWith("v")) ? version.substring(1) : version;
    }

    // ==================== CONFIG.JSON MODIFICATION ====================

    /**
     * Modify config.json to add database client and Redis client configurations.
     */
    private void modifyConfigJson(Path projectPath, List<DependencyDefinition> builtInDeps, Integer port) {
        Path configPath = projectPath.resolve("config.json");

        if (!Files.exists(configPath)) {
            log.warn("config.json not found at {}, skipping config modification", configPath);
            return;
        }

        try {
            String content = Files.readString(configPath, StandardCharsets.UTF_8);

            // Drogon's config.json may contain C-style comments; strip them for parsing
            String cleanContent = stripJsonComments(content);

            ObjectNode root = (ObjectNode) objectMapper.readTree(cleanContent);

            // Update port in listeners
            if (port != null && root.has("listeners")) {
                ArrayNode listeners = (ArrayNode) root.get("listeners");
                if (!listeners.isEmpty()) {
                    ObjectNode firstListener = (ObjectNode) listeners.get(0);
                    firstListener.put("port", port);
                }
            }

            // Add db_clients entries
            List<DependencyDefinition> dbDeps = builtInDeps.stream()
                    .filter(dep -> "db_clients".equals(dep.getConfigJsonKey()))
                    .collect(Collectors.toList());

            if (!dbDeps.isEmpty()) {
                ArrayNode dbClients = root.has("db_clients")
                        ? (ArrayNode) root.get("db_clients")
                        : root.putArray("db_clients");

                for (DependencyDefinition dep : dbDeps) {
                    JsonNode snippet = objectMapper.readTree(dep.getConfigJsonSnippet());
                    dbClients.add(snippet);
                    log.info("Added {} to config.json db_clients", dep.getId());
                }
            }

            // Add redis_clients entries
            List<DependencyDefinition> redisDeps = builtInDeps.stream()
                    .filter(dep -> "redis_clients".equals(dep.getConfigJsonKey()))
                    .collect(Collectors.toList());

            if (!redisDeps.isEmpty()) {
                ArrayNode redisClients = root.has("redis_clients")
                        ? (ArrayNode) root.get("redis_clients")
                        : root.putArray("redis_clients");

                for (DependencyDefinition dep : redisDeps) {
                    JsonNode snippet = objectMapper.readTree(dep.getConfigJsonSnippet());
                    redisClients.add(snippet);
                    log.info("Added {} to config.json redis_clients", dep.getId());
                }
            }

            // Write back with pretty-printing
            String updatedContent = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(root);
            Files.writeString(configPath, updatedContent, StandardCharsets.UTF_8);

            log.info("config.json updated successfully");

        } catch (IOException e) {
            log.error("Failed to modify config.json: {}", e.getMessage(), e);
        }
    }

    /**
     * Update only the port in config.json (when no built-in deps selected).
     */
    private void updatePort(Path projectPath, Integer port) {
        if (port == null) {
            return;
        }

        Path configPath = projectPath.resolve("config.json");
        if (!Files.exists(configPath)) {
            return;
        }

        try {
            String content = Files.readString(configPath, StandardCharsets.UTF_8);
            String cleanContent = stripJsonComments(content);
            ObjectNode root = (ObjectNode) objectMapper.readTree(cleanContent);

            if (root.has("listeners")) {
                ArrayNode listeners = (ArrayNode) root.get("listeners");
                if (!listeners.isEmpty()) {
                    ObjectNode firstListener = (ObjectNode) listeners.get(0);
                    firstListener.put("port", port);
                }
            }

            String updatedContent = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(root);
            Files.writeString(configPath, updatedContent, StandardCharsets.UTF_8);

            log.info("Updated config.json port to {}", port);

        } catch (IOException e) {
            log.error("Failed to update port in config.json: {}", e.getMessage(), e);
        }
    }

    // ==================== CMAKELISTS.TXT MODIFICATION ====================

    /**
     * Modify CMakeLists.txt to add FetchContent declarations and link targets
     * for external C++ libraries.
     */
    private void modifyCMakeLists(Path projectPath, List<DependencyDefinition> externalDeps) {
        Path cmakePath = projectPath.resolve("CMakeLists.txt");

        if (!Files.exists(cmakePath)) {
            log.warn("CMakeLists.txt not found at {}, skipping CMake modification", cmakePath);
            return;
        }

        try {
            String content = Files.readString(cmakePath, StandardCharsets.UTF_8);

            // 1. Build FetchContent block
            StringBuilder fetchContentBlock = new StringBuilder();
            fetchContentBlock.append("\n# ==========================================\n");
            fetchContentBlock.append("# Dependencies added by Drogon Scaffolder\n");
            fetchContentBlock.append("# ==========================================\n");
            fetchContentBlock.append("include(FetchContent)\n\n");

            for (DependencyDefinition dep : externalDeps) {
                if (dep.getCmakeFetchContent() != null) {
                    fetchContentBlock.append("# ").append(dep.getName()).append("\n");
                    fetchContentBlock.append(dep.getCmakeFetchContent()).append("\n\n");
                }
            }

            // 2. Build link targets list
            List<String> linkTargets = externalDeps.stream()
                    .filter(dep -> dep.getCmakeLinkTarget() != null)
                    .map(DependencyDefinition::getCmakeLinkTarget)
                    .collect(Collectors.toList());

            // 3. Insert FetchContent block after find_package(Drogon)
            String insertAnchor = "find_package(Drogon CONFIG REQUIRED)";
            int anchorIndex = content.indexOf(insertAnchor);

            if (anchorIndex == -1) {
                log.warn("Could not find 'find_package(Drogon CONFIG REQUIRED)' in CMakeLists.txt");
                return;
            }

            int insertPoint = content.indexOf('\n', anchorIndex);
            if (insertPoint == -1) {
                insertPoint = anchorIndex + insertAnchor.length();
            }

            content = content.substring(0, insertPoint + 1)
                    + fetchContentBlock
                    + content.substring(insertPoint + 1);

            // 4. Expand target_link_libraries to include new targets
            String linkAnchor = "target_link_libraries(${PROJECT_NAME} PRIVATE Drogon::Drogon)";
            if (content.contains(linkAnchor)) {
                String expandedLink = "target_link_libraries(${PROJECT_NAME} PRIVATE\n"
                        + "    Drogon::Drogon\n"
                        + linkTargets.stream()
                        .map(t -> "    " + t)
                        .collect(Collectors.joining("\n"))
                        + "\n)";
                content = content.replace(linkAnchor, expandedLink);
            } else {
                // Try to find a more generic pattern
                log.warn("Could not find standard target_link_libraries line, " +
                        "appending link targets at end of file");
                content += "\n# Additional link targets from Drogon Scaffolder\n";
                content += "target_link_libraries(${PROJECT_NAME} PRIVATE\n";
                content += linkTargets.stream()
                        .map(t -> "    " + t)
                        .collect(Collectors.joining("\n"));
                content += "\n)\n";
            }

            Files.writeString(cmakePath, content, StandardCharsets.UTF_8);
            log.info("CMakeLists.txt updated with {} external dependencies", externalDeps.size());

        } catch (IOException e) {
            log.error("Failed to modify CMakeLists.txt: {}", e.getMessage(), e);
        }
    }

    // ==================== EXAMPLE FILES ====================

    /**
     * Write example C++ source files into the project's controllers/ directory.
     * If multiple database deps are selected, only the first example is written
     * (since they share the same filename) to avoid overwriting.
     */
    private void addExampleFiles(Path projectPath, List<DependencyDefinition> selectedDeps) {
        Path controllersDir = projectPath.resolve("controllers");

        try {
            Files.createDirectories(controllersDir);
        } catch (IOException e) {
            log.error("Failed to create controllers directory: {}", e.getMessage());
            return;
        }

        // Track which filenames we've already written to avoid overwrites
        java.util.Set<String> writtenFiles = new java.util.HashSet<>();

        for (DependencyDefinition dep : selectedDeps) {
            if (dep.getExampleCode() == null || dep.getExampleFileName() == null) {
                continue;
            }

            // Skip if we already wrote this filename (e.g., ExampleDbController.cc
            // from postgresql when mysql is also selected)
            if (writtenFiles.contains(dep.getExampleFileName())) {
                log.info("Skipping duplicate example file {} for {}", dep.getExampleFileName(), dep.getId());
                continue;
            }

            Path exampleFile = controllersDir.resolve(dep.getExampleFileName());

            try {
                Files.writeString(exampleFile, dep.getExampleCode(), StandardCharsets.UTF_8);
                writtenFiles.add(dep.getExampleFileName());
                log.info("Created example file: {}", exampleFile.getFileName());
            } catch (IOException e) {
                log.error("Failed to write example file {}: {}", dep.getExampleFileName(), e.getMessage());
            }
        }
    }

    // ==================== C++ STANDARD ====================

    /**
     * Update CMAKE_CXX_STANDARD in CMakeLists.txt to match the requested C++ standard.
     *
     * Uses anchor-based string replacement (same pattern as modifyCMakeLists):
     * - If 'set(CMAKE_CXX_STANDARD' is found, rewrite the version number.
     * - If not found, insert after 'cmake_minimum_required' or at top of file.
     */
    private void updateCppStandard(Path projectPath, String cppStandard) {
        Path cmakePath = projectPath.resolve("CMakeLists.txt");

        if (!Files.exists(cmakePath)) {
            log.warn("CMakeLists.txt not found at {}, skipping C++ standard update", cmakePath);
            return;
        }

        try {
            String content = Files.readString(cmakePath, StandardCharsets.UTF_8);

            // Try to find existing set(CMAKE_CXX_STANDARD ...) line
            String stdAnchor = "set(CMAKE_CXX_STANDARD";
            int stdIndex = content.indexOf(stdAnchor);

            if (stdIndex != -1) {
                // Found existing line — replace the entire line
                int lineEnd = content.indexOf('\n', stdIndex);
                if (lineEnd == -1) lineEnd = content.length();

                content = content.substring(0, stdIndex)
                        + "set(CMAKE_CXX_STANDARD " + cppStandard + ")"
                        + content.substring(lineEnd);

                log.info("Updated existing CMAKE_CXX_STANDARD to {} in CMakeLists.txt", cppStandard);
            } else {
                // Not found — insert after cmake_minimum_required or at the top
                String insertBlock = "\nset(CMAKE_CXX_STANDARD " + cppStandard + ")\n"
                        + "set(CMAKE_CXX_STANDARD_REQUIRED ON)\n"
                        + "set(CMAKE_CXX_EXTENSIONS OFF)\n";

                String cmakeMinAnchor = "cmake_minimum_required";
                int cmakeMinIndex = content.indexOf(cmakeMinAnchor);

                if (cmakeMinIndex != -1) {
                    // Insert after the cmake_minimum_required line
                    int lineEnd = content.indexOf('\n', cmakeMinIndex);
                    if (lineEnd == -1) lineEnd = content.length();

                    // Also skip the project() line if it immediately follows
                    int projectIndex = content.indexOf("project(", lineEnd);
                    if (projectIndex != -1 && projectIndex - lineEnd < 5) {
                        lineEnd = content.indexOf('\n', projectIndex);
                        if (lineEnd == -1) lineEnd = content.length();
                    }

                    content = content.substring(0, lineEnd + 1)
                            + insertBlock
                            + content.substring(lineEnd + 1);
                } else {
                    log.warn("Could not find cmake_minimum_required anchor, prepending C++ standard at top");
                    content = insertBlock + content;
                }

                log.info("Inserted CMAKE_CXX_STANDARD {} into CMakeLists.txt", cppStandard);
            }

            Files.writeString(cmakePath, content, StandardCharsets.UTF_8);

        } catch (IOException e) {
            log.error("Failed to update C++ standard in CMakeLists.txt: {}", e.getMessage(), e);
        }
    }

    // ==================== UTILITIES ====================

    /**
     * Strip C and C++ style comments from JSON content.
     * Drogon's config.json supports line comments (//) and block comments
     * which are not valid JSON and would cause Jackson to fail.
     */
    private String stripJsonComments(String json) {
        StringBuilder result = new StringBuilder();
        boolean inString = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            char next = (i + 1 < json.length()) ? json.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                    result.append(c);
                }
                continue;
            }

            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++; // skip '/'
                }
                continue;
            }

            if (inString) {
                result.append(c);
                if (c == '\\' && i + 1 < json.length()) {
                    result.append(json.charAt(i + 1));
                    i++; // skip escaped char
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            // Not in string or comment
            if (c == '"') {
                inString = true;
                result.append(c);
            } else if (c == '/' && next == '/') {
                inLineComment = true;
                i++; // skip second '/'
            } else if (c == '/' && next == '*') {
                inBlockComment = true;
                i++; // skip '*'
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }
}
