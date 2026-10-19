package com.notathome.ratelimit;

import java.time.Duration;

/**
 * The limits (NFR-2). Each one allows {@code capacity} requests per {@code period}, refilled
 * continuously: 5 per hour means a new request becomes possible every 12 minutes. The "per IP" ones
 * are applied with {@link RateLimited}; the "per address" ones are used by the code that sends the
 * emails, so that one inbox can't be flooded from many IP addresses.
 */
public enum RateLimitPolicy {

  /** Per IP. Slows password guessing; a real person rarely needs more than a few tries. */
  LOGIN(10, Duration.ofMinutes(1)),
  /** Per IP. */
  REGISTER(5, Duration.ofHours(1)),
  /** Per IP. */
  FORGOT_PASSWORD(5, Duration.ofHours(1)),
  /** Per IP. The tokens can't be guessed; this only bounds the work. */
  RESET_PASSWORD(10, Duration.ofHours(1)),
  /** Per IP. */
  VERIFY_EMAIL(30, Duration.ofHours(1)),
  /** Per IP. */
  RESEND_VERIFICATION(10, Duration.ofHours(1)),
  /** Per IP. Reads (serving a file) are never limited; this only bounds new uploads. */
  UPLOAD(30, Duration.ofHours(1)),
  /** Per IP. Starting topics, replying and editing posts share it (NFR-2). */
  POSTING(30, Duration.ofHours(1)),
  /** Per IP. Each of these checks the current password, so they must not allow guessing it. */
  CHANGE_PASSWORD(10, Duration.ofHours(1)),
  /** Per IP. */
  CHANGE_EMAIL(10, Duration.ofHours(1)),
  /** Per IP. */
  DELETE_ACCOUNT(10, Duration.ofHours(1)),

  /**
   * Per email address, shared by the registration email and every resend. Stops someone who
   * registered with a victim's address from mailing it again and again.
   */
  VERIFICATION_EMAIL_ADDRESS(5, Duration.ofHours(1)),
  /** Per email address. Over the limit, no email is sent and the request is not told. */
  PASSWORD_RESET_ADDRESS(3, Duration.ofHours(1));

  private final long capacity;
  private final Duration period;

  RateLimitPolicy(long capacity, Duration period) {
    this.capacity = capacity;
    this.period = period;
  }

  public long capacity() {
    return capacity;
  }

  public Duration period() {
    return period;
  }
}
