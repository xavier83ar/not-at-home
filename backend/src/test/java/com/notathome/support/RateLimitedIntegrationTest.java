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
 * Like {@link IntegrationTest}, but with rate limiting <em>on</em> and a clock the test controls.
 * It is its own application context, so the limits don't leak into the other tests.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({
  TestcontainersConfiguration.class,
  RecordingMailConfiguration.class,
  ManualClockConfiguration.class
})
@SpringBootTest(properties = "app.rate-limit.enabled=true")
@AutoConfigureMockMvc
public @interface RateLimitedIntegrationTest {}
