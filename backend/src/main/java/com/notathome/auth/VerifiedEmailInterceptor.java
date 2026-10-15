package com.notathome.auth;

import com.notathome.users.User;
import com.notathome.users.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Enforces {@link RequiresVerifiedEmail}. */
@Configuration
class VerifiedEmailInterceptor implements HandlerInterceptor, WebMvcConfigurer {

  private final UserRepository users;

  VerifiedEmailInterceptor(UserRepository users) {
    this.users = users;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(this);
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(handler instanceof HandlerMethod method) || !requiresVerifiedEmail(method)) {
      return true;
    }
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication != null && authentication.getPrincipal() instanceof AuthUser principal)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    boolean verified =
        users
            .findByIdAndDeletedAtIsNull(principal.getId())
            .map(User::isEmailVerified)
            .orElse(false);
    if (!verified) {
      throw new EmailNotVerifiedException();
    }
    return true;
  }

  private static boolean requiresVerifiedEmail(HandlerMethod method) {
    return AnnotatedElementUtils.hasAnnotation(method.getMethod(), RequiresVerifiedEmail.class)
        || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), RequiresVerifiedEmail.class);
  }
}
