package com.notathome.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.notathome.categories.CategoryRepository;
import com.notathome.support.IntegrationTest;
import com.notathome.uploads.UploadRepository;
import com.notathome.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Runs with the seed properties unset, like every other test sharing {@link IntegrationTest}. */
@IntegrationTest
class SeedDataServiceValidationTests {

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
  void refusesToSeedWithoutCredentials() {
    int exitCode = seedDataService.seed();

    assertThat(exitCode).isEqualTo(1);
    assertThat(users.count()).isZero();
    assertThat(categories.count()).isZero();
    assertThat(uploads.count()).isZero();
  }
}
