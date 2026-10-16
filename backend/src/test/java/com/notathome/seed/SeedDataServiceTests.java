package com.notathome.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.notathome.categories.Category;
import com.notathome.categories.CategoryRepository;
import com.notathome.support.SeedIntegrationTest;
import com.notathome.uploads.UploadRepository;
import com.notathome.users.User;
import com.notathome.users.UserRepository;
import com.notathome.users.UserRole;
import java.util.List;
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
  void cleanUp() {
    jdbc.update("delete from categories");
    jdbc.update("delete from uploads");
    jdbc.update("delete from spring_session");
    jdbc.update("delete from users");
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
  void skipsWhenTheDatabaseAlreadyHasUsers() {
    users.save(new User("someone", "someone@example.com", "Someone", "hash"));

    int exitCode = seedDataService.seed();

    assertThat(exitCode).isZero();
    assertThat(users.count()).isEqualTo(1);
    assertThat(categories.count()).isZero();
    assertThat(uploads.count()).isZero();
  }
}
