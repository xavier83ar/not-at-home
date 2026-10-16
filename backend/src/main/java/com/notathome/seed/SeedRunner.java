package com.notathome.seed;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The {@code seed} profile is a one-off install step (FR-10.3), not a server: this always exits
 * instead of going on to serve requests, whether seeding happened, was skipped, or failed.
 */
@Component
@Profile("seed")
class SeedRunner implements CommandLineRunner {

  private final SeedDataService seedDataService;
  private final ConfigurableApplicationContext context;

  SeedRunner(SeedDataService seedDataService, ConfigurableApplicationContext context) {
    this.seedDataService = seedDataService;
    this.context = context;
  }

  @Override
  public void run(String... args) {
    int exitCode = seedDataService.seed();
    System.exit(SpringApplication.exit(context, () -> exitCode));
  }
}
