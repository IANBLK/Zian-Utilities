package com.zianblk.zianutilities.core.pase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The battle pass rules, with no Minecraft in it. Order of a grant: day roll, caps (day, source, boosted day), x2 inside the
 * last days of the season, then the XP fills the previous season first and the current one after; what is left past level
 * 200 becomes extra levels (current season only, at base XP, never multiplied).
 */
public final class PaseEngine {
  /** Where the per-player data lives; {@code get} creates an empty record for a new player. */
  public interface Store {
    PlayerPase get(UUID id);
  }

  public record MissionResult(MissionType type, int progress, int target, boolean completed, GrantResult grant) {}

  public record MissionLine(MissionType type, int progress, int target, int xp, boolean done) {}

  public record View(
      String seasonId, int level, long xpIntoLevel, int xpPerLevel, int maxLevel, int extraLevels, int maxExtraLevels, long extraIntoLevel,
      String previousId, int previousLevel, boolean previousComplete, boolean boost, long capTotal, long capLeft, long daysLeft,
      boolean premium, List<MissionLine> missions) {}

  private final PaseRules rules;
  private final SeasonBook seasons;
  private final Store store;
  private final Clock clock;

  public PaseEngine(PaseRules rules, SeasonBook seasons, Store store, Clock clock) {
    this.rules = rules;
    this.seasons = seasons;
    this.store = store;
    this.clock = clock;
  }

  public PaseRules rules() { return rules; }

  public SeasonBook seasons() { return seasons; }

  public Instant now() { return clock.instant(); }

  /** The game day of an instant: the calendar day in the configured zone, moved back by the reset time. */
  public LocalDate dayOf(Instant instant) {
    var local = instant.atZone(rules.zone()).toLocalDateTime();
    return local.minusHours(rules.resetTime().getHour()).minusMinutes(rules.resetTime().getMinute()).toLocalDate();
  }

  /** Moves to the next season if the current one has ended. True if it changed. */
  public boolean rollSeason() {
    return seasons.rollTo(clock.instant(), rules.seasonMonths(), rules.zone());
  }

  /** Ends the current season now and starts the next one. */
  public Season newSeason() {
    Season cur = seasons.current();
    seasons.start(Season.ofMonths("temporada-" + (cur.number() + 1), clock.instant(), rules.seasonMonths(), rules.zone()));
    return seasons.current();
  }

  private PlayerPase open(UUID id, Instant now) {
    rollSeason();
    PlayerPase p = store.get(id);
    if (p.locked) return null;
    String today = dayOf(now).toString();
    // A clock that went back never wipes a day that already counted.
    if (p.day.isEmpty() || today.compareTo(p.day) > 0) {
      p.clearDaily();
      p.day = today;
      p.dirty = true;
      Set<String> keep = Set.of(seasons.current().id(), seasons.previous() == null ? "" : seasons.previous().id());
      p.explored.keySet().removeIf(k -> !keep.contains(k));
    }
    return p;
  }

  // ------------------------------------------------------------------ XP

  /** Gives base XP from a source, respecting every cap. {@code baseXp} is before the x2. */
  public GrantResult grant(UUID id, String source, int baseXp) {
    if (baseXp <= 0) return GrantResult.NOTHING;
    Instant now = clock.instant();
    PlayerPase p = open(id, now);
    if (p == null) return new GrantResult(0, GrantResult.Limit.LOCKED, List.of());
    boolean boost = rules.inBoostWindow(seasons.current(), now);
    int m = boost ? rules.boostFactor() : 1;
    long room = rules.dailyCap() - p.baseToday;
    long sourceRoom = rules.sourceCap(source) - p.perSource.getOrDefault(source, 0);
    long finalRoom = (rules.dayFinalCap(boost) - p.finalToday) / m;
    long base = Math.min(baseXp, Math.min(room, Math.min(sourceRoom, finalRoom)));
    GrantResult.Limit limit = base >= baseXp ? GrantResult.Limit.NONE
        : (sourceRoom < room && sourceRoom <= finalRoom ? GrantResult.Limit.SOURCE : GrantResult.Limit.DAY);
    if (base <= 0) return new GrantResult(0, limit, List.of());
    p.baseToday += base;
    p.finalToday += base * m;
    p.perSource.merge(source, (int) base, Integer::sum);
    p.dirty = true;
    List<LevelEvent> events = new ArrayList<>();
    long applied = distribute(p, base * m, m, true, now, events);
    p.events.addAll(events);
    return new GrantResult(applied, limit, List.copyOf(events));
  }

