package com.notathome.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limits how often one client (by IP address) may call this endpoint; over the limit the answer is
 * 429 with a {@code Retry-After} header. Checked before the request body is read, so malformed
 * requests count too, but after CSRF protection, so requests that never get past it don't.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

  RateLimitPolicy value();
}
