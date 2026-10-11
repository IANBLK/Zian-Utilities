package com.zianblk.zianutilities.neoforge.breeding;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.authlib.GameProfile;
import com.zianblk.zianutilities.core.breeding.BreedingJob;
import com.zianblk.zianutilities.core.generation.GenerationState;
import com.zianblk.zianutilities.neoforge.breeding.genetics.FrozenOffspring;
import com.zianblk.zianutilities.neoforge.generation.NeoForgeGenerationStateStore;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.LoggerFactory;

/**
 * No command or production shortcut: opt-in fixture restricted to its localhost build directory.
 */
final class BreedingSmoke {
  private static ServerPlayer player;
  private static UUID jobId;
  private static boolean done;
  private static int ticks;

  private static Pokemon pokemon(String properties) {
    return PokemonProperties.Companion.parse(properties, " ").create(null);
  }

  private static void require(boolean condition, String reason) {
    if (!condition) throw new IllegalStateException(reason);
  }

  public static void tick() {
    if (!"true".equals(System.getenv("ZIANUTILITIES_BREEDING_SMOKE"))
        || done
        || ++ticks % 20 != 0
        || BreedingRuntime.server == null) return;
    try {
      require(
          FMLPaths.GAMEDIR.get().toAbsolutePath().normalize().endsWith("build/breeding-smoke"),
          "Unsafe smoke directory");
      require(
          "127.0.0.1".equals(BreedingRuntime.server.getLocalIp()), "Smoke must be localhost only");
      require(BreedingRuntime.enabled(), "Smoke requires enabled fixture config");
      var generations = new NeoForgeGenerationStateStore(BreedingRuntime.server);
      if (player == null) {
        generations.save(GenerationState.Companion.none());
        BreedingRuntime.config.respectGenerationRestrictions = false;
        var previewDitto =
            pokemon(
                "ditto hp_iv=31 attack_iv=31 defence_iv=31 special_attack_iv=31"
                    + " special_defence_iv=31 speed_iv=31");
        previewDitto.swapHeldItem(
            new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        "cobblemon", "destiny_knot"))),
            false,
            false);
        var previewLucario = pokemon("lucario");
        int jobsBeforePreview = BreedingRuntime.store.jobs.size();
        var childPreview = BreedingPreview.of(previewDitto, previewLucario);
        var repeatedPreview = BreedingPreview.of(previewDitto, previewLucario);
        require(
            childPreview.size() == 1
                && childPreview.getFirst().name().equals("Riolu")
                && childPreview.getFirst().ivs().equals(repeatedPreview.getFirst().ivs())
                && childPreview.getFirst().stats().equals(repeatedPreview.getFirst().stats())
                && !childPreview.getFirst().icon().isEmpty()
                && BreedingRuntime.store.jobs.size() == jobsBeforePreview
                && !previewDitto.heldItem().isEmpty(),
            "Preview rolled offspring, consumed held item or changed jobs");
        require(
            BreedingPreview.of(previewDitto, pokemon("lugia")).isEmpty(),
            "Incompatible pair leaked preview");
        require(
            BreedingCompatibility.pair(pokemon("ditto"), pokemon("lucario")).isEmpty(),
            "Wild-spawn restriction leaked into Lucario/Ditto breeding");
        require(
            BreedingCompatibility.pair(pokemon("ditto"), pokemon("chikorita")).isEmpty(),
            "Wild-spawn restriction leaked into Chikorita/Ditto breeding");
        require(
            !BreedingCompatibility.pair(pokemon("ditto"), pokemon("lugia")).isEmpty(),
            "Legendary breeding restriction bypassed");
        BreedingRuntime.config.respectGenerationRestrictions = true;
        require(
            !BreedingCompatibility.pair(pokemon("ditto"), pokemon("lucario")).isEmpty(),
            "Explicit breeding generation restriction ignored");
        generations.save(GenerationState.Companion.all());
        var profile = new GameProfile(UUID.randomUUID(), "ZianBreedSmoke");
        player =
            new ServerPlayer(
                BreedingRuntime.server,
                BreedingRuntime.server.overworld(),
                profile,
                ClientInformation.createDefault()) {
              @Override
              public void sendSystemMessage(Component message) {}
            };
        BreedingRuntime.server.getPlayerList().op(profile);
        var mother =
            pokemon(
                "bulbasaur gender=female nature=adamant hp_iv=31 attack_iv=31 defence_iv=31"
                    + " special_attack_iv=31 special_defence_iv=31 speed_iv=31");
        var father = pokemon("bulbasaur gender=male nature=timid");
        mother.swapHeldItem(new ItemStack(CobblemonItems.EVERSTONE), false, false);
        father.swapHeldItem(
            new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        "cobblemon", "destiny_knot"))),
            false,
            false);
        var party = Cobblemon.INSTANCE.getStorage().getParty(player);
        require(party.add(mother) && party.add(father), "Parents were not inserted");
        var uiSession = new BreedingNetwork.Session();
        uiSession.select(mother.getUuid(), 2);
        require(
            uiSession.first == null && uiSession.second.equals(mother.getUuid()),
            "Selecting mother filled wrong slot");
        uiSession.select(father.getUuid(), 1);
        uiSession.select(mother.getUuid(), 1);
        require(
            uiSession.first.equals(mother.getUuid()) && uiSession.second.equals(father.getUuid()),
            "Slot swap duplicated parent");
        var selectedUiPreview = BreedingNetwork.view(player, uiSession, false);
        require(
            selectedUiPreview.compatible() && selectedUiPreview.offspring().size() == 1,
            "Selected parents did not produce UI preview");
        for (int seed = 0; seed < 25; seed++) {
          var frozen = FrozenOffspring.generate(List.of(mother, father), seed, form -> true, 0);
          var stats =
              List.of(
                  com.cobblemon.mod.common.api.pokemon.stats.Stats.HP,
                  com.cobblemon.mod.common.api.pokemon.stats.Stats.ATTACK,
                  com.cobblemon.mod.common.api.pokemon.stats.Stats.DEFENCE,
                  com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_ATTACK,
                  com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_DEFENCE,
                  com.cobblemon.mod.common.api.pokemon.stats.Stats.SPEED);
          int inherited = 0;
          for (int i = 0; i < 6; i++)
            if (frozen.ivs().get(i).equals(mother.getIvs().get(stats.get(i)))
                || frozen.ivs().get(i).equals(father.getIvs().get(stats.get(i)))) inherited++;
          require(inherited >= 5, "Destiny Knot did not inherit five IVs");
          require(
              frozen.nature().equals(mother.getNature().getName().toString()),
              "Everstone inheritance failed");
          require(
              FrozenOffspring.matches(FrozenOffspring.materialize(frozen), frozen),
              "Frozen outcome rerolled");
        }
        require(
            mother.heldItem().getItem() == CobblemonItems.EVERSTONE
                && father.heldItem().getItem()
                    == net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            "cobblemon", "destiny_knot")),
            "Held items consumed");
        require(
            FrozenOffspring.generate(
                    List.of(pokemon("magnemite"), pokemon("ditto")), 5, f -> true, 0)
                .species()
                .equals("cobblemon:magnemite"),
            "Genderless/Ditto failed");
        try {
          FrozenOffspring.generate(List.of(mother, father), 1, f -> false, 0);
          throw new AssertionError("Blocked generation accepted");
        } catch (IllegalStateException expected) {
        }
        for (String parent :
            List.of(
                "ferroseed",
                "vulpix alolan=true",
                "meowth galarian=true",
                "basculegion",
                "nidoranf",
                "illumise",
                "mareep",
                "wooloo")) {
          var frozen =
              FrozenOffspring.generate(
                  List.of(pokemon(parent), pokemon("ditto")), 17, f -> true, 0.05);
          require(
              FrozenOffspring.matches(FrozenOffspring.materialize(frozen), frozen),
              "Special offspring replay failed: " + parent);
        }
        for (var incompatible :
            List.of(
                List.of(pokemon("ditto"), pokemon("ditto")),
                List.of(pokemon("pichu"), pokemon("ditto")),
                List.of(pokemon("bulbasaur gender=male"), pokemon("bulbasaur gender=male")))) {
          try {
            FrozenOffspring.generate(incompatible, 1, f -> true, 0);
            throw new AssertionError("Incompatible parents accepted");
          } catch (IllegalStateException expected) {
          }
        }
        var pikachu = pokemon("pikachu gender=female");
        pikachu.swapHeldItem(
            new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        "cobblemon", "light_ball"))),
            false,
            false);
        var pichu = FrozenOffspring.generate(List.of(pikachu, pokemon("ditto")), 8, f -> true, 0);
        require(
            pichu.species().equals("cobblemon:pichu")
                && FrozenOffspring.materialize(pichu).getMoveSet().getMoves().stream()
                    .anyMatch(move -> move.getTemplate().getName().equals("volttackle")),
            "Light Ball egg move inheritance failed: " + pichu.properties());
        require(
            BreedingCompatibility.pair(mother, father).isEmpty(), "Valid pair preview rejected");
        require(
            BreedingCompatibility.pair(mother, pokemon("bulbasaur gender=female"))
                .contains("macho"),
            "Same-sex incompatibility reason missing");
        require(
            BreedingCompatibility.individual(pokemon("pichu")).contains("descubierto"),
            "Ineligible species explanation missing");
        jobId = UUID.randomUUID();
        var reserved = BreedingRuntime.start(player, mother.getUuid(), father.getUuid(), jobId);
        require(
            BreedingRuntime.start(player, mother.getUuid(), father.getUuid(), jobId)
                .equals(reserved),
            "Confirmation replay created another job");
        try {
          BreedingRuntime.start(player, mother.getUuid(), father.getUuid(), UUID.randomUUID());
          throw new AssertionError("Parent lease ignored");
        } catch (IllegalStateException expected) {
        }
        if (net.neoforged.fml.ModList.get().isLoaded("avecoins")) {
          var probe = com.zianblk.zianutilities.neoforge.economy.AvecoinsContractProbe.inspect();
          require(probe.compatible(), "Real AVECOINS probe rejected: " + probe.detail());
          String currency = probe.currencies().getFirst();
          BreedingRuntime.config.currencyReductionsMinutes = Map.of(currency, 2L);
          BreedingRuntime.config.validate();
          var economy = new com.zianblk.zianutilities.neoforge.economy.AvecoinsEconomyPort();
          require(
              economy.credit(
                      new com.zianblk.zianutilities.core.economy.EconomyOperation(
                          UUID.randomUUID(), player.getUUID(), currency, 2))
                  instanceof com.zianblk.zianutilities.core.economy.EconomyMutationResult.Applied,
              "Fixture wallet credit failed");
          UUID purchaseId = UUID.randomUUID();
          BreedingRuntime.accelerate(player, jobId, currency, 1, purchaseId);
          require(
              ((com.zianblk.zianutilities.core.economy.EconomyBalanceResult.Available)
                          economy.balance(player.getUUID(), currency))
                      .getAmount()
                  == 1,
              "Wallet charged wrong quantity");
          try {
            BreedingRuntime.accelerate(player, jobId, currency, 1, purchaseId);
            throw new AssertionError("Repeated purchase accepted");
          } catch (IllegalStateException expected) {
          }
          try {
            BreedingRuntime.accelerate(player, jobId, currency, 2, UUID.randomUUID());
            throw new AssertionError("Insufficient wallet funds accepted");
          } catch (IllegalStateException expected) {
          }
          require(
              ((com.zianblk.zianutilities.core.economy.EconomyBalanceResult.Available)
                          economy.balance(player.getUUID(), currency))
                      .getAmount()
                  == 1,
              "Repeated/rejected purchase spent wallet funds");
          reserved = BreedingRuntime.store.jobs.get(jobId);
          require(reserved.reductionMillis() == 120000, "Confirmed purchase reduction incorrect");
          var interrupted =
              new BreedingJob.Purchase(
                  UUID.randomUUID(), currency, 1, 120000, 120000, BreedingJob.Payment.PENDING);
          BreedingRuntime.store.put(reserved.begin(interrupted, BreedingRuntime.store.now()));
          require(
              economy.debit(
                      new com.zianblk.zianutilities.core.economy.EconomyOperation(
                          interrupted.id(), player.getUUID(), currency, 1))
                  instanceof com.zianblk.zianutilities.core.economy.EconomyMutationResult.Applied,
              "Interrupted debit fixture failed");
          var currentServer = BreedingRuntime.server;
          BreedingRuntime.stop();
          BreedingRuntime.start(currentServer);
          var recovered = BreedingRuntime.store.jobs.get(jobId);
          require(
              recovered.status() == BreedingJob.Status.UNCERTAIN
                  && recovered.reductionMillis() == 120000,
              "Interrupted debit was automatically acknowledged or retried");
          require(
              ((com.zianblk.zianutilities.core.economy.EconomyBalanceResult.Available)
                          economy.balance(player.getUUID(), currency))
                      .getAmount()
                  == 0,
              "Recovery changed wallet balance");
          try {
            BreedingRuntime.claim(player, jobId);
            throw new AssertionError("Uncertain purchase allowed delivery");
          } catch (IllegalStateException expected) {
          }
          require(
              currentServer
                      .getCommands()
                      .getDispatcher()
                      .execute(
                          "ZianUtilities breeding-admin resolve-debit "
                              + jobId
                              + " true isolated wallet debit proven",
                          currentServer.createCommandSourceStack())
                  == 1,
              "Admin reconciliation failed");
          reserved = BreedingRuntime.store.jobs.get(jobId);
          require(
              reserved.reductionMillis() == 240000
                  && reserved.status() == BreedingJob.Status.ACTIVE,
              "Admin reconciliation did not record proven debit");
          LoggerFactory.getLogger("ZianUtilities/Breeding")
              .info(
                  "Breeding real AVECOINS smoke PASSED: currency={}, debit replay blocked,"
                      + " insufficient funds rejected, interrupted debit held for admin",
                  currency);
        }
        var dispatcher = BreedingRuntime.server.getCommands().getDispatcher();
        var console = BreedingRuntime.server.createCommandSourceStack();
        var suggestions =
            dispatcher
                .getCompletionSuggestions(
                    dispatcher.parse("ZianUtilities breeding-admin complete ", console))
                .join();
        require(
            suggestions.getList().stream().anyMatch(v -> v.getText().equals(jobId.toString())),
            "Admin UUID autocomplete missing");
        var ordinary =
            new ServerPlayer(
                BreedingRuntime.server,
                BreedingRuntime.server.overworld(),
                new GameProfile(UUID.randomUUID(), "BreedPrivate"),
                ClientInformation.createDefault()) {
              @Override
              public void sendSystemMessage(Component m) {}
            };
        var privateSuggestions =
            dispatcher
                .getCompletionSuggestions(
                    dispatcher.parse(
                        "ZianUtilities crianza accelerate ", ordinary.createCommandSourceStack()))
                .join();
        require(
            privateSuggestions.getList().stream()
                .noneMatch(v -> v.getText().equals(jobId.toString())),
            "UUID autocomplete leaked another player's breeding");
        require(
            BreedingRuntime.server
                    .getCommands()
                    .getDispatcher()
                    .execute(
                        "ZianUtilities breeding-admin complete " + jobId,
                        BreedingRuntime.server.createCommandSourceStack())
                == 1,
            "Admin instant completion command failed");
        require(
            BreedingRuntime.store.jobs.get(jobId).readyAt() <= BreedingRuntime.store.now()
                && BreedingRuntime.store.jobs.get(jobId).startedAt() == reserved.startedAt(),
            "Admin override changed original timing");
        var preview = BreedingNetwork.preview(player);
        var buffer =
            new net.minecraft.network.RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), player.registryAccess());
        try {
          BreedingNetwork.State.CODEC.encode(buffer, selectedUiPreview);
          var decoded = BreedingNetwork.State.CODEC.decode(buffer);
          require(
              decoded.parents().size() == 2
                  && decoded.parents().getFirst().stats().size() == 6
                  && !decoded.parents().getFirst().icon().isEmpty()
                  && !decoded.parents().getFirst().held().isEmpty()
                  && decoded.first().equals(mother.getUuid().toString())
                  && decoded.second().equals(father.getUuid().toString())
                  && decoded.offspring().size() == 1
                  && decoded
                      .offspring()
                      .getFirst()
                      .ivs()
                      .equals(selectedUiPreview.offspring().getFirst().ivs())
                  && decoded
                      .offspring()
                      .getFirst()
                      .stats()
                      .equals(selectedUiPreview.offspring().getFirst().stats()),
              "Pokemon UI snapshot/codec failed");
        } finally {
          buffer.release();
        }
        generations.save(GenerationState.Companion.none());
        BreedingRuntime.config.respectGenerationRestrictions = true;
        BreedingRuntime.claim(player, jobId);
        require(
            BreedingRuntime.store.jobs.get(jobId).status() == BreedingJob.Status.BLOCKED,
            "Delivery ignored generation restriction");
        generations.save(GenerationState.Companion.all());
        require(party.remove(father), "Could not remove parent fixture");
        BreedingRuntime.claim(player, jobId);
        require(
            BreedingRuntime.store.jobs.get(jobId).reason().equals("PARENTS_NOT_OWNED"),
            "Lost parent ownership ignored");
        require(party.add(father), "Could not restore parent fixture");
        var pc = BreedingStorage.open(player).pc();
        var lock = pc.getClass().getDeclaredMethod("setLockedSize", boolean.class);
        lock.setAccessible(true);
        lock.invoke(pc, true);
        pc.getBoxes().subList(1, pc.getBoxes().size()).clear();
        var fillers = new ArrayList<Pokemon>();
        while (pc.getFirstAvailablePosition() != null) {
          var filler = pokemon("bulbasaur");
          require(pc.add(filler), "Fixture PC fill failed");
          fillers.add(filler);
          require(fillers.size() <= 30, "PC exceeded fixed fixture size");
        }
        BreedingRuntime.claim(player, jobId);
        require(
            BreedingRuntime.store.jobs.get(jobId).reason().equals("PC_FULL"),
            "PC full did not keep delivery pending");
        require(
            BreedingRuntime.store.jobs.get(jobId).child().uuid().equals(reserved.child().uuid()),
            "PC full rerolled child");
        for (var filler : fillers) pc.remove(filler);
        BreedingRuntime.claim(player, jobId);
      }
      var job = BreedingRuntime.store.jobs.get(jobId);
      if (job.status() == BreedingJob.Status.DELIVERING) {
        BreedingRuntime.verify(player, job);
        return;
      }
      require(
          job.status() == BreedingJob.Status.DELIVERED,
          "Delivery did not persist: " + job.status() + " " + job.reason());
      var handle = BreedingStorage.open(player);
      require(
          FrozenOffspring.matches(
              BreedingStorage.saved(handle, player, job.child().uuid()), job.child()),
          "PC readback mismatch");
      try {
        BreedingRuntime.claim(player, jobId);
        throw new AssertionError("Completed delivery reopened");
      } catch (IllegalStateException expected) {
      }
      done = true;
      LoggerFactory.getLogger("ZianUtilities/Breeding")
          .info(
              "Breeding native smoke PASSED: UI codec, admin instant, compatibility, genetics,"
                  + " generation block, parent ownership, PC full, parent lease, nonce replay,"
                  + " persisted PC UUID={}",
              job.child().uuid());
    } catch (Throwable failure) {
      done = true;
      LoggerFactory.getLogger("ZianUtilities/Breeding")
          .error("Breeding native smoke FAILED", failure);
    }
  }
}
