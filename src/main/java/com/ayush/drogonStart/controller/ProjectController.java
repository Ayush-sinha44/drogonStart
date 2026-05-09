package com.ayush.drogonStart.controller;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.file.Files;
import com.ayush.drogonStart.dto.JobResponse;
import com.ayush.drogonStart.dto.JobStatusResponse;
import com.ayush.drogonStart.dto.ProjectRequest;
import com.ayush.drogonStart.model.Job;
import com.ayush.drogonStart.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
@Slf4j
public class ProjectController {

    private final ProjectService projectService;


    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        e.printStackTrace(); // This will force the full error into your terminal
        return ResponseEntity.status(500).body(e.getMessage());
    }

    /**
     * Create a new project scaffolding job
     * POST /api/v1/projects
     */
    @PostMapping
    public ResponseEntity<JobResponse> createProject(@Valid @RequestBody ProjectRequest request) {
        log.info("Received project creation request: {}", request);

        Job job = projectService.createProject(request);

        JobResponse response = JobResponse.builder()
                .jobId(job.getId())
                .status(job.getStatus())
                .message("Project scaffolding job created successfully")
                .statusUrl("/api/v1/projects/" + job.getId() + "/status")
                .createdAt(job.getCreatedAt())
                .build();

        return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
    }

    /**
     * Get job status
     * GET /api/v1/projects/{jobId}/status
     */
    @GetMapping("/{jobId}/status")
    public ResponseEntity<JobStatusResponse> getJobStatus(@PathVariable String jobId) {
        log.info("Checking status for job: {}", jobId);

        JobStatusResponse response = projectService.getJobStatus(jobId);
        return ResponseEntity.ok(response);
    }
    /**
     * Download project ZIP file
     * GET /api/v1/projects/{jobId}/download
     */
    @GetMapping("/{jobId}/download")
    public ResponseEntity<Resource> downloadProject(@PathVariable String jobId) throws IOException, IOException {
        log.info("Download requested for job: {}", jobId);

        Path zipPath = projectService.getDownloadPath(jobId);

        if (!Files.exists(zipPath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new org.springframework.core.io.FileSystemResource(zipPath);

        String filename = zipPath.getFileName().toString();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(zipPath))
                .body(resource);
    }

    /**
     * Get job details (alias for status)
     * GET /api/v1/projects/{jobId}
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<JobStatusResponse> getJob(@PathVariable String jobId) {
        return getJobStatus(jobId);
    }

    /**
     * Get all jobs
     * GET /api/v1/projects
     */
    @GetMapping
    public ResponseEntity<List<Job>> getAllJobs() {
        log.info("Fetching all jobs");
        List<Job> jobs = projectService.getAllJobs();
        return ResponseEntity.ok(jobs);
    }

    /**
     * Delete a job
     * DELETE /api/v1/projects/{jobId}
     */
    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> deleteJob(@PathVariable String jobId) {
        log.info("Deleting job: {}", jobId);
        projectService.deleteJob(jobId);
        return ResponseEntity.noContent().build();
    }


}
