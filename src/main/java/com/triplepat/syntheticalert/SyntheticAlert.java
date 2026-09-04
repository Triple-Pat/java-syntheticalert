package com.triplepat.syntheticalert;

import java.time.Duration;

/** A measurement callback for a synthetic alert. */
public final class SyntheticAlert {
  /** Default mean silent gap between firings. */
  public static final Duration DEFAULT_MEAN_INTERVAL = Duration.ofHours(1);

  /** Default lower bound on the silent gap. */
  public static final Duration DEFAULT_MIN_INTERVAL = Duration.ofMinutes(10);

  /** Default upper bound on the silent gap. */
  public static final Duration DEFAULT_MAX_INTERVAL = Duration.ofHours(2);

  /** Default length of each firing. */
  public static final Duration DEFAULT_FIRING_DURATION = Duration.ofMinutes(10);

  private SyntheticAlert() {}
}
