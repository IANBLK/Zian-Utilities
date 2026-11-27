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
  private static final Map<String, Long> SINCE = new HashMap<>();

  private final ServerPlayer player;

  PaseDelivery(ServerPlayer player) { this.player = player; }

  // ---------------------------------------------------------------- validation (config load)

  static String validate(PaseReward r, HolderLookup.Provider registries, Set<String> commands) {
    try {
      switch (r.kind()) {
        case ITEM -> stack(r, registries);
        case COINS -> {
          if (ResourceLocation.tryParse(r.currency()) == null) return "moneda no válida: " + r.currency();
        }
        case COMMAND -> {
          if (!commands.contains(r.command())) return "el comando «" + r.command() + "» no está en la lista blanca (config/zianutilities-pase-commands.json)";
        }
        case POKEMON -> {
          var s = r.pokemon();
          if (s.species() != null && species(s.species()) == null) return "especie desconocida: " + s.species();
          for (String id : s.pool()) if (species(id) == null) return "especie desconocida en pool: " + id;
        }
      }
      return null;
    } catch (CommandSyntaxException | RuntimeException bad) {
      return "premio no válido (" + r.kind() + "): " + bad.getMessage();
    }
  }

  static ItemStack stack(PaseReward r, HolderLookup.Provider registries) throws CommandSyntaxException {
    String text = r.item() + (r.components() == null || r.components().isBlank() ? "" : "[" + r.components() + "]");
    var parsed = new ItemParser(registries).parse(new StringReader(text));
    if (parsed.item().value() == Items.AIR) throw new IllegalArgumentException("el objeto no puede ser aire");
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

  private static boolean denied(Species s, com.zianblk.zianutilities.core.generation.GenerationState state) {
    return GenerationEnforcement.decide(RESOLVER.resolve(s), state) instanceof SpawnDecision.Deny;
  }

  /** The species a Pokemon prize gives: the fixed one, or a deterministic pick of the allowed candidates. Null (with a reason in {@code why}) if there is none. */
  private String choose(Prize z, String[] why) {
    var spec = z.reward.pokemon();
    var state = spec.ignoreGeneration() ? null : new NeoForgeGenerationStateStore(PaseModule.server).load();
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

  private UUID pokemonId(Prize z) {
    return UUID.nameUUIDFromBytes(("zian-pase-pokemon|" + player.getUUID() + "|" + z.id).getBytes(StandardCharsets.UTF_8));
  }

  // ---------------------------------------------------------------- Gateway

  @Override
  public String check(Prize z) {
    SeasonRewards season = PaseRewards.rewardsOf(z.seasonId);
    if (season == null) return "CONFIG_INVALID";
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
    while (!rest.isEmpty()) {
      ItemStack part = rest.split(Math.min(rest.getCount(), rest.getMaxStackSize()));
      if (!player.getInventory().add(part) && !part.isEmpty()) player.drop(part, false);
    }
    return PrizeClaimer.Outcome.delivered();
  }

  // ---------------------------------------------------------------- coins

  private PrizeClaimer.Outcome giveCoins(Prize z) {
    UUID claimId = UUID.nameUUIDFromBytes(("zian-pase|" + player.getUUID() + "|" + z.id).getBytes(StandardCharsets.UTF_8));
    var claim = new RewardClaim(claimId, player.getUUID(), "pase", z.id, List.of(new RewardComponent("coins", new Reward.Currency(z.reward.currency(), z.reward.amount()))));
    var record = PaseRewards.coins().claim(claim);
    if (record.status() == ClaimStatus.CLAIMED) return PrizeClaimer.Outcome.delivered();
    if (record.status() == ClaimStatus.REJECTED) {
      ComponentRecord c = record.getComponents().get("coins");
      return PrizeClaimer.Outcome.blocked(c == null || c.getReason() == null ? "REJECTED" : c.getReason());
    }
    return PrizeClaimer.Outcome.uncertain("COINS_" + record.status());
  }

  // ---------------------------------------------------------------- commands

  private PrizeClaimer.Outcome giveCommand(Prize z) {
    String command = z.reward.command().replace("{player}", player.getGameProfile().getName());
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
    var spec = z.reward.pokemon();
    SeasonRewards season = PaseRewards.rewardsOf(z.seasonId);
    int level = spec.ignoreLevelCap() ? spec.level() : Math.min(spec.level(), season.levelCap());
    UUID id = pokemonId(z);
    long seed = com.zianblk.zianutilities.core.pase.LootTable.seed(player.getUUID(), z.id, 1);
    String[] uncertain = new String[1];
    var step = PokemonDelivery.deliver(player, id,
        () -> PaseMons.build(z.detail, level, spec.ivs(), spec.minPerfectIvs(), spec.alpha(), spec.shiny(), id, seed, player.getUUID()),
        (phase, reason) -> { if (phase == PokemonDelivery.Phase.UNCERTAIN) uncertain[0] = reason; });
    if (step == PokemonDelivery.Step.BLOCKED) return PrizeClaimer.Outcome.blocked("PC_FULL");
    SINCE.put(z.id, System.currentTimeMillis());
    return prove(player, z, false);
  }

  /** Reads the saved copy back: DELIVERED, still waiting (DELIVERING) or UNCERTAIN. */
  static PrizeClaimer.Outcome prove(ServerPlayer p, Prize z, boolean timedOut) throws Exception {
    UUID id = UUID.nameUUIDFromBytes(("zian-pase-pokemon|" + p.getUUID() + "|" + z.id).getBytes(StandardCharsets.UTF_8));
    String[] uncertain = new String[1];
    var proof = PokemonDelivery.verify(p, id, found -> id.equals(found.getUuid()) && z.detail.equals(found.getSpecies().getResourceIdentifier().toString()), timedOut,
        (phase, reason) -> { if (phase == PokemonDelivery.Phase.UNCERTAIN) uncertain[0] = reason; }, found -> { });
    return switch (proof) {
      case DELIVERED -> PrizeClaimer.Outcome.delivered();
      case UNCERTAIN -> PrizeClaimer.Outcome.uncertain(uncertain[0] == null ? "DELIVERY_NOT_PROVEN" : uncertain[0]);
      case PENDING -> PrizeClaimer.Outcome.proof();
    };
  }

  /** Every few seconds: Pokemon of online players still waiting for their read-back proof. */
  static void proveAll() {
    for (ServerPlayer sp : new ArrayList<>(PaseModule.server.getPlayerList().getPlayers())) {
      PlayerPase p = PaseModule.store.get(sp.getUUID());
      if (p.locked) continue;
      boolean changed = false;
      for (Prize z : new ArrayList<>(p.prizes.values())) {
        if (z.state != Prize.State.DELIVERING || z.reward.kind() != PaseReward.Kind.POKEMON) continue;
        try {
          boolean timedOut = System.currentTimeMillis() - SINCE.computeIfAbsent(z.id, k -> System.currentTimeMillis()) > PROOF_MILLIS;
          var o = prove(sp, z, timedOut);
          if (o.state() == Prize.State.DELIVERING) continue;
          z.state = o.state();
          z.reason = o.reason();
          z.updated = PaseModule.CLOCK.millis();
          SINCE.remove(z.id);
          changed = true;
          if (o.state() == Prize.State.DELIVERED) sp.sendSystemMessage(Messages.format(PaseMessages.rewardClaimed(describe(z, sp.registryAccess()))));
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

  static Component describe(Prize z, HolderLookup.Provider registries) {
    PaseReward r = z.reward;
    switch (r.kind()) {
      case ITEM -> {
        try {
          return PaseMessages.rewardItem(r.count(), stack(r, registries).getHoverName());
        } catch (CommandSyntaxException | RuntimeException bad) {
          return PaseMessages.rewardItem(r.count(), Component.literal(r.item()));
        }
      }
      case COINS -> {
        ResourceLocation key = ResourceLocation.tryParse(r.currency());
        Component name = Component.literal(key == null ? r.currency() : key.getPath());
        if (key != null) {
          var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(key);
          if (item != Items.AIR) name = item.getDescription();
        }
        return PaseMessages.rewardCoins(r.amount(), name);
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
