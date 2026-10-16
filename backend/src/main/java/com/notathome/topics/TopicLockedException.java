package com.notathome.topics;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * 403 with {@code code: TOPIC_LOCKED}: nobody, admins included, replies to a locked topic (FR-3.4).
 */
public class TopicLockedException extends ErrorResponseException {

  public static final String CODE = "TOPIC_LOCKED";

  public TopicLockedException() {
    super(HttpStatus.FORBIDDEN, problem(), null);
  }

  private static ProblemDetail problem() {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "This topic is locked.");
    problem.setProperty("code", CODE);
    return problem;
  }
}
