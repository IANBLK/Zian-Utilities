package com.zianblk.zianutilities.neoforge.pase;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zianblk.zianutilities.core.generation.SpawnDecision;
import com.zianblk.zianutilities.core.pase.PaseReward;
import com.zianblk.zianutilities.core.pase.PlayerPase;
import com.zianblk.zianutilities.core.pase.Prize;
import com.zianblk.zianutilities.core.pase.PrizeClaimer;
import com.zianblk.zianutilities.core.pase.SeasonRewards;
import com.zianblk.zianutilities.core.rewards.ClaimStatus;
import com.zianblk.zianutilities.core.rewards.ComponentRecord;
import com.zianblk.zianutilities.core.rewards.Reward;
import com.zianblk.zianutilities.core.rewards.RewardClaim;
import com.zianblk.zianutilities.core.rewards.RewardComponent;
import com.zianblk.zianutilities.core.economy.EconomyBalanceResult;
import com.zianblk.zianutilities.neoforge.LocalizedException;
import com.zianblk.zianutilities.neoforge.Messages;
import com.zianblk.zianutilities.neoforge.cobblemon.CobblemonGenerationResolver;
import com.zianblk.zianutilities.neoforge.delivery.PokemonDelivery;
import com.zianblk.zianutilities.neoforge.generation.GenerationEnforcement;
import com.zianblk.zianutilities.neoforge.generation.NeoForgeGenerationStateStore;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Minecraft half of the prize journal: checks and gives one prize to one online player.
 * Items: only given when the whole stack fits in the main inventory (otherwise it stays pending, nothing is dropped on the
 * floor); the rare leftover that still does not fit is dropped at the player's feet so it is never lost.
 * Coins: through the shared reward claim journal with a fixed claim id (AVECOINS does not honour operation ids).
 * Commands: only templates in the whitelist file. Pokemon: PokemonDelivery with a fixed Pokemon id and a read-back proof.
 */
