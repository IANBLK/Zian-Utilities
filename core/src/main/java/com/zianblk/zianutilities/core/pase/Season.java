package com.zianblk.zianutilities.core.pase;

import java.time.Instant;
import java.time.ZoneId;

/** A pass season: id like "temporada-2", start inclusive, end exclusive. */
public record Season(String id, Instant start, Instant end) {
  public Season {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("id vacio");
    if (!end.isAfter(start)) throw new IllegalArgumentException("el fin debe ser posterior al inicio");
  }

  /** A season of whole calendar months in the given zone (two by default). */
  public static Season ofMonths(String id, Instant start, int months, ZoneId zone) {
    return new Season(id, start, start.atZone(zone).plusMonths(months).toInstant());
  }

  /** The start of the game day that contains {@code now}: that day's reset time in the zone (the day before if it is not reached yet). */
  public static Instant alignedStart(Instant now, java.time.LocalTime reset, ZoneId zone) {
    var day = now.atZone(zone).toLocalDateTime().minusHours(reset.getHour()).minusMinutes(reset.getMinute()).toLocalDate();
    return day.atTime(reset).atZone(zone).toInstant();
  }

  public boolean contains(Instant now) {
    return !now.isBefore(start) && now.isBefore(end);
  }

  /** Number in "temporada-N", or 0 when the id has another shape. */
  public int number() {
    return numberOf(id);
  }

  public static int numberOf(String id) {
    if (id.startsWith("temporada-")) {
      try {
        return Integer.parseInt(id.substring(10));
      } catch (NumberFormatException ignored) {
        return 0;
      }
    }
    return 0;
  }
}
