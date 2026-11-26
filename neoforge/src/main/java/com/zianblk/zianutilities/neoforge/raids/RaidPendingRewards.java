package com.zianblk.zianutilities.neoforge.raids;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.necro.raid.dens.common.events.RewardPokemonEvent;
import com.zianblk.zianutilities.core.storage.StorageFiles;
import com.zianblk.zianutilities.neoforge.Messages;
import com.zianblk.zianutilities.neoforge.Tr;
import com.zianblk.zianutilities.neoforge.delivery.PokemonDelivery;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;

/**
 * Raid Pokemon rewards with a journal. Every reward is written to pending-rewards/ before Raid Dens inserts it,
 * then proven by reading the saved copy back (PokemonDelivery). A full PC cancels the event instead of losing the
 * Pokemon: the reward waits here (also for offline players) and is delivered when there is room.
 * Phases: DELIVERING (waiting for proof), BLOCKED (PC_FULL, retried), UNCERTAIN (an admin must review).
 */
final class RaidPendingRewards {
  static final int FORMAT_VERSION = 1;
  private static final long PROOF_MILLIS = 30_000;
  private static final Map<UUID, CompoundTag> ENTRIES = new LinkedHashMap<>();
  private static final Map<UUID, Long> SINCE = new LinkedHashMap<>();
  private static int ticks;

  private RaidPendingRewards() {}

  private static Path dir() { return RaidsModule.dataDir().resolve("pending-rewards"); }

  private static Path file(UUID id) { return dir().resolve(id + ".nbt"); }

