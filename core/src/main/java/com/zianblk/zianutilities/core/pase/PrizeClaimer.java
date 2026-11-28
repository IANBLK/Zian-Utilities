package com.zianblk.zianutilities.core.pase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The delivery journal. For each pending prize: check it can be given, write DELIVERING to disk, give it, write the result.
 * A crash after the first write leaves DELIVERING, and {@link #recover} turns that into something safe on the next load: coins
 * and Pokemon can be repeated without duplicates (a claim id and a fixed Pokemon id), items and commands become UNCERTAIN for
 * an admin. Nothing here knows Minecraft; the {@link Gateway} does.
 */
public final class PrizeClaimer {
  public interface Gateway {
    /** null if the prize can be given now, otherwise the reason code it stays pending for. May fill {@code prize.detail}. */
    String check(Prize prize);

    /** Gives it. PENDING means nothing was given (reason says why), DELIVERING means given and waiting for proof. */
    Outcome give(Prize prize) throws Exception;
  }

  public interface Saver {
    void save(PlayerPase p) throws IOException;
  }

  public record Outcome(Prize.State state, String reason) {
    public static Outcome delivered() { return new Outcome(Prize.State.DELIVERED, ""); }
    public static Outcome blocked(String reason) { return new Outcome(Prize.State.PENDING, reason); }
    public static Outcome uncertain(String reason) { return new Outcome(Prize.State.UNCERTAIN, reason); }
    public static Outcome proof() { return new Outcome(Prize.State.DELIVERING, "PROOF"); }
  }

  /** {@code remaining} is how many pending prizes were left for the next call because of the per-call limit. */
  public record Report(List<Prize> delivered, List<Prize> blocked, List<Prize> uncertain, List<Prize> proving, int remaining) {
    public boolean empty() { return delivered.isEmpty() && blocked.isEmpty() && uncertain.isEmpty() && proving.isEmpty() && remaining == 0; }
  }

  /** Most prizes one claim gives; the rest wait for the next call. */
  public static final int BATCH = 25;

  private PrizeClaimer() {}

  public static Report claim(PlayerPase p, Gateway gateway, Saver saver, Consumer<String> warn, long now) {
    return claim(p, gateway, saver, warn, now, Integer.MAX_VALUE);
  }

  /**
   * Delivers up to {@code limit} pending prizes it can, one by one: DELIVERING is written to disk before each prize is given and
   * the result right after, so a crash can only leave the single prize that was in progress as DELIVERING (UNCERTAIN for items and
   * commands, resumable for coins and Pokemon). If a write fails, that prize and the ones behind it stay PENDING (SAVE_FAILED).
   * A locked player gets nothing.
   */
  public static Report claim(PlayerPase p, Gateway gateway, Saver saver, Consumer<String> warn, long now, int limit) {
    Report r = new Report(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 0);
    if (p.locked) return r;
    List<Prize> batch = new ArrayList<>();
    int remaining = 0;
    for (Prize z : new ArrayList<>(p.prizes.values())) {
      if (z.state != Prize.State.PENDING) continue;
      if (batch.size() >= limit) {
        remaining++;
        continue;
      }
      String why;
      try {
        why = gateway.check(z);
      } catch (Exception failure) {
        why = "CHECK_FAILED";
        warn.accept("No se pudo comprobar el premio " + z.id + " de " + p.id + ": " + failure);
      }
      if (why != null) {
        z.reason = why;
        z.attempts++;
        p.dirty = true;
        r.blocked.add(z);
        continue;
      }
      batch.add(z);
    }
    for (int n = 0; n < batch.size(); n++) {
      Prize z = batch.get(n);
      mark(z, now);
      try {
        saver.save(p);
      } catch (IOException failure) {
        // The journal cannot be written: this prize and every one behind it in the batch stay PENDING, none is left DELIVERING.
        warn.accept("No se pudo escribir el diario del premio " + z.id + " de " + p.id + "; no se entrega este ni los siguientes: " + failure);
        for (Prize left : batch.subList(n, batch.size())) {
          left.state = Prize.State.PENDING;
          left.reason = "SAVE_FAILED";
          r.blocked.add(left);
        }
        p.dirty = true;
        break;
      }
      Outcome o;
      try {
        o = gateway.give(z);
      } catch (Exception failure) {
        o = Outcome.uncertain("EXCEPTION");
        warn.accept("La entrega del premio " + z.id + " de " + p.id + " falló a medias y queda por revisar: " + failure);
      }
      z.state = o.state();
      z.reason = o.reason();
      z.updated = now;
      if (o.state() == Prize.State.PENDING) z.attempts++;
      switch (o.state()) {
        case DELIVERED -> r.delivered.add(z);
        case PENDING -> r.blocked.add(z);
        case UNCERTAIN -> r.uncertain.add(z);
        case DELIVERING -> r.proving.add(z);
      }
      p.dirty = true;
      try {
        saver.save(p);
      } catch (IOException failure) {
        warn.accept("No se pudo guardar el resultado del premio " + z.id + " de " + p.id + " (se reintenta; si cae el servidor quedará por revisar): " + failure);
      }
    }
    return new Report(r.delivered, r.blocked, r.uncertain, r.proving, remaining);
  }

  private static void mark(Prize z, long now) {
    z.state = Prize.State.DELIVERING;
    z.reason = "";
    z.updated = now;
  }

  /** After a load: a DELIVERING left by a crash. Returns how many changed. */
  public static int recover(PlayerPase p) {
    int n = 0;
    for (Prize z : p.prizes.values()) {
      if (z.state != Prize.State.DELIVERING) continue;
      boolean repeatable = z.reward.kind() == PaseReward.Kind.COINS || z.reward.kind() == PaseReward.Kind.POKEMON;
      z.state = repeatable ? Prize.State.PENDING : Prize.State.UNCERTAIN;
      z.reason = repeatable ? "RESUME" : "INTERRUPTED";
      n++;
    }
    if (n > 0) p.dirty = true;
    return n;
  }

  /** The admin looked and wants it delivered again: UNCERTAIN goes back to PENDING (paid prizes only while the player still has the premium). Coins and Pokemon reuse their ids, so even this cannot duplicate them. */
  public static int reopen(PlayerPase p) {
    int n = 0;
    for (Prize z : p.prizes.values()) {
      if (z.state != Prize.State.UNCERTAIN) continue;
      if (z.premiumOnly() && !p.premium.contains(z.seasonId)) continue;
      z.state = Prize.State.PENDING;
      z.reason = "ADMIN_REDELIVER";
      n++;
    }
    if (n > 0) p.dirty = true;
    return n;
  }
}
