package com.ayush.drogonStart.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
public class CleanupService {

    private static final String WORKSPACE_DIR = "/tmp/drogon-workspaces";
    private static final long MAX_AGE_MINUTES = 60; // ⏱ change if needed

    @Scheduled(fixedRate = 15 * 60 * 1000) // every 15 minutes
    public void cleanupOldWorkspaces() {
        log.info("Running cleanup job...");

        try {
            Path workspaceRoot = Paths.get(WORKSPACE_DIR);

            if (!Files.exists(workspaceRoot)) {
                return;
            }

            Files.list(workspaceRoot).forEach(path -> {
                try {
                    Instant lastModified = Files.getLastModifiedTime(path).toInstant();
                    Instant cutoff = Instant.now().minus(MAX_AGE_MINUTES, ChronoUnit.MINUTES);

                    if (lastModified.isBefore(cutoff)) {
                        deleteRecursively(path);
                        log.info("Deleted old workspace: {}", path);
                    }

                } catch (Exception e) {
                    log.warn("Failed to check/delete: {}", path, e);
                }
            });

        } catch (IOException e) {
            log.error("Cleanup job failed", e);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        Files.walk(path)
                .sorted((a, b) -> b.compareTo(a)) // delete children first
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        log.warn("Failed to delete {}", p);
                    }
                });
    }
}