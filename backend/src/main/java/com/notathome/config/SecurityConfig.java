package com.notathome.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;

/**
 * Session-based authentication. Login and logout are JSON controllers (see {@code AuthController}),
 * not Spring's form-login filter, so the rules here only open the public endpoints and set up CSRF.
 */
@Configuration
class SecurityConfig {

  /**
   * The CSRF token lives in a cookie the frontend reads and sends back in the {@code X-XSRF-TOKEN}
   * header. The cookie is not {@code HttpOnly} on purpose, so JavaScript can read it.
   */
  @Bean
  CsrfTokenRepository csrfTokenRepository() {
    CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
    return repository;
  }

  /**
   * The browser only has the plain cookie value, so the default BREACH-masking handler can't be
   * used. Masking protects secrets echoed in response bodies; the token is never in a body here.
   */
  @Bean
  CsrfTokenRequestHandler csrfTokenRequestHandler() {
    return new CsrfTokenRequestAttributeHandler();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  AuthenticationManager authenticationManager(
      UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      CsrfTokenRepository csrfTokenRepository,
      CsrfTokenRequestHandler csrfTokenRequestHandler) {
    http.csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(csrfTokenRequestHandler))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        // Tomcat forwards sendError() (e.g. a CSRF 403) here; if it needed a login
                        // the real status would turn into a 401.
                        "/error",
                        "/api/health",
                        "/api/info",
                        "/api/csrf",
                        "/api/auth/login",
                        "/api/auth/logout",
                        "/api/v3/api-docs/**",
                        "/api/swagger-ui.html",
                        "/api/swagger-ui/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        // Nothing to redirect back to in an API. The default cache would save every rejected
        // request in a new session, so each guest page view (which calls /api/me) would add a row.
        .requestCache(RequestCacheConfigurer::disable)
        .exceptionHandling(
            e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
    return http.build();
  }
}
