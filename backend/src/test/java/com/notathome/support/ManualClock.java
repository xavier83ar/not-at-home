package com.notathome.support;

import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/** A clock that only moves when the test says so, for deterministic rate limit refills. */
public class ManualClock implements TimeMeter {

  private final AtomicLong nanos = new AtomicLong(1_000_000_000L);

  public void advance(Duration duration) {
    nanos.addAndGet(duration.toNanos());
  }

  @Override
  public long currentTimeNanos() {
    return nanos.get();
  }

  @Override
  public boolean isWallClockBased() {
    return false;
  }
}
