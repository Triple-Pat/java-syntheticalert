package com.triplepat.syntheticalert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.lang.ref.WeakReference;
import org.junit.jupiter.api.Test;

class MicrometerTest {
  @Test
  void theReadmeSnippetDrivesAMicrometerGaugeAndRendersThePrometheusName() {
    FakeClock clock = new FakeClock(Alerts.START);
    SyntheticAlert alert = SyntheticAlert.builder().build(clock);
    PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    // README snippet, with `alert` from the clock-injecting test constructor.
    Gauge.builder("triplepat.synthetic.alert", alert, SyntheticAlert::value)
        .description(Alerts.DESCRIPTION)
        .strongReference(true)
        .register(registry);

    assertTrue(registry.scrape().contains("triplepat_synthetic_alert 0.0"), registry::scrape);
    clock.set(alert.next);
    assertTrue(registry.scrape().contains("triplepat_synthetic_alert 1.0"), registry::scrape);
    clock.set(alert.next);
    assertTrue(registry.scrape().contains("triplepat_synthetic_alert 0.0"), registry::scrape);
  }

  @Test
  void theGaugeKeepsTheAlertAliveWhenNothingElseDoes() {
    // Micrometer holds a gauge's state object weakly by default, so a caller
    // who inlines SyntheticAlert.create() into the builder would watch the
    // gauge turn to NaN after the next collection. The README snippet asks
    // for a strong reference; this test drops its own and collects.
    PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    WeakReference<SyntheticAlert> witness = register(registry);
    for (int i = 0; i < 10 && witness.get() != null; i++) {
      System.gc();
    }
    String scrape = registry.scrape();
    assertTrue(scrape.contains("triplepat_synthetic_alert 0.0"), scrape);
  }

  /** Registers a fresh alert and returns only a weak reference to it. */
  private static WeakReference<SyntheticAlert> register(PrometheusMeterRegistry registry) {
    SyntheticAlert alert = SyntheticAlert.create();
    Gauge.builder("triplepat.synthetic.alert", alert, SyntheticAlert::value)
        .description(Alerts.DESCRIPTION)
        .strongReference(true)
        .register(registry);
    return new WeakReference<>(alert);
  }
}
