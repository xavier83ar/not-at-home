package com.notathome.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint (or a whole controller) that only users with a verified email may call; others
 * get a 403 with code {@code EMAIL_NOT_VERIFIED} (FR-1.9). The check reads the database, not the
 * session, so verifying takes effect straight away, even from another device.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresVerifiedEmail {}
