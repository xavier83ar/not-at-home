package com.notathome.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Applies {@link RateLimited}. */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
class RateLimitConfiguration implements WebMvcConfigurer {

  private final RateLimiter rateLimiter;

  RateLimitConfiguration(RateLimiter rateLimiter) {
    this.rateLimiter = rateLimiter;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
        new HandlerInterceptor() {
          @Override
          public boolean preHandle(
              HttpServletRequest request, HttpServletResponse response, Object handler) {
            if (handler instanceof HandlerMethod method) {
              RateLimited limited =
                  AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RateLimited.class);
              if (limited != null) {
                rateLimiter.consume(limited.value(), ClientAddress.of(request));
              }
            }
            return true;
          }
        });
  }
}
