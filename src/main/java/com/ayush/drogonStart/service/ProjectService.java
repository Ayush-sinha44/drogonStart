package com.ayush.drogonStart.service;

import com.ayush.drogonStart.dto.JobStatusResponse;
import com.ayush.drogonStart.dto.ProjectRequest;
import com.ayush.drogonStart.exception.JobNotFoundException;
import com.ayush.drogonStart.model.Job;
import com.ayush.drogonStart.model.JobStatus;
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

    /**
     * Creates a new project scaffolding job
     */
    public Job createProject(ProjectRequest request) {
        String jobId = UUID.randomUUID().toString();

        Job job = Job.builder()
                .id(jobId)
                .projectName(request.getName())
                .projectType(request.getProjectType())
                .port(request.getPort())
                .status(JobStatus.QUEUED)
                .createdAt(LocalDateTime.now())
                .build();

        jobRepository.save(job);

        log.info("Created job {} for project: {}", jobId, request.getName());

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
                    workspace
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

            // Analyze generated project
            int fileCount = fileManager.countFiles(projectPath);
            long projectSize = fileManager.calculateSize(projectPath);

            if (fileCount == 0) {
                throw new RuntimeException("No files were generated");
            }

            log.info("Project generated: {} files, {} bytes", fileCount, projectSize);

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
}