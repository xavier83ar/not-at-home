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
 * Like {@link IntegrationTest}, but with seed credentials configured. Its own application context,
 * since the seed properties are blank everywhere else (see {@code SeedProperties}).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({TestcontainersConfiguration.class, RecordingMailConfiguration.class})
@SpringBootTest(
    properties = {
      "app.rate-limit.enabled=false",
      "app.seed.admin-username=admin",
      "app.seed.admin-email=admin@example.com",
      "app.seed.admin-password=correct horse",
      "app.seed.user-username=demo",
      "app.seed.user-email=demo@example.com",
      "app.seed.user-password=correct horse"
    })
@AutoConfigureMockMvc
public @interface SeedIntegrationTest {}
