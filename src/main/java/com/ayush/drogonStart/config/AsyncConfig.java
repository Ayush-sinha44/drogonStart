package com.ayush.drogonStart.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
/**
 * Enables asynchronous method execution across the application.
 *
 * Marking this class with @EnableAsync activates Spring's async processing,
 * allowing methods annotated with @Async to run on a background thread instead
 * of blocking the caller. This is particularly useful for long-running tasks
 * such as Docker container orchestration, where the HTTP request thread should
 * not be held up waiting for the operation to complete.
 *
 * No custom thread pool is defined here, so Spring uses its default executor.
 * If finer control over pool size or queue capacity is needed in production,
 * implement AsyncConfigurer and define a custom ThreadPoolTaskExecutor bean.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    // This enables @Async annotation support
}
