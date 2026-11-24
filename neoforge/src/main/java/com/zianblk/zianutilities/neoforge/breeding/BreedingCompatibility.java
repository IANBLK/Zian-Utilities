package com.zianblk.zianutilities.neoforge.breeding;

import com.cobblemon.mod.common.api.pokemon.egg.EggGroup;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.zianblk.zianutilities.neoforge.Tr;
import com.zianblk.zianutilities.neoforge.breeding.genetics.BreedingUtilities;
import java.util.List;

/** Eligibility previews inspect species data only: never roll a hidden offspring. */
final class BreedingCompatibility {
  static String individual(Pokemon p) {
    if (p == null) return Tr.enc("zianutilities.breeding.msg.not_in_team", "Este Pokémon ya no está en tu equipo");
    if (p.getPersistentData().getBoolean("neutered"))
      return Tr.enc("zianutilities.breeding.why.neutered", "Este Pokémon no puede criar (esterilizado)");
    if (p.getForm().getEggGroups().contains(EggGroup.UNDISCOVERED))
      return Tr.enc("zianutilities.breeding.why.undiscovered", "Grupo No descubierto: no puede criar");
    if (BreedingRuntime.store.parentBusy(p.getUuid())) return Tr.enc("zianutilities.breeding.why.busy", "Ya participa en una crianza activa");
    return "";
  }

  static String pair(Pokemon a, Pokemon b) {
    if (a == null || b == null) return Tr.enc("zianutilities.breeding.why.select_two", "Selecciona dos Pokémon de tu equipo");
    if (a.getUuid().equals(b.getUuid())) return Tr.enc("zianutilities.breeding.why.distinct", "Debes elegir dos Pokémon distintos");
    String why = individual(a);
    if (!why.isEmpty()) return why;
    why = individual(b);
    if (!why.isEmpty()) return why;
    boolean da = a.getForm().getEggGroups().contains(EggGroup.DITTO),
        db = b.getForm().getEggGroups().contains(EggGroup.DITTO);
    if (da && db) return Tr.enc("zianutilities.breeding.why.ditto", "Ditto no puede criar con otro Ditto");
    if (!da && !db) {
      if (a.getGender() == Gender.GENDERLESS || b.getGender() == Gender.GENDERLESS)
        return Tr.enc("zianutilities.breeding.why.genderless", "Los Pokémon sin sexo necesitan un Ditto");
      if (a.getGender() == b.getGender()) return Tr.enc("zianutilities.breeding.why.gender", "La pareja debe tener un macho y una hembra");
      if (a.getForm().getEggGroups().stream().noneMatch(b.getForm().getEggGroups()::contains))
        return Tr.enc("zianutilities.breeding.why.egg_group", "No comparten ningún grupo huevo");
    }
    try {
      var eggs = BreedingUtilities.getPossibleEggs(List.of(a, b));
      if (eggs.isEmpty()) return Tr.enc("zianutilities.breeding.why.no_egg", "Esta pareja no puede producir una cría");
      if (eggs.stream().noneMatch(e -> BreedingRuntime.allowed(e.getKey().getSpecies())))
        return Tr.enc("zianutilities.breeding.why.generation", "La generación de sus posibles crías está deshabilitada");
      return "";
    } catch (Exception error) {
      return Tr.enc("zianutilities.breeding.why.validate", "No se pudieron validar los datos de especie o forma");
    }
  }
}
