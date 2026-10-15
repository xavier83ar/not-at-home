package com.notathome.ratelimit;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * 429 with a {@code Retry-After} header and, in the body, {@code code: RATE_LIMITED} and {@code
 * retryAfterSeconds}, so the frontend can say how long to wait.
 */
public class RateLimitExceededException extends ErrorResponseException {

  public static final String CODE = "RATE_LIMITED";

  private final long retryAfterSeconds;

  public RateLimitExceededException(long retryAfterSeconds) {
    super(HttpStatus.TOO_MANY_REQUESTS, problem(retryAfterSeconds), null);
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }

  @Override
  public HttpHeaders getHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
    return headers;
  }

  private static ProblemDetail problem(long retryAfterSeconds) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please try again later.");
    problem.setProperty("code", CODE);
    problem.setProperty("retryAfterSeconds", retryAfterSeconds);
    return problem;
  }
}
