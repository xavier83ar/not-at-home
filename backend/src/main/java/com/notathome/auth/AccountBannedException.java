package com.notathome.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * 403 for a banned user who gave the right password (FR-1.10). It is only raised after the password
 * has been checked, so it can't be used to find out which accounts are banned.
 */
class AccountBannedException extends ErrorResponseException {

  static final String CODE = "ACCOUNT_BANNED";

  AccountBannedException() {
    super(HttpStatus.FORBIDDEN, problem(), null);
  }

  private static ProblemDetail problem() {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "This account has been banned.");
    problem.setProperty("code", CODE);
    return problem;
  }
}
