package com.zianblk.zianutilities.neoforge.raids;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.necro.raid.dens.common.data.raid.RaidBoss;
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

  static boolean isWeekly(RaidBoss boss) {
    ResourceLocation id = boss == null ? null : boss.getId();
    return id != null && NAMESPACE.equals(id.getNamespace()) && id.getPath().startsWith(PATH_PREFIX);
  }
}
