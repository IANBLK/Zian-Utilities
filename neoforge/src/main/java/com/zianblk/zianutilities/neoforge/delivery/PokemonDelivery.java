package com.zianblk.zianutilities.neoforge.delivery;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.PokemonStore;
import com.cobblemon.mod.common.api.storage.PokemonStoreManager;
import com.cobblemon.mod.common.api.storage.adapter.flatfile.FileStoreAdapter;
import com.cobblemon.mod.common.api.storage.factory.FileBackedPokemonStoreFactory;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Safe delivery of one Pokemon to a player PC with a journal: write the phase first, insert, save, then read
 * the saved copy back before calling it delivered. Extracted from BreedingRuntime.deliver/verify so Breeding and
 * Raids share one implementation. The caller owns the journal: every phase change goes through {@link Sink}.
 */
public final class PokemonDelivery {
  public enum Phase { DELIVERING, DELIVERED, UNCERTAIN, BLOCKED }

  public interface Sink {
    void phase(Phase phase, String reason) throws Exception;
  }

  /** AWAITING_PROOF: inserted (or already present), call verify. BLOCKED: nothing was inserted. */
  public enum Step { AWAITING_PROOF, BLOCKED }

  public enum Proof { PENDING, UNCERTAIN, DELIVERED }

  public record Handle(
      PCStore pc,
      PlayerPartyStore party,
      FileBackedPokemonStoreFactory<?> factory,
      FileStoreAdapter<?> adapter) {}

  private PokemonDelivery() {}

  /** Reuses the pinned save/readback boundary of Zian RCT, without tracking another live store. */
  public static Handle open(ServerPlayer p) throws ReflectiveOperationException {
    var manager = Cobblemon.INSTANCE.getStorage();
    var pc = manager.getPC(p);
    var party = manager.getParty(p);
    var field = PokemonStoreManager.class.getDeclaredField("factories");
    field.setAccessible(true);
    for (Object object : (Iterable<?>) field.get(manager))
      if (object instanceof FileBackedPokemonStoreFactory<?> factory
          && factory.isCached(pc)
          && factory.getPC(p.getUUID(), p.registryAccess()) == pc) {
        var method = FileBackedPokemonStoreFactory.class.getDeclaredMethod("getAdapter");
        method.setAccessible(true);
        return new Handle(pc, party, factory, (FileStoreAdapter<?>) method.invoke(factory));
      }
    throw new IllegalStateException("Proveedor de PC sin confirmación de guardado compatible");
  }

  public static Pokemon live(Handle h, UUID id) {
    var p = h.pc().get(id);
    return p != null ? p : h.party().get(id);
  }

  public static Pokemon saved(Handle h, ServerPlayer p, UUID id) throws Exception {
    var found = find(h.adapter().load(PCStore.class, h.pc().getUuid(), p.registryAccess()), id);
    return found != null
        ? found
        : find(
            h.adapter().load(PlayerPartyStore.class, h.party().getUuid(), p.registryAccess()), id);
  }

  public static Pokemon find(PokemonStore<?> store, UUID id) {
    if (store != null) for (var pokemon : store) if (pokemon.getUuid().equals(id)) return pokemon;
    return null;
  }

  /** The maker is only called when there is room, so a blocked delivery never builds or mutates a Pokemon. */
  public static Step deliver(ServerPlayer p, UUID id, Supplier<Pokemon> maker, Sink sink) throws Exception {
    var h = open(p);
    if (live(h, id) != null) {
      sink.phase(Phase.DELIVERING, "EXISTING_UUID");
      return Step.AWAITING_PROOF;
    }
    if (h.pc().getFirstAvailablePosition() == null) {
      sink.phase(Phase.BLOCKED, "PC_FULL");
      return Step.BLOCKED;
    }
    var pokemon = maker.get();
    sink.phase(Phase.DELIVERING, "");
    try {
      if (!h.pc().add(pokemon)) throw new IllegalStateException("PC insertion unconfirmed");
      h.factory().save(h.pc(), p.registryAccess());
    } catch (Exception error) {
      sink.phase(Phase.UNCERTAIN, "DELIVERY");
      throw error;
    }
    return Step.AWAITING_PROOF;
  }

  /**
   * Reads the saved copy back. Missing: PENDING until timedOut, then UNCERTAIN (DELIVERY_NOT_PROVEN).
   * Present but different: UNCERTAIN (DELIVERY_MISMATCH). Same: DELIVERED, and the saved Pokemon is reported.
   */
  public static Proof verify(ServerPlayer p, UUID id, Predicate<Pokemon> matches, boolean timedOut,
      Sink sink, Consumer<Pokemon> onDelivered) throws Exception {
    var h = open(p);
    var found = saved(h, p, id);
    if (found == null) {
      if (timedOut) {
        sink.phase(Phase.UNCERTAIN, "DELIVERY_NOT_PROVEN");
        return Proof.UNCERTAIN;
      }
      return Proof.PENDING;
    }
    if (!matches.test(found)) {
      sink.phase(Phase.UNCERTAIN, "DELIVERY_MISMATCH");
      return Proof.UNCERTAIN;
    }
    sink.phase(Phase.DELIVERED, "");
    onDelivered.accept(found);
    return Proof.DELIVERED;
  }
}
