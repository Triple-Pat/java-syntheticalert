package com.triplepat.syntheticalert;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.prometheus.metrics.core.metrics.GaugeWithCallback;
import io.prometheus.metrics.expositionformats.PrometheusTextFormatWriter;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PrometheusClientTest {
  private static String scrape(PrometheusRegistry registry) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    PrometheusTextFormatWriter.builder().build().write(out, registry.scrape());
    return out.toString(StandardCharsets.UTF_8);
  }

  @Test
  void theReadmeSnippetDrivesAGaugeWithCallback() throws IOException {
    FakeClock clock = new FakeClock(Alerts.START);
    SyntheticAlert alert = SyntheticAlert.builder().build(clock);
    PrometheusRegistry registry = new PrometheusRegistry();

    // README snippet, registered on a private registry rather than the default.
    GaugeWithCallback.builder()
        .name("triplepat_synthetic_alert")
        .help("Set to 1 when the synthetic alert should fire and 0 otherwise.")
        .callback(cb -> cb.call(alert.value()))
        .register(registry);

    assertTrue(scrape(registry).contains("triplepat_synthetic_alert 0.0"));
    clock.set(alert.next);
    assertTrue(scrape(registry).contains("triplepat_synthetic_alert 1.0"));
    clock.set(alert.next);
    assertTrue(scrape(registry).contains("triplepat_synthetic_alert 0.0"));
  }
}
