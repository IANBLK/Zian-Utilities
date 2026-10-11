package com.zianblk.zianutilities.neoforge.breeding;

import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.zianblk.zianutilities.neoforge.breeding.genetics.BreedingUtilities;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

final class BreedingPreview {
  private static final List<Stats> ORDER =
      List.of(
          Stats.HP,
          Stats.ATTACK,
          Stats.DEFENCE,
          Stats.SPECIAL_ATTACK,
          Stats.SPECIAL_DEFENCE,
          Stats.SPEED);
  private static final List<String> POWER =
      List.of(
          "power_weight", "power_bracer", "power_belt", "power_lens", "power_band", "power_anklet");

  static List<BreedingNetwork.Offspring> of(Pokemon a, Pokemon b) {
    if (!BreedingCompatibility.pair(a, b).isEmpty()) return List.of();
    var powers = new ArrayList<BreedingEstimate.Power>();
    boolean knot = false;
    for (var parent : List.of(a, b)) {
      var id = BuiltInRegistries.ITEM.getKey(parent.heldItem().getItem());
      if (id.equals(ResourceLocation.fromNamespaceAndPath("cobblemon", "destiny_knot")))
        knot = true;
      int index = id.getNamespace().equals("cobblemon") ? POWER.indexOf(id.getPath()) : -1;
      if (index >= 0)
        powers.add(new BreedingEstimate.Power(index, parent.getIvs().get(ORDER.get(index))));
    }
    var expected =
        BreedingEstimate.ivs(
            ORDER.stream().map(s -> a.getIvs().get(s)).toList(),
            ORDER.stream().map(s -> b.getIvs().get(s)).toList(),
            knot,
            powers);
    String detail =
        (knot ? "Lazo Destino: 5 IV heredados" : "3 IV heredados")
            + " entre ambos padres"
            + (powers.isEmpty() ? "" : " · Objeto recio: un IV fijado")
            + ". Valores medios, no garantizados. Nivel 1, EV 0 y naturaleza neutra para la"
            + " estimación.";
    return BreedingUtilities.getPossibleEggs(List.of(a, b)).stream()
        .map(Map.Entry::getKey)
        .filter(f -> BreedingRuntime.allowed(f.getSpecies()))
        .sorted(
            Comparator.comparing(
                f -> f.getSpecies().getResourceIdentifier().toString() + f.formOnlyShowdownId()))
        .limit(8)
        .map(
            f ->
                new BreedingNetwork.Offspring(
                    PokemonItem.Companion.from(
                        f.getSpecies(), f.getAspects().toArray(String[]::new)),
                    f.getSpecies().getName(),
                    f.formOnlyShowdownId(),
                    BreedingEstimate.stats(
                        ORDER.stream().map(s -> f.getBaseStats().get(s)).toList(), expected),
                    expected,
                    detail))
        .toList();
  }
}
