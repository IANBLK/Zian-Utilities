package com.zianblk.zianutilities.neoforge.breeding;

import com.cobblemon.mod.common.api.pokemon.egg.EggGroup;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.zianblk.zianutilities.neoforge.breeding.genetics.BreedingUtilities;
import java.util.List;

/** Eligibility previews inspect species data only: never roll a hidden offspring. */
final class BreedingCompatibility {
  static String individual(Pokemon p) {
    if (p == null) return "Este Pokémon ya no está en tu equipo";
    if (p.getPersistentData().getBoolean("neutered"))
      return "Este Pokémon no puede criar (esterilizado)";
    if (p.getForm().getEggGroups().contains(EggGroup.UNDISCOVERED))
      return "Grupo No descubierto: no puede criar";
    if (BreedingRuntime.store.parentBusy(p.getUuid())) return "Ya participa en una crianza activa";
    return "";
  }

  static String pair(Pokemon a, Pokemon b) {
    if (a == null || b == null) return "Selecciona dos Pokémon de tu equipo";
    if (a.getUuid().equals(b.getUuid())) return "Debes elegir dos Pokémon distintos";
    String why = individual(a);
    if (!why.isEmpty()) return why;
    why = individual(b);
    if (!why.isEmpty()) return why;
    boolean da = a.getForm().getEggGroups().contains(EggGroup.DITTO),
        db = b.getForm().getEggGroups().contains(EggGroup.DITTO);
    if (da && db) return "Ditto no puede criar con otro Ditto";
    if (!da && !db) {
      if (a.getGender() == Gender.GENDERLESS || b.getGender() == Gender.GENDERLESS)
        return "Los Pokémon sin sexo necesitan un Ditto";
      if (a.getGender() == b.getGender()) return "La pareja debe tener un macho y una hembra";
      if (a.getForm().getEggGroups().stream().noneMatch(b.getForm().getEggGroups()::contains))
        return "No comparten ningún grupo huevo";
    }
    try {
      var eggs = BreedingUtilities.getPossibleEggs(List.of(a, b));
      if (eggs.isEmpty()) return "Esta pareja no puede producir una cría";
      if (eggs.stream().noneMatch(e -> BreedingRuntime.allowed(e.getKey().getSpecies())))
        return "La generación de sus posibles crías está deshabilitada";
      return "";
    } catch (Exception error) {
      return "No se pudieron validar los datos de especie o forma";
    }
  }
}
