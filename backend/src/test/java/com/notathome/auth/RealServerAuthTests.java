package com.notathome.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.notathome.TestcontainersConfiguration;
import com.notathome.users.User;
import com.notathome.users.UserRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Talks to a real Tomcat. MockMvc skips the servlet container, so it can't see cookie attributes or
 * the error dispatch that Tomcat does for {@code sendError}; both matter for security here.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = "app.rate-limit.enabled=false")
class RealServerAuthTests {

  @LocalServerPort int port;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired JdbcTemplate jdbc;

  final HttpClient http = HttpClient.newHttpClient();

  @BeforeEach
  void createUser() {
    jdbc.update("delete from persistent_logins");
    jdbc.update("delete from spring_session");
    jdbc.update("delete from users");
    users.save(
        new User("alice", "alice@example.com", "Alice", passwordEncoder.encode("secret-pw")));
  }

  HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
    return http.send(request.build(), BodyHandlers.ofString());
  }

  HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
  }

  HttpRequest.Builder login(String xsrf) {
    HttpRequest.Builder builder =
        request("/api/auth/login")
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString("{\"login\":\"alice\",\"password\":\"secret-pw\"}"));
    if (xsrf != null) {
      builder.header("Cookie", "XSRF-TOKEN=" + xsrf).header("X-XSRF-TOKEN", xsrf);
    }
    return builder;
  }

  @Test
  void csrfCookieIsReadableByJavaScriptAndSameSiteLax() throws Exception {
    HttpResponse<String> response = send(request("/api/csrf").GET());

    assertThat(response.statusCode()).isEqualTo(204);
    String cookie = response.headers().firstValue("Set-Cookie").orElseThrow();
    assertThat(cookie)
        .startsWith("XSRF-TOKEN=")
        .contains("SameSite=Lax")
        .doesNotContain("HttpOnly");
  }

  @Test
  void sessionCookieIsHttpOnlyAndSameSiteLax() throws Exception {
    String xsrf = tokenFrom(send(request("/api/csrf").GET()));

    HttpResponse<String> response = send(login(xsrf));

    assertThat(response.statusCode()).isEqualTo(200);
    List<String> cookies = response.headers().allValues("Set-Cookie");
    assertThat(cookies)
        .anySatisfy(
            cookie ->
                assertThat(cookie)
                    .startsWith("SESSION=")
                    .contains("HttpOnly")
                    .contains("SameSite=Lax"));
  }

  @Test
  void rememberMeCookieIsHttpOnlySameSiteLaxAndLastsThirtyDays() throws Exception {
    String xsrf = tokenFrom(send(request("/api/csrf").GET()));

    HttpResponse<String> response =
        send(
            request("/api/auth/login")
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + xsrf)
                .header("X-XSRF-TOKEN", xsrf)
                .POST(
                    BodyPublishers.ofString(
                        "{\"login\":\"alice\",\"password\":\"secret-pw\",\"rememberMe\":true}")));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().allValues("Set-Cookie"))
        .anySatisfy(
            cookie ->
                assertThat(cookie)
                    .startsWith("remember-me=")
                    .contains("HttpOnly")
                    .contains("SameSite=Lax")
                    .contains("Max-Age=2592000")
                    .contains("Path=/"));
  }

  @Test
  void theRememberMeCookieAloneBringsTheSessionBack() throws Exception {
    String xsrf = tokenFrom(send(request("/api/csrf").GET()));
    HttpResponse<String> login =
        send(
            request("/api/auth/login")
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + xsrf)
                .header("X-XSRF-TOKEN", xsrf)
                .POST(
                    BodyPublishers.ofString(
                        "{\"login\":\"alice\",\"password\":\"secret-pw\",\"rememberMe\":true}")));
    String rememberMe =
        login.headers().allValues("Set-Cookie").stream()
            .filter(cookie -> cookie.startsWith("remember-me="))
            .map(cookie -> cookie.substring(0, cookie.indexOf(';')))
            .findFirst()
            .orElseThrow();

    // A browser restart: no SESSION cookie, only the persistent one.
    HttpResponse<String> me = send(request("/api/me").header("Cookie", rememberMe).GET());

    assertThat(me.statusCode()).isEqualTo(200);
    assertThat(me.body()).contains("\"username\":\"alice\"");
    assertThat(me.headers().allValues("Set-Cookie"))
        .anySatisfy(cookie -> assertThat(cookie).startsWith("SESSION=").contains("HttpOnly"))
        .anySatisfy(cookie -> assertThat(cookie).startsWith("remember-me=").contains("HttpOnly"));
  }

  @Test
  void missingCsrfTokenIsForbiddenNotUnauthorized() throws Exception {
    assertThat(send(login(null)).statusCode()).isEqualTo(403);
  }

  @Test
  void validationMessagesAreInEnglishWhateverTheBrowserLanguage() throws Exception {
    String xsrf = tokenFrom(send(request("/api/csrf").GET()));

    HttpResponse<String> response =
        send(
            request("/api/auth/login")
                .header("Content-Type", "application/json")
                .header("Accept-Language", "es-AR")
                .header("Cookie", "XSRF-TOKEN=" + xsrf)
                .header("X-XSRF-TOKEN", xsrf)
                .POST(BodyPublishers.ofString("{\"login\":\"\",\"password\":\"\"}")));

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("must not be blank");
  }

  static String tokenFrom(HttpResponse<?> response) {
    String cookie = response.headers().firstValue("Set-Cookie").orElseThrow();
    return cookie.substring("XSRF-TOKEN=".length(), cookie.indexOf(';'));
  }
}
