package com.triplepat.syntheticalert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SmokeTest {
  @Test
  void defaultsMatchTheSeries() {
    assertEquals(Duration.ofHours(1), SyntheticAlert.DEFAULT_MEAN_INTERVAL);
    assertEquals(3600, SyntheticAlert.DEFAULT_MEAN_INTERVAL.toSeconds());
  }
}
