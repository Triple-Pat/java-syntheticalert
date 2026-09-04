/**
 * A time-based callback to drive a synthetic alert metric, so a <a
 * href="https://triplepat.com">Triple Pat</a> check-in timer can verify an alerting pipeline end to
 * end.
 *
 * <p>A broken alerting pipeline looks exactly like a healthy system. {@link
 * com.triplepat.syntheticalert.SyntheticAlert#value()} is 1 while a synthetic alert should be
 * firing and 0 otherwise, on a memoryless schedule. Hand it to whichever metrics client you already
 * use as a gauge callback, alert on the gauge, and route that alert to a Triple Pat check-in timer.
 * Every delivered alert becomes a check-in, and the timer raises an alarm if the alerts stop
 * arriving, which is the one failure your alerting system cannot report about itself.
 *
 * <p>The library owns no meter, starts no thread, and depends on nothing but the JSpecify nullness
 * annotations: everything in this package is non-null unless marked otherwise.
 */
@NullMarked
package com.triplepat.syntheticalert;

import org.jspecify.annotations.NullMarked;
