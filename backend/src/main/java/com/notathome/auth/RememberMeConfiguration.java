package com.notathome.auth;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;

@Configuration
class RememberMeConfiguration {

  /** Table created by the Flyway migration, not by Spring. */
  @Bean
  RememberMeTokenRepository rememberMeTokenRepository(DataSource dataSource) {
    return new RememberMeTokenRepository(new JdbcTemplate(dataSource));
  }

  /**
   * @param secureCookies the same switch as the session cookie's {@code Secure} flag (on in prod)
   */
  @Bean
  AuthRememberMeServices rememberMeServices(
      UserDetailsService userDetailsService,
      RememberMeTokenRepository tokenRepository,
      @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies) {
    AuthRememberMeServices services =
        new AuthRememberMeServices(userDetailsService, tokenRepository);
    if (secureCookies) {
      // Don't leave it to request.isSecure(): after a browser restart the cookie is re-issued
      // during a server-side render, where the frontend calls the backend over plain HTTP and
      // the request does not look secure. This cookie is a 30-day credential.
      services.setUseSecureCookie(true);
    }
    return services;
  }
}
