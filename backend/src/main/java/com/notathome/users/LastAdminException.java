package com.notathome.users;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * 409 with {@code code: LAST_ADMIN}: the forum must always keep an admin, so the last one can't
 * delete their account (FR-8.5) or be demoted (FR-9.4).
 */
public class LastAdminException extends ErrorResponseException {

  public static final String CODE = "LAST_ADMIN";

  public LastAdminException() {
    super(HttpStatus.CONFLICT, problem(), null);
  }

  private static ProblemDetail problem() {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "There must always be at least one admin. Make someone else an admin first.");
    problem.setProperty("code", CODE);
    return problem;
  }
}
