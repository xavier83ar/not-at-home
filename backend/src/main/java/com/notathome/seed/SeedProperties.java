package com.notathome.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.seed.*}: credentials for the default admin and user (FR-10.3, FR-10.4). Deliberately
 * unvalidated here (no {@code @NotBlank}): these are blank on every normal run, since the {@code
 * seed} profile is the only time they matter. {@link SeedDataService} checks them itself, only when
 * it actually runs.
 */
@ConfigurationProperties("app.seed")
record SeedProperties(
    String adminUsername,
    String adminEmail,
    String adminPassword,
    String userUsername,
    String userEmail,
    String userPassword) {}