  /** Admin XP: no caps, no x2, previous season first. */
  public GrantResult adminGrant(UUID id, long xp) {
    return adminGive(id, xp, true);
  }

  /** Admin levels of the current season: n * xpPerLevel XP, no caps, no x2. */
  public GrantResult adminGiveLevels(UUID id, int levels) {
    return adminGive(id, (long) levels * rules.xpPerLevel(), false);
  }

  private GrantResult adminGive(UUID id, long xp, boolean previousFirst) {
    if (xp <= 0) return GrantResult.NOTHING;
    Instant now = clock.instant();
    PlayerPase p = open(id, now);
    if (p == null) return new GrantResult(0, GrantResult.Limit.LOCKED, List.of());
    List<LevelEvent> events = new ArrayList<>();
    long applied = distribute(p, xp, 1, previousFirst, now, events);
    p.events.addAll(events);
    p.dirty = true;
    return new GrantResult(applied, GrantResult.Limit.NONE, List.copyOf(events));
  }

  /** Takes XP from the current season (extra XP first). Level events already handed out stay. */
  public long adminRemove(UUID id, long xp) {
    PlayerPase p = open(id, clock.instant());
    if (p == null || xp <= 0) return 0;
    PlayerPase.Progress pr = p.progress(seasons.current().id());
    long fromExtra = Math.min(pr.extraXp, xp);
    pr.extraXp -= fromExtra;
    long fromXp = Math.min(pr.xp, xp - fromExtra);
    pr.xp -= fromXp;
    p.dirty = true;
    return fromExtra + fromXp;
  }

  private long distribute(PlayerPase p, long finalXp, int m, boolean previousFirst, Instant now, List<LevelEvent> out) {
    long left = finalXp;
    Season prev = seasons.previous();
    if (previousFirst && prev != null) left = addNormal(p, prev.id(), left, now, out);
    left = addNormal(p, seasons.current().id(), left, now, out);
    long applied = finalXp - left;
    if (left > 0) applied += addExtra(p, seasons.current().id(), left / m, now, out) * m;
    return applied;
  }

  private long addNormal(PlayerPase p, String seasonId, long left, Instant now, List<LevelEvent> out) {
    long take = Math.min(rules.totalXp() - p.xp(seasonId), left);
    if (take <= 0) return left;
    PlayerPase.Progress pr = p.progress(seasonId);
    long before = pr.xp / rules.xpPerLevel();
    pr.xp += take;
    for (long l = before + 1; l <= pr.xp / rules.xpPerLevel(); l++) out.add(new LevelEvent(seasonId, (int) l, false, now.toEpochMilli()));
    return left - take;
  }

  private long addExtra(PlayerPase p, String seasonId, long baseXp, Instant now, List<LevelEvent> out) {
    PlayerPase.Progress pr = p.progress(seasonId);
    long take = Math.min((long) rules.maxExtraLevels() * rules.extraLevelXp() - pr.extraXp, baseXp);
    if (take <= 0) return 0;
    long before = pr.extraXp / rules.extraLevelXp();
    pr.extraXp += take;
    for (long l = before + 1; l <= pr.extraXp / rules.extraLevelXp(); l++) out.add(new LevelEvent(seasonId, (int) l, true, now.toEpochMilli()));
    return take;
  }

  // ------------------------------------------------------------------ missions

  /** True if today's set has this mission and the player has not finished it. */
  public boolean missionOpen(UUID id, MissionType type) {
    PlayerPase p = open(id, clock.instant());
    return p != null && MissionBoard.find(LocalDate.parse(p.day), rules, type) != null && !p.missionDone.contains(type.name());
  }

