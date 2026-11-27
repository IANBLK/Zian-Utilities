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

  public record Report(List<Prize> delivered, List<Prize> blocked, List<Prize> uncertain, List<Prize> proving) {
    public boolean empty() { return delivered.isEmpty() && blocked.isEmpty() && uncertain.isEmpty() && proving.isEmpty(); }
  }

  private PrizeClaimer() {}

  /** Delivers every pending prize it can. A locked player gets nothing. */
  public static Report claim(PlayerPase p, Gateway gateway, Saver saver, Consumer<String> warn, long now) {
    Report r = new Report(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    if (p.locked) return r;
    for (Prize z : new ArrayList<>(p.prizes.values())) {
      if (z.state != Prize.State.PENDING) continue;
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
      z.state = Prize.State.DELIVERING;
      z.reason = "";
      z.updated = now;
      try {
        saver.save(p);
      } catch (IOException failure) {
        z.state = Prize.State.PENDING;
        z.reason = "SAVE_FAILED";
        warn.accept("No se pudo escribir el diario del premio " + z.id + " de " + p.id + "; no se entrega: " + failure);
        r.blocked.add(z);
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
    return r;
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

  /** The admin looked and wants it delivered again: UNCERTAIN goes back to PENDING. Coins and Pokemon reuse their ids, so even this cannot duplicate them. */
  public static int reopen(PlayerPase p) {
    int n = 0;
    for (Prize z : p.prizes.values()) {
      if (z.state != Prize.State.UNCERTAIN) continue;
      z.state = Prize.State.PENDING;
      z.reason = "ADMIN_REDELIVER";
      n++;
    }
    if (n > 0) p.dirty = true;
    return n;
  }
}
