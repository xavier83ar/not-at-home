package com.notathome.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** 403 with a machine-readable {@code code}, so the frontend can offer to resend the email. */
class EmailNotVerifiedException extends ErrorResponseException {

  static final String CODE = "EMAIL_NOT_VERIFIED";

  EmailNotVerifiedException() {
    super(HttpStatus.FORBIDDEN, problem(), null);
  }

  private static ProblemDetail problem() {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN, "Verify your email address to do this.");
    problem.setProperty("code", CODE);
    return problem;
  }
}
