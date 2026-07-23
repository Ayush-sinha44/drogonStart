package com.ayush.drogonStart.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ContainerManager {

    private final DockerClient dockerClient;
    private final String imageName;
    private final String imageTag;
    private final int timeoutSeconds;
    private final long memoryLimitBytes;

    public ContainerManager(
            @Value("${docker.host}") String dockerHost,
            @Value("${docker.image.name}") String imageName,
            @Value("${docker.image.tag}") String imageTag,
            @Value("${docker.container.timeout-seconds}") int timeoutSeconds,
            @Value("${docker.container.memory-limit-mb}") int memoryLimitMb) {

        // 1. Create Config0
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();

        // 2. Create HttpClient
        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .build();

        // 3. Instantiate Client directly (This replaces DockerClientBuilder)
        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);

        this.imageName = imageName;
        this.imageTag = imageTag;
        this.timeoutSeconds = timeoutSeconds;
        this.memoryLimitBytes = memoryLimitMb * 1024L * 1024L;

        log.info("ContainerManager initialized successfully with image: {}:{}", imageName, imageTag);
    }

    /**
     * Execute drogon_ctl in a container to generate a project
     */
    public ContainerExecutionResult executeScaffolding(
            String jobId,
            String projectName,
            String projectType,
            Path workspacePath,
            String cppStandard,
            String drogonVersion) {

        String containerId = null;

        try {
            log.info("Creating container for job {} to generate project {} (Drogon {}, C++{})",
                    jobId, projectName, drogonVersion, cppStandard);

            containerId = createContainer(jobId, projectName, projectType, workspacePath);
            dockerClient.startContainerCmd(containerId).exec();

            Integer exitCode = dockerClient.waitContainerCmd(containerId)
                    .exec(new WaitContainerResultCallback())
                    .awaitStatusCode(timeoutSeconds, TimeUnit.SECONDS);

            String logs = getContainerLogs(containerId);

            if (exitCode == null || exitCode != 0) {
                return ContainerExecutionResult.failure(
                        "Container exited with code " + exitCode,
                        logs
                );
            }

            // Patch the generated CMakeLists.txt on the host filesystem
            // (bind-mounted workspace is directly accessible from Java)
            patchCMakeLists(workspacePath, projectName, drogonVersion, cppStandard);

            return ContainerExecutionResult.success(logs);

        } catch (Exception e) {
            return ContainerExecutionResult.failure("Execution failed: " + e.getMessage(), "");

        } finally {
            if (containerId != null) {
                cleanupContainer(containerId);
            }
        }
    }

    private String createContainer(String jobId, String projectName, String projectType,
                                   Path workspacePath) {

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(new Bind(workspacePath.toAbsolutePath().toString(),
                        new Volume("/workspace")))
                .withMemory(memoryLimitBytes)
                .withNanoCPUs(1_000_000_000L)  // 1 CPU
                .withPidsLimit(100L)
                .withNetworkMode("none");  // No network access

        // Always use the single configured base image (e.g., drogon-scaffold:1.0)
        // Version/standard selection is handled by post-generation CMakeLists.txt patching
        String fullImageName = imageName + ":" + imageTag;
        log.info("Using Docker image: {}", fullImageName);

        // Build drogon_ctl command
        String[] command = buildDrogonCommand(projectName, projectType);

        CreateContainerResponse container = dockerClient.createContainerCmd(fullImageName)
                .withName("drogon-scaffold-" + jobId)
                .withHostConfig(hostConfig)
                .withWorkingDir("/workspace")
                // .withUser("1001:1001")  // COMMENTED OUT - was causing permission errors
                .withUser("1000:1000") // Resolved the issue by rebuilding the image
                .withCmd(command)
                .withLabels(Map.of(
                        "job-id", jobId,
                        "project-name", projectName,
                        "managed-by", "drogon-scaffolder"
                ))
                .exec();

        return container.getId();
    }

    private String[] buildDrogonCommand(String projectName, String projectType) {
        return new String[]{
                "drogon_ctl",
                "create",
                "project",
                projectName
        };
    }

    private String getContainerLogs(String containerId) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .exec(new com.github.dockerjava.core.command.LogContainerResultCallback() {
                        @Override
                        public void onNext(com.github.dockerjava.api.model.Frame frame) {
                            try {
                                outputStream.write(frame.getPayload());
                            } catch (IOException ignored) {
                            }
                        }
                    })
                    .awaitCompletion(5, TimeUnit.SECONDS);

            return outputStream.toString();

        } catch (Exception e) {
            return "Failed to retrieve logs: " + e.getMessage();
        }
    }

    private void cleanupContainer(String containerId) {
        try {
            try {
                dockerClient.stopContainerCmd(containerId)
                        .withTimeout(10)
                        .exec();
            } catch (Exception ignored) {
            }

            dockerClient.removeContainerCmd(containerId)
                    .withForce(true)
                    .exec();

        } catch (Exception e) {
            log.warn("Failed to cleanup container {}: {}", containerId, e.getMessage());
        }
    }

    /**
     * Patch the generated CMakeLists.txt to reflect the user's chosen Drogon version
     * and C++ standard.
     *
     * drogon_ctl generates a template CMakeLists.txt with:
     * - A conditional block that auto-detects the C++ standard (lines 9-17 in template)
     * - find_package(Drogon CONFIG REQUIRED) with no version pin
     *
     * This method replaces the conditional block with a fixed standard and adds
     * a minimum version to find_package.
     */
    private void patchCMakeLists(Path workspacePath, String projectName,
                                String drogonVersion, String cppStandard) {
        // The project may be directly in workspace or in a subdirectory
        Path cmakeFile = workspacePath.resolve(projectName).resolve("CMakeLists.txt");
        if (!Files.exists(cmakeFile)) {
            cmakeFile = workspacePath.resolve("CMakeLists.txt");
        }
        if (!Files.exists(cmakeFile)) {
            log.warn("CMakeLists.txt not found in workspace, skipping patch");
            return;
        }

        try {
            String content = Files.readString(cmakeFile);

            // 1. Replace the entire C++ standard conditional block with a fixed value.
            //    The generated template has:
            //      if (NOT "${CMAKE_CXX_STANDARD}" STREQUAL "")
            //          # Do nothing
            //      elseif (...)
            //          set(CMAKE_CXX_STANDARD 17)
            //      ...
            //      endif ()
            if (cppStandard != null) {
                // Match the full conditional block from 'if (NOT "${CMAKE_CXX_STANDARD}"'
                // through 'endif ()'
                content = content.replaceAll(
                        "(?s)if\\s*\\(\\s*NOT\\s+\"\\$\\{CMAKE_CXX_STANDARD\\}\".*?endif\\s*\\(\\s*\\)",
                        "set(CMAKE_CXX_STANDARD " + cppStandard + ")"
                );
                log.info("Patched CMAKE_CXX_STANDARD to {} in CMakeLists.txt", cppStandard);
            }

            // 2. Pin Drogon version in find_package.
            //    Generated template: find_package(Drogon CONFIG REQUIRED)
            //    Patched:             find_package(Drogon 1.9.8 CONFIG REQUIRED)
            if (drogonVersion != null) {
                String numericVersion = stripLeadingV(drogonVersion);
                content = content.replace(
                        "find_package(Drogon CONFIG REQUIRED)",
                        "find_package(Drogon " + numericVersion + " CONFIG REQUIRED)"
                );
                log.info("Pinned Drogon version to {} in CMakeLists.txt", numericVersion);
            }

            Files.writeString(cmakeFile, content);
            log.info("CMakeLists.txt patched successfully for project {}", projectName);

        } catch (IOException e) {
            log.warn("Failed to patch CMakeLists.txt: {}", e.getMessage());
        }
    }

    /**
     * Strip the leading 'v' from a version tag (e.g., "v1.9.8" → "1.9.8").
     */
    private String stripLeadingV(String version) {
        return (version != null && version.startsWith("v")) ? version.substring(1) : version;
    }

    /**
     * Result of container execution
     */
    public static class ContainerExecutionResult {

        private final boolean success;
        private final String message;
        private final String logs;

        private ContainerExecutionResult(boolean success, String message, String logs) {
            this.success = success;
            this.message = message;
            this.logs = logs;
        }

        public static ContainerExecutionResult success(String logs) {
            return new ContainerExecutionResult(true, "Success", logs);
        }

        public static ContainerExecutionResult failure(String message, String logs) {
            return new ContainerExecutionResult(false, message, logs);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getLogs() {
            return logs;
        }
    }
}