final class PaseDelivery implements PrizeClaimer.Gateway {
  private static final CobblemonGenerationResolver RESOLVER = new CobblemonGenerationResolver(null);
  private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.]{1,32}");
  private static final long PROOF_MILLIS = 30_000;
  /** When each Pokemon prize started waiting for its proof, by "player|prize id"; cleared when the player leaves. */
  private static final Map<String, Long> SINCE = new HashMap<>();
  private static com.zianblk.zianutilities.core.generation.GenerationState generation;
  private static long generationTick = -1;

  private final ServerPlayer player;

  PaseDelivery(ServerPlayer player) { this.player = player; }

  // ---------------------------------------------------------------- validation (config load)

  /** The problem of a reward whose item, species, currency or command does not exist or is not allowed, or null. Translatable. */
  static RuntimeException validate(PaseReward r, HolderLookup.Provider registries, Set<String> commands) {
    try {
      switch (r.kind()) {
        case ITEM -> stack(r, registries);
        case COINS -> {
          if (ResourceLocation.tryParse(r.currency()) == null) return PaseRewardJson.bad("bad_currency", "Moneda no válida: %s", r.currency());
        }
        case COMMAND -> {
          if (!commands.contains(r.command())) return PaseRewardJson.bad("command_not_whitelisted", "El comando «%s» no está en la lista blanca (config/zianutilities-pase-commands.json)", r.command());
        }
        case POKEMON -> {
          var s = r.pokemon();
          if (s.species() != null && species(s.species()) == null) return PaseRewardJson.bad("unknown_species", "Especie desconocida: %s", s.species());
          for (String id : s.pool()) if (species(id) == null) return PaseRewardJson.bad("unknown_species", "Especie desconocida: %s", id);
        }
      }
      return null;
    } catch (LocalizedException.Argument bad) {
      return bad;
    } catch (CommandSyntaxException | RuntimeException bad) {
      return PaseRewardJson.bad("bad_item", "Objeto no válido «%s»: %s", String.valueOf(r.item()), String.valueOf(bad.getMessage()));
    }
  }

  static ItemStack stack(PaseReward r, HolderLookup.Provider registries) throws CommandSyntaxException {
    String text = r.item() + (r.components() == null || r.components().isBlank() ? "" : "[" + r.components() + "]");
    var parsed = new ItemParser(registries).parse(new StringReader(text));
    if (parsed.item().value() == Items.AIR) throw PaseRewardJson.bad("air", "El objeto no puede ser aire");
    ItemStack stack = new ItemStack(parsed.item(), r.count());
    stack.applyComponents(parsed.components());
    return stack;
  }

  // ---------------------------------------------------------------- Pokemon helpers

  static Species species(String id) {
    ResourceLocation key = id.indexOf(':') >= 0 ? ResourceLocation.tryParse(id) : ResourceLocation.tryBuild("cobblemon", id.toLowerCase(java.util.Locale.ROOT));
    return key == null ? null : PokemonSpecies.INSTANCE.getByIdentifier(key);
  }

  private static boolean legendary(Species s) {
    var labels = s.getLabels();
    return labels.contains("legendary") || labels.contains("mythical");
  }

  /** The generation state, read from disk at most once per server tick (a claim checks many prizes in one tick). */
  private static com.zianblk.zianutilities.core.generation.GenerationState generationState() {
    if (generation == null || generationTick != PaseModule.ticks) {
      generation = new NeoForgeGenerationStateStore(PaseModule.server).load();
      generationTick = PaseModule.ticks;
    }
    return generation;
  }

  private static boolean denied(Species s, com.zianblk.zianutilities.core.generation.GenerationState state) {
    return GenerationEnforcement.decide(RESOLVER.resolve(s), state) instanceof SpawnDecision.Deny;
  }

  /** The species a Pokemon prize gives: the fixed one, or a deterministic pick of the allowed candidates. Null (with a reason in {@code why}) if there is none. */
  private String choose(Prize z, String[] why) {
    var spec = z.reward.pokemon();
    var state = spec.ignoreGeneration() ? null : generationState();
    if (spec.species() != null) {
      Species s = species(spec.species());
      if (s == null) { why[0] = "NO_SPECIES"; return null; }
      if (state != null && denied(s, state)) { why[0] = "GENERATION_DISABLED"; return null; }
      return s.getResourceIdentifier().toString();
    }
    List<Species> candidates = new ArrayList<>();
    if (spec.pool().isEmpty()) candidates.addAll(PokemonSpecies.INSTANCE.getImplemented());
    else for (String id : spec.pool()) { Species s = species(id); if (s != null) candidates.add(s); }
    candidates.removeIf(s -> (spec.legendary() == PaseReward.Legendary.ONLY && !legendary(s)) || (spec.legendary() == PaseReward.Legendary.NONE && legendary(s)));
    if (state != null) candidates.removeIf(s -> denied(s, state));
    if (candidates.isEmpty()) { why[0] = "NO_SPECIES"; return null; }
    candidates.sort(Comparator.comparing(s -> s.getResourceIdentifier().toString()));
    long seed = com.zianblk.zianutilities.core.pase.LootTable.seed(player.getUUID(), z.id, 0);
    return candidates.get(new SplittableRandom(seed).nextInt(candidates.size())).getResourceIdentifier().toString();
  }

  /** Fixed per player and prize: delivering the same prize again can only ever find or insert this one Pokemon. */
  static UUID pokemonId(UUID playerId, Prize z) {
    return UUID.nameUUIDFromBytes(("zian-pase-pokemon|" + playerId + "|" + z.id).getBytes(StandardCharsets.UTF_8));
  }

  /** The claim of a coins prize: the id is fixed per player and prize, so the shared journal credits it at most once. */
  static RewardClaim coinsClaim(UUID playerId, Prize z) {
    UUID claimId = UUID.nameUUIDFromBytes(("zian-pase|" + playerId + "|" + z.id).getBytes(StandardCharsets.UTF_8));
    return new RewardClaim(claimId, playerId, "pase", z.id, List.of(new RewardComponent("coins", new Reward.Currency(z.reward.currency(), z.reward.amount()))));
  }

  /** The Pokemon of a prize with its species already chosen (z.detail), at the level the cap allows. */
  static Pokemon build(ServerPlayer player, Prize z, SeasonRewards season) {
    var spec = z.reward.pokemon();
    int level = spec.ignoreLevelCap() ? spec.level() : Math.min(spec.level(), Math.min(season.levelCap(), RctLevelCap.of(player)));
    long seed = com.zianblk.zianutilities.core.pase.LootTable.seed(player.getUUID(), z.id, 1);
    return PaseMons.build(z.detail, level, spec.ivs(), spec.minPerfectIvs(), spec.alpha(), spec.shiny(), pokemonId(player.getUUID(), z), seed, player.getUUID());
  }

  // ---------------------------------------------------------------- Gateway

  @Override
  public String check(Prize z) {
    SeasonRewards season = PaseRewards.rewardsOf(z.seasonId);
    if (season == null) return "CONFIG_INVALID";
    if (z.premiumOnly() && !PaseModule.store.get(player.getUUID()).premium.contains(z.seasonId)) return "NO_PREMIUM";
    switch (z.reward.kind()) {
      case ITEM -> {
        try {
          if (!fits(player.getInventory(), stack(z.reward, player.registryAccess()))) return "INVENTORY_FULL";
        } catch (CommandSyntaxException | RuntimeException bad) {
          return "BAD_ITEM";
        }
      }
      case COINS -> {
        var port = PaseRewards.economyPort();
        if (port == null || PaseRewards.coins() == null) return "NO_ECONOMY";
        if (port.balance(player.getUUID(), z.reward.currency()) instanceof EconomyBalanceResult.Unavailable u) return "unsupported_currency".equals(u.getReason()) ? "UNSUPPORTED_CURRENCY" : "NO_ECONOMY";
      }
      case COMMAND -> {
        if (!PaseRewards.commands().contains(z.reward.command())) return "COMMAND_NOT_ALLOWED";
        if (!NAME.matcher(player.getGameProfile().getName()).matches()) return "BAD_PLAYER_NAME";
      }
      case POKEMON -> {
        if (z.detail.isEmpty()) {
          String[] why = new String[1];
          String chosen = choose(z, why);
          if (chosen == null) return why[0];
          z.detail = chosen;
        }
      }
    }
    return null;
  }

  @Override
  public PrizeClaimer.Outcome give(Prize z) throws Exception {
    return switch (z.reward.kind()) {
      case ITEM -> giveItem(z);
      case COINS -> giveCoins(z);
      case COMMAND -> giveCommand(z);
      case POKEMON -> givePokemon(z);
    };
  }

  // ---------------------------------------------------------------- items

  static boolean fits(Inventory inventory, ItemStack stack) {
    int left = stack.getCount();
    int max = stack.getMaxStackSize();
    for (ItemStack slot : inventory.items) {
      if (slot.isEmpty()) left -= max;
      else if (ItemStack.isSameItemSameComponents(slot, stack)) left -= Math.max(0, max - slot.getCount());
      if (left <= 0) return true;
    }
    return false;
  }

  private PrizeClaimer.Outcome giveItem(Prize z) throws CommandSyntaxException {
    ItemStack rest = stack(z.reward, player.registryAccess());
    // Earlier items of the same batch may have taken the room that check() saw.
    if (!fits(player.getInventory(), rest)) return PrizeClaimer.Outcome.blocked("INVENTORY_FULL");
    while (!rest.isEmpty()) {
      ItemStack part = rest.split(Math.min(rest.getCount(), rest.getMaxStackSize()));
      if (!player.getInventory().add(part) && !part.isEmpty()) {
        PaseModule.LOG.warn("Premio {} de {}: no cupo en el inventario y se soltó a sus pies ({} x {})", z.id, player.getGameProfile().getName(), part.getCount(), part.getItem());
        player.sendSystemMessage(Messages.format(PaseMessages.itemDropped(part.getHoverName())));
        player.drop(part, false);
      }
    }
    return PrizeClaimer.Outcome.delivered();
  }

  // ---------------------------------------------------------------- coins

  private PrizeClaimer.Outcome giveCoins(Prize z) {
    var record = PaseRewards.coins().claim(coinsClaim(player.getUUID(), z));
    if (record.status() == ClaimStatus.CLAIMED) return PrizeClaimer.Outcome.delivered();
    if (record.status() == ClaimStatus.REJECTED) {
      ComponentRecord c = record.getComponents().get("coins");
      String why = c == null || c.getReason() == null ? "REJECTED" : c.getReason();
      // Only a full wallet is a certain "nothing happened" (and the claim journal retries it); any other rejection needs a look.
      return "wallet_full".equals(why) ? PrizeClaimer.Outcome.blocked(why) : PrizeClaimer.Outcome.uncertain("COINS_" + why);
    }
    return PrizeClaimer.Outcome.uncertain("COINS_" + record.status());
  }

  // ---------------------------------------------------------------- commands

  private PrizeClaimer.Outcome giveCommand(Prize z) {
    String command = z.reward.command().replace("{player}", player.getGameProfile().getName()).replace("{uuid}", player.getUUID().toString());
    var server = PaseModule.server;
    try {
      int result = server.getCommands().getDispatcher().execute(command.startsWith("/") ? command.substring(1) : command, server.createCommandSourceStack().withSuppressedOutput());
      return result > 0 ? PrizeClaimer.Outcome.delivered() : PrizeClaimer.Outcome.uncertain("COMMAND_RESULT_" + result);
    } catch (CommandSyntaxException rejected) {
      return PrizeClaimer.Outcome.blocked("COMMAND_REJECTED");
    }
  }

  // ---------------------------------------------------------------- Pokemon

  private PrizeClaimer.Outcome givePokemon(Prize z) throws Exception {
    SeasonRewards season = PaseRewards.rewardsOf(z.seasonId);
    var step = PokemonDelivery.deliver(player, pokemonId(player.getUUID(), z), () -> build(player, z, season), (phase, reason) -> { });
    if (step == PokemonDelivery.Step.BLOCKED) return PrizeClaimer.Outcome.blocked("PC_FULL");
    SINCE.put(player.getUUID() + "|" + z.id, System.currentTimeMillis());
    return prove(player, z, false);
  }

  /** Reads the saved copy back: DELIVERED, still waiting (DELIVERING) or UNCERTAIN. */
  static PrizeClaimer.Outcome prove(ServerPlayer p, Prize z, boolean timedOut) throws Exception {
    UUID id = pokemonId(p.getUUID(), z);
    String[] uncertain = new String[1];
    var proof = PokemonDelivery.verify(p, id, found -> id.equals(found.getUuid()) && z.detail.equals(found.getSpecies().getResourceIdentifier().toString()), timedOut,
        (phase, reason) -> { if (phase == PokemonDelivery.Phase.UNCERTAIN) uncertain[0] = reason; }, found -> obtain(p, found));
    return switch (proof) {
      case DELIVERED -> PrizeClaimer.Outcome.delivered();
      case UNCERTAIN -> PrizeClaimer.Outcome.uncertain(uncertain[0] == null ? "DELIVERY_NOT_PROVEN" : uncertain[0]);
      case PENDING -> PrizeClaimer.Outcome.proof();
    };
  }

  /** The Pokemon counts for the player's Pokedex, like a bred one; a failure here never undoes the delivery. */
  private static void obtain(ServerPlayer p, Pokemon found) {
    try {
      com.cobblemon.mod.common.Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(p.getUUID()).obtain(found);
    } catch (Exception | LinkageError failure) {
      PaseModule.LOG.warn("Pokémon de premio entregado a {}, pero no se pudo anotar en la Pokédex", p.getGameProfile().getName(), failure);
    }
  }

  /** The player left: forget the proof timers of their Pokemon prizes. */
  static void forget(UUID playerId) { SINCE.keySet().removeIf(k -> k.startsWith(playerId + "|")); }

  /** Every few seconds: Pokemon of online players still waiting for their read-back proof. */
  static void proveAll() {
    for (ServerPlayer sp : new ArrayList<>(PaseModule.server.getPlayerList().getPlayers())) {
      PlayerPase p = PaseModule.store.get(sp.getUUID());
      if (p.locked) continue;
      boolean changed = false;
      for (Prize z : new ArrayList<>(p.prizes.values())) {
        if (z.state != Prize.State.DELIVERING || z.reward.kind() != PaseReward.Kind.POKEMON) continue;
        try {
          boolean timedOut = System.currentTimeMillis() - SINCE.computeIfAbsent(sp.getUUID() + "|" + z.id, k -> System.currentTimeMillis()) > PROOF_MILLIS;
          var o = prove(sp, z, timedOut);
          if (o.state() == Prize.State.DELIVERING) continue;
          z.state = o.state();
          z.reason = o.reason();
          z.updated = PaseModule.CLOCK.millis();
          SINCE.remove(sp.getUUID() + "|" + z.id);
          changed = true;
          if (o.state() == Prize.State.DELIVERED) sp.sendSystemMessage(Messages.format(PaseMessages.rewardClaimed(describe(z, sp.registryAccess(), PaseSources.lang(sp)))));
          else PaseModule.LOG.warn("El Pokémon del premio {} de {} no se pudo comprobar y queda por revisar ({})", z.id, p.id, o.reason());
        } catch (Exception failure) {
          PaseModule.LOG.error("Comprobación del premio {} de {} fallida; se reintenta", z.id, p.id, failure);
        }
      }
      if (changed) {
        p.dirty = true;
        try {
          PaseModule.store.saveNow(p);
        } catch (java.io.IOException failure) {
          PaseModule.LOG.error("No se pudo guardar la comprobación de premios de {}", p.id, failure);
        }
      }
    }
  }

  // ---------------------------------------------------------------- text

  static Component describe(Prize z, HolderLookup.Provider registries, String lang) {
    PaseReward r = z.reward;
    switch (r.kind()) {
      case ITEM -> {
        try {
          return PaseMessages.rewardItem(r.count(), stack(r, registries).getHoverName(), lang);
        } catch (CommandSyntaxException | RuntimeException bad) {
          return PaseMessages.rewardItem(r.count(), Component.literal(r.item()), lang);
        }
      }
      case COINS -> {
        ResourceLocation key = ResourceLocation.tryParse(r.currency());
        Component name = Component.literal(key == null ? r.currency() : key.getPath());
        if (key != null) {
          var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(key);
          if (item != Items.AIR) name = item.getDescription();
        }
        return PaseMessages.rewardCoins(r.amount(), name, lang);
      }
      case POKEMON -> {
        Species s = z.detail.isEmpty() ? null : species(z.detail);
        return s == null ? PaseMessages.rewardPokemonRandom() : PaseMessages.rewardPokemon(s.getTranslatedName());
      }
      default -> {
        return PaseMessages.rewardCommand();
      }
    }
  }
}
