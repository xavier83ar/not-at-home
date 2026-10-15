package com.notathome.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AppPropertiesTests {

  @Test
  void frontendUrlLosesItsTrailingSlash() {
    AppProperties properties =
        new AppProperties("https://notathome.example/", new AppProperties.Mail("a@b.c"));

    assertThat(properties.frontendUrl()).isEqualTo("https://notathome.example");
  }

  @Test
  void frontendUrlWithoutSlashIsUnchanged() {
    AppProperties properties =
        new AppProperties("http://localhost:3000", new AppProperties.Mail("a@b.c"));

    assertThat(properties.frontendUrl()).isEqualTo("http://localhost:3000");
  }
}
