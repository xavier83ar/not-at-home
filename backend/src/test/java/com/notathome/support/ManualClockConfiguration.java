package com.notathome.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Replaces the rate limiter's clock. Inject {@link ManualClock} to move time. */
@TestConfiguration(proxyBeanMethods = false)
public class ManualClockConfiguration {

  @Bean
  ManualClock manualClock() {
    return new ManualClock();
  }
}
