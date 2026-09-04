package com.triplepat.syntheticalert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class GapTest {
  private static final int N = 10_000;

  private static long nanos(long seconds) {
    return Duration.ofSeconds(seconds).toNanos();
  }

  private static long[] draws(long mean, long min, long max) {
    long[] out = new long[N];
    for (int i = 0; i < N; i++) {
      out[i] = Gap.truncatedExponential(mean, min, max);
    }
    return out;
  }

  /** The mean of an exponential with the given mean, truncated to [min, max], in closed form. */
  private static double truncatedMean(double mean, double min, double max) {
    double sMin = Math.exp(-min / mean);
    double sMax = Math.exp(-max / mean);
    return mean + (min * sMin - max * sMax) / (sMin - sMax);
  }

  private static void assertMeanNear(long[] draws, double expected, double tolerance) {
    double actual = LongStream.of(draws).average().orElseThrow();
    assertTrue(
        Math.abs(actual - expected) <= tolerance,
        () -> "mean " + actual + " not within " + tolerance + " of " + expected);
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
  void theSampleMeanMatchesTheTruncatedExponential() {
    // A constant, a uniform on the window, or a sign slip in the interpolation
    // all move the mean by seconds; the standard error at N is about 0.3 s.
    long mean = nanos(100);
    long min = nanos(50);
    long max = nanos(150);
    long[] draws = draws(mean, min, max);
    assertMeanNear(
        draws, truncatedMean((double) mean, (double) min, (double) max), (double) nanos(2));
    assertTrue(LongStream.of(draws).distinct().count() > N / 2, "draws are not all alike");
  }

  @Test
  void farFromTheMaxTheGapIsMemorylessPastTheMin() {
    // With max effectively infinite, memorylessness says the gap is min plus
    // a fresh exponential: mean 2 s, standard error about 10 ms at N.
    long mean = nanos(1);
    long min = nanos(1);
    long max = nanos(1_000_000);
    assertMeanNear(draws(mean, min, max), 2.0 * (double) nanos(1), (double) nanos(1) / 10.0);
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
