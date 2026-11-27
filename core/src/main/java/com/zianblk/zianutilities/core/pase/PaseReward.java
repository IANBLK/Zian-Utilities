package com.zianblk.zianutilities.core.pase;

import java.util.List;

/** One reward as the admin wrote it. Plain data: the Minecraft side turns it into a real item, coins, Pokemon or command. */
public record PaseReward(Kind kind, String item, int count, String components, String currency, long amount, String command, PokemonSpec pokemon) {
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
      if (species != null && species.isBlank()) throw new IllegalArgumentException("species vacia");
      pool = List.copyOf(pool);
      if (level < 1 || level > 100) throw new IllegalArgumentException("level debe estar entre 1 y 100");
      if (ivs < -1 || ivs > 31) throw new IllegalArgumentException("ivs debe estar entre 0 y 31");
      if (minPerfectIvs < 0 || minPerfectIvs > 6) throw new IllegalArgumentException("min_perfect_ivs debe estar entre 0 y 6");
      if (ivs >= 0 && minPerfectIvs > 0) throw new IllegalArgumentException("ivs y min_perfect_ivs no van juntos");
    }
  }

  public PaseReward {
    switch (kind) {
      case ITEM -> {
        if (item == null || item.isBlank()) throw new IllegalArgumentException("item sin id");
        if (count < 1 || count > 6400) throw new IllegalArgumentException("count debe estar entre 1 y 6400");
      }
      case COINS -> {
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("coins sin moneda");
        if (amount < 1 || amount > 1_000_000) throw new IllegalArgumentException("amount debe estar entre 1 y 1000000");
      }
      case COMMAND -> {
        if (command == null || command.isBlank() || command.length() > 256 || command.indexOf('\n') >= 0) throw new IllegalArgumentException("comando invalido");
      }
      case POKEMON -> {
        if (pokemon == null) throw new IllegalArgumentException("pokemon sin datos");
      }
    }
  }

  public static PaseReward item(String id, int count, String components) { return new PaseReward(Kind.ITEM, id, count, components, null, 0, null, null); }

  public static PaseReward coins(String currency, long amount) { return new PaseReward(Kind.COINS, null, 0, null, currency, amount, null, null); }

  public static PaseReward command(String command) { return new PaseReward(Kind.COMMAND, null, 0, null, null, 0, command, null); }

  public static PaseReward pokemon(PokemonSpec spec) { return new PaseReward(Kind.POKEMON, null, 0, null, null, 0, null, spec); }
}
