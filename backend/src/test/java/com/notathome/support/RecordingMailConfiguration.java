package com.notathome.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the SMTP sender in tests. Inject {@link RecordingMailSender} to read what was sent. */
@TestConfiguration(proxyBeanMethods = false)
public class RecordingMailConfiguration {

  @Bean
  @Primary
  RecordingMailSender recordingMailSender() {
    return new RecordingMailSender();
  }
}
