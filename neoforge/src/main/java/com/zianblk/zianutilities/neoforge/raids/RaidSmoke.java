package com.zianblk.zianutilities.neoforge.raids;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.authlib.GameProfile;
import com.necro.raid.dens.common.data.raid.RaidBoss;
import com.necro.raid.dens.common.data.raid.RaidTier;
import com.necro.raid.dens.common.events.RewardPokemonEvent;
import com.necro.raid.dens.common.network.packets.RaidChallengePacket;
import com.necro.raid.dens.common.raids.RaidSession;
import com.necro.raid.dens.common.raids.RaidState;
import com.necro.raid.dens.common.raids.helpers.RaidHelper;
import com.necro.raid.dens.common.registry.CustomRaidRegistries;
import com.zianblk.zianutilities.neoforge.delivery.PokemonDelivery;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Local server smoke with the real Raid Dens jar (ZIANUTILITIES_RAIDS_SMOKE=true, never packaged in the jar).
 * Opens a tier 5 raid by API with a fake player and plays two duels: a win, and a lethal hit with a totem held.
 * Writes raids-smoke-result.txt next to the server and halts it.
 */
public final class RaidSmoke {
  private static int step, wait, age, waited;
  private static ServerPlayer player;
  private static RaidSession session;
  private static PokemonEntity boss;
  private static Vec3 spot;
  private static final StringBuilder REPORT = new StringBuilder();

  private RaidSmoke() {}

  private static void check(boolean ok, String what) {
    REPORT.append(ok ? "PASS " : "FAIL ").append(what).append('\n');
    if (!ok) throw new IllegalStateException(what);
  }

