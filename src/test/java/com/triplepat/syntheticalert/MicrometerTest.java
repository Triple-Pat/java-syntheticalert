package com.triplepat.syntheticalert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

class MicrometerTest {
  @Test
  void theReadmeSnippetDrivesAMicrometerGaugeAndRendersThePrometheusName() {
    FakeClock clock = new FakeClock(1_000_000_000L);
    SyntheticAlert alert = SyntheticAlert.builder().build(clock);
    PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    // README snippet, with `alert` from the clock-injecting test constructor.
    Gauge.builder("triplepat.synthetic.alert", alert, SyntheticAlert::value)
        .description(
            "Set to 1 when the synthetic alert should fire and 0 otherwise. "
                + "Alert on this metric and route the alert to a Triple Pat check-in "
                + "timer to continuously test your alerting pipeline.")
        .register(registry);

    assertTrue(registry.scrape().contains("triplepat_synthetic_alert 0.0"), registry::scrape);
    clock.set(alert.next);
    assertTrue(registry.scrape().contains("triplepat_synthetic_alert 1.0"), registry::scrape);
    clock.set(alert.next);
    assertTrue(registry.scrape().contains("triplepat_synthetic_alert 0.0"), registry::scrape);
  }
}
