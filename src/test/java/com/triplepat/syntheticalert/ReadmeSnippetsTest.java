package com.triplepat.syntheticalert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** README snippets that no other test compiles. */
class ReadmeSnippetsTest {
  @Test
  void theNoMagicSubstituteCompilesAndReadsZeroOrOne() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    // README snippet: the deterministic substitute the library exists to improve on.
    Gauge.builder(
            "triplepat.synthetic.alert",
            () -> LocalTime.now(ZoneId.systemDefault()).getMinute() < 10 ? 1 : 0)
        .register(registry);

    double value = registry.get("triplepat.synthetic.alert").gauge().value();
    assertTrue(value == 0.0 || value == 1.0);
  }
}
