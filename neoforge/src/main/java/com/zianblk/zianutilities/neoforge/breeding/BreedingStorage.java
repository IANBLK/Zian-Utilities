package com.zianblk.zianutilities.neoforge.breeding;

import com.cobblemon.mod.common.api.storage.PokemonStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.zianblk.zianutilities.neoforge.delivery.PokemonDelivery;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Breeding view of the shared storage boundary that now lives in {@link PokemonDelivery}. */
final class BreedingStorage {
  static PokemonDelivery.Handle open(ServerPlayer p) throws ReflectiveOperationException {
    return PokemonDelivery.open(p);
  }

  static Pokemon live(PokemonDelivery.Handle h, UUID id) {
    return PokemonDelivery.live(h, id);
  }

  static Pokemon saved(PokemonDelivery.Handle h, ServerPlayer p, UUID id) throws Exception {
    return PokemonDelivery.saved(h, p, id);
  }

  static Pokemon find(PokemonStore<?> store, UUID id) {
    return PokemonDelivery.find(store, id);
  }
}
