package com.notathome.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Token buckets in memory, one per policy and key: enough for a single backend instance (the plan's
 * scale). Keys come from clients, so the table is kept small: a bucket nobody used for a whole
 * period is full again and is forgotten, and there is a hard cap on the number of buckets.
 */
@Service
public class RateLimiter {

  static final int DEFAULT_MAX_BUCKETS = 100_000;
  private static final int SWEEP_EVERY_CALLS = 1024;

  private record Entry(Bucket bucket, RateLimitPolicy policy, AtomicLong lastUsedNanos) {}

  private final boolean enabled;
  private final TimeMeter clock;
  private final int maxBuckets;
  private final Map<String, Entry> buckets = new ConcurrentHashMap<>();
  private final AtomicInteger calls = new AtomicInteger();

  @Autowired
  RateLimiter(RateLimitProperties properties, ObjectProvider<TimeMeter> clock) {
    this(
        properties.enabled(),
        clock.getIfAvailable(() -> TimeMeter.SYSTEM_NANOTIME),
        DEFAULT_MAX_BUCKETS);
  }

  RateLimiter(boolean enabled, TimeMeter clock, int maxBuckets) {
    this.enabled = enabled;
    this.clock = clock;
    this.maxBuckets = maxBuckets;
  }

  /** The key for limits per email address: ignores case and surrounding spaces. */
  public static String emailKey(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }

  /**
   * Counts one request for {@code key} under {@code policy}.
   *
   * @throws RateLimitExceededException if the limit is used up
   */
  public void consume(RateLimitPolicy policy, String key) {
    ConsumptionProbe probe = attempt(policy, key);
    if (probe != null && !probe.isConsumed()) {
      long seconds = Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
      throw new RateLimitExceededException(seconds);
    }
  }

  /** Like {@link #consume}, for callers that handle the refusal themselves. */
  public boolean tryConsume(RateLimitPolicy policy, String key) {
    ConsumptionProbe probe = attempt(policy, key);
    return probe == null || probe.isConsumed();
  }

  /** Null when limiting is switched off. */
  private ConsumptionProbe attempt(RateLimitPolicy policy, String key) {
    if (!enabled) {
      return null;
    }
    sweepIfDue();
    Entry entry =
        buckets.computeIfAbsent(
            policy.name() + ":" + key,
            k -> new Entry(newBucket(policy), policy, new AtomicLong(clock.currentTimeNanos())));
    entry.lastUsedNanos().set(clock.currentTimeNanos());
    return entry.bucket().tryConsumeAndReturnRemaining(1);
  }

  private Bucket newBucket(RateLimitPolicy policy) {
    return Bucket.builder()
        .addLimit(
            limit ->
                limit.capacity(policy.capacity()).refillGreedy(policy.capacity(), policy.period()))
        .withCustomTimePrecision(clock)
        .build();
  }

  private void sweepIfDue() {
    boolean due = calls.incrementAndGet() % SWEEP_EVERY_CALLS == 0 || buckets.size() > maxBuckets;
    if (!due) {
      return;
    }
    long now = clock.currentTimeNanos();
    // Idle for a whole period means full again, so forgetting it loses nothing.
    buckets.values().removeIf(e -> now - e.lastUsedNanos().get() > e.policy().period().toNanos());
    if (buckets.size() > maxBuckets) {
      // Still too many in use at once (someone is inventing keys): make room, oldest news first
      // is not worth tracking, so drop arbitrary entries down to 90% of the cap.
      int target = (int) (maxBuckets * 0.9);
      for (Iterator<String> it = buckets.keySet().iterator();
          it.hasNext() && buckets.size() > target; ) {
        it.next();
        it.remove();
      }
    }
  }

  /** For tests. */
  int size() {
    return buckets.size();
  }
}
