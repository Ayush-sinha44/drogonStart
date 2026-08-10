package com.ayush.drogonStart.service;

import com.ayush.drogonStart.dto.JobStatusResponse;
import com.ayush.drogonStart.dto.ProjectRequest;
import com.ayush.drogonStart.exception.JobNotFoundException;
import com.ayush.drogonStart.model.Job;
import com.ayush.drogonStart.model.JobStatus;
import com.ayush.drogonStart.registry.BuildOptionsRegistry;
import com.ayush.drogonStart.registry.DependencyRegistry;
import com.ayush.drogonStart.repository.JobRepository;
import com.ayush.drogonStart.service.LambdaScaffoldService.ScaffoldResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
    private final LambdaScaffoldService lambdaScaffoldService;
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
     * Process project generation via AWS Lambda + S3.
     *
     * Flow:
     * 1. Invoke Lambda → generates base project → uploads ZIP to S3
     * 2. Download ZIP from S3 → extract to local temp dir
     * 3. Apply customizations (dependencies, C++ standard, Drogon version, port)
     * 4. Re-zip → upload final ZIP to S3
     * 5. Store presigned download URL in job
     */
    @Async
    public CompletableFuture<Void> processProjectAsync(String jobId) {
        log.info("Starting Lambda-based processing for job: {}", jobId);

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        Path workspace = null;

        try {
            // Update status to PROCESSING
            job.setStatus(JobStatus.PROCESSING);
            jobRepository.save(job);
            log.info("Job {} status changed to PROCESSING", jobId);

            // Step 1: Invoke Lambda to generate the base project
            log.info("Invoking Lambda for job {}...", jobId);
            ScaffoldResult result = lambdaScaffoldService.invokeScaffoldLambda(
                    jobId,
                    job.getProjectName(),
                    job.getProjectType()
            );

            if (!result.isSuccess()) {
                throw new RuntimeException("Lambda scaffolding failed: " + result.getMessage());
            }

            log.info("Lambda execution successful. Base project at S3 key: {}", result.getS3Key());

            // Step 2: Download and extract the base project from S3
            workspace = fileManager.createWorkspace(jobId);
            lambdaScaffoldService.downloadAndExtract(result.getS3Key(), workspace);

            // Lambda generates the project inside a subdirectory named after the project
            Path projectPath = workspace.resolve(job.getProjectName());

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

            // Step 3: Apply customizations (dependencies, C++ standard, Drogon version, port)
            List<String> depIds = parseSelectedDependencies(job.getSelectedDependencies());
            projectCustomizer.customize(
                    projectPath,
                    depIds,
                    job.getPort(),
                    job.getCppStandard(),
                    job.getDrogonVersion()
            );

            // Re-count after customization (example files may have been added)
            fileCount = fileManager.countFiles(projectPath);
            long projectSize = fileManager.calculateSize(projectPath);

            log.info("Final project: {} files, {} bytes (after customization)", fileCount, projectSize);

            // Step 4: ZIP the customized project and upload to S3
            Path zipPath = fileManager.zipDirectory(projectPath, job.getProjectName());
            String finalS3Key = lambdaScaffoldService.uploadFinalZip(zipPath, jobId, job.getProjectName());

            // Step 5: Generate presigned download URL
            String downloadUrl = lambdaScaffoldService.generatePresignedUrl(finalS3Key);

            // Update job with success
            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            job.setFilesGenerated(fileCount);
            job.setProjectSizeBytes(projectSize);
            job.setDownloadUrl(downloadUrl);

            jobRepository.save(job);
            log.info("Job {} completed successfully", jobId);

            // Clean up the base ZIP from S3 (we only need the final one)
            lambdaScaffoldService.deleteFromS3(result.getS3Key());

        } catch (Exception e) {
            log.error("Job {} failed with error: {}", jobId, e.getMessage(), e);

            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);

        } finally {
            // Cleanup local workspace
            if (workspace != null) {
                fileManager.deleteDirectory(workspace);
            }
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
     * Get the download URL for a completed job.
     * In the Lambda+S3 architecture, this returns the presigned S3 URL
     * stored in the job record.
     */
    public String getDownloadUrl(String jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        if (job.getStatus() != JobStatus.COMPLETED) {
            throw new IllegalStateException("Job is not completed yet");
        }

        // If the presigned URL has expired, regenerate it
        // For now, return the stored URL (valid for 1 hour from generation)
        return job.getDownloadUrl();
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