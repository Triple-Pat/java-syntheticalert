package com.triplepat.syntheticalert;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Draws the silent gaps between firings.
 *
 * <p>This is deliberately bespoke and single-use, per the series philosophy of reimplementing the
 * memoryless sampler in each library rather than sharing one.
 */
final class Gap {
  private Gap() {}

  /**
   * Draws one silent gap, in nanoseconds, from the exponential distribution with the given mean,
   * truncated to {@code [min, max]}.
   *
   * <p>Inverse-CDF sampling: pick a uniform point within the probability mass the exponential puts
   * on the window, then map it back through the exponential's quantile function. One draw, exact
   * shape, and the bounds hold literally.
   *
   * <p>Preconditions, validated once by the builder rather than on every draw: all three are
   * positive and {@code min <= mean <= max}. The finiteness argument below relies on {@code min <=
   * mean}.
   */
  static long truncatedExponential(long mean, long min, long max) {
    // Work with the survival function S(x) = exp(-x / mean), which is strictly
    // positive at min (min <= mean, so the exponent is at least -1) but
    // underflows to exactly 0 when max is hundreds of means away. nextDouble()
    // is in [0, 1), so 1 - nextDouble() is in (0, 1] and u lands in
    // (sMax, sMin]: never equal to sMax, so Math.log never sees 0.
    double sMax = Math.exp(-(double) max / mean);
    double sMin = Math.exp(-(double) min / mean);
    double u = sMax + (1.0 - ThreadLocalRandom.current().nextDouble()) * (sMin - sMax);
    double gap = -(double) mean * Math.log(u);
    // Mathematically gap is already in [min, max): this is not clamping a
    // distribution, it corrects the few ulps by which exp followed by log can
    // miss a round trip, so the bounds hold literally rather than to within
    // floating-point rounding. Round first and clamp in long arithmetic, so
    // the bounds hold for every long even above 2^53 ns, where a long is no
    // longer exactly representable as a double.
    return Math.min(Math.max(Math.round(gap), min), max);
  }
}
