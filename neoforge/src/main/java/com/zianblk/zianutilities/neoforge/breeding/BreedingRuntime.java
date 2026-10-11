package com.zianblk.zianutilities.neoforge.breeding;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.*;
import com.zianblk.zianutilities.core.breeding.*;
import com.zianblk.zianutilities.core.economy.*;
import com.zianblk.zianutilities.core.generation.SpawnDecision;
import com.zianblk.zianutilities.neoforge.breeding.genetics.FrozenOffspring;
import com.zianblk.zianutilities.neoforge.cobblemon.CobblemonGenerationResolver;
import com.zianblk.zianutilities.neoforge.economy.*;
import com.zianblk.zianutilities.neoforge.generation.*;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.*;

public final class BreedingRuntime {
  private static final Logger LOG = LoggerFactory.getLogger("ZianUtilities/Breeding");
  private static final CobblemonGenerationResolver RESOLVER = new CobblemonGenerationResolver(null);
  private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
  static MinecraftServer server;
  static BreedingConfig config;
  static BreedingStore store;
  private static long ticks, lastCheckpoint;
  private static final Set<UUID> recovering = new HashSet<>();
  private static final Map<UUID, Long> verifying = new HashMap<>();

  private BreedingRuntime() {}

  public static void install() {
    NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> start(e.getServer()));
    NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> stop());
    NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick());
    NeoForge.EVENT_BUS.addListener(BreedingCommands::register);
  }

  static void start(MinecraftServer s) {
    server = s;
    store = null;
    recovering.clear();
    verifying.clear();
    ticks = 0;
    try {
      reload();
      store =
          new BreedingStore(
              s.getWorldPath(LevelResource.ROOT)
                  .resolve("data/zianutilities/breeding_v1/journal.wal"));
      for (var j : store.jobs.values())
        if (j.status() == BreedingJob.Status.DELIVERING) recovering.add(j.id());
        else if (j.status() == BreedingJob.Status.ACCELERATING)
          store.jobs.put(j.id(), j.phase(BreedingJob.Status.UNCERTAIN, "PAYMENT", store.now()));
      lastCheckpoint = store.now();
      LOG.info(
          "Breeding ready: enabled={} jobs={}; free start, optional wallet acceleration",
          config.enabled,
          store.jobs.size());
    } catch (Exception e) {
      LOG.error("Breeding unavailable; original journal preserved", e);
    }
  }

  static void stop() {
    if (store != null)
      try {
        store.close();
      } catch (Exception e) {
        LOG.error("Breeding journal close failed", e);
      }
    store = null;
    server = null;
  }

  static void reload() throws Exception {
    var candidate =
        BreedingConfig.load(FMLPaths.CONFIGDIR.get().resolve("zianutilities-breeding.json"));
    config = candidate;
    var probe = AvecoinsContractProbe.inspect();
    if (probe.compatible()) {
      for (String currency : candidate.currencyReductionsMinutes.keySet())
        if (!probe.currencies().contains(currency))
          LOG.warn("Breeding acceleration currency disabled: unknown managed ID {}", currency);
    } else
      LOG.info(
          "Breeding acceleration unavailable: {}; free starts remain independent", probe.detail());
  }

  public static boolean enabled() {
    return store != null && config != null && config.enabled && !store.locked();
  }

  static void check(ServerPlayer p) {
    if (!enabled() || !BreedingPermissions.allows(p, "use", false))
      throw new IllegalStateException("Crianza no disponible o sin permiso");
    if (!server.isSameThread()) throw new IllegalStateException("Server thread required");
  }

  static List<BreedingJob> mine(ServerPlayer p) {
    return store == null
        ? List.of()
        : store.jobs.values().stream().filter(j -> j.owner().equals(p.getUUID())).toList();
  }

  static BreedingJob owned(ServerPlayer p, UUID id) {
    check(p);
    var j = store.jobs.get(id);
    if (j == null || !j.owner().equals(p.getUUID()))
      throw new IllegalArgumentException("Crianza inexistente");
    return j;
  }

  static boolean allowed(Species species) {
    return !(GenerationEnforcement.decide(
            RESOLVER.resolve(species), new NeoForgeGenerationStateStore(server).load())
        instanceof SpawnDecision.Deny);
  }

  static BreedingJob start(ServerPlayer p, UUID one, UUID two, UUID confirmation) throws Exception {
    check(p);
    if (store.jobs.containsKey(confirmation)) return owned(p, confirmation);
    if (mine(p).stream().filter(BreedingJob::occupiesSlot).count()
        >= BreedingPermissions.slots(p, config))
      throw new IllegalStateException("Todas tus ranuras están ocupadas");
    if (store.jobs.values().stream()
        .filter(BreedingJob::occupiesSlot)
        .anyMatch(
            j ->
                j.first().equals(one)
                    || j.second().equals(one)
                    || j.first().equals(two)
                    || j.second().equals(two)))
      throw new IllegalStateException("Un padre ya tiene una crianza activa");
    var party = Cobblemon.INSTANCE.getStorage().getParty(p);
    var first = party.get(one);
    var second = party.get(two);
    if (first == null || second == null || one.equals(two))
      throw new IllegalArgumentException("Elige dos padres distintos de tu equipo");
    var child =
        FrozenOffspring.generate(
            List.of(first, second),
            RANDOM.nextLong(),
            form -> allowed(form.getSpecies()),
            config.alphaChance);
    FrozenOffspring.materialize(child);
    long now = store.now();
    var j =
        new BreedingJob(
            confirmation,
            p.getUUID(),
            one,
            two,
            child,
            now,
            Math.multiplyExact(config.baseMinutes, 60000),
            Math.multiplyExact(config.minimumMinutes, 60000),
            0,
            now,
            BreedingJob.Status.ACTIVE,
            "",
            List.of());
    store.put(j);
    LOG.info("Breeding reserved owner={} job={} child={}", j.owner(), j.id(), j.child().uuid());
    return j;
  }

  static List<String> currencies() {
    var probe = AvecoinsContractProbe.inspect();
    return probe.compatible()
        ? config.currencyReductionsMinutes.keySet().stream()
            .filter(probe.currencies()::contains)
            .sorted()
            .toList()
        : List.of();
  }

  static BreedingRules.Quote quote(BreedingJob j, String currency, long units) {
    if (j.readyAt() <= store.now())
      throw new IllegalStateException("La crianza ya terminó; no necesita aceleración");
    if (!currencies().contains(currency))
      throw new IllegalArgumentException("Moneda desconocida o deshabilitada");
    return BreedingRules.quote(
        j.startedAt(),
        j.baseMillis(),
        j.minimumMillis(),
        j.reductionMillis(),
        store.now(),
        Math.multiplyExact(config.currencyReductionsMinutes.get(currency), 60000),
        units);
  }

  static void accelerate(ServerPlayer p, UUID id, String currency, long units, UUID confirmation)
      throws Exception {
    var j = owned(p, id);
    if (j.status() != BreedingJob.Status.ACTIVE || store.purchaseExists(confirmation))
      throw new IllegalStateException("Compra no disponible o ya procesada");
    var q = quote(j, currency, units);
    var purchase =
        new BreedingJob.Purchase(
            confirmation,
            currency,
            units,
            Math.multiplyExact(config.currencyReductionsMinutes.get(currency), 60000),
            q.reductionMillis(),
            BreedingJob.Payment.PENDING);
    var pending = j.begin(purchase, store.now());
    store.put(pending);
    EconomyMutationResult result;
    try {
      result =
          new AvecoinsEconomyPort()
              .debit(new EconomyOperation(confirmation, p.getUUID(), currency, units));
    } catch (Exception failure) {
      store.put(pending.payment(BreedingJob.Payment.UNCERTAIN, store.now()));
      throw new IllegalStateException("Cobro incierto: necesita revisión; no se repetirá", failure);
    }
    var outcome =
        result instanceof EconomyMutationResult.Applied
            ? BreedingJob.Payment.APPLIED
            : result instanceof EconomyMutationResult.Rejected
                ? BreedingJob.Payment.REJECTED
                : BreedingJob.Payment.UNCERTAIN;
    try {
      store.put(pending.payment(outcome, store.now()));
    } catch (Exception error) {
      store.jobs.put(
          id, pending.phase(BreedingJob.Status.UNCERTAIN, "PAYMENT_CONFIRMATION", store.now()));
      throw error;
    }
    if (outcome != BreedingJob.Payment.APPLIED)
      throw new IllegalStateException(
          outcome == BreedingJob.Payment.UNCERTAIN
              ? "Cobro incierto: necesita revisión; no se repetirá"
              : "Saldo insuficiente o moneda rechazada");
  }

  static void completeNow(UUID id, String operator) throws Exception {
    if (!enabled()) throw new IllegalStateException("Crianza no disponible");
    var job = store.jobs.get(id);
    if (job == null) throw new IllegalArgumentException("Crianza inexistente");
    var ready = job.completeNow(store.now(), operator);
    store.put(ready);
    LOG.info("Admin breeding completion operator={} owner={} job={}", operator, job.owner(), id);
    var player = server.getPlayerList().getPlayer(job.owner());
    if (player != null) deliver(player, ready);
  }

  static void cancel(ServerPlayer p, UUID id) throws Exception {
    var j = owned(p, id);
    if (j.status() == BreedingJob.Status.DELIVERING
        || j.status() == BreedingJob.Status.ACCELERATING
        || j.status() == BreedingJob.Status.UNCERTAIN
        || !j.occupiesSlot())
      throw new IllegalStateException("No se puede cancelar esta operación");
    store.put(j.phase(BreedingJob.Status.CANCELLED, "Sin reembolso de aceleraciones", store.now()));
  }

  static void claim(ServerPlayer p, UUID id) throws Exception {
    var j = owned(p, id);
    if (j.status() != BreedingJob.Status.ACTIVE && j.status() != BreedingJob.Status.BLOCKED)
      throw new IllegalStateException("Entrega no disponible");
    if (store.now() < j.readyAt()) throw new IllegalStateException("Todavía falta tiempo");
    deliver(p, j);
  }

  private static void deliver(ServerPlayer p, BreedingJob j) throws Exception {
    var species =
        PokemonSpecies.getByIdentifier(
            net.minecraft.resources.ResourceLocation.parse(j.child().species()));
    if (species == null || !allowed(species)) {
      store.put(j.phase(BreedingJob.Status.BLOCKED, "SPECIES_OR_GENERATION", store.now()));
      return;
    }
    var h = BreedingStorage.open(p);
    if (BreedingStorage.live(h, j.first()) == null || BreedingStorage.live(h, j.second()) == null) {
      store.put(j.phase(BreedingJob.Status.BLOCKED, "PARENTS_NOT_OWNED", store.now()));
      return;
    }
    if (BreedingStorage.live(h, j.child().uuid()) != null) {
      store.put(j.phase(BreedingJob.Status.DELIVERING, "EXISTING_UUID", store.now()));
      verifying.put(j.id(), store.now());
      return;
    }
    if (h.pc().getFirstAvailablePosition() == null) {
      store.put(j.phase(BreedingJob.Status.BLOCKED, "PC_FULL", store.now()));
      return;
    }
    var child = FrozenOffspring.materialize(j.child());
    child.setOriginalTrainer(p.getUUID());
    store.put(j.phase(BreedingJob.Status.DELIVERING, "", store.now()));
    try {
      if (!h.pc().add(child)) throw new IllegalStateException("PC insertion unconfirmed");
      h.factory().save(h.pc(), p.registryAccess());
      verifying.put(j.id(), store.now());
    } catch (Exception error) {
      store.put(j.phase(BreedingJob.Status.UNCERTAIN, "DELIVERY", store.now()));
      throw error;
    }
  }

  static void verify(ServerPlayer p, BreedingJob j) throws Exception {
    var h = BreedingStorage.open(p);
    var found = BreedingStorage.saved(h, p, j.child().uuid());
    if (found == null) {
      if (recovering.remove(j.id()) || store.now() - verifying.getOrDefault(j.id(), 0L) > 30000) {
        store.put(j.phase(BreedingJob.Status.UNCERTAIN, "DELIVERY_NOT_PROVEN", store.now()));
        verifying.remove(j.id());
      }
      return;
    }
    if (!FrozenOffspring.matches(found, j.child())) {
      store.put(j.phase(BreedingJob.Status.UNCERTAIN, "DELIVERY_MISMATCH", store.now()));
      verifying.remove(j.id());
      recovering.remove(j.id());
      return;
    }
    store.put(j.phase(BreedingJob.Status.DELIVERED, "", store.now()));
    verifying.remove(j.id());
    recovering.remove(j.id());
    try {
      Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(p.getUUID()).obtain(found);
      p.sendSystemMessage(
          Component.literal("§aCrianza completada: tu Pokémon está guardado en el PC."));
    } catch (Exception notificationFailure) {
      LOG.warn(
          "Child delivered; optional Pokedex/chat notification failed job={}",
          j.id(),
          notificationFailure);
    }
  }

  private static void tick() {
    if ("true".equals(System.getenv("ZIANUTILITIES_BREEDING_SMOKE"))) {
      try {
        Class.forName("com.zianblk.zianutilities.neoforge.breeding.BreedingSmoke")
            .getDeclaredMethod("tick")
            .invoke(null);
      } catch (ReflectiveOperationException e) {
        LOG.error("Local smoke fixture unavailable", e);
      }
    }
    if (++ticks % 20 != 0 || !enabled()) return;
    long now = store.now();
    try {
      if (now - lastCheckpoint >= 60000) {
        store.checkpoint();
        lastCheckpoint = now;
      }
      int budget = 2;
      for (var j : new ArrayList<>(store.jobs.values())) {
        var p = server.getPlayerList().getPlayer(j.owner());
        if (p == null) continue;
        if (j.status() == BreedingJob.Status.DELIVERING) {
          try {
            verify(p, j);
          } catch (Exception failure) {
            if (!store.locked())
              store.put(
                  j.phase(BreedingJob.Status.UNCERTAIN, "DELIVERY_VERIFICATION", store.now()));
            verifying.remove(j.id());
            LOG.error("Delivery verification requires review job={}", j.id(), failure);
          }
          continue;
        }
        if (budget > 0 && j.status() == BreedingJob.Status.ACTIVE && now >= j.readyAt()) {
          try {
            deliver(p, j);
          } catch (Exception failure) {
            var current = store.jobs.get(j.id());
            if (!store.locked()
                && (current.status() == BreedingJob.Status.ACTIVE
                    || current.status() == BreedingJob.Status.BLOCKED))
              store.put(
                  current.phase(BreedingJob.Status.BLOCKED, "DELIVERY_PREPARATION", store.now()));
            LOG.error("Delivery paused job={}", j.id(), failure);
          }
          budget--;
        }
      }
    } catch (Exception error) {
      LOG.error("Breeding paused at uncertain boundary", error);
    }
  }

  static String remaining(BreedingJob j) {
    long seconds = Math.max(0, (j.readyAt() - store.now() + 999) / 1000);
    return String.format("%dh %02dm %02ds", seconds / 3600, seconds / 60 % 60, seconds % 60);
  }
}
