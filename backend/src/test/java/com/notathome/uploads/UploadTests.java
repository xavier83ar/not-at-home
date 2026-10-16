package com.notathome.uploads;

import static com.notathome.support.Accounts.PASSWORD;
import static com.notathome.support.Accounts.register;
import static com.notathome.support.Accounts.session;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.notathome.support.Csrf;
import com.notathome.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@IntegrationTest
class UploadTests {

  private static final byte[] JPEG = image(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 64);
  private static final byte[] PNG =
      image(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, 64);
  private static final byte[] WEBP = webp(64);

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void cleanUp() {
    jdbc.update("delete from uploads");
    jdbc.update("delete from spring_session");
    jdbc.update("delete from users");
  }

  @Test
  void uploadsAJpegAndServesItBack() throws Exception {
    Cookie session = loggedIn("alice");

    MvcResult created =
        mockMvc
            .perform(
                multipart("/api/uploads")
                    .file(new MockMultipartFile("file", "cover.jpg", "image/jpeg", JPEG))
                    .cookie(session)
                    .with(Csrf.token(mockMvc)))
            .andReturn();

    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    String body = created.getResponse().getContentAsString();
    assertThat(body).contains("\"contentType\":\"image/jpeg\"", "\"size\":64");
    String url = url(body);
    assertThat(url).matches("/api/uploads/[A-Za-z0-9_-]+\\.jpg");

    mockMvc
        .perform(get(url))
        .andExpect(status().isOk())
        .andExpect(
            result -> assertThat(result.getResponse().getContentType()).isEqualTo("image/jpeg"))
        .andExpect(
            result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(JPEG))
        .andExpect(
            result ->
                assertThat(result.getResponse().getHeader("X-Content-Type-Options"))
                    .isEqualTo("nosniff"))
        .andExpect(
            result ->
                assertThat(result.getResponse().getHeader("Content-Security-Policy"))
                    .isEqualTo("sandbox"));
  }

  @Test
  void acceptsPngAndWebp() throws Exception {
    Cookie session = loggedIn("alice");

    mockMvc
        .perform(
            multipart("/api/uploads")
                .file(new MockMultipartFile("file", "a.png", "image/png", PNG))
                .cookie(session)
                .with(Csrf.token(mockMvc)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.contentType").value("image/png"));

    mockMvc
        .perform(
            multipart("/api/uploads")
                .file(new MockMultipartFile("file", "a.webp", "image/webp", WEBP))
                .cookie(session)
                .with(Csrf.token(mockMvc)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.contentType").value("image/webp"));
  }

  @Test
  void rejectsSomethingThatIsNotAnImage() throws Exception {
    Cookie session = loggedIn("alice");

    MvcResult result =
        mockMvc
            .perform(
                multipart("/api/uploads")
                    .file(
                        new MockMultipartFile(
                            "file", "a.jpg", "image/jpeg", "not an image".getBytes()))
                    .cookie(session)
                    .with(Csrf.token(mockMvc)))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(result.getResponse().getContentAsString())
        .contains("\"file\":[\"must be a JPEG, PNG or WebP image\"]");
    assertThat(jdbc.queryForObject("select count(*) from uploads", Integer.class)).isZero();
  }

  @Test
  void rejectsAFileOverFiveMegabytesButUnderTheServletLimit() throws Exception {
    Cookie session = loggedIn("alice");
    byte[] tooBig = image(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 6 * 1024 * 1024);

    MvcResult result =
        mockMvc
            .perform(
                multipart("/api/uploads")
                    .file(new MockMultipartFile("file", "big.jpg", "image/jpeg", tooBig))
                    .cookie(session)
                    .with(Csrf.token(mockMvc)))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(result.getResponse().getContentAsString())
        .contains("\"file\":[\"must be 5 MB or smaller\"]");
  }

  @Test
  void rejectsAFileOverTheServletLimitToo() throws Exception {
    Cookie session = loggedIn("alice");
    byte[] wayTooBig = image(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 9 * 1024 * 1024);

    MvcResult result =
        mockMvc
            .perform(
                multipart("/api/uploads")
                    .file(new MockMultipartFile("file", "huge.jpg", "image/jpeg", wayTooBig))
                    .cookie(session)
                    .with(Csrf.token(mockMvc)))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(result.getResponse().getContentAsString())
        .contains("\"file\":[\"must be 5 MB or smaller\"]");
  }

  @Test
  void uploadingNeedsALogin() throws Exception {
    mockMvc
        .perform(
            multipart("/api/uploads")
                .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG))
                .with(Csrf.token(mockMvc)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void uploadingNeedsACsrfToken() throws Exception {
    Cookie session = loggedIn("alice");

    mockMvc
        .perform(
            multipart("/api/uploads")
                .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG))
                .cookie(session))
        .andExpect(status().isForbidden());
  }

  @Test
  void servingAnUnknownKeyIsNotFound() throws Exception {
    mockMvc.perform(get("/api/uploads/does-not-exist.jpg")).andExpect(status().isNotFound());
  }

  @Test
  void servingDoesNotNeedALogin() throws Exception {
    Cookie session = loggedIn("alice");
    MvcResult created =
        mockMvc
            .perform(
                multipart("/api/uploads")
                    .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG))
                    .cookie(session)
                    .with(Csrf.token(mockMvc)))
            .andReturn();
    String url = url(created.getResponse().getContentAsString());

    mockMvc.perform(get(url)).andExpect(status().isOk());
  }

  @Test
  void uploadIsDocumentedInOpenApi() throws Exception {
    mockMvc
        .perform(get("/api/v3/api-docs"))
        .andExpect(jsonPath("$.paths['/api/uploads']").exists());
  }

  private static String url(String responseBody) {
    Matcher matcher = Pattern.compile("\"url\":\"([^\"]+)\"").matcher(responseBody);
    assertThat(matcher.find()).as("response has a url: %s", responseBody).isTrue();
    return matcher.group(1);
  }

  private Cookie loggedIn(String username) throws Exception {
    MvcResult result = register(mockMvc, username, username + "@example.com", PASSWORD);
    return session(result);
  }

  private static byte[] image(byte[] magic, int totalSize) {
    byte[] content = new byte[totalSize];
    System.arraycopy(magic, 0, content, 0, magic.length);
    return content;
  }

  private static byte[] webp(int totalSize) {
    byte[] content = new byte[Math.max(totalSize, 12)];
    System.arraycopy("RIFF".getBytes(), 0, content, 0, 4);
    System.arraycopy("WEBP".getBytes(), 0, content, 8, 4);
    return content;
  }
}
