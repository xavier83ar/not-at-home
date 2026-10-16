package com.notathome.seed;

import com.notathome.categories.AdminCategoryService;
import com.notathome.categories.Category;
import com.notathome.topics.TopicService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

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

  private static final List<SeedTopic> DEMO_TOPICS =
      List.of(
          new SeedTopic(
              "Hiking",
              "Best trails in Patagonia",
              false,
              """
              {"blocks": [
                {"type": "paragraph", "data": {"text": "I'm planning <b>two weeks</b> in Patagonia next February and I'd love to hear where you would go."}},
                {"type": "header", "data": {"text": "What I'm after", "level": 2}},
                {"type": "list", "data": {"style": "unordered", "items": [
                  {"content": "Good views for the effort", "meta": {}, "items": []},
                  {"content": "Trails that are not packed in high season", "meta": {}, "items": []},
                  {"content": "Somewhere to camp or a basic refugio every night", "meta": {}, "items": []}]}}
              ]}
              """,
              List.of(
                  """
                  {"blocks": [
                    {"type": "paragraph", "data": {"text": "The <b>Dientes de Navarino</b> circuit is wilder and quieter than Torres del Paine, and you can still do it in five days."}}
                  ]}
                  """)),
          new SeedTopic(
              "Gear",
              "What's in your pack for a three-day trek?",
              true,
              """
              {"blocks": [
                {"type": "paragraph", "data": {"text": "Let's settle it: what do you <i>always</i> carry, and what do you leave at home? Here is my starting list."}},
                {"type": "list", "data": {"style": "checklist", "items": [
                  {"content": "Tent, sleeping bag and mat", "meta": {"checked": true}, "items": []},
                  {"content": "Stove and a lighter (and a backup lighter)", "meta": {"checked": true}, "items": []},
                  {"content": "Water filter", "meta": {"checked": true}, "items": []},
                  {"content": "Camp chair", "meta": {"checked": false}, "items": []}]}}
              ]}
              """,
              List.of()),
          new SeedTopic(
              "Trip Reports",
              "Three days around Fitz Roy",
              false,
              """
              {"blocks": [
                {"type": "paragraph", "data": {"text": "Back from El Chaltén. Short version: it rained on day one, and day two made up for it."}},
                {"type": "quote", "data": {"text": "The mountain is not a place you conquer, it is a place that lets you in.", "caption": "A tired hiker"}},
                {"type": "code", "data": {"code": "Day 1: El Chaltén > Poincenot (12 km)\\nDay 2: Poincenot > Laguna de los Tres > Piedra del Fraile\\nDay 3: Piedra del Fraile > Loma del Pliegue Tumbado > El Chaltén"}},
                {"type": "paragraph", "data": {"text": "Happy to answer questions about the route."}}
              ]}
              """,
              List.of(
                  """
                  {"blocks": [
                    {"type": "paragraph", "data": {"text": "Great report! Did you book the campsites in advance?"}}
                  ]}
                  """)));

  private final SeedProperties properties;
  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final AdminCategoryService categories;
  private final UploadService uploads;
  private final TopicService topics;
  private final JsonMapper mapper;

  SeedDataService(
      SeedProperties properties,
      UserRepository users,
      PasswordEncoder passwordEncoder,
      AdminCategoryService categories,
      UploadService uploads,
      TopicService topics,
      JsonMapper mapper) {
    this.properties = properties;
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.categories = categories;
    this.uploads = uploads;
    this.topics = topics;
    this.mapper = mapper;
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
    Map<String, Category> seededCategories = seedCategories(admin.getId());
    seedDemoTopics(seededCategories, admin, user);

    log.info(
        "Seeded the database: admin '{}', user '{}', {} categories, {} demo topics.",
        admin.getUsername(),
        user.getUsername(),
        DEFAULT_CATEGORIES.size(),
        DEMO_TOPICS.size());
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

  private Map<String, Category> seedCategories(Long ownerId) {
    Map<String, Category> created = new HashMap<>();
    for (SeedCategory definition : DEFAULT_CATEGORIES) {
      Upload cover = uploads.storeTrusted(readImage(definition.imageResource()), ownerId);
      created.put(
          definition.name(),
          categories.create(definition.name(), definition.description(), cover.getId()));
    }
    return created;
  }

  /** FR-10.3: the demo topics. The default user starts them; the admin chimes in on some. */
  private void seedDemoTopics(Map<String, Category> seededCategories, User admin, User user) {
    for (SeedTopic demo : DEMO_TOPICS) {
      User author = demo.byAdmin() ? admin : user;
      User replier = demo.byAdmin() ? user : admin;
      Long topicId =
          topics
              .create(
                  author.getId(),
                  seededCategories.get(demo.category()).getId(),
                  demo.title(),
                  mapper.readTree(demo.content()))
              .id();
      for (String reply : demo.replies()) {
        topics.reply(topicId, replier.getId(), mapper.readTree(reply));
      }
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

  /** {@code content} and each reply are Editor.js documents, as JSON. */
  private record SeedTopic(
      String category, String title, boolean byAdmin, String content, List<String> replies) {}
}
