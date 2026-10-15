package com.notathome.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Turns on {@code @Async}; Spring Boot's application task executor runs the methods. */
@Configuration
@EnableAsync
class AsyncConfig {}
