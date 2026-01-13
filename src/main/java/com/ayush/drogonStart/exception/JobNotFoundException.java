package com.ayush.drogonStart.exception;

public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(String jobId) {
        super("Job not found with ID: " + jobId);
    }
}
