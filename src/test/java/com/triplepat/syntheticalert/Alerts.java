package com.triplepat.syntheticalert;

import static org.junit.jupiter.api.Assertions.assertTrue;

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

  /**
   * After a scrape at {@code now} that returned {@code value}, the pending transition is either the
   * end of the current firing (at most the firing duration away) or the start of the next one (at
   * most the max interval away). One extra toggle from an interleaved replay breaks this.
   */
  static void assertOneTransitionAhead(SyntheticAlert alert, long now, double value) {
    long ahead = alert.next - now;
    assertTrue(ahead > 0, "the pending transition is in the future");
    long bound = value == 1.0 ? alert.firingNanos : alert.maxNanos;
    assertTrue(ahead <= bound, "pending transition " + ahead + " ns ahead exceeds " + bound);
  }
}
