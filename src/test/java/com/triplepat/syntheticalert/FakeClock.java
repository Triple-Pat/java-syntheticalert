package com.triplepat.syntheticalert;

import java.time.Duration;
import java.util.function.LongSupplier;

/** A nanosecond clock that only moves when told to. */
final class FakeClock implements LongSupplier {
  private long now;

  FakeClock(long now) {
    this.now = now;
  }

  @Override
  public long getAsLong() {
    return now;
  }

  long now() {
    return now;
  }

  void advance(Duration d) {
    now += d.toNanos();
  }

  void set(long nanos) {
    now = nanos;
  }
}
