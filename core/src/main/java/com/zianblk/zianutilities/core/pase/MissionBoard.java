package com.zianblk.zianutilities.core.pase;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** The daily set: the same for every player, drawn from the date alone, so nothing needs storing and no past day can be done late. */
public final class MissionBoard {
  private MissionBoard() {}

  public static List<MissionDef> forDay(LocalDate day, PaseRules rules) {
    List<MissionDef> all = new ArrayList<>();
    for (MissionType t : MissionType.values()) {
      MissionDef d = rules.missions().get(t);
      if (d != null) all.add(d);
    }
    Collections.shuffle(all, new Random(day.toEpochDay() * 0x9E3779B97F4A7C15L + 17));
    return List.copyOf(all.subList(0, Math.min(rules.missionsPerDay(), all.size())));
  }

  public static MissionDef find(LocalDate day, PaseRules rules, MissionType type) {
    for (MissionDef d : forDay(day, rules)) if (d.type() == type) return d;
    return null;
  }
}
