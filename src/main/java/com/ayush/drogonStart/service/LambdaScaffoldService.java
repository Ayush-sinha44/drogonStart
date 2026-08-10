package com.ayush.drogonStart.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * Replaces ContainerManager. Instead of spinning up Docker containers,
 * this service invokes an AWS Lambda function (Go binary) that generates
 * the base Drogon project, zips it, and uploads to S3.
 *
 * After Lambda returns, this service downloads the base ZIP, extracts it
 * to a local temp directory so that ProjectCustomizer can apply patches,
 * then re-zips and re-uploads the final version to S3.
 */
@Service
@Slf4j
public class LambdaScaffoldService {

    private final LambdaClient lambdaClient;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ObjectMapper objectMapper;
    private final String functionName;
    private final String s3Bucket;
    private final String awsRegion;

    public LambdaScaffoldService(
            @Value("${aws.lambda.function-name}") String functionName,
            @Value("${aws.s3.bucket}") String s3Bucket,
            @Value("${aws.region}") String awsRegion) {

        this.functionName = functionName;
        this.s3Bucket = s3Bucket;
        this.awsRegion = awsRegion;
        this.objectMapper = new ObjectMapper();

        Region region = Region.of(awsRegion);

        this.lambdaClient = LambdaClient.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        this.s3Client = S3Client.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        this.s3Presigner = S3Presigner.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        log.info("LambdaScaffoldService initialized — function: {}, bucket: {}, region: {}",
                functionName, s3Bucket, awsRegion);
    }

    /**
     * Invoke the Lambda function to generate a base Drogon project.
     *
     * @param jobId       Unique job identifier
     * @param projectName Name of the project to generate
     * @param projectType Project type (e.g., "api")
     * @return Result containing the S3 key of the base project ZIP
     */
    public ScaffoldResult invokeScaffoldLambda(String jobId, String projectName, String projectType) {
        try {
            // Build the Lambda payload
            String payload = objectMapper.writeValueAsString(Map.of(
                    "jobId", jobId,
                    "projectName", projectName,
                    "projectType", projectType
            ));

            log.info("Invoking Lambda {} for job {} (project: {})", functionName, jobId, projectName);

            InvokeRequest request = InvokeRequest.builder()
                    .functionName(functionName)
                    .payload(SdkBytes.fromUtf8String(payload))
                    .build();

            InvokeResponse response = lambdaClient.invoke(request);

            // Check for Lambda-level errors
            if (response.functionError() != null && !response.functionError().isEmpty()) {
                String errorPayload = response.payload().asUtf8String();
                log.error("Lambda function error: {}", errorPayload);
                return ScaffoldResult.failure("Lambda execution error: " + errorPayload);
            }

            // Parse the response
            String responsePayload = response.payload().asUtf8String();
            JsonNode responseJson = objectMapper.readTree(responsePayload);

            String s3Key = responseJson.get("s3Key").asText();
            int fileCount = responseJson.has("fileCount") ? responseJson.get("fileCount").asInt() : 0;

            log.info("Lambda returned s3Key: {}, fileCount: {}", s3Key, fileCount);
            return ScaffoldResult.success(s3Key, fileCount);

        } catch (Exception e) {
            log.error("Failed to invoke Lambda: {}", e.getMessage(), e);
            return ScaffoldResult.failure("Lambda invocation failed: " + e.getMessage());
        }
    }

    /**
     * Download a ZIP from S3 and extract it to a local directory.
     *
     * @param s3Key         The S3 key of the ZIP file
     * @param extractToPath The local directory to extract into
     * @return Path to the extracted project directory
     */
    public Path downloadAndExtract(String s3Key, Path extractToPath) throws IOException {
        log.info("Downloading {} from S3 bucket {}", s3Key, s3Bucket);

        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(s3Bucket)
                .key(s3Key)
                .build();

        Path zipPath = extractToPath.resolve("base-project.zip");

        try (InputStream inputStream = s3Client.getObject(getRequest)) {
            Files.copy(inputStream, zipPath);
        }

        log.info("Downloaded {} bytes, extracting...", Files.size(zipPath));

        // Extract ZIP
        extractZip(zipPath, extractToPath);

        // Clean up the downloaded ZIP
        Files.deleteIfExists(zipPath);

        return extractToPath;
    }

    /**
     * Upload the final customized project ZIP to S3.
     *
     * @param zipPath Local path to the ZIP file
     * @param jobId   Job identifier for S3 key
     * @param projectName Project name for the filename
     * @return S3 key of the uploaded file
     */
    public String uploadFinalZip(Path zipPath, String jobId, String projectName) throws IOException {
        String s3Key = "jobs/" + jobId + "/" + projectName + ".zip";

        log.info("Uploading final ZIP to s3://{}/{}", s3Bucket, s3Key);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(s3Bucket)
                .key(s3Key)
                .contentType("application/zip")
                .build();

        s3Client.putObject(putRequest, zipPath);

        log.info("Upload complete: {}", s3Key);
        return s3Key;
    }

    /**
     * Generate a presigned download URL for a ZIP file in S3.
     *
     * @param s3Key Key of the file in S3
     * @return Presigned URL valid for 1 hour
     */
    public String generatePresignedUrl(String s3Key) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(s3Bucket)
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1))
                .getObjectRequest(getRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

        String url = presignedRequest.url().toString();
        log.info("Generated presigned URL for {} (expires in 1 hour)", s3Key);
        return url;
    }

    /**
     * Delete a file from S3 (cleanup after download or expiry).
     */
    public void deleteFromS3(String s3Key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Bucket)
                    .key(s3Key)
                    .build());
            log.info("Deleted s3://{}/{}", s3Bucket, s3Key);
        } catch (Exception e) {
            log.warn("Failed to delete S3 object {}: {}", s3Key, e.getMessage());
        }
    }

    /**
     * Extract a ZIP file to a target directory using commons-compress.
     */
    private void extractZip(Path zipPath, Path targetDir) throws IOException {
        try (var zipFile = new org.apache.commons.compress.archivers.zip.ZipFile(zipPath.toFile())) {
            var entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                Path entryPath = targetDir.resolve(entry.getName()).normalize();

                // Prevent zip-slip vulnerability
                if (!entryPath.startsWith(targetDir)) {
                    throw new IOException("Zip entry outside target dir: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        Files.copy(is, entryPath);
                    }
                }
            }
        }
        log.info("Extracted ZIP to {}", targetDir);
    }

    // ==================== RESULT CLASS ====================

    /**
     * Result of Lambda scaffold invocation.
     */
    public static class ScaffoldResult {
        private final boolean success;
        private final String message;
        private final String s3Key;
        private final int fileCount;

        private ScaffoldResult(boolean success, String message, String s3Key, int fileCount) {
            this.success = success;
            this.message = message;
            this.s3Key = s3Key;
            this.fileCount = fileCount;
        }

        public static ScaffoldResult success(String s3Key, int fileCount) {
            return new ScaffoldResult(true, "Success", s3Key, fileCount);
        }

        public static ScaffoldResult failure(String message) {
            return new ScaffoldResult(false, message, null, 0);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getS3Key() { return s3Key; }
        public int getFileCount() { return fileCount; }
    }
}
