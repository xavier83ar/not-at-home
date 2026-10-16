package com.notathome.categories;

import static com.notathome.support.Accounts.PASSWORD;
import static com.notathome.support.Accounts.login;
import static com.notathome.support.Accounts.register;
import static com.notathome.support.Accounts.session;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.notathome.support.Csrf;
import com.notathome.support.IntegrationTest;
import com.notathome.users.User;
import com.notathome.users.UserRepository;
import com.notathome.users.UserRole;
import jakarta.servlet.http.Cookie;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@IntegrationTest
class CategoryTests {

  private static final byte[] JPEG = jpeg();

  @Autowired MockMvc mockMvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void cleanUp() {
    jdbc.update("delete from categories");
    jdbc.update("delete from uploads");
    jdbc.update("delete from spring_session");
    jdbc.update("delete from users");
  }

  @Test
  void adminCanCreateACategoryWithACoverImage() throws Exception {
    Cookie admin = adminSession();
    Long uploadId = uploadCover(admin);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/admin/categories")
                    .cookie(admin)
                    .with(Csrf.token(mockMvc))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(categoryJson("Hiking", "Trails and summits", uploadId)))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    String body = result.getResponse().getContentAsString();
    assertThat(body)
        .contains(
            "\"name\":\"Hiking\"",
            "\"slug\":\"hiking\"",
            "\"description\":\"Trails and summits\"",
            "\"position\":0",
            "\"topicCount\":0",
            "\"postCount\":0")
        .contains("\"coverImageUrl\":\"/api/uploads/");

