package com.notathome.seed;

import com.notathome.categories.AdminCategoryService;
import com.notathome.uploads.Upload;
import com.notathome.uploads.UploadService;
import com.notathome.users.User;
import com.notathome.users.UserRepository;
import com.notathome.users.UserRole;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The actual seeding (FR-10.3, FR-10.4); {@code SeedRunner} only decides when to call it. */
@Service
class SeedDataService {

  private static final Logger log = LoggerFactory.getLogger(SeedDataService.class);
  private static final int MIN_PASSWORD_LENGTH = 8;

  private static final List<SeedCategory> DEFAULT_CATEGORIES =
      List.of(
          new SeedCategory("Hiking", "Trails, summits and the best lookout points.", "hiking.jpg"),
          new SeedCategory(
              "Climbing", "Routes, gear and the thrill of the ascent.", "climbing.jpg"),
          new SeedCategory(
              "Camping", "Campsites, campfires and nights under the stars.", "camping.jpg"),
          new SeedCategory(
              "Gear", "What to bring, what to skip, and what broke on the trail.", "gear.jpg"),
          new SeedCategory(
              "Trip Reports",
              "Where you went, what happened, and the photos to prove it.",
              "trip-reports.jpg"));

  private final SeedProperties properties;
  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final AdminCategoryService categories;
  private final UploadService uploads;

  SeedDataService(
      SeedProperties properties,
      UserRepository users,
      PasswordEncoder passwordEncoder,
      AdminCategoryService categories,
      UploadService uploads) {
    this.properties = properties;
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.categories = categories;
    this.uploads = uploads;
  }

  /**
   * @return the process exit code: 0 for a clean run (seeded, or correctly skipped because the
   *     database already has users), 1 if the configured credentials are unusable.
   */
  @Transactional
  int seed() {
    if (users.count() > 0) {
      log.info("Seed skipped: the database already has users.");
      return 0;
    }
    List<String> problems = validate();
    if (!problems.isEmpty()) {
      problems.forEach(log::error);
      return 1;
    }

    User admin =
        createUser(
            properties.adminUsername(),
            properties.adminEmail(),
            properties.adminPassword(),
            UserRole.ADMIN);
    User user =
        createUser(
            properties.userUsername(),
            properties.userEmail(),
            properties.userPassword(),
            UserRole.USER);
    seedCategories(admin.getId());

    log.info(
        "Seeded the database: admin '{}', user '{}', {} categories.",
        admin.getUsername(),
        user.getUsername(),
        DEFAULT_CATEGORIES.size());
    return 0;
  }

  private List<String> validate() {
    List<String> problems = new ArrayList<>();
    requireNonBlank(problems, "SEED_ADMIN_USERNAME", properties.adminUsername());
    requireNonBlank(problems, "SEED_ADMIN_EMAIL", properties.adminEmail());
    requirePassword(problems, "SEED_ADMIN_PASSWORD", properties.adminPassword());
    requireNonBlank(problems, "SEED_USER_USERNAME", properties.userUsername());
    requireNonBlank(problems, "SEED_USER_EMAIL", properties.userEmail());
    requirePassword(problems, "SEED_USER_PASSWORD", properties.userPassword());
    if (problems.isEmpty()) {
      if (properties.adminUsername().equalsIgnoreCase(properties.userUsername())) {
        problems.add("SEED_ADMIN_USERNAME and SEED_USER_USERNAME must be different.");
      }
      if (properties.adminEmail().equalsIgnoreCase(properties.userEmail())) {
        problems.add("SEED_ADMIN_EMAIL and SEED_USER_EMAIL must be different.");
      }
    }
    return problems;
  }

  private static void requireNonBlank(List<String> problems, String name, String value) {
    if (value == null || value.isBlank()) {
      problems.add(name + " is required.");
    }
  }

  private static void requirePassword(List<String> problems, String name, String value) {
    if (value == null || value.isBlank()) {
      problems.add(name + " is required.");
    } else if (value.length() < MIN_PASSWORD_LENGTH) {
      problems.add(name + " must be at least " + MIN_PASSWORD_LENGTH + " characters.");
    }
  }

  /** Verified and active from the start: nobody can email-verify an account at install time. */
  private User createUser(String username, String email, String password, UserRole role) {
    User user = new User(username, email, username, passwordEncoder.encode(password));
    user.markEmailVerified(Instant.now());
    user.setRole(role);
    return users.save(user);
  }

  private void seedCategories(Long ownerId) {
    for (SeedCategory definition : DEFAULT_CATEGORIES) {
      Upload cover = uploads.storeTrusted(readImage(definition.imageResource()), ownerId);
      categories.create(definition.name(), definition.description(), cover.getId());
    }
  }

  private static byte[] readImage(String fileName) {
    try (InputStream in = new ClassPathResource("seed/images/" + fileName).getInputStream()) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private record SeedCategory(String name, String description, String imageResource) {}
}
