package com.notathome.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.notathome.categories.Category;
import com.notathome.categories.CategoryRepository;
import com.notathome.support.Forum;
import com.notathome.support.SeedIntegrationTest;
import com.notathome.uploads.UploadRepository;
import com.notathome.users.User;
import com.notathome.users.UserRepository;
import com.notathome.users.UserRole;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@SeedIntegrationTest
class SeedDataServiceTests {

  @Autowired SeedDataService seedDataService;
  @Autowired UserRepository users;
  @Autowired CategoryRepository categories;
  @Autowired UploadRepository uploads;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  @AfterEach
  void cleanUp() {
    // Seeded topics reference users and categories, so everything goes in one statement.
    Forum.reset(jdbc);
  }

  @Test
  void seedsAFreshDatabase() {
    int exitCode = seedDataService.seed();

    assertThat(exitCode).isZero();

    List<User> created = users.findAll();
    assertThat(created).hasSize(2);
    User admin =
        created.stream().filter(u -> u.getUsername().equals("admin")).findFirst().orElseThrow();
    User user =
        created.stream().filter(u -> u.getUsername().equals("demo")).findFirst().orElseThrow();
    assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
    assertThat(admin.isEmailVerified()).isTrue();
    assertThat(user.getRole()).isEqualTo(UserRole.USER);
    assertThat(user.isEmailVerified()).isTrue();

    List<Category> createdCategories = categories.findAllByDeletedAtIsNullOrderByPositionAsc();
    assertThat(createdCategories)
        .extracting(Category::getName)
        .containsExactly("Hiking", "Climbing", "Camping", "Gear", "Trip Reports");
    assertThat(createdCategories).allSatisfy(c -> assertThat(c.getCoverUploadId()).isNotNull());

    assertThat(uploads.count()).isEqualTo(5);
  }

  @Test
  void seedsThreeDemoTopicsThatReadLikeRealOnes() {
    seedDataService.seed();

    List<Map<String, Object>> topics =
        jdbc.queryForList(
            "select t.title, c.name as category, u.username as author, t.reply_count"
                + " from topics t join categories c on c.id = t.category_id"
                + " join users u on u.id = t.author_id order by t.id");
    assertThat(topics)
        .extracting(t -> t.get("title"), t -> t.get("category"), t -> t.get("author"))
        .containsExactly(
            tuple("Best trails in Patagonia", "Hiking", "demo"),
            tuple("What's in your pack for a three-day trek?", "Gear", "admin"),
            tuple("Three days around Fitz Roy", "Trip Reports", "demo"));
    assertThat(topics).extracting(t -> t.get("reply_count")).containsExactly(1, 0, 1);
    assertThat(jdbc.queryForObject("select count(*) from posts", Long.class)).isEqualTo(5);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from posts where deleted_at is not null", Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject("select search_text from posts order by id limit 1", String.class))
        .contains("two weeks in Patagonia");
  }

  @Test
  void skipsWhenTheDatabaseAlreadyHasUsers() {
    users.save(new User("someone", "someone@example.com", "Someone", "hash"));

    int exitCode = seedDataService.seed();

    assertThat(exitCode).isZero();
    assertThat(users.count()).isEqualTo(1);
    assertThat(categories.count()).isZero();
    assertThat(uploads.count()).isZero();
  }
}
