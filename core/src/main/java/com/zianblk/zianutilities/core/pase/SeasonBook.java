package com.zianblk.zianutilities.core.pase;

import java.time.Instant;
import java.time.ZoneId;

/** The current season and the one before it (only one back). {@link #dirty} tells the storage to write it. */
public final class SeasonBook {
  private Season current;
  private Season previous;
  public boolean dirty;

  public SeasonBook(Season current, Season previous) {
    this.current = current;
    this.previous = previous;
  }

  public Season current() { return current; }

  public Season previous() { return previous; }

  /** The current season becomes the previous one and {@code next} the current. The one before is forgotten. */
  public void start(Season next) {
    previous = current;
    current = next;
    dirty = true;
  }

  /** Starts the following seasons while the current one has ended (one per call per elapsed season). True if it changed. */
  public boolean rollTo(Instant now, int months, ZoneId zone) {
    boolean changed = false;
    while (!now.isBefore(current.end())) {
      start(Season.ofMonths("temporada-" + (current.number() + 1), current.end(), months, zone));
      changed = true;
    }
    return changed;
  }
}
