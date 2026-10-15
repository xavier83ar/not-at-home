package com.notathome.support;

import com.notathome.TestcontainersConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * A full application (real PostgreSQL, recorded emails) driven through MockMvc, with rate limiting
 * off (the tests log in far more often than a real person; see {@link RateLimitedIntegrationTest}).
 * Test classes that use it share one cached application context, which keeps the suite fast.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({TestcontainersConfiguration.class, RecordingMailConfiguration.class})
@SpringBootTest(properties = "app.rate-limit.enabled=false")
@AutoConfigureMockMvc
public @interface IntegrationTest {}
