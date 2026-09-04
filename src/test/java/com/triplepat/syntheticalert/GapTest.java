package com.triplepat.syntheticalert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GapTest {
  private static final int N = 10_000;

  private static long nanos(long seconds) {
    return Duration.ofSeconds(seconds).toNanos();
  }

  @Test
  void everyGapLiesWithinTheBounds() {
    long mean = nanos(100);
    long min = nanos(50);
    long max = nanos(150);
    for (int i = 0; i < N; i++) {
      long gap = Gap.truncatedExponential(mean, min, max);
      assertTrue(
          min <= gap && gap <= max, () -> "gap " + gap + " outside [" + min + ", " + max + "]");
    }
  }

  @Test
  void aZeroWidthWindowReturnsExactlyTheMean() {
    long mean = nanos(60);
    for (int i = 0; i < N; i++) {
      assertEquals(mean, Gap.truncatedExponential(mean, mean, mean));
    }
  }

  @Test
  void aMaxHundredsOfMeansAwayStaysFiniteAndInBounds() {
    // exp(-max / mean) underflows to exactly 0 here; a CDF-space draw would
    // hand Math.log a zero for the largest random values.
    long mean = nanos(1);
    long min = nanos(1);
    long max = nanos(1_000_000);
    for (int i = 0; i < N; i++) {
      long gap = Gap.truncatedExponential(mean, min, max);
      assertTrue(
          min <= gap && gap <= max, () -> "gap " + gap + " outside [" + min + ", " + max + "]");
    }
  }
}
