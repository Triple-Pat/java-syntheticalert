package com.triplepat.syntheticalert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class GapTest {
  private static final int N = 10_000;

  // One-sample Kolmogorov-Smirnov at alpha = 0.01: the critical value for the
  // largest gap between the empirical CDF and the true one, at N samples.
  private static final double ALPHA = 0.01;
  private static final double KS_CRITICAL = Math.sqrt(-Math.log(ALPHA / 2) / (2.0 * N));

  private static final double MEAN = (double) SyntheticAlert.DEFAULT_MEAN_INTERVAL.toNanos();
  private static final double MIN = (double) SyntheticAlert.DEFAULT_MIN_INTERVAL.toNanos();
  private static final double MAX = (double) SyntheticAlert.DEFAULT_MAX_INTERVAL.toNanos();

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

  /** CDF of the exponential with the given mean, truncated to [min, max]. */
  private static double truncatedCdf(double x, double mean, double min, double max) {
    double atMin = 1.0 - Math.exp(-min / mean);
    double atMax = 1.0 - Math.exp(-max / mean);
    return (1.0 - Math.exp(-x / mean) - atMin) / (atMax - atMin);
  }

  /** Quantile function of the exponential with the given mean, truncated to [min, max]. */
  private static double truncatedQuantile(double u, double mean, double min, double max) {
    double atMin = 1.0 - Math.exp(-min / mean);
    double atMax = 1.0 - Math.exp(-max / mean);
    return -mean * Math.log(1.0 - (atMin + u * (atMax - atMin)));
  }

  /** The largest distance between the samples' empirical CDF and the truncated exponential's. */
  private static double ksStatistic(double[] samples, double mean, double min, double max) {
    double[] sorted = samples.clone();
    Arrays.sort(sorted);
    int n = sorted.length;
    double d = 0.0;
    for (int i = 0; i < n; i++) {
      double theoretical = truncatedCdf(sorted[i], mean, min, max);
      d = Math.max(d, Math.abs((i + 1) / (double) n - theoretical));
      d = Math.max(d, Math.abs(i / (double) n - theoretical));
    }
    return d;
  }

  private static double[] sample(double mean, double min, double max) {
    double[] out = new double[N];
    for (int i = 0; i < N; i++) {
      out[i] = (double) Gap.truncatedExponential((long) mean, (long) min, (long) max);
    }
    return out;
  }

  /** Evenly spaced quantiles: a noise-free stand-in for a sample from a distribution. */
  private static double[] quantiles(DoubleUnaryOperator inverseCdf) {
    double[] out = new double[N];
    for (int i = 0; i < N; i++) {
      out[i] = inverseCdf.applyAsDouble((i + 0.5) / N);
    }
    return out;
  }

  @Test
  void theGapsAreMemoryless() {
    // A correct sampler fails one K-S attempt about 1% of the time by
    // construction; three attempts put the false-failure rate near 1e-6. The
    // wrong distributions below fail every attempt.
    double best = Double.MAX_VALUE;
    for (int attempt = 0; attempt < 3; attempt++) {
      best = Math.min(best, ksStatistic(sample(MEAN, MIN, MAX), MEAN, MIN, MAX));
      if (best <= KS_CRITICAL) {
        return;
      }
    }
    throw new AssertionError("K-S statistic " + best + " exceeds " + KS_CRITICAL + " three times");
  }

  @Test
  void theShapeHoldsInTheFarTailWhereTheSurvivalFunctionUnderflows() {
    double mean = (double) nanos(1);
    double min = (double) nanos(1);
    double max = (double) nanos(1_000_000);
    double best = Double.MAX_VALUE;
    for (int attempt = 0; attempt < 3; attempt++) {
      best = Math.min(best, ksStatistic(sample(mean, min, max), mean, min, max));
      if (best <= KS_CRITICAL) {
        return;
      }
    }
    throw new AssertionError("K-S statistic " + best + " exceeds " + KS_CRITICAL + " three times");
  }

  @Test
  void theKsTestAcceptsTheRightDistribution() {
    // Positive control: noise-free quantiles of the right distribution pass.
    double[] exact = quantiles(u -> truncatedQuantile(u, MEAN, MIN, MAX));
    assertTrue(ksStatistic(exact, MEAN, MIN, MAX) < KS_CRITICAL);
  }

  @Test
  void theKsTestRejectsWrongDistributions() {
    // Negative controls prove the test has teeth. Each is what a plausible
    // mistake would produce.
    double[] uniform = quantiles(u -> MIN + u * (MAX - MIN));
    double[] clamped = quantiles(u -> Math.min(Math.max(-MEAN * Math.log(1.0 - u), MIN), MAX));
    double[] meanOffByAQuarter = quantiles(u -> truncatedQuantile(u, MEAN * 1.25, MIN, MAX));
    assertTrue(ksStatistic(uniform, MEAN, MIN, MAX) > KS_CRITICAL, "uniform on the window");
    assertTrue(ksStatistic(clamped, MEAN, MIN, MAX) > KS_CRITICAL, "clamped, not truncated");
    assertTrue(ksStatistic(meanOffByAQuarter, MEAN, MIN, MAX) > KS_CRITICAL, "mean off by 25%");
  }

  @Test
  void theRealizedMeanGapUnderTheDefaultsIsAboutFortyNineMinutes() {
    // Pins the README's claim. Truncation to [10m, 2h] pulls the hour-long
    // mean down to about 49 minutes.
    double expected = truncatedMean(MEAN, MIN, MAX);
    assertTrue(
        Duration.ofMinutes(48).toNanos() < expected && expected < Duration.ofMinutes(50).toNanos());
    assertMeanNear(
        draws((long) MEAN, (long) MIN, (long) MAX),
        expected,
        (double) Duration.ofMinutes(2).toNanos());
  }
}
