package com.zianblk.zianutilities.core.pase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** System time plus an offset a smoke test can move forward to cross the daily reset or the last days of a season. */
public final class OffsetClock extends Clock {
  private volatile Duration offset = Duration.ZERO;

  public void advance(Duration by) { offset = offset.plus(by); }

  public void reset() { offset = Duration.ZERO; }

  @Override public ZoneId getZone() { return ZoneOffset.UTC; }

  @Override public Clock withZone(ZoneId zone) { return this; }

  @Override public Instant instant() { return Instant.now().plus(offset); }
}
