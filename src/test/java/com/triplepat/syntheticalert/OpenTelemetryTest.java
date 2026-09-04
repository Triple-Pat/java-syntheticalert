package com.triplepat.syntheticalert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.Test;

class OpenTelemetryTest {
  private static double latestValue(InMemoryMetricReader reader) {
    MetricData metric = reader.collectAllMetrics().iterator().next();
    assertEquals("triplepat.synthetic.alert", metric.getName());
    return metric.getDoubleGaugeData().getPoints().iterator().next().getValue();
  }

  @Test
  void theReadmeSnippetFeedsAnObservableGauge() {
    FakeClock clock = new FakeClock(1_000_000_000L);
    SyntheticAlert alert = SyntheticAlert.builder().build(clock);
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    SdkMeterProvider provider = SdkMeterProvider.builder().registerMetricReader(reader).build();
    try {
      Meter meter = provider.get("test");

      // README snippet.
      meter
          .gaugeBuilder("triplepat.synthetic.alert")
          .setDescription("Set to 1 when the synthetic alert should fire and 0 otherwise.")
          .buildWithCallback(m -> m.record(alert.value()));

      assertEquals(0.0, latestValue(reader));
      clock.set(alert.next);
      assertEquals(1.0, latestValue(reader));
      clock.set(alert.next);
      assertEquals(0.0, latestValue(reader));
    } finally {
      provider.close();
    }
  }
}