  /** Counts progress on a mission of today. {@code distinctKey} (a villager and its level, say) only counts once a day. Null if it does not apply. */
  public MissionResult progress(UUID id, MissionType type, int amount, String distinctKey) {
    PlayerPase p = open(id, clock.instant());
    if (p == null || amount <= 0) return null;
    MissionDef def = MissionBoard.find(LocalDate.parse(p.day), rules, type);
    String k = type.name();
    if (def == null || p.missionDone.contains(k)) return null;
    if (distinctKey != null && !p.trades.add(k + ":" + distinctKey)) return null;
    int now = Math.min(def.target(), p.missionProgress.getOrDefault(k, 0) + amount);
    p.missionProgress.put(k, now);
    p.dirty = true;
    if (now < def.target()) return new MissionResult(type, now, def.target(), false, GrantResult.NOTHING);
    p.missionDone.add(k);
    return new MissionResult(type, now, def.target(), true, grant(id, PaseRules.MISSION, def.xp()));
  }

  /** A biome or structure seen for the first time this season; only counts (and is only marked) while the explore mission is open. */
  public MissionResult explore(UUID id, String key) {
    PlayerPase p = open(id, clock.instant());
    if (p == null || !missionOpen(id, MissionType.EXPLORE)) return null;
    Set<String> seen = p.explored.computeIfAbsent(seasons.current().id(), s -> new LinkedHashSet<>());
    if (!seen.add(key)) return null;
    p.dirty = true;
    return progress(id, MissionType.EXPLORE, 1, null);
  }

  // ------------------------------------------------------------------ views and admin

  public View view(UUID id) {
    Instant now = clock.instant();
    PlayerPase p = open(id, now);
    if (p == null) return null;
    Season cur = seasons.current();
    boolean boost = rules.inBoostWindow(cur, now);
    int m = boost ? rules.boostFactor() : 1;
    PlayerPase.Progress pr = p.seasons.getOrDefault(cur.id(), new PlayerPase.Progress());
    int per = rules.xpPerLevel();
    long capTotal = Math.min((long) rules.dailyCap() * m, rules.dayFinalCap(boost));
    long capLeft = Math.max(0, Math.min((rules.dailyCap() - p.baseToday) * m, rules.dayFinalCap(boost) - p.finalToday));
    Season prev = seasons.previous();
    long prevXp = prev == null ? 0 : p.xp(prev.id());
    List<MissionLine> lines = new ArrayList<>();
    for (MissionDef d : MissionBoard.forDay(LocalDate.parse(p.day), rules))
      lines.add(new MissionLine(d.type(), p.missionProgress.getOrDefault(d.type().name(), 0), d.target(), d.xp(), p.missionDone.contains(d.type().name())));
    long days = Math.max(0, (Duration.between(now, cur.end()).toHours() + 23) / 24);
    return new View(cur.id(), (int) (pr.xp / per), pr.xp % per, per, rules.levels(), (int) (pr.extraXp / rules.extraLevelXp()), rules.maxExtraLevels(),
        pr.extraXp % rules.extraLevelXp(), prev == null ? null : prev.id(), (int) (prevXp / per), prevXp >= rules.totalXp(), boost, capTotal, capLeft, days,
        p.premium.contains(cur.id()), List.copyOf(lines));
  }

  /** Marks the paid track of a season. P2 calls this from the Tebex command. */
  public boolean setPremium(UUID id, String seasonId, boolean value) {
    PlayerPase p = open(id, clock.instant());
    if (p == null) return false;
    boolean changed = value ? p.premium.add(seasonId) : p.premium.remove(seasonId);
    p.dirty |= changed;
    return changed;
  }

  /** Level events waiting for their reward; P2 drains them after delivering. */
  public List<LevelEvent> pendingEvents(UUID id) {
    return List.copyOf(store.get(id).events);
  }

  /** Clears today's caps, mission progress, trades and the explored list of the current season. */
  public void resetDaily(UUID id) {
    PlayerPase p = open(id, clock.instant());
    if (p == null) return;
    p.clearDaily();
    p.explored.remove(seasons.current().id());
    p.dirty = true;
  }
}
