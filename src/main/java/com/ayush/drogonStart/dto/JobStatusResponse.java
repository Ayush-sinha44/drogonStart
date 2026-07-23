package com.ayush.drogonStart.dto;

import com.ayush.drogonStart.model.JobStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class JobStatusResponse {
    private String jobId;
    private JobStatus status;
    private String projectName;
    private String projectType;
    private Integer port;
    private String cppStandard;
    private String drogonVersion;
    private List<String> selectedDependencies;
    private Integer filesGenerated;
    private Long projectSizeBytes;
    private String downloadUrl;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private Long durationSeconds;
}
