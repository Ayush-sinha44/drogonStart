package com.ayush.drogonStart.model;

import com.ayush.drogonStart.model.JobStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {

    @Id
    private String id;

    @Column(nullable = false)
    private String projectName;

    @Column(nullable = false)
    private String projectType;

    @Column
    private Integer port;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(length = 2000)
    private String errorMessage;

    @Column(length = 500)
    private String downloadUrl;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime completedAt;

    @Column
    private Integer filesGenerated;

    @Column
    private Long projectSizeBytes;

    /**
     * Comma-separated list of selected dependency IDs.
     * e.g. "postgresql,redis,spdlog"
     */
    @Column(length = 1000)
    private String selectedDependencies;

    /** C++ standard used for this project (e.g. "17", "20") */
    @Column(length = 10)
    private String cppStandard;

    /** Drogon framework version tag used (e.g. "v1.9.8") */
    @Column(length = 20)
    private String drogonVersion;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = JobStatus.QUEUED;
        }
    }
}