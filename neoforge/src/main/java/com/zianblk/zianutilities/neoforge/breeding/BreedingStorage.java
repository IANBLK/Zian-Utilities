package com.zianblk.zianutilities.neoforge.breeding;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.*;
import com.cobblemon.mod.common.api.storage.adapter.flatfile.FileStoreAdapter;
import com.cobblemon.mod.common.api.storage.factory.FileBackedPokemonStoreFactory;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/** Reuses the pinned save/readback boundary of Zian RCT, without tracking another live store. */
final class BreedingStorage {
  record Handle(
      PCStore pc,
      PlayerPartyStore party,
      FileBackedPokemonStoreFactory<?> factory,
      FileStoreAdapter<?> adapter) {}

  static Handle open(ServerPlayer p) throws ReflectiveOperationException {
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

  static Pokemon live(Handle h, UUID id) {
    var p = h.pc().get(id);
    return p != null ? p : h.party().get(id);
  }

  static Pokemon saved(Handle h, ServerPlayer p, UUID id) throws Exception {
    var found = find(h.adapter().load(PCStore.class, h.pc().getUuid(), p.registryAccess()), id);
    return found != null
        ? found
        : find(
            h.adapter().load(PlayerPartyStore.class, h.party().getUuid(), p.registryAccess()), id);
  }

  static Pokemon find(PokemonStore<?> store, UUID id) {
    if (store != null) for (var pokemon : store) if (pokemon.getUuid().equals(id)) return pokemon;
    return null;
  }
}
