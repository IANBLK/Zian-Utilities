package com.zianblk.zianutilities.neoforge.raids;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.necro.raid.dens.common.network.packets.RaidChallengePacket;
import com.necro.raid.dens.common.raids.RaidInstance;
import com.necro.raid.dens.common.raids.RaidSession;
import com.necro.raid.dens.common.raids.RaidState;
import com.necro.raid.dens.common.raids.helpers.RaidHelper;
import com.necro.raid.dens.common.raids.helpers.RaidJoinHelper;
import com.necro.raid.dens.common.util.IRaidAccessor;
import com.necro.raid.dens.common.util.RaidUtils;
import com.zianblk.zianutilities.core.raids.DuelEngine;
import com.zianblk.zianutilities.core.raids.DuelParams;
import com.zianblk.zianutilities.core.raids.DuelResult;
import com.zianblk.zianutilities.core.raids.RaidRewardPolicy;
import com.zianblk.zianutilities.core.raids.RaidTierPolicy;
import com.zianblk.zianutilities.core.raids.SafeSteps;
import com.zianblk.zianutilities.neoforge.Messages;
import com.zianblk.zianutilities.neoforge.Tr;
import com.zianblk.zianutilities.neoforge.UtilitiesPermissions;
import com.zianblk.zianutilities.neoforge.breeding.LuckPermsLookup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The duel itself: a one-player scripted fight against the raid boss. The boss keeps noAI; this class moves it,
 * telegraphs and lands the area attacks, turns the player hits into Raid Dens damage records and closes the raid
 * through queueStopInstance. It never touches the player inventory, armor or Curios, and it never lets anyone die.
 */
public final class RaidDuelRuntime {
  static final class Duel {
    final UUID id = UUID.randomUUID();
    final UUID player;
    final int tier;
    final DuelParams params;
    final DuelEngine engine;
    final RaidSession session;
    final RaidInstance instance;
    final PokemonEntity boss;
    final UUID raidId;
    final ResourceKey<Level> entryDim;
    final Vec3 entryPos;
    final float yRot, xRot;
    final boolean bossWasInvulnerable;
    final Vec3 bossHome;
    final float bossYRot;
    final ServerBossEvent bar;
    final float maxHealth;
    Vec3 strikeCenter;
    int telegraphLeft;
    double telegraphRadius;
    double recorded;
    boolean withdrawing;
    boolean finished;

    Duel(ServerPlayer p, int tier, DuelParams params, RaidSession session, RaidInstance instance, PokemonEntity boss, UUID raidId) {
      this.player = p.getUUID();
      this.tier = tier;
      this.params = params;
      this.engine = new DuelEngine(params);
      this.session = session;
      this.instance = instance;
      this.boss = boss;
      this.raidId = raidId;
      this.entryDim = p.level().dimension();
      this.entryPos = p.position();
      this.yRot = p.getYRot();
      this.xRot = p.getXRot();
      this.bossWasInvulnerable = boss.isInvulnerable();
      this.bossHome = boss.position();
      this.bossYRot = boss.getYRot();
      this.maxHealth = instance.getMaxHealth();
      this.bar = new ServerBossEvent(Component.literal(boss.getDisplayName().getString()), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    }
  }

  private static final Map<UUID, Duel> BY_PLAYER = new HashMap<>();
  private static final Map<UUID, Duel> BY_RAID = new HashMap<>();
  private static final Map<UUID, Integer> GRACE = new HashMap<>();
  private static final int GRACE_TICKS = 100;
  private static int ticks;

  private RaidDuelRuntime() {}

  // ---------------------------------------------------------------- events

