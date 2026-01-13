package com.ayush.drogonStart.dto;

import com.ayush.drogonStart.model.JobStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class JobResponse {
    private String jobId;
    private JobStatus status;
    private String message;
    private String statusUrl;
    private LocalDateTime createdAt;
}
