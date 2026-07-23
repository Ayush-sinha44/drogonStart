package com.ayush.drogonStart.service;

import com.ayush.drogonStart.dto.JobStatusResponse;
import com.ayush.drogonStart.dto.ProjectRequest;
import com.ayush.drogonStart.exception.JobNotFoundException;
import com.ayush.drogonStart.model.Job;
import com.ayush.drogonStart.model.JobStatus;
import com.ayush.drogonStart.registry.BuildOptionsRegistry;
import com.ayush.drogonStart.registry.DependencyRegistry;
import com.ayush.drogonStart.repository.JobRepository;
import com.ayush.drogonStart.service.ContainerManager.ContainerExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final JobRepository jobRepository;
    private final ContainerManager containerManager;
    private final FileManager fileManager;
    private final ProjectCustomizer projectCustomizer;
    private final DependencyRegistry dependencyRegistry;
    private final BuildOptionsRegistry buildOptionsRegistry;

    /**
     * Creates a new project scaffolding job
     */
    public Job createProject(ProjectRequest request) {
        String jobId = UUID.randomUUID().toString();

        // Validate dependency IDs if any are provided
        if (request.getDependencies() != null && !request.getDependencies().isEmpty()) {
            List<String> invalidIds = dependencyRegistry.validateIds(request.getDependencies());
            if (!invalidIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "Unknown dependency IDs: " + String.join(", ", invalidIds)
                        + ". Use GET /api/v1/dependencies to see available options.");
            }
        }

        // Apply defaults for C++ standard and Drogon version
        String cppStandard = (request.getCppStandard() != null)
                ? request.getCppStandard()
                : BuildOptionsRegistry.DEFAULT_CPP_STANDARD;

        String drogonVersion = (request.getDrogonVersion() != null)
                ? request.getDrogonVersion()
                : BuildOptionsRegistry.DEFAULT_DROGON_VERSION;

        // Validate against allowed values
        String cppError = buildOptionsRegistry.validateCppStandard(cppStandard);
        if (cppError != null) {
            throw new IllegalArgumentException(cppError);
        }

        String drogonError = buildOptionsRegistry.validateDrogonVersion(drogonVersion);
        if (drogonError != null) {
            throw new IllegalArgumentException(drogonError);
        }

        // Store selected dependencies as comma-separated string
        String selectedDeps = (request.getDependencies() != null && !request.getDependencies().isEmpty())
                ? String.join(",", request.getDependencies())
                : null;

        Job job = Job.builder()
                .id(jobId)
                .projectName(request.getName())
                .projectType(request.getProjectType())
                .port(request.getPort())
                .cppStandard(cppStandard)
                .drogonVersion(drogonVersion)
                .selectedDependencies(selectedDeps)
                .status(JobStatus.QUEUED)
                .createdAt(LocalDateTime.now())
                .build();

        jobRepository.save(job);

        log.info("Created job {} for project: {} (C++{}, Drogon {}, deps: {})",
                jobId, request.getName(), cppStandard, drogonVersion, selectedDeps);

        // Start async processing
        processProjectAsync(jobId);

        return job;
    }

    /**
     * Process project generation using Docker
     */
    @Async
    public CompletableFuture<Void> processProjectAsync(String jobId) {
        log.info("Starting Docker-based processing for job: {}", jobId);

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        Path workspace = null;
        Path projectPath = null;
        Path zipPath = null;

        try {
            // Update status to PROCESSING
            job.setStatus(JobStatus.PROCESSING);
            jobRepository.save(job);
            log.info("Job {} status changed to PROCESSING", jobId);

            // Create workspace
            workspace = fileManager.createWorkspace(jobId);

            // Execute Docker container
            log.info("Executing Docker container for job {}...", jobId);
            ContainerExecutionResult result = containerManager.executeScaffolding(
                    jobId,
                    job.getProjectName(),
                    job.getProjectType(),
                    workspace,
                    job.getCppStandard(),
                    job.getDrogonVersion()
            );

            if (!result.isSuccess()) {
                throw new RuntimeException(result.getMessage() + "\nLogs:\n" + result.getLogs());
            }

            log.info("Container execution successful. Checking generated files...");

            // Drogon creates project directly in workspace OR in a subdirectory
            // Check both locations
            projectPath = workspace.resolve(job.getProjectName());

            if (!java.nio.file.Files.exists(projectPath)) {
                log.info("Project subdirectory not found, using workspace directly");
                projectPath = workspace;
            }

            if (!java.nio.file.Files.exists(projectPath) || !java.nio.file.Files.isDirectory(projectPath)) {
                throw new RuntimeException("Project directory not found at: " + projectPath);
            }

            // Analyze generated project (before customization)
            int fileCount = fileManager.countFiles(projectPath);

            if (fileCount == 0) {
                throw new RuntimeException("No files were generated");
            }

            log.info("Base project generated: {} files", fileCount);

            // Apply dependency customizations (modify CMakeLists.txt, config.json, add examples)
            List<String> depIds = parseSelectedDependencies(job.getSelectedDependencies());
            if (!depIds.isEmpty()) {
                log.info("Applying {} dependency customizations...", depIds.size());
                projectCustomizer.customize(projectPath, depIds, job.getPort(), job.getCppStandard());
            } else {
                // Still apply port and C++ standard customization even without dependencies
                projectCustomizer.customize(projectPath, Collections.emptyList(), job.getPort(), job.getCppStandard());
            }

            // Re-count after customization (example files may have been added)
            fileCount = fileManager.countFiles(projectPath);
            long projectSize = fileManager.calculateSize(projectPath);

            log.info("Final project: {} files, {} bytes (after customization)", fileCount, projectSize);

            // Create ZIP file
            zipPath = fileManager.zipDirectory(projectPath, job.getProjectName());

            // Update job with success
            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            job.setFilesGenerated(fileCount);
            job.setProjectSizeBytes(projectSize);
            job.setDownloadUrl("/api/v1/projects/" + jobId + "/download");

            jobRepository.save(job);
            log.info("Job {} completed successfully", jobId);

        } catch (Exception e) {
            log.error("Job {} failed with error: {}", jobId, e.getMessage(), e);

            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);

        } finally {
//            // Cleanup workspace (but keep ZIP for download)
//            if (workspace != null) {
//                fileManager.deleteDirectory(workspace);
//            }
             log.info("Finally block reached. Workspace NOT deleted for debugging.");
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Get job status
     */
    public JobStatusResponse getJobStatus(String jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        Long durationSeconds = null;
        if (job.getCompletedAt() != null) {
            durationSeconds = Duration.between(job.getCreatedAt(), job.getCompletedAt()).getSeconds();
        }

        return JobStatusResponse.builder()
                .jobId(job.getId())
                .status(job.getStatus())
                .projectName(job.getProjectName())
                .projectType(job.getProjectType())
                .port(job.getPort())
                .cppStandard(job.getCppStandard())
                .drogonVersion(job.getDrogonVersion())
                .selectedDependencies(parseSelectedDependencies(job.getSelectedDependencies()))
                .filesGenerated(job.getFilesGenerated())
                .projectSizeBytes(job.getProjectSizeBytes())
                .downloadUrl(job.getDownloadUrl())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .durationSeconds(durationSeconds)
                .build();
    }

    /**
     * Get all jobs
     */
    public List<Job> getAllJobs() {
        return jobRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Delete a job
     */
    public void deleteJob(String jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        jobRepository.delete(job);
        log.info("Deleted job: {}", jobId);
    }

    /**
     * Get ZIP file path for download
     */
    public Path getDownloadPath(String jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        if (job.getStatus() != JobStatus.COMPLETED) {
            throw new IllegalStateException("Job is not completed yet");
        }

        return Path.of("/tmp/drogon-workspaces/" + job.getProjectName() + ".zip");
    }

    /**
     * Parse comma-separated dependency IDs into a list.
     */
    private List<String> parseSelectedDependencies(String selectedDependencies) {
        if (selectedDependencies == null || selectedDependencies.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.asList(selectedDependencies.split(","));
    }
}