package com.triplepat.syntheticalert;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * A measurement callback for a synthetic alert.
 *
 * <p>Each firing holds the value at 1 for exactly the firing duration. The silent gap between
 * firings, from the end of one to the start of the next, is exponentially distributed (memoryless)
 * with the configured mean: an attempt at a Poisson process, which cannot synchronize with cron
 * jobs or scrape cycles. As a nod to practicality the gap is truncated to the configured min and
 * max, which makes the process only roughly Poisson; widen the bounds to get closer.
 *
 * <p>The schedule advances lazily: nothing happens until {@link #value()} is called, at which point
 * every transition up to now is replayed, on {@link System#nanoTime()}. {@code value()} is safe to
 * call from any number of threads; one instance per JVM is the intended use.
 *
 * <p>Every duration is a {@link Duration}, so the unit lives in the code rather than in the
 * documentation. Nullness follows the package's {@code @NullMarked} contract: there are no runtime
 * null checks, and a {@code null} duration fails with the JVM's own {@link NullPointerException}.
 */
public final class SyntheticAlert {
  /** Default mean silent gap between firings. */
  public static final Duration DEFAULT_MEAN_INTERVAL = Duration.ofHours(1);

  /** Default lower bound on the silent gap. */
  public static final Duration DEFAULT_MIN_INTERVAL = Duration.ofMinutes(10);

  /** Default upper bound on the silent gap. */
  public static final Duration DEFAULT_MAX_INTERVAL = Duration.ofHours(2);

  /** Default length of each firing. */
  public static final Duration DEFAULT_FIRING_DURATION = Duration.ofMinutes(10);

  // The schedule runs in whole nanoseconds, the unit of System.nanoTime().
  // Package-private so tests in this package can read the configuration.
  final long meanNanos;
  final long minNanos;
  final long maxNanos;
  final long firingNanos;

  private final LongSupplier clock;
  private final Object lock = new Object();

  /** Whether the synthetic alert is firing. Guarded by {@code lock}. */
  private boolean firing;

  /**
   * When the pending transition happens, in the clock's nanoseconds. Guarded by {@code lock};
   * package-private so tests in this package can read it between calls.
   */
  long next;

  private SyntheticAlert(
      long meanNanos, long minNanos, long maxNanos, long firingNanos, LongSupplier clock) {
    this.meanNanos = meanNanos;
    this.minNanos = minNanos;
    this.maxNanos = maxNanos;
    this.firingNanos = firingNanos;
    this.clock = clock;
    // The first firing starts one silent gap after construction.
    next = clock.getAsLong() + Gap.truncatedExponential(meanNanos, minNanos, maxNanos);
  }

  /**
   * Returns 1 if the synthetic alert should be firing right now and 0 otherwise. Replays every
   * schedule transition between the last call and now, so the realized schedule is the same
   * whatever the scrape cadence.
   *
   * @return {@code 1.0} while firing, {@code 0.0} otherwise
   */
  public double value() {
    // Why carry state and replay transitions, rather than compute the state
    // from the clock alone?
    //
    // A stateless answer to "is a firing in progress?" needs the firing times
    // to be a pure function of wall-clock time. That is possible for a plain
    // Poisson process, because it has independent increments: chop time into
    // epochs, seed a PRNG from the epoch index, draw that epoch's arrivals, and
    // check whether one falls within the last firing duration. It has a real
    // attraction, too: every replica of a service would compute the same
    // schedule and raise one alert instead of N.
    //
    // But the min and max bounds on the silent gap make each gap depend on
    // where the previous firing ended, which destroys independent increments;
    // epochs can no longer be generated in isolation. Thinning and back-filling
    // a plain Poisson stream to fake the bounds would have to peek across epoch
    // boundaries and would no longer have a distribution the tests can name.
    // The bounds exist for practical reasons (the alert must visibly resolve;
    // the check-in timer must not false-alarm), so we honor them exactly with
    // an alternating renewal process: fixed firings, i.i.d. truncated-
    // exponential gaps, and two fields of state.
    //
    // Replaying every missed transition, rather than jumping to the current
    // state, keeps the realized schedule identical whatever the scrape cadence.
    // It costs one loop iteration per elapsed transition, about fifty a day at
    // the defaults, so even a scrape after a week of silence is trivial.
    //
    // System.nanoTime() is monotonic, so a wall-clock step neither skips nor
    // repeats a transition. Its values are only meaningful as differences and
    // may be negative or wrap, hence `now - next >= 0` rather than `now >= next`.
    //
    // The critical section is a few arithmetic operations with no blocking, so
    // plain synchronization is the right tool; a scrape from many threads at
    // once serializes for nanoseconds.
    synchronized (lock) {
      long now = clock.getAsLong();
      while (now - next >= 0) {
        firing = !firing;
        next += firing ? firingNanos : Gap.truncatedExponential(meanNanos, minNanos, maxNanos);
      }
      return firing ? 1.0 : 0.0;
    }
  }

  /**
   * Returns a synthetic alert with the default schedule.
   *
   * @return a synthetic alert configured with the {@code DEFAULT_*} durations
   */
  public static SyntheticAlert create() {
    return builder().build();
  }

  /**
   * Returns a builder whose every option starts at its default.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Configures a {@link SyntheticAlert}. Every option has a default, so {@code builder().build()}
   * is the same as {@link #create()}.
   */
  public static final class Builder {
    /** The longest duration that fits in a long of nanoseconds: about 292 years. */
    private static final Duration LONGEST = Duration.ofNanos(Long.MAX_VALUE);

    private Duration meanInterval = DEFAULT_MEAN_INTERVAL;
    private Duration minInterval = DEFAULT_MIN_INTERVAL;
    private Duration maxInterval = DEFAULT_MAX_INTERVAL;
    private Duration firingDuration = DEFAULT_FIRING_DURATION;

    private Builder() {}

    /**
     * Sets the mean of the exponential distribution the silent gaps are drawn from, before
     * truncation to the min and max bounds. The gap is measured from the end of one firing to the
     * start of the next. Truncation pulls the realized average toward the window's interior: with
     * the defaults it is about 49 minutes, not an hour.
     *
     * @param d the mean silent gap; must be positive
     * @return this builder
     */
    public Builder meanInterval(Duration d) {
      meanInterval = d;
      return this;
    }

    /**
     * Sets a lower bound on the silent gap between firings, guaranteeing the alert stays resolved
     * at least that long. It must not exceed the mean interval. Setting min, mean, and max all
     * equal is allowed: every gap is then exactly that long and the schedule is periodic, which is
     * pointless in production but handy for deterministic debugging.
     *
     * @param d the lower bound; must be positive
     * @return this builder
     */
    public Builder minInterval(Duration d) {
      minInterval = d;
      return this;
    }

    /**
     * Sets an upper bound on the silent gap between firings. It must be at least the mean interval.
     * See {@link #minInterval(Duration)} for the all-equal case.
     *
     * @param d the upper bound; must be positive
     * @return this builder
     */
    public Builder maxInterval(Duration d) {
      maxInterval = d;
      return this;
    }

    /**
     * Sets how long the value stays at 1 during each firing. It must be shorter than the mean
     * interval between firings.
     *
     * @param d the firing duration; must be positive
     * @return this builder
     */
    public Builder firingDuration(Duration d) {
      firingDuration = d;
      return this;
    }

    /**
     * Validates the options and returns the synthetic alert. Validation happens here, never at
     * scrape time.
     *
     * @return the configured synthetic alert
     * @throws IllegalArgumentException if a duration is not positive or is too long to count in
     *     nanoseconds (about 292 years), the firing duration is not shorter than the mean interval,
     *     or the min and max intervals do not bracket the mean
     */
    public SyntheticAlert build() {
      return build(System::nanoTime);
    }

    // Package-private so tests can inject a clock. The public API has no clock
    // option: the schedule always runs on System.nanoTime().
    SyntheticAlert build(LongSupplier clock) {
      long meanNanos = nanos("mean interval", meanInterval);
      long minNanos = nanos("min interval", minInterval);
      long maxNanos = nanos("max interval", maxInterval);
      long firingNanos = nanos("firing duration", firingDuration);
      if (firingDuration.compareTo(meanInterval) >= 0) {
        throw new IllegalArgumentException(
            "firing duration ("
                + firingDuration
                + ") must be less than the mean interval ("
                + meanInterval
                + ")");
      }
      if (minInterval.compareTo(meanInterval) > 0 || meanInterval.compareTo(maxInterval) > 0) {
        throw new IllegalArgumentException(
            "min interval ("
                + minInterval
                + ") and max interval ("
                + maxInterval
                + ") must bracket the mean interval ("
                + meanInterval
                + ")");
      }
      return new SyntheticAlert(meanNanos, minNanos, maxNanos, firingNanos, clock);
    }

    /** Checks the duration is positive and fits in nanoseconds, and converts it. */
    private static long nanos(String name, Duration d) {
      if (d.compareTo(Duration.ZERO) <= 0) {
        throw new IllegalArgumentException(name + " must be positive, got " + d);
      }
      if (d.compareTo(LONGEST) > 0) {
        throw new IllegalArgumentException(
            name + " must be at most " + LONGEST + " (about 292 years), got " + d);
      }
      return d.toNanos();
    }
  }
}
