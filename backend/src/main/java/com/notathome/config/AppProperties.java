package com.notathome.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Application settings under the {@code app} prefix. */
@Validated
@ConfigurationProperties("app")
public record AppProperties(@NotBlank String frontendUrl, @Valid Mail mail) {

  /** {@code from} is the sender address of every email. */
  public record Mail(@NotBlank String from) {}

  /** The public address of the site, without a trailing slash; used for links in emails. */
  @Override
  public String frontendUrl() {
    return frontendUrl.endsWith("/")
        ? frontendUrl.substring(0, frontendUrl.length() - 1)
        : frontendUrl;
  }
}
