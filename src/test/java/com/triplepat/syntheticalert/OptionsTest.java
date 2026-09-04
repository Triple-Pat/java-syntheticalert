package com.triplepat.syntheticalert;

import static com.triplepat.syntheticalert.SyntheticAlert.DEFAULT_FIRING_DURATION;
import static com.triplepat.syntheticalert.SyntheticAlert.DEFAULT_MAX_INTERVAL;
import static com.triplepat.syntheticalert.SyntheticAlert.DEFAULT_MEAN_INTERVAL;
import static com.triplepat.syntheticalert.SyntheticAlert.DEFAULT_MIN_INTERVAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OptionsTest {
  private static final long START = 1_000_000_000L;

  @Test
  void defaultsMatchTheSiblingLibraries() {
    // go-, python-, ruby-, and node-syntheticalert all default to 1h / 10m / 2h / 10m.
    assertEquals(Duration.ofHours(1), DEFAULT_MEAN_INTERVAL);
    assertEquals(Duration.ofMinutes(10), DEFAULT_MIN_INTERVAL);
    assertEquals(Duration.ofHours(2), DEFAULT_MAX_INTERVAL);
    assertEquals(Duration.ofMinutes(10), DEFAULT_FIRING_DURATION);
  }

  @Test
  void defaultsSatisfyTheirOwnValidationRules() {
    // Retuning one default must not silently break create().
    assertTrue(DEFAULT_FIRING_DURATION.compareTo(DEFAULT_MEAN_INTERVAL) < 0);
    assertTrue(DEFAULT_MIN_INTERVAL.compareTo(DEFAULT_MEAN_INTERVAL) <= 0);
    assertTrue(DEFAULT_MEAN_INTERVAL.compareTo(DEFAULT_MAX_INTERVAL) <= 0);
    SyntheticAlert alert = SyntheticAlert.create();
    assertEquals(DEFAULT_MEAN_INTERVAL.toNanos(), alert.meanNanos);
    assertEquals(DEFAULT_MIN_INTERVAL.toNanos(), alert.minNanos);
    assertEquals(DEFAULT_MAX_INTERVAL.toNanos(), alert.maxNanos);
    assertEquals(DEFAULT_FIRING_DURATION.toNanos(), alert.firingNanos);
  }

  @Test
  void theBuilderSetsEachOption() {
    SyntheticAlert alert =
        SyntheticAlert.builder()
            .meanInterval(Duration.ofMinutes(30))
            .minInterval(Duration.ofMinutes(5))
            .maxInterval(Duration.ofHours(1))
            .firingDuration(Duration.ofMinutes(2))
            .build();
    assertEquals(Duration.ofMinutes(30).toNanos(), alert.meanNanos);
    assertEquals(Duration.ofMinutes(5).toNanos(), alert.minNanos);
    assertEquals(Duration.ofHours(1).toNanos(), alert.maxNanos);
    assertEquals(Duration.ofMinutes(2).toNanos(), alert.firingNanos);
  }

  @Test
  void theFirstFiringStartsOneSilentGapAfterConstruction() {
    FakeClock clock = new FakeClock(START);
    SyntheticAlert alert = SyntheticAlert.builder().build(clock);
    assertTrue(START + DEFAULT_MIN_INTERVAL.toNanos() <= alert.next);
    assertTrue(alert.next <= START + DEFAULT_MAX_INTERVAL.toNanos());
  }

  @Test
  void aZeroWidthWindowIsLegalAndExact() {
    // min == mean == max: every gap is exactly that long, for deterministic debugging.
    FakeClock clock = new FakeClock(START);
    SyntheticAlert alert =
        Alerts.deterministic(Duration.ofMinutes(1), Duration.ofSeconds(1), clock);
    assertEquals(START + Duration.ofMinutes(1).toNanos(), alert.next);
  }

  static Stream<Arguments> badOptions() {
    return Stream.of(
        bad(b -> b.meanInterval(Duration.ZERO), "mean interval must be positive, got PT0S"),
        bad(
            b -> b.meanInterval(Duration.ofSeconds(-1)),
            "mean interval must be positive, got PT-1S"),
        bad(b -> b.minInterval(Duration.ZERO), "min interval must be positive, got PT0S"),
        bad(b -> b.maxInterval(Duration.ZERO), "max interval must be positive, got PT0S"),
        bad(b -> b.firingDuration(Duration.ZERO), "firing duration must be positive, got PT0S"),
        bad(
            b -> b.firingDuration(Duration.ofHours(1)),
            "firing duration (PT1H) must be less than the mean interval (PT1H)"),
        bad(
            b -> b.minInterval(Duration.ofMinutes(70)),
            "min interval (PT1H10M) and max interval (PT2H) must bracket the mean interval (PT1H)"),
        bad(
            b -> b.maxInterval(Duration.ofMinutes(50)),
            "min interval (PT10M) and max interval (PT50M) must bracket the mean interval (PT1H)"));
  }

  private static Arguments bad(
      UnaryOperator<SyntheticAlert.Builder> configure, String expectedMessage) {
    return Arguments.of(configure, expectedMessage);
  }

  @ParameterizedTest
  @MethodSource("badOptions")
  void badOptionsFailAtBuildTime(
      UnaryOperator<SyntheticAlert.Builder> configure, String expectedMessage) {
    SyntheticAlert.Builder builder = configure.apply(SyntheticAlert.builder());
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, builder::build);
    assertEquals(expectedMessage, e.getMessage());
  }

  @Test
  @SuppressWarnings("NullAway") // Deliberately violating the @NullMarked contract to document it.
  void aNullDurationFailsWithTheJvmsOwnNullPointerException() {
    // There is no runtime null check; the type contract is the check, and a
    // caller who ignores it fails at the first comparison.
    Duration none = null;
    SyntheticAlert.Builder builder = SyntheticAlert.builder().meanInterval(none);
    assertThrows(NullPointerException.class, builder::build);
  }
}
