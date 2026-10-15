package com.notathome;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.notathome.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class HealthEndpointTests {

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbc;

  @Test
  void healthIsPublicAndUp() throws Exception {
    mockMvc
        .perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void flywayBaselineCreatedSessionTables() {
    Integer count =
        jdbc.queryForObject(
            "select count(*) from information_schema.tables where table_name = 'spring_session'",
            Integer.class);
    assertThat(count).isEqualTo(1);
  }

  @Test
  void otherEndpointsRequireAuthentication() throws Exception {
    mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void infoIsPublicAndDocumentedInOpenApi() throws Exception {
    mockMvc
        .perform(get("/api/info"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("not-at-home"))
        .andExpect(jsonPath("$.version").isNotEmpty());
    mockMvc
        .perform(get("/api/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/info']").exists());
  }
}