  public static void tick() {
    var server = RaidsModule.server;
    if (server == null || step < 0) return;
    age++;
    try {
      if (wait > 0) { wait--; return; }
      switch (step) {
        case 0 -> {
          if (age < 100) return;
          check(RaidsModule.active(), "module active with enabled:true config");
          GameProfile profile = new GameProfile(UUID.randomUUID(), "RaidSmoker");
          player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
          player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) {}
          };
          register(server.getPlayerList(), player);
          var spawnShield = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
          spawnShield.setAccessible(true);
          spawnShield.setInt(player, 0);
          player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
          spot = new Vec3(0.5, server.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0) + 1, 0.5);
          step = 1;
        }
        case 1 -> {
          if (!findTierFive()) { wait = 20; if (age > 2000) check(false, "no tier 5 boss loaded"); return; }
          openRaid(); step = 11; waited = 0;
        }
        case 11 -> {
          if (!bossReady()) { if (++waited > 300) check(false, "boss entity never spawned by session.start"); return; }
          REPORT.append("boss ready after ").append(waited).append(" ticks\n");
          challenge(); wait = 5; step = 20;
        }
        case 20 -> {
          check(RaidDuelRuntime.inDuel(player.getUUID()), "mixin applied: the duel took over the challenge packet");
          check(RaidsModule.log.unfinished().size() == 1, "duel STARTED in the journal");
          step = 2;
        }
        case 2 -> {
          boss.hurt(player.damageSources().playerAttack(player), 200F);
          if (!RaidDuelRuntime.inDuel(player.getUUID())) {
            step = 3; wait = 100;
          } else if (age % 7 == 0) { REPORT.append("hit ").append(age).append('\n'); }
          if (age > 3000) check(false, "duel never ended by hits (boss invulnerable?)");
        }
        case 3 -> {
          var state = session.getActive() == null ? session.getRaidState() : session.getActive().getRaidState();
          REPORT.append("after win raid state=").append(state).append('\n');
          check(state == RaidState.SUCCESS || session.getRaidState() == RaidState.SUCCESS, "win closed the raid as SUCCESS");
          check(RaidsModule.log.unfinished().isEmpty(), "journal ENDED written");
          check(RaidsModule.cooldowns.remainingMillis(player.getUUID(), 5, System.currentTimeMillis()) > 0, "cooldown stored for player and tier 5");
          RaidsModule.cooldowns.reset(player.getUUID(), 5);
          step = 4;
        }
        case 4 -> { openRaid(); step = 41; waited = 0; }
        case 41 -> {
          if (!bossReady()) { if (++waited > 300) check(false, "boss entity never spawned (2)"); return; }
          challenge(); wait = 5; step = 5;
        }
        case 5 -> {
          check(RaidDuelRuntime.inDuel(player.getUUID()), "second duel started");
          for (int i = 0; i < 3; i++) boss.hurt(player.damageSources().playerAttack(player), 200F);
          player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
          player.setHealth(2.0F);
          Vec3 before = player.position();
          player.invulnerableTime = 0;
          boolean applied = player.hurt(player.level().damageSources().mobAttack(boss), 10F);
          REPORT.append("hurt returned ").append(applied).append(" invulnerable=").append(player.isInvulnerable()).append(" withdrawing-engine-ended=").append(!RaidDuelRuntime.inDuel(player.getUUID())).append('\n');
          check(player.isAlive() && player.getHealth() == 2.0F, "lethal boss hit cancelled, player alive with the same health");
          check(player.getOffhandItem().is(Items.TOTEM_OF_UNDYING), "totem of undying not spent");
          wait = 10; step = 6;
          REPORT.append("entry=").append(before).append('\n');
        }
        case 6 -> {
          check(!RaidDuelRuntime.inDuel(player.getUUID()), "duel closed as a defeat after the lethal hit");
          check(player.isAlive() && player.getOffhandItem().is(Items.TOTEM_OF_UNDYING), "alive, totem still there");
          check(RaidsModule.cooldowns.remainingMillis(player.getUUID(), 5, System.currentTimeMillis()) > 0, "attempt spent after a defeat");
          wait = 100; step = 7;
        }
        case 7 -> {
          var state = session.getActive() == null ? session.getRaidState() : session.getActive().getRaidState();
          REPORT.append("after defeat raid state=").append(state).append(" required=").append(session.boss().getRequiredDamage()).append('\n');
          pendingReward();
        }
        case 8 -> proveReward();
        default -> { }
      }
    } catch (Throwable failure) {
      REPORT.append("ERROR ").append(failure).append('\n');
      RaidsModule.LOG.error("Raid smoke failed", failure);
      finish(false);
    }
  }

  /** placeNewPlayer needs a real network channel; the fixture only needs the player to be findable by UUID. */
  @SuppressWarnings("unchecked")
  private static void register(net.minecraft.server.players.PlayerList list, ServerPlayer p) throws ReflectiveOperationException {
    var byUuid = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
    byUuid.setAccessible(true);
    ((java.util.Map<UUID, ServerPlayer>) byUuid.get(list)).put(p.getUUID(), p);
  }

  private static RaidBoss tierFive;

  /** Raid Dens loads bosses asynchronously on the server thread: ask, then wait for the callbacks (join() would deadlock). */
  private static boolean findTierFive() {
    if (tierFive != null) return true;
    for (var id : CustomRaidRegistries.RAID_REGISTRY.getAll())
      CustomRaidRegistries.RAID_REGISTRY.requestRaidBoss(id, boss -> { if (boss != null && boss.getTier() == RaidTier.TIER_FIVE && tierFive == null) tierFive = boss; }, RaidsModule.server);
    return tierFive != null;
  }

  private static void openRaid() {
    var server = RaidsModule.server;
    RaidBoss chosen = tierFive;
    check(chosen != null, "a tier 5 raid boss exists in Raid Dens");
    UUID raidId = UUID.randomUUID();
    session = new RaidSession(player.getUUID(), raidId, chosen, spot.add(8, 0, 0), server.overworld());
    RaidHelper.ACTIVE_RAIDS.put(raidId, session);
    session.setFlags(true, true, true);
    session.start(server);
    boss = null;
    player.moveTo(spot.x, spot.y, spot.z, 0, 0);
    player.setHealth(player.getMaxHealth());
  }

  /** session.start spawns the boss a few ticks later; true once the entity is there. */
  private static boolean bossReady() {
    if (session.getActive() == null || session.getActive().getBossEntity() == null) return false;
    boss = session.getActive().getBossEntity();
    return true;
  }

  private static void challenge() {
    var packet = new RaidChallengePacket(boss.getId(), UUID.randomUUID(), BattleFormat.Companion.getGEN_9_SINGLES());
    check(((Object) packet) instanceof RaidDuelHooked, "RaidChallengePacket carries the hook marker");
    packet.handleServer(player);
  }

  private static void pendingReward() throws Exception {
    var pokemon = PokemonProperties.Companion.parse("bulbasaur level=5", " ", "=").create();
    RaidPendingRewards.onReward(new RewardPokemonEvent(player, pokemon));
    check(RaidPendingRewards.review().stream().anyMatch(l -> l.contains(pokemon.getUuid().toString())), "reward written to the pending journal before Raid Dens inserts it");
    Cobblemon.INSTANCE.getStorage().getPC(player).add(pokemon);
    rewardId = pokemon.getUuid().toString();
    step = 8;
    waited = 0;
  }

  private static String rewardId;

  /** The PC save is asynchronous: the journal entry must clear within a few seconds, never instantly and never by guessing. */
  private static void proveReward() throws Exception {
    RaidPendingRewards.run();
    if (RaidPendingRewards.review().stream().noneMatch(l -> l.contains(rewardId))) {
      check(true, "reward proven by reading the saved PC back (DELIVERED) after " + waited + " tries");
      check(PokemonDelivery.live(PokemonDelivery.open(player), java.util.UUID.fromString(rewardId)) != null, "Pokemon in the PC");
      finish(true);
      return;
    }
    wait = 10;
    if (++waited > 40) check(false, "reward never proven: " + RaidPendingRewards.review());
  }

  private static void finish(boolean ok) {
    step = -1;
    try {
      Path out = Path.of("raids-smoke-result.txt");
      Files.writeString(out, (ok ? "PASS\n" : "FAIL\n") + REPORT);
    } catch (Exception ignored) { }
    RaidsModule.LOG.info("RAID SMOKE {}\n{}", ok ? "PASS" : "FAIL", REPORT);
    RaidsModule.server.halt(false);
  }
}
