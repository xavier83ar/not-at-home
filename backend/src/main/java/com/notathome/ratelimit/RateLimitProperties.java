package com.notathome.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code app.rate-limit.enabled}: on by default. Switch it off (environment variable {@code
 * APP_RATE_LIMIT_ENABLED=false}) only for local testing, where you may well sign up or log in more
 * often than a real person would.
 */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(@DefaultValue("true") boolean enabled) {}