  public static void install() {
    NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, RaidDuelRuntime::onIncoming);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RaidDuelRuntime::onPre);
    NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, RaidDuelRuntime::onDeath);
    NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
      if (e.getEntity() instanceof ServerPlayer p && BY_PLAYER.get(p.getUUID()) instanceof Duel d) finish(d, DuelResult.SURRENDERED, false);
    });
    NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
      if (e.getEntity() instanceof ServerPlayer p && BY_PLAYER.get(p.getUUID()) instanceof Duel d && !d.withdrawing && e.getTo() != d.entryDim)
        finish(d, DuelResult.SURRENDERED, false);
    });
    CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.HIGHEST, event -> {
      for (var p : event.getBattle().getPlayers())
        if (BY_PLAYER.containsKey(p.getUUID())) {
          event.cancel();
          p.sendSystemMessage(Messages.format(Tr.c("zianutilities.raids.msg.no_battle_in_duel", "§cNo puedes iniciar otra batalla mientras estás en un duelo de raid.")));
          return;
        }
    });
  }

  /** Hit on the boss: the real damage is cancelled and becomes duel damage. Damage on the player: nobody dies. */
  private static void onIncoming(LivingIncomingDamageEvent e) {
    LivingEntity victim = e.getEntity();
    if (victim instanceof ServerPlayer sp) {
      Duel d = BY_PLAYER.get(sp.getUUID());
      if (d == null) {
        if (inGrace(sp) && !e.getSource().is(DamageTypes.GENERIC_KILL) && e.getSource().is(DamageTypeTags.BYPASSES_ARMOR)
            && DuelEngine.isLethal(sp.getHealth() + sp.getAbsorptionAmount(), e.getAmount())) e.setCanceled(true);
        return;
      }
      if (protectedUntilFinish(d)) { e.setCanceled(true); return; }
      DamageSource s = e.getSource();
      if (s.is(DamageTypes.GENERIC_KILL)) return;
      if (s.is(DamageTypeTags.BYPASSES_ARMOR) && d.engine.incomingDamage(sp.getHealth() + sp.getAbsorptionAmount(), e.getAmount())) {
        d.withdrawing = true;
        e.setCanceled(true);
      }
      return;
    }
    if (victim instanceof PokemonEntity boss && boss instanceof IRaidAccessor acc && acc.crd_isRaidBoss()) {
      Duel d = BY_RAID.get(acc.crd_getRaidId());
      if (d == null) return;
      e.setCanceled(true);
      Entity attacker = e.getSource().getEntity();
      if (attacker instanceof ServerPlayer p && p.getUUID().equals(d.player)) hit(d, p, e.getAmount());
    }
  }

  /** After armor and effects: the exact damage. Setting it to zero before health changes is what keeps the totem unspent. */
  private static void onPre(LivingDamageEvent.Pre e) {
    if (!(e.getEntity() instanceof ServerPlayer sp)) return;
    Duel d = BY_PLAYER.get(sp.getUUID());
    if (d == null) {
      if (inGrace(sp) && !e.getSource().is(DamageTypes.GENERIC_KILL) && DuelEngine.isLethal(sp.getHealth() + sp.getAbsorptionAmount(), e.getNewDamage())) e.setNewDamage(0);
      return;
    }
    if (protectedUntilFinish(d)) { e.setNewDamage(0); return; }
    if (e.getSource().is(DamageTypes.GENERIC_KILL)) return;
    if (d.engine.incomingDamage(sp.getHealth() + sp.getAbsorptionAmount(), e.getNewDamage())) {
      d.withdrawing = true;
      e.setNewDamage(0);
    }
  }

  /** Once the duel is decided (lost, won, withdrawing) the player takes no damage until finish() has moved them out. */
  private static boolean protectedUntilFinish(Duel d) {
    return d.withdrawing || d.finished || d.engine.state() == DuelEngine.State.ENDED;
  }

  private static boolean inGrace(ServerPlayer p) {
    Integer until = GRACE.get(p.getUUID());
    if (until == null) return false;
    if (RaidsModule.server == null || RaidsModule.server.getTickCount() > until) { GRACE.remove(p.getUUID()); return false; }
    return true;
  }

  /** Cooldown bypass only with an explicit LuckPerms TRUE: no default for OPs. */
  private static boolean bypassesCooldown(ServerPlayer p) {
    try {
      return LuckPermsLookup.lookup(p, "zianutilities.raid.bypass_cooldown") == LuckPermsLookup.Decision.TRUE;
    } catch (Exception | LinkageError unavailable) {
      return false;
    }
  }

  private static void onDeath(LivingDeathEvent e) {
    if (!(e.getEntity() instanceof ServerPlayer sp)) return;
    Duel d = BY_PLAYER.get(sp.getUUID());
    if (d == null || d.finished) return;
    if (e.getSource().is(DamageTypes.GENERIC_KILL)) {
      d.engine.playerDied();
      return;
    }
    // Safety net only: a death that slipped past both damage hooks. The totem may already be spent at this point.
    e.setCanceled(true);
    sp.setHealth(1.0F);
    d.engine.incomingDamage(0, 1);
    d.withdrawing = true;
  }

  // ---------------------------------------------------------------- start

  /** Called from the mixin at the head of RaidChallengePacket.handleServer. True means the packet is handled here. */
  public static boolean intercept(RaidChallengePacket packet, ServerPlayer player) {
    if (!RaidsModule.active()) return false;
    try {
      if (!(player.level().getEntity(packet.targetedEntityId()) instanceof PokemonEntity boss)) return pass("target is not a Pokemon entity");
      if (!(boss instanceof IRaidAccessor acc) || !acc.crd_isRaidBoss() || acc.crd_getRaidState() == RaidState.FAILED) return pass("not a live raid boss, state=" + (boss instanceof IRaidAccessor a ? a.crd_getRaidState() : null));
      if (!RaidJoinHelper.isParticipating(player, false) && RaidUtils.isRaidDimension(player.level())) return pass("raid dimension without participation");
      RaidSession session = RaidHelper.ACTIVE_RAIDS.get(acc.crd_getRaidId());
      if (session == null || session.getActive() == null || session.hasFailed(player)) return pass("no session/instance or player failed");
      int tier = RaidTierPolicy.tierOfOrdinal(session.boss().getTier().ordinal());
      if (!RaidTierPolicy.isDuel(tier, RaidsModule.config.duelFromTier())) return pass("tier " + tier + " is a normal battle");
      String refusal = refusal(player, session, boss, tier);
      if (refusal != null) {
        RaidsModule.LOG.debug("Duel refused for {}: {}", player.getGameProfile().getName(), Tr.decS(refusal));
        player.sendSystemMessage(Messages.format(Tr.dec(refusal)));
        return true;
      }
      start(player, session, boss, tier, acc.crd_getRaidId());
    } catch (Exception | LinkageError failure) {
      RaidsModule.LOG.error("Duel could not start; nothing was spent", failure);
      player.sendSystemMessage(Messages.format(Tr.c("zianutilities.raids.err.start_failed", "§cNo se pudo iniciar el duelo; no se ha gastado el intento.")));
    }
    return true;
  }

  private static boolean pass(String why) {
    RaidsModule.LOG.debug("Duel hook passes the challenge to Raid Dens: {}", why);
    return false;
  }

  private static String refusal(ServerPlayer p, RaidSession session, PokemonEntity boss, int tier) {
    if (!UtilitiesPermissions.allows(p, "raid.use", false))
      return Tr.enc("zianutilities.raids.err.no_permission", "§cNo tienes permiso para los duelos de raid.");
    if (BY_PLAYER.containsKey(p.getUUID()))
      return Tr.enc("zianutilities.raids.err.already_dueling", "§cYa estás en un duelo.");
    if (BY_RAID.containsKey(session.getRaid()) || !session.getActive().getBattles().isEmpty())
      return Tr.enc("zianutilities.raids.err.raid_busy", "§cOtro jugador está en duelo con este jefe. Espera tu turno.");
    if (BattleRegistry.getBattleByParticipatingPlayer(p) != null)
      return Tr.enc("zianutilities.raids.err.in_battle", "§cNo puedes iniciar un duelo durante una batalla.");
    if (p.distanceTo(boss) > 24)
      return Tr.enc("zianutilities.raids.err.too_far", "§cEstás demasiado lejos del jefe.");
    if (RaidGenerationFilter.denied(session.boss()))
      return Tr.enc("zianutilities.raids.err.generation_denied", "§cEste jefe pertenece a una generación desactivada.");
    long left = RaidsModule.cooldowns.remainingMillis(p.getUUID(), tier, System.currentTimeMillis());
    if (left > 0 && !bypassesCooldown(p))
      return Tr.enc("zianutilities.raids.err.cooldown", "§cDebes esperar %s para volver a intentar este tier.", RaidCommands.duration(left));
    return null;
  }

  private static void start(ServerPlayer p, RaidSession session, PokemonEntity boss, int tier, UUID raidId) throws Exception {
    DuelParams params = RaidsModule.config.paramsFor(tier);
    long now = System.currentTimeMillis();
    var log = RaidsModule.log;
    Duel d = null;
    boolean logged = false;
    try {
      if (!session.isPlayerIn(p)) session.addPlayer(p);
      d = new Duel(p, tier, params, session, session.getActive(), boss, raidId);
      log.started(d.id, p.getUUID(), tier, raidId.toString(), String.valueOf(session.boss().getId()), now + params.timeLimitSeconds() * 1000L, boss.getUUID(), d.bossWasInvulnerable);
      logged = true;
      if (params.cooldownMinutes() > 0 && !bypassesCooldown(p))
        RaidsModule.cooldowns.start(p.getUUID(), tier, now + params.cooldownMinutes() * 60_000L);
      d.instance.removeFromBossEvent(p);
      boss.setInvulnerable(false);
      d.engine.start();
      d.bar.addPlayer(p);
      BY_PLAYER.put(p.getUUID(), d);
      BY_RAID.put(raidId, d);
      p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
      p.connection.send(new ClientboundSetTitleTextPacket(Tr.c("zianutilities.raids.title.start", "§6¡Duelo!")));
      p.connection.send(new ClientboundSetSubtitleTextPacket(Tr.c("zianutilities.raids.subtitle.start", "§7Esquiva los ataques y golpea al jefe con tu equipo")));
      p.sendSystemMessage(Messages.format(Tr.c("zianutilities.raids.msg.start", "§6Duelo iniciado: esquiva las zonas marcadas y golpea al jefe. Tienes %s segundos.", params.timeLimitSeconds())));
      RaidsModule.LOG.info("[ZIAN-AUDIT] raid duel started duel={} player={} tier={} raid={}", d.id, p.getUUID(), tier, raidId);
    } catch (Exception | LinkageError failure) {
      rollbackStart(d, logged, p, tier, raidId);
      throw failure;
    }
  }

  /** A start that failed half way is closed ABORTED: journal ended, cooldown released, boss restored, nobody left registered. */
  private static void rollbackStart(Duel d, boolean logged, ServerPlayer p, int tier, UUID raidId) {
    BY_PLAYER.remove(p.getUUID());
    BY_RAID.remove(raidId);
    if (d == null) return;
    d.finished = true;
    var failures = SafeSteps.runAll(
        () -> d.bar.removeAllPlayers(),
        () -> restoreBoss(d),
        () -> { if (logged) RaidsModule.log.ended(d.id, DuelResult.ABORTED, 0); },
        () -> RaidsModule.cooldowns.reset(p.getUUID(), tier));
    failures.forEach(f -> RaidsModule.LOG.error("Duel start rollback step failed duel={}", d.id, f));
  }

  /** Boss goes back to the exact state and place it had before the duel. */
  private static void restoreBoss(Duel d) {
    d.boss.setInvulnerable(d.bossWasInvulnerable);
    if (d.boss.isAlive() && !d.boss.isRemoved()) d.boss.moveTo(d.bossHome.x, d.bossHome.y, d.bossHome.z, d.bossYRot, 0);
  }

  // ---------------------------------------------------------------- tick

  static void tick() {
    if (BY_PLAYER.isEmpty()) return;
    ticks++;
    for (Duel d : new ArrayList<>(BY_PLAYER.values())) {
      if (d.finished) continue;
      SafeSteps.guard(() -> tickOne(d), failure -> {
        RaidsModule.LOG.error("Duel tick failed; closing it as ABORTED without spending the attempt duel={}", d.id, failure);
        finish(d, DuelResult.ABORTED, true);
      });
    }
  }

  private static void tickOne(Duel d) {
    ServerPlayer p = RaidsModule.server.getPlayerList().getPlayer(d.player);
    if (p == null) { finish(d, DuelResult.SURRENDERED, false); return; }
    if (d.engine.state() == DuelEngine.State.ENDED) { finish(d, d.engine.result(), false); return; }
    if (d.boss.isRemoved() || !d.boss.isAlive()) { finish(d, DuelResult.ABORTED, true); return; }
    if (p.level().dimension() != d.entryDim || p.distanceTo(d.boss) > 64) { finish(d, DuelResult.SURRENDERED, false); return; }
    for (var event : d.engine.tick()) {
      if (event instanceof DuelEngine.Telegraph t) telegraph(d, p, t);
      else if (event instanceof DuelEngine.Strike s) strike(d, p, s);
    }
    if (d.telegraphLeft > 0) {
      d.telegraphLeft--;
      if (ticks % 3 == 0) ring(d);
    }
    if (ticks % 10 == 0) updateBar(d);
    if (d.engine.state() == DuelEngine.State.ENDED) finish(d, d.engine.result(), false);
  }

  private static void telegraph(Duel d, ServerPlayer p, DuelEngine.Telegraph t) {
    ServerLevel level = (ServerLevel) p.level();
    Vec3 target = p.position();
    Vec3 away = d.boss.position().subtract(target);
    Vec3 dir = away.horizontalDistanceSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : new Vec3(away.x, 0, away.z).normalize();
    if (away.horizontalDistance() > 7) d.boss.moveTo(target.x + dir.x * 4, target.y, target.z + dir.z * 4);
    d.boss.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, p.getEyePosition());
    d.strikeCenter = target;
    d.telegraphLeft = t.delayTicks();
    d.telegraphRadius = t.radius();
    level.playSound(null, target.x, target.y, target.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.0F, 1.0F);
    ring(d);
  }

  private static void ring(Duel d) {
    if (d.strikeCenter == null || !(d.boss.level() instanceof ServerLevel level)) return;
    int points = Math.max(12, (int) (d.telegraphRadius * 8));
    for (int i = 0; i < points; i++) {
      double a = Math.PI * 2 * i / points;
      level.sendParticles(ParticleTypes.FLAME, d.strikeCenter.x + Math.cos(a) * d.telegraphRadius, d.strikeCenter.y + 0.1, d.strikeCenter.z + Math.sin(a) * d.telegraphRadius, 1, 0, 0, 0, 0);
    }
  }

  private static void strike(Duel d, ServerPlayer p, DuelEngine.Strike s) {
    Vec3 c = d.strikeCenter;
    d.strikeCenter = null;
    d.telegraphLeft = 0;
    if (c == null || !(p.level() instanceof ServerLevel level)) return;
    level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 6, s.radius() / 3, 0.2, s.radius() / 3, 0);
    level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 1.2F);
    double dx = p.getX() - c.x, dz = p.getZ() - c.z;
    if (dx * dx + dz * dz <= s.radius() * s.radius() && Math.abs(p.getY() - c.y) < 3.0)
      p.hurt(level.damageSources().mobAttack(d.boss), (float) s.damage());
  }

  private static void hit(Duel d, ServerPlayer p, float rawDamage) {
    double applied = d.engine.hit(rawDamage);
    if (applied <= 0) return;
    double target = d.engine.state() == DuelEngine.State.ENDED && d.engine.result() == DuelResult.WON ? 1.0 : d.engine.dealtRatio();
    double add = Math.max(0, target - d.recorded);
    d.recorded = target;
    d.session.getDamageRecord(p).addDamage((float) (add * d.maxHealth));
    updateBar(d);
  }

  private static void updateBar(Duel d) {
    d.bar.setProgress((float) Math.max(0, Math.min(1, d.engine.bossHpFraction())));
    int secs = d.engine.ticksLeft() / 20;
    d.bar.setName(Component.literal(d.boss.getDisplayName().getString() + "  " + (secs / 60) + ":" + String.format("%02d", secs % 60)));
  }

  // ---------------------------------------------------------------- finish

  /**
   * Closes a duel. refund releases the cooldown (server crash, admin cancel); otherwise the attempt stays spent.
   * Every step runs in its own try, boss first, so one failure never leaves the boss changed or the raid open.
   */
  static void finish(Duel d, DuelResult result, boolean refund) {
    if (d.finished) return;
    d.finished = true;
    BY_PLAYER.remove(d.player);
    BY_RAID.remove(d.raidId);
    ServerPlayer p = RaidsModule.server == null ? null : RaidsModule.server.getPlayerList().getPlayer(d.player);
    double ratio = d.engine.dealtRatio();
    double need = 0;
    try {
      Float required = d.session.boss().getRequiredDamage();
      need = required == null ? 0 : required;
    } catch (Throwable unreadable) {
      RaidsModule.LOG.error("requiredDamage unreadable duel={}", d.id, unreadable);
    }
    final double required = need;
    boolean success = RaidRewardPolicy.closeAsSuccess(result, ratio, required);
    var failures = SafeSteps.runAll(
        () -> restoreBoss(d),
        () -> d.bar.removeAllPlayers(),
        () -> RaidsModule.log.ended(d.id, result, ratio),
        () -> { if (refund) RaidsModule.cooldowns.reset(d.player, d.tier); },
        () -> {
          if (success && p != null && result == DuelResult.WON) {
            double missing = Math.max(0, 1.0 - d.recorded);
            if (missing > 0) d.session.getDamageRecord(p).addDamage((float) (missing * d.maxHealth));
          }
        },
        () -> d.instance.queueStopInstance(success),
        () -> { if (p != null) notifyEnd(d, p, result, success); });
    failures.forEach(f -> RaidsModule.LOG.error("Duel close step failed duel={}", d.id, f));
    RaidsModule.LOG.info("[ZIAN-AUDIT] raid duel ended duel={} player={} result={} ratio={} required={} success={} refund={} failures={}", d.id, d.player, result, ratio, required, success, refund, failures.size());
  }

  private static void notifyEnd(Duel d, ServerPlayer p, DuelResult result, boolean success) {
    if (d.engine.lethalCapped()) {
      ServerLevel home = RaidsModule.server.getLevel(d.entryDim);
      if (home != null && p.isAlive()) p.teleportTo(home, d.entryPos.x, d.entryPos.y, d.entryPos.z, d.yRot, d.xRot);
      p.clearFire();
      p.resetFallDistance();
      for (MobEffectInstance effect : new ArrayList<>(p.getActiveEffects()))
        if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) p.removeEffect(effect.getEffect());
      GRACE.put(p.getUUID(), RaidsModule.server.getTickCount() + GRACE_TICKS);
      p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
      p.connection.send(new ClientboundSetTitleTextPacket(Tr.c("zianutilities.raids.title.withdraw", "§cTu derrota era inminente...")));
      p.connection.send(new ClientboundSetSubtitleTextPacket(Tr.c("zianutilities.raids.subtitle.withdraw", "§7Te retiras del combate para luchar otro día")));
      p.sendSystemMessage(Messages.format(Tr.c("zianutilities.raids.msg.withdraw", "§cTu derrota era inminente... Te retiras del combate para luchar otro día. Vuelve a intentarlo en otra ocasión.")));
      if (success)
        p.sendSystemMessage(Messages.format(Tr.c("zianutilities.raids.msg.withdraw_reward", "§aComo llegaste al daño mínimo, aun así ganas tu recompensa.")));
      return;
    }
    switch (result) {
      case WON -> {
        p.connection.send(new ClientboundSetTitleTextPacket(Tr.c("zianutilities.raids.title.won", "§a¡Victoria!")));
        p.sendSystemMessage(Messages.format(Tr.c("zianutilities.raids.msg.won", "§a¡Has derrotado al jefe!")));
      }
      case TIMEOUT -> {
        p.sendSystemMessage(Messages.format(Tr.c("zianutilities.raids.msg.timeout", "§cSe acabó el tiempo.")));
        rewardNote(p, success);
      }
      case SURRENDERED, LOST -> {
        p.sendSystemMessage(Messages.format(Tr.c("zianutilities.raids.msg.lost", "§cHas perdido el duelo.")));
        rewardNote(p, success);
      }
      case ABORTED -> p.sendSystemMessage(Messages.format(Tr.c("zianutilities.raids.msg.aborted", "§eEl duelo se ha cancelado.")));
    }
  }

  private static void rewardNote(ServerPlayer p, boolean success) {
    if (success) p.sendSystemMessage(Messages.format(Tr.c("zianutilities.raids.msg.reward_note", "§aLlegaste al daño mínimo: ganas tu recompensa.")));
  }

  // ---------------------------------------------------------------- recovery and admin

  /** Duels still open in the journal were cut by a crash: they close as ABORTED, the cooldown is released and the boss shield is put back if the boss is loaded. */
  static void recover() throws Exception {
    for (var open : RaidsModule.log.unfinished().values()) {
      RaidsModule.log.ended(open.duelId(), DuelResult.ABORTED, 0);
      RaidsModule.cooldowns.reset(open.player(), open.tier());
      boolean restored = false;
      if (open.bossEntity() != null)
        for (ServerLevel level : RaidsModule.server.getAllLevels()) {
          Entity boss = level.getEntity(open.bossEntity());
          if (boss != null) { boss.setInvulnerable(open.bossWasInvulnerable()); restored = true; }
        }
      RaidsModule.LOG.warn("[ZIAN-AUDIT] raid duel {} of {} was cut by a shutdown: closed as ABORTED, cooldown released, boss restored={}", open.duelId(), open.player(), restored);
    }
  }

  static void abortAll() {
    for (Duel d : new ArrayList<>(BY_PLAYER.values())) {
      d.finished = true;
      var failures = SafeSteps.runAll(
          () -> d.bar.removeAllPlayers(),
          () -> restoreBoss(d),
          () -> RaidsModule.log.ended(d.id, DuelResult.ABORTED, d.engine.dealtRatio()),
          () -> RaidsModule.cooldowns.reset(d.player, d.tier));
      failures.forEach(f -> RaidsModule.LOG.error("Duel abort on shutdown failed duel={}", d.id, f));
    }
    BY_PLAYER.clear();
    BY_RAID.clear();
    GRACE.clear();
  }

  static boolean cancel(UUID player) {
    Duel d = BY_PLAYER.get(player);
    if (d == null) return false;
    d.engine.abort();
    finish(d, DuelResult.ABORTED, true);
    return true;
  }

  static boolean inDuel(UUID player) { return BY_PLAYER.containsKey(player); }
}
