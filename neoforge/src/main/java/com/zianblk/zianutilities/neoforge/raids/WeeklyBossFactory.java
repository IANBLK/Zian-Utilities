package com.zianblk.zianutilities.neoforge.raids;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.necro.raid.dens.common.data.raid.RaidBoss;
import com.necro.raid.dens.common.registry.CustomRaidRegistries;
import com.necro.raid.dens.common.registry.custom.RaidRegistry;
import com.necro.raid.dens.common.registry.store.RegistryCache;
import net.minecraft.resources.ResourceLocation;

/** The only place where the JSON of a weekly raid becomes a Raid Dens RaidBoss (RaidBoss.GSON, same keys as its datapack). */
final class WeeklyBossFactory {
  static final String NAMESPACE = "zianutilities";
  static final String PATH_PREFIX = "weekly/";

  private WeeklyBossFactory() {}

  static RaidBoss build(WeeklyConfig.Raid raid) {
    RaidBoss boss;
    try {
      boss = RaidBoss.GSON.fromJson(raid.boss().deepCopy(), RaidBoss.class);
      if (boss == null) throw new IllegalArgumentException("vacío");
      boss.setId(ResourceLocation.fromNamespaceAndPath(NAMESPACE, PATH_PREFIX + raid.id()));
      boss.applyDefaults();
    } catch (RuntimeException bad) {
      throw new IllegalArgumentException("raid " + raid.id() + ": Raid Dens no acepta el jefe: " + bad.getMessage(), bad);
    }
    String species = boss.getBossProperties().getSpecies();
    ResourceLocation id = species.indexOf(':') >= 0 ? ResourceLocation.tryParse(species) : ResourceLocation.fromNamespaceAndPath("cobblemon", species);
    if (id == null || PokemonSpecies.INSTANCE.getByIdentifier(id) == null)
      throw new IllegalArgumentException("raid " + raid.id() + ": la especie " + species + " no existe en Cobblemon");
    return boss;
  }

  /**
   * RaidInstance finds its boss again by id in the Raid Dens registry, which only knows the datapack bosses on disk.
   * The weekly boss is put straight into the registry cache (there is no public call for one boss); the runtime checks
   * it every second because the cache lets idle entries expire. Without this Raid Dens throws when the raid starts.
   */
  @SuppressWarnings("unchecked")
  static void ensureRegistered(RaidBoss boss) throws ReflectiveOperationException {
    RaidRegistry registry = CustomRaidRegistries.RAID_REGISTRY;
    if (registry.cacheHas(boss.getId())) return;
    var field = RaidRegistry.class.getDeclaredField("cache");
    field.setAccessible(true);
    var cache = (RegistryCache<ResourceLocation, RaidBoss>) field.get(registry);
    cache.putIfCurrent(boss.getId(), boss, cache.currentEpoch());
  }

  static boolean isWeekly(RaidBoss boss) {
    ResourceLocation id = boss == null ? null : boss.getId();
    return id != null && NAMESPACE.equals(id.getNamespace()) && id.getPath().startsWith(PATH_PREFIX);
  }
}
