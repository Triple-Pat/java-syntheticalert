package com.triplepat.syntheticalert;

import static com.triplepat.syntheticalert.SyntheticAlert.DEFAULT_FIRING_DURATION;
import static com.triplepat.syntheticalert.SyntheticAlert.DEFAULT_MAX_INTERVAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ScheduleTest {
  private static final long START = 1_000_000_000L;
  private static final Duration GAP = Duration.ofMinutes(1);
  private static final Duration FIRING = Duration.ofSeconds(10);
  private static final Duration ONE_NANO = Duration.ofNanos(1);
  private static final Duration TEN_DAYS = Duration.ofDays(10);

  @Test
  void startsResolved() {
    SyntheticAlert alert = Alerts.deterministic(GAP, FIRING, new FakeClock(START));
    assertEquals(0.0, alert.value());
  }

  @Test
  void firesAfterExactlyOneGapAndResolvesAfterExactlyOneFiring() {
    FakeClock clock = new FakeClock(START);
    SyntheticAlert alert = Alerts.deterministic(GAP, FIRING, clock);
    for (int cycle = 0; cycle < 100; cycle++) {
      String at = "cycle " + cycle;
      clock.advance(GAP.minus(ONE_NANO));
      assertEquals(0.0, alert.value(), at + ": firing just before the gap elapsed");
      clock.advance(ONE_NANO);
      assertEquals(1.0, alert.value(), at + ": not firing at the end of the gap");
      clock.advance(FIRING.minus(ONE_NANO));
      assertEquals(1.0, alert.value(), at + ": resolved before the firing duration elapsed");
      clock.advance(ONE_NANO);
      assertEquals(0.0, alert.value(), at + ": still firing after the firing duration");
    }
  }

  @Test
  void theGapIsMeasuredFromTheEndOfTheFiringNotItsStart() {
    FakeClock clock = new FakeClock(START);
    SyntheticAlert alert = Alerts.deterministic(GAP, FIRING, clock);
    // First firing at START + GAP, resolving at START + GAP + FIRING. If the
    // gap were measured from the start of the firing, the second firing would
    // begin at START + 2 GAP; it must begin at START + 2 GAP + FIRING.
    clock.set(START + GAP.multipliedBy(2).toNanos());
    assertEquals(0.0, alert.value());
    clock.set(START + GAP.multipliedBy(2).plus(FIRING).toNanos() - 1);
    assertEquals(0.0, alert.value());
    clock.advance(ONE_NANO);
    assertEquals(1.0, alert.value());
  }

  @Test
  void aLongPauseReplaysEveryTransitionToThePhasePlainArithmeticPredicts() {
    // Ten days is 12,342 full cycles of 70 s plus exactly 60 s: the gap has
    // just elapsed, so the alert is firing at that instant, still firing 5 s
    // later, and resolved 15 s later.
    Duration cycle = GAP.plus(FIRING);
    long remainder = TEN_DAYS.toNanos() % cycle.toNanos();
    assertEquals(GAP.toNanos(), remainder, "the arithmetic the test relies on");

    FakeClock clock = new FakeClock(START);
    SyntheticAlert alert = Alerts.deterministic(GAP, FIRING, clock);
    clock.advance(TEN_DAYS);
    assertEquals(1.0, alert.value());
    clock.advance(Duration.ofSeconds(5));
    assertEquals(1.0, alert.value());
    clock.advance(Duration.ofSeconds(10));
    assertEquals(0.0, alert.value());
  }

  @Test
  void aLongPauseLeavesTheDefaultScheduleOneTransitionAhead() {
    FakeClock clock = new FakeClock(START);
    SyntheticAlert alert = SyntheticAlert.builder().build(clock);
    clock.advance(TEN_DAYS);
    double value = alert.value();
    assertTrue(value == 0.0 || value == 1.0, "value " + value);
    long ahead = alert.next - clock.now();
    assertTrue(ahead > 0, "the pending transition is in the future");
    assertTrue(
        ahead <= DEFAULT_MAX_INTERVAL.plus(DEFAULT_FIRING_DURATION).toNanos(),
        "the pending transition is at most one cycle away");
  }

  @Test
  void theScheduleSurvivesTheNanosecondClockWrappingAround() {
    // System.nanoTime() may be negative and may wrap; only differences mean
    // anything. Start 30 s short of Long.MAX_VALUE so the first firing lands
    // past the wrap.
    FakeClock clock = new FakeClock(Long.MAX_VALUE - Duration.ofSeconds(30).toNanos());
    SyntheticAlert alert = Alerts.deterministic(GAP, FIRING, clock);
    clock.advance(GAP.minus(ONE_NANO));
    assertEquals(0.0, alert.value());
    clock.advance(ONE_NANO);
    assertTrue(clock.now() < 0, "the clock has wrapped");
    assertEquals(1.0, alert.value());
    clock.advance(FIRING);
    assertEquals(0.0, alert.value());
  }
}
