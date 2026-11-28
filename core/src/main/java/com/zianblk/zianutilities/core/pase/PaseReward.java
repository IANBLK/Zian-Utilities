package com.zianblk.zianutilities.core.pase;

import java.util.List;

/** One reward as the admin wrote it. Plain data: the Minecraft side turns it into a real item, coins, Pokemon or command. */
public record PaseReward(Kind kind, String item, int count, String components, String currency, long amount, String command, PokemonSpec pokemon, String id) {
  public enum Kind { ITEM, COINS, POKEMON, COMMAND }

  /** Filter of a random species: any, only legendary ones, or none of them. */
  public enum Legendary { ANY, ONLY, NONE }

  /**
   * A Pokemon reward. {@code species} null means a random one taken from {@code pool} (every species when empty) after the
   * {@code legendary} filter. {@code ivs} is -1 (random) or 0-31 for all six stats; {@code minPerfectIvs} makes that many stats 31.
   * The generation filter and the level cap apply unless the entry says otherwise.
   */
  public record PokemonSpec(String species, List<String> pool, Legendary legendary, int level, int ivs, int minPerfectIvs, boolean alpha, boolean shiny,
      boolean ignoreGeneration, boolean ignoreLevelCap) {
    public PokemonSpec {
      if (species != null && species.isBlank()) throw PaseDataException.missing("species");
      pool = List.copyOf(pool);
      if (level < 1 || level > 100) throw PaseDataException.range("level", 1, 100);
      if (ivs < -1 || ivs > 31) throw PaseDataException.range("ivs", 0, 31);
      if (minPerfectIvs < 0 || minPerfectIvs > 6) throw PaseDataException.range("min_perfect_ivs", 0, 6);
      if (ivs >= 0 && minPerfectIvs > 0) throw PaseDataException.conflict("ivs", "min_perfect_ivs");
    }
  }

  public PaseReward {
    switch (kind) {
      case ITEM -> {
        if (item == null || item.isBlank()) throw PaseDataException.missing("id");
        if (count < 1 || count > 6400) throw PaseDataException.range("count", 1, 6400);
      }
      case COINS -> {
        if (currency == null || currency.isBlank()) throw PaseDataException.missing("currency");
        if (amount < 1 || amount > 1_000_000) throw PaseDataException.range("amount", 1, 1_000_000);
      }
      case COMMAND -> {
        if (command == null || command.isBlank() || command.length() > 256 || command.indexOf('\n') >= 0) throw PaseDataException.range("command", 1, 256);
      }
      case POKEMON -> {
        if (pokemon == null) throw PaseDataException.missing("pokemon");
      }
    }
  }

  /** The id this reward has inside its level and track (null for loot-table entries); it is what makes the prize id stable. */
  public PaseReward withId(String newId) { return new PaseReward(kind, item, count, components, currency, amount, command, pokemon, newId); }

  public static PaseReward item(String id, int count, String components) { return new PaseReward(Kind.ITEM, id, count, components, null, 0, null, null, null); }

  public static PaseReward coins(String currency, long amount) { return new PaseReward(Kind.COINS, null, 0, null, currency, amount, null, null, null); }

  public static PaseReward command(String command) { return new PaseReward(Kind.COMMAND, null, 0, null, null, 0, command, null, null); }

  public static PaseReward pokemon(PokemonSpec spec) { return new PaseReward(Kind.POKEMON, null, 0, null, null, 0, null, spec, null); }
}
