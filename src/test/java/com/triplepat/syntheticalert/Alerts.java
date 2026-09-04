package com.triplepat.syntheticalert;

import java.time.Duration;
import java.util.function.LongSupplier;

/** Test constructors. */
final class Alerts {
  private Alerts() {}

  /**
   * An alert whose silent gaps are all exactly {@code gap} and whose firings last exactly {@code
   * firing}: min == mean == max makes the schedule periodic.
   */
  static SyntheticAlert deterministic(Duration gap, Duration firing, LongSupplier clock) {
    return SyntheticAlert.builder()
        .meanInterval(gap)
        .minInterval(gap)
        .maxInterval(gap)
        .firingDuration(firing)
        .build(clock);
  }
}
