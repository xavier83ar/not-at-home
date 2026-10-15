package com.notathome.ratelimit;

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
 * Talks to a real Tomcat, as the frontend's proxy does. Which address a request is limited by is
 * decided by Tomcat reading {@code X-Forwarded-For} from a trusted proxy (here the test client on
 * loopback), and MockMvc skips all of that. The limits use the real clock, so each test uses
 * addresses of its own.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = "app.rate-limit.enabled=true")
class RealServerRateLimitTests {

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

  HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
  }

  String csrfToken() throws Exception {
    HttpResponse<String> response =
        http.send(request("/api/csrf").GET().build(), BodyHandlers.ofString());
    String cookie = response.headers().firstValue("Set-Cookie").orElseThrow();
    return cookie.substring("XSRF-TOKEN=".length(), cookie.indexOf(';'));
  }

  /** A login attempt as the proxy would send it; {@code headers} are extra request headers. */
  HttpResponse<String> login(String password, boolean rememberMe, String... headers)
      throws Exception {
    String xsrf = csrfToken();
    HttpRequest.Builder builder =
        request("/api/auth/login")
            .header("Content-Type", "application/json")
            .header("Cookie", "XSRF-TOKEN=" + xsrf)
            .header("X-XSRF-TOKEN", xsrf);
    for (int i = 0; i < headers.length; i += 2) {
      builder.header(headers[i], headers[i + 1]);
    }
    builder.POST(
        BodyPublishers.ofString(
            "{\"login\":\"alice\",\"password\":\"%s\",\"rememberMe\":%s}"
                .formatted(password, rememberMe)));
    return http.send(builder.build(), BodyHandlers.ofString());
  }

  int attempt(String forwardedFor) throws Exception {
    return forwardedFor == null
        ? login("wrong", false).statusCode()
        : login("wrong", false, "X-Forwarded-For", forwardedFor).statusCode();
  }

  void exhaust(String forwardedFor) throws Exception {
    for (int i = 0; i < 10; i++) {
      assertThat(attempt(forwardedFor)).isEqualTo(401);
    }
    assertThat(attempt(forwardedFor)).as("the 11th attempt from %s", forwardedFor).isEqualTo(429);
  }

  @Test
  void theAddressInXForwardedForDecidesWhoIsLimited() throws Exception {
    exhaust("198.51.100.1");

    assertThat(attempt("198.51.100.2")).as("another visitor").isEqualTo(401);
    assertThat(attempt(null)).as("the proxy's own address is a different client").isEqualTo(401);
  }

  @Test
  void aForgedAddressInFrontOfTheRealOneChangesNothing() throws Exception {
    exhaust("198.51.100.3");

    // Each attempt claims to be a new visitor, but the real client is the last entry, the one the
    // proxy added; entries before it are whatever the client sent.
    for (int i = 0; i < 5; i++) {
      assertThat(attempt("203.0.113.%d, 198.51.100.3".formatted(i))).isEqualTo(429);
    }
  }

  @Test
  void aRefusalCarriesRetryAfterOverTheWire() throws Exception {
    for (int i = 0; i < 10; i++) {
      login("wrong", false, "X-Forwarded-For", "198.51.100.4");
    }

    HttpResponse<String> refused = login("wrong", false, "X-Forwarded-For", "198.51.100.4");

    assertThat(refused.statusCode()).isEqualTo(429);
    assertThat(Integer.parseInt(refused.headers().firstValue("Retry-After").orElseThrow()))
        .isBetween(1, 6);
    assertThat(refused.headers().firstValue("Content-Type").orElseThrow())
        .contains("application/problem+json");
    assertThat(refused.body()).contains("\"code\":\"RATE_LIMITED\"", "\"retryAfterSeconds\"");
  }

  @Test
  void anIpv6VisitorCannotDodgeTheLimitByChangingAddressInsideItsSlash64() throws Exception {
    exhaust("2001:db8:aa:bb::1");

    assertThat(attempt("2001:db8:aa:bb:ffff:eeee:dddd:cccc")).as("same /64").isEqualTo(429);
    assertThat(attempt("2001:db8:aa:cc::1")).as("a different /64").isEqualTo(401);
  }

  @Test
  void theProtocolHeaderIsStillHonouredSoCookiesAreSecureBehindTlsTermination() throws Exception {
    HttpResponse<String> plain = login("secret-pw", true, "X-Forwarded-For", "198.51.100.5");
    HttpResponse<String> behindTls =
        login("secret-pw", true, "X-Forwarded-For", "198.51.100.6", "X-Forwarded-Proto", "https");

    assertThat(rememberMeCookie(plain)).doesNotContain("Secure");
    assertThat(rememberMeCookie(behindTls)).contains("Secure");
  }

  private static String rememberMeCookie(HttpResponse<?> response) {
    return response.headers().allValues("Set-Cookie").stream()
        .filter(cookie -> cookie.startsWith("remember-me="))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void readingEndpointsAreNeverLimited() throws Exception {
    for (int i = 0; i < 30; i++) {
      assertThat(
              http.send(
                      request("/api/me").header("X-Forwarded-For", "198.51.100.7").GET().build(),
                      BodyHandlers.ofString())
                  .statusCode())
          .isEqualTo(401);
    }
  }
}