    mockMvc
        .perform(get("/api/categories/hiking"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Hiking"));
  }

  @Test
  void duplicateNamesGetDistinctSlugs() throws Exception {
    Cookie admin = adminSession();

    createCategory(admin, "Gear!", "Equipment talk");
    createCategory(admin, "Gear?", "More equipment talk");

    mockMvc.perform(get("/api/categories/gear")).andExpect(status().isOk());
    mockMvc.perform(get("/api/categories/gear-2")).andExpect(status().isOk());
  }

  @Test
  void nonAdminCannotCreateACategory() throws Exception {
    Cookie user = userSession();

    mockMvc
        .perform(
            post("/api/admin/categories")
                .cookie(user)
                .with(Csrf.token(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content(categoryJson("Hiking", "Trails", null)))
        .andExpect(status().isForbidden());
  }

  @Test
  void guestCannotCreateACategory() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/categories")
                .with(Csrf.token(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content(categoryJson("Hiking", "Trails", null)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void creatingACategoryNeedsACsrfToken() throws Exception {
    Cookie admin = adminSession();

    mockMvc
        .perform(
            post("/api/admin/categories")
                .cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(categoryJson("Hiking", "Trails", null)))
        .andExpect(status().isForbidden());
  }

  @Test
  void creatingWithAnUnknownCoverUploadIdFails() throws Exception {
    Cookie admin = adminSession();

    MvcResult result =
        mockMvc
            .perform(
                post("/api/admin/categories")
                    .cookie(admin)
                    .with(Csrf.token(mockMvc))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(categoryJson("Hiking", "Trails", 999_999L)))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(result.getResponse().getContentAsString())
        .contains("\"coverUploadId\":[\"upload not found\"]");
  }

  @Test
  void listExcludesDeletedCategoriesAndRecompactsPositions() throws Exception {
    Cookie admin = adminSession();
    Long first = createCategory(admin, "Hiking", "Trails");
    Long second = createCategory(admin, "Climbing", "Routes");
    Long third = createCategory(admin, "Camping", "Sites");

    mockMvc
        .perform(delete("/api/admin/categories/" + second).cookie(admin).with(Csrf.token(mockMvc)))
        .andExpect(status().isNoContent());

    MvcResult result = mockMvc.perform(get("/api/categories")).andReturn();
    String body = result.getResponse().getContentAsString();
    assertThat(body)
        .contains("\"name\":\"Hiking\"", "\"name\":\"Camping\"")
        .doesNotContain("Climbing");
    // Hiking (created first) keeps position 0; Camping closes the gap left by Climbing's deletion.
    assertThat(body.indexOf("\"name\":\"Hiking\""))
        .isLessThan(body.indexOf("\"name\":\"Camping\""));

    assertThat(first).isNotNull();
    assertThat(third).isNotNull();
  }

  @Test
  void adminCanEditNameAndDescriptionWithoutChangingTheSlug() throws Exception {
    Cookie admin = adminSession();
    Long id = createCategory(admin, "Hiking", "Trails");

    mockMvc
        .perform(
            put("/api/admin/categories/" + id)
                .cookie(admin)
                .with(Csrf.token(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content(categoryJson("Hiking Trips", "Updated description", null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Hiking Trips"))
        .andExpect(jsonPath("$.slug").value("hiking"));

    mockMvc
        .perform(get("/api/categories/hiking"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Hiking Trips"));
  }

  @Test
  void reorderChangesTheDisplayOrder() throws Exception {
    Cookie admin = adminSession();
    Long first = createCategory(admin, "Hiking", "Trails");
    Long second = createCategory(admin, "Climbing", "Routes");

    mockMvc
        .perform(
            put("/api/admin/categories/order")
                .cookie(admin)
                .with(Csrf.token(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderedIds\":[%d,%d]}".formatted(second, first)))
        .andExpect(status().isOk());

    MvcResult result = mockMvc.perform(get("/api/categories")).andReturn();
    String body = result.getResponse().getContentAsString();
    assertThat(body.indexOf("\"name\":\"Climbing\""))
        .isLessThan(body.indexOf("\"name\":\"Hiking\""));
  }

  @Test
  void reorderRejectsAListThatDoesNotMatchTheActiveCategories() throws Exception {
    Cookie admin = adminSession();
    Long first = createCategory(admin, "Hiking", "Trails");
    createCategory(admin, "Climbing", "Routes");

    MvcResult result =
        mockMvc
            .perform(
                put("/api/admin/categories/order")
                    .cookie(admin)
                    .with(Csrf.token(mockMvc))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"orderedIds\":[%d]}".formatted(first)))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(result.getResponse().getContentAsString()).contains("\"orderedIds\":[");
  }

  @Test
  void unknownSlugIsNotFound() throws Exception {
    mockMvc.perform(get("/api/categories/does-not-exist")).andExpect(status().isNotFound());
  }

  @Test
  void categoriesAreDocumentedInOpenApi() throws Exception {
    mockMvc
        .perform(get("/api/v3/api-docs"))
        .andExpect(jsonPath("$.paths['/api/categories']").exists())
        .andExpect(jsonPath("$.paths['/api/admin/categories']").exists());
  }

  private Long createCategory(Cookie admin, String name, String description) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/admin/categories")
                    .cookie(admin)
                    .with(Csrf.token(mockMvc))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(categoryJson(name, description, null)))
            .andReturn();
    return id(result.getResponse().getContentAsString());
  }

  private Long uploadCover(Cookie admin) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                multipart("/api/uploads")
                    .file(new MockMultipartFile("file", "cover.jpg", "image/jpeg", JPEG))
                    .cookie(admin)
                    .with(Csrf.token(mockMvc)))
            .andReturn();
    return id(result.getResponse().getContentAsString());
  }

  private Cookie adminSession() throws Exception {
    User admin = new User("admin", "admin@example.com", "Admin", passwordEncoder.encode(PASSWORD));
    admin.setRole(UserRole.ADMIN);
    users.save(admin);
    return session(login(mockMvc, "admin", PASSWORD, false));
  }

  private Cookie userSession() throws Exception {
    return session(register(mockMvc, "alice", "alice@example.com", PASSWORD));
  }

  private static String categoryJson(String name, String description, Long coverUploadId) {
    String cover = coverUploadId == null ? "null" : coverUploadId.toString();
    return "{\"name\":\"%s\",\"description\":\"%s\",\"coverUploadId\":%s}"
        .formatted(name, description, cover);
  }

  private static Long id(String responseBody) {
    Matcher matcher = Pattern.compile("\"id\":(\\d+)").matcher(responseBody);
    assertThat(matcher.find()).as("response has an id: %s", responseBody).isTrue();
    return Long.valueOf(matcher.group(1));
  }

  private static byte[] jpeg() {
    byte[] content = new byte[64];
    content[0] = (byte) 0xFF;
    content[1] = (byte) 0xD8;
    content[2] = (byte) 0xFF;
    return content;
  }
}
