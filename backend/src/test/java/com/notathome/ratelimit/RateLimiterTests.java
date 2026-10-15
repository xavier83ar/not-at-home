package com.notathome.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.notathome.support.ManualClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimiterTests {

  final ManualClock clock = new ManualClock();
  final RateLimiter limiter = new RateLimiter(true, clock, RateLimiter.DEFAULT_MAX_BUCKETS);

  void use(RateLimitPolicy policy, String key, int times) {
    for (int i = 0; i < times; i++) {
      limiter.consume(policy, key);
    }
  }

  @Test
  void allowsTheCapacityThenRefuses() {
    use(RateLimitPolicy.LOGIN, "1.2.3.4", 10);

    assertThatThrownBy(() -> limiter.consume(RateLimitPolicy.LOGIN, "1.2.3.4"))
        .isInstanceOf(RateLimitExceededException.class);
  }

  @Test
  void theRefusalSaysHowLongToWait() {
    use(RateLimitPolicy.LOGIN, "1.2.3.4", 10);

    assertThatThrownBy(() -> limiter.consume(RateLimitPolicy.LOGIN, "1.2.3.4"))
        .isInstanceOfSatisfying(
            RateLimitExceededException.class,
            e -> {
              assertThat(e.getRetryAfterSeconds()).isEqualTo(6); // 10 per minute: one per 6 s
              assertThat(e.getHeaders().getFirst("Retry-After")).isEqualTo("6");
              assertThat(e.getStatusCode().value()).isEqualTo(429);
              assertThat(e.getBody().getProperties())
                  .containsEntry("code", "RATE_LIMITED")
                  .containsEntry("retryAfterSeconds", 6L);
            });
  }

  @Test
  void tokensComeBackGraduallyNotAllAtOnce() {
    use(RateLimitPolicy.LOGIN, "1.2.3.4", 10);

    clock.advance(Duration.ofSeconds(6));
    limiter.consume(RateLimitPolicy.LOGIN, "1.2.3.4"); // one token is back
    assertThatThrownBy(() -> limiter.consume(RateLimitPolicy.LOGIN, "1.2.3.4"))
        .isInstanceOf(RateLimitExceededException.class);

    clock.advance(Duration.ofMinutes(1));
    use(RateLimitPolicy.LOGIN, "1.2.3.4", 10); // a full minute: all ten are back
  }

  @Test
  void keysAndPoliciesAreIndependent() {
    use(RateLimitPolicy.LOGIN, "1.2.3.4", 10);

    limiter.consume(RateLimitPolicy.LOGIN, "5.6.7.8");
    limiter.consume(RateLimitPolicy.REGISTER, "1.2.3.4");
  }

  @Test
  void tryConsumeReportsInsteadOfThrowing() {
    for (int i = 0; i < 3; i++) {
      assertThat(limiter.tryConsume(RateLimitPolicy.PASSWORD_RESET_ADDRESS, "a@b.c")).isTrue();
    }

    assertThat(limiter.tryConsume(RateLimitPolicy.PASSWORD_RESET_ADDRESS, "a@b.c")).isFalse();
  }

  @Test
  void aRefusedRequestDoesNotUseUpTheFutureAllowance() {
    use(RateLimitPolicy.REGISTER, "1.2.3.4", 5);
    for (int i = 0; i < 50; i++) {
      assertThat(limiter.tryConsume(RateLimitPolicy.REGISTER, "1.2.3.4")).isFalse();
    }

    clock.advance(Duration.ofMinutes(12)); // one token per 12 minutes, however hard it was hit

    assertThat(limiter.tryConsume(RateLimitPolicy.REGISTER, "1.2.3.4")).isTrue();
  }

  @Test
  void switchedOffItNeverRefuses() {
    RateLimiter off = new RateLimiter(false, clock, RateLimiter.DEFAULT_MAX_BUCKETS);

    for (int i = 0; i < 1000; i++) {
      off.consume(RateLimitPolicy.LOGIN, "1.2.3.4");
    }
    assertThat(off.size()).isZero();
  }

  @Test
  void emailKeysIgnoreCaseAndSpaces() {
    assertThat(RateLimiter.emailKey("  Alice@Example.COM ")).isEqualTo("alice@example.com");
  }

  @Test
  void bucketsThatAreFullAgainAreForgotten() {
    for (int i = 0; i < 100; i++) {
      limiter.consume(RateLimitPolicy.LOGIN, "10.0.0." + i);
    }
    assertThat(limiter.size()).isEqualTo(100);

    clock.advance(Duration.ofMinutes(2)); // longer than the login period
    for (int i = 0; i < 1024; i++) { // a sweep runs every 1024 calls
      limiter.tryConsume(RateLimitPolicy.REGISTER, "same-key");
      clock.advance(Duration.ofSeconds(1));
    }

    assertThat(limiter.size()).as("the idle login buckets are gone").isLessThan(10);
  }

  @Test
  void theNumberOfBucketsIsCappedEvenWhenEveryKeyIsFresh() {
    RateLimiter small = new RateLimiter(true, clock, 1000);

    for (int i = 0; i < 20_000; i++) {
      small.tryConsume(RateLimitPolicy.FORGOT_PASSWORD, "invented-" + i);
    }

    assertThat(small.size()).isLessThanOrEqualTo(1001);
  }
}
