package com.triplepat.syntheticalert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class ConcurrencyTest {
  private static final int THREADS = 8;

  @Test
  void threadsRacingToReplayTheSamePauseAllObserveOneState() throws Exception {
    // Every thread wakes at the same instant into a ten-day backlog of
    // transitions. With the lock, one thread replays and the rest observe its
    // result; without it, the replay loops interleave and the schedule runs
    // ahead of any single replay.
    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      for (int round = 0; round < 50; round++) {
        FakeClock clock = new FakeClock(Alerts.START);
        SyntheticAlert alert = SyntheticAlert.builder().build(clock);
        clock.advance(Duration.ofDays(10));
        CyclicBarrier startingGun = new CyclicBarrier(THREADS);
        List<Callable<Double>> scrapes = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
          scrapes.add(
              () -> {
                startingGun.await();
                return alert.value();
              });
        }
        Set<Double> observed = new HashSet<>();
        for (Future<Double> f : pool.invokeAll(scrapes)) {
          observed.add(f.get());
        }
        assertEquals(1, observed.size(), "round " + round + ": every thread sees the same state");
        Alerts.assertOneTransitionAhead(alert, clock.now(), observed.iterator().next());
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void concurrentScrapesOnTheRealClockLeaveTheScheduleOneTransitionAhead() throws Exception {
    // Millisecond-scale durations on the real clock, eight threads hammering
    // value(). A lost update or an interleaved replay would leave the pending
    // transition further ahead than one firing or one gap.
    SyntheticAlert alert =
        SyntheticAlert.builder()
            .meanInterval(Duration.ofMillis(2))
            .minInterval(Duration.ofMillis(1))
            .maxInterval(Duration.ofMillis(4))
            .firingDuration(Duration.ofMillis(1))
            .build();
    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      List<Callable<Boolean>> scrapers = new ArrayList<>();
      for (int i = 0; i < THREADS; i++) {
        scrapers.add(
            () -> {
              for (int n = 0; n < 1000; n++) {
                double v = alert.value();
                if (v != 0.0 && v != 1.0) {
                  return false;
                }
              }
              return true;
            });
      }
      for (Future<Boolean> f : pool.invokeAll(scrapers)) {
        assertTrue(f.get());
      }
      double value = alert.value();
      Alerts.assertAtMostOneTransitionAhead(alert, System.nanoTime(), value);
    } finally {
      pool.shutdownNow();
    }
  }
}
