package com.ayush.drogonStart.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.Stream;

@Service
@Slf4j
public class FileManager {

    private final Path workspaceBase;

    public FileManager(@Value("${scaffolding.workspace-base}") String workspaceBase) {
        this.workspaceBase = Paths.get(workspaceBase);
        try {
            Files.createDirectories(this.workspaceBase);
            log.info("Workspace base directory: {}", this.workspaceBase.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to create workspace base directory", e);
            throw new RuntimeException("Failed to initialize workspace", e);
        }
    }

    /**
     * Create a unique workspace directory for a job
     */
    public Path createWorkspace(String jobId) throws IOException {
        Path workspace = workspaceBase.resolve(jobId);
        Files.createDirectories(workspace);
        log.info("Created workspace: {}", workspace.toAbsolutePath());
        return workspace;
    }

    /**
     * Count files in a directory
     */
    public int countFiles(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            return (int) files.filter(Files::isRegularFile).count();
        }
    }

    /**
     * Calculate directory size
     */
    public long calculateSize(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
        }
    }

    /**
     * ZIP a directory
     */
    public Path zipDirectory(Path sourceDir, String projectName) throws IOException {
        Path zipPath = workspaceBase.resolve(projectName + ".zip");

        try (ZipArchiveOutputStream zipOut = new ZipArchiveOutputStream(Files.newOutputStream(zipPath))) {
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relativePath = sourceDir.relativize(file);
                    ZipArchiveEntry entry = new ZipArchiveEntry(file.toFile(), relativePath.toString());
                    zipOut.putArchiveEntry(entry);
                    Files.copy(file, zipOut);
                    zipOut.closeArchiveEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        log.info("Created ZIP file: {}", zipPath.toAbsolutePath());
        return zipPath;
    }

    /**
     * Delete a directory recursively
     */
    public void deleteDirectory(Path directory) {
        try {
            if (Files.exists(directory)) {
                Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
                log.info("Deleted directory: {}", directory);
            }
        } catch (IOException e) {
            log.error("Failed to delete directory: {}", directory, e);
        }
    }
}