  static void load() throws IOException {
    ENTRIES.clear();
    SINCE.clear();
    if (!StorageFiles.isDirectory(dir())) return;
    try (var files = StorageFiles.list(dir())) {
      for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".nbt")).toList()) {
        CompoundTag tag;
        try (InputStream in = StorageFiles.newInputStream(path)) { tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap()); }
        if (tag.getInt("formatVersion") != FORMAT_VERSION) throw new IOException("Unsupported pending reward formatVersion in " + path.getFileName());
        UUID id = tag.getUUID("id");
        ENTRIES.put(id, tag);
        SINCE.put(id, 0L);
      }
    }
  }

  private static void write(CompoundTag tag) throws IOException {
    UUID id = tag.getUUID("id");
    StorageFiles.createDirectories(dir());
    Path temp = StorageFiles.createTempFile(dir(), "reward-", ".tmp");
    try {
      try (OutputStream out = StorageFiles.newOutputStream(temp)) { NbtIo.writeCompressed(tag, out); }
      StorageFiles.move(temp, file(id), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      StorageFiles.deleteIfExists(temp);
    }
    ENTRIES.put(id, tag);
  }

  private static void remove(UUID id) throws IOException {
    StorageFiles.deleteIfExists(file(id));
    ENTRIES.remove(id);
    SINCE.remove(id);
  }

  private static void note(String type, UUID id, Object... details) {
    try { RaidsModule.log.note(type, id, details); } catch (Exception failure) { RaidsModule.LOG.error("Reward journal write failed {}", id, failure); }
  }

  private static void phase(CompoundTag tag, String phase, String reason) throws IOException {
    tag.putString("phase", phase);
    tag.putString("reason", reason);
    write(tag);
    note("REWARD_" + phase, tag.getUUID("id"), tag.getUUID("player"), reason);
  }

  // ---------------------------------------------------------------- the Raid Dens event

  /** Fires before Raid Dens adds the Pokemon. Not cancelled unless the species is denied or the PC is full. */
  static void onReward(RewardPokemonEvent event) {
    if (!RaidsModule.active() || !RaidsModule.config.safeRewardDelivery()) return;
    ServerPlayer player = event.getPlayer();
    Pokemon pokemon = event.getPokemon();
    try {
      if (RaidGenerationFilter.deniedSpecies(pokemon.getSpecies().getResourceIdentifier().toString())) {
        event.cancel();
        note("REWARD_DENIED_GENERATION", pokemon.getUuid(), player.getUUID(), pokemon.getSpecies().getName());
        player.sendSystemMessage(Messages.format(Tr.c("zianutilities.raids.err.reward_generation", "§cEl Pokémon de premio pertenece a una generación desactivada y no se entrega.")));
        return;
      }
      CompoundTag tag = new CompoundTag();
      tag.putInt("formatVersion", FORMAT_VERSION);
      tag.putUUID("id", pokemon.getUuid());
      tag.putUUID("player", player.getUUID());
      tag.putLong("createdAt", System.currentTimeMillis());
      tag.put("pokemon", pokemon.saveToNBT(player.registryAccess(), new CompoundTag()));
      boolean full = PokemonDelivery.open(player).pc().getFirstAvailablePosition() == null;
      phase(tag, full ? "BLOCKED" : "DELIVERING", full ? "PC_FULL" : "");
      SINCE.put(pokemon.getUuid(), System.currentTimeMillis());
      if (full) {
        event.cancel();
        player.sendSystemMessage(Messages.format(Tr.c("zianutilities.raids.msg.pc_full", "§eTu PC está lleno: el premio queda guardado y se entregará cuando liberes espacio.")));
      }
    } catch (Exception | LinkageError failure) {
      RaidsModule.LOG.error("Reward journal failed; Raid Dens delivers as usual", failure);
    }
  }

  // ---------------------------------------------------------------- retries and proof

  static void tick() {
    if (++ticks % 20 != 0 || ENTRIES.isEmpty() || !RaidsModule.active()) return;
    for (CompoundTag tag : new ArrayList<>(ENTRIES.values())) {
      ServerPlayer p = RaidsModule.server.getPlayerList().getPlayer(tag.getUUID("player"));
      if (p == null) continue;
      UUID id = tag.getUUID("id");
      try {
        switch (tag.getString("phase")) {
          case "DELIVERING" -> prove(p, tag);
          case "BLOCKED", "QUEUED" -> {
            var step = PokemonDelivery.deliver(p, id, () -> pokemonOf(p, tag), (phase, reason) -> phase(tag, phase.name(), reason));
            if (step == PokemonDelivery.Step.AWAITING_PROOF) SINCE.put(id, System.currentTimeMillis());
          }
          default -> { }
        }
      } catch (Exception failure) {
        RaidsModule.LOG.error("Reward delivery needs review id={}", id, failure);
        try { phase(tag, "UNCERTAIN", "TICK_FAILURE"); } catch (IOException ignored) { }
      }
    }
  }

  private static void prove(ServerPlayer p, CompoundTag tag) throws Exception {
    UUID id = tag.getUUID("id");
    var h = PokemonDelivery.open(p);
    if (PokemonDelivery.live(h, id) != null) h.factory().save(h.pc(), p.registryAccess());
    long since = SINCE.getOrDefault(id, 0L);
    boolean timedOut = System.currentTimeMillis() - since > PROOF_MILLIS;
    var proof = PokemonDelivery.verify(p, id, found -> true, timedOut, (phase, reason) -> phase(tag, phase.name(), reason), found -> { });
    if (proof == PokemonDelivery.Proof.DELIVERED) {
      remove(id);
      p.sendSystemMessage(Messages.format(Tr.c("zianutilities.raids.msg.reward_in_pc", "§aTu Pokémon de premio está guardado en el PC.")));
    }
  }

  private static Pokemon pokemonOf(ServerPlayer p, CompoundTag tag) {
    Pokemon pokemon = new Pokemon().loadFromNBT(p.registryAccess(), tag.getCompound("pokemon"));
    pokemon.setOriginalTrainer(p.getUUID());
    return pokemon;
  }

  // ---------------------------------------------------------------- admin

  static List<String> review() {
    var out = new ArrayList<String>();
    for (CompoundTag t : ENTRIES.values())
      out.add(t.getUUID("id") + " " + t.getString("phase") + " " + t.getString("reason") + " player=" + t.getUUID("player"));
    return out;
  }

  /** The admin saw the Pokemon in the PC: close the entry. */
  static boolean confirmDelivered(UUID id) throws IOException {
    if (!ENTRIES.containsKey(id)) return false;
    note("REWARD_CONFIRMED_BY_ADMIN", id);
    remove(id);
    return true;
  }

  /** Put an UNCERTAIN entry back in the queue so it is delivered again. */
  static boolean requeue(UUID id) throws IOException {
    CompoundTag tag = ENTRIES.get(id);
    if (tag == null) return false;
    phase(tag, "QUEUED", "ADMIN_REQUEUE");
    return true;
  }

  static UUID parse(String text) {
    for (UUID id : ENTRIES.keySet()) if (id.toString().startsWith(text)) return id;
    return null;
  }
}
