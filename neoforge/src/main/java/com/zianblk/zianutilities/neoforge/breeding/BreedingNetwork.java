package com.zianblk.zianutilities.neoforge.breeding;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.item.PokemonItem;
import com.zianblk.zianutilities.core.breeding.*;
import com.zianblk.zianutilities.core.economy.EconomyBalanceResult;
import com.zianblk.zianutilities.neoforge.ZianUtilitiesMod;
import com.zianblk.zianutilities.neoforge.economy.AvecoinsEconomyPort;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Server-owned selections, quotes and confirmations. Clients send only session-bound actions. */
public final class BreedingNetwork {
  private static final Map<UUID, Session> sessions = new HashMap<>();

  private static final class Session {
    final UUID token = UUID.randomUUID();
    UUID first, second, job;
    String currency;
    Pending pending;
    long seen, lastPoll;
    String message = "";
  }

  private record Pending(
      String kind, UUID nonce, UUID job, String currency, long units, long rate, long cut) {}

  public record Parent(
      String id,
      ItemStack icon,
      String name,
      int level,
      String sex,
      String nature,
      String ability,
      String groups,
      List<Integer> stats,
      List<Integer> ivs,
      String reason) {}

  public record Job(String id, String status, long readyAt, String reason) {}

  public record Currency(String id, long minutes, long balance) {}

  public record State(
      boolean open,
      String session,
      List<Parent> parents,
      List<Job> jobs,
      List<Currency> currencies,
      String first,
      String second,
      String job,
      String currency,
      String compatibility,
      boolean compatible,
      int used,
      int capacity,
      long baseMinutes,
      long minimumMinutes,
      String pending,
      String confirmation,
      String message,
      long serverNow)
      implements CustomPacketPayload {
    public static final Type<State> TYPE =
        new Type<>(
            ResourceLocation.fromNamespaceAndPath(ZianUtilitiesMod.MOD_ID, "breeding_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC =
        StreamCodec.of(
            (b, s) -> {
              b.writeBoolean(s.open);
              text(b, s.session, 36);
              b.writeVarInt(s.parents.size());
              for (var p : s.parents) {
                text(b, p.id, 36);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(b, p.icon);
                text(b, p.name, 128);
                b.writeVarInt(p.level);
                text(b, p.sex, 16);
                text(b, p.nature, 128);
                text(b, p.ability, 128);
                text(b, p.groups, 256);
                for (int n : p.stats) b.writeVarInt(n);
                for (int n : p.ivs) b.writeVarInt(n);
                text(b, p.reason, 512);
              }
              b.writeVarInt(s.jobs.size());
              for (var j : s.jobs) {
                text(b, j.id, 36);
                text(b, j.status, 24);
                b.writeLong(j.readyAt);
                text(b, j.reason, 512);
              }
              b.writeVarInt(s.currencies.size());
              for (var c : s.currencies) {
                text(b, c.id, 128);
                b.writeLong(c.minutes);
                b.writeLong(c.balance);
              }
              for (String v : List.of(s.first, s.second, s.job, s.currency)) text(b, v, 128);
              text(b, s.compatibility, 512);
              b.writeBoolean(s.compatible);
              b.writeVarInt(s.used);
              b.writeVarInt(s.capacity);
              b.writeLong(s.baseMinutes);
              b.writeLong(s.minimumMinutes);
              text(b, s.pending, 24);
              text(b, s.confirmation, 512);
              text(b, s.message, 512);
              b.writeLong(s.serverNow);
            },
            b -> {
              boolean open = b.readBoolean();
              String session = b.readUtf(36);
              var parents = new ArrayList<Parent>();
              int count = bounded(b.readVarInt(), 6);
              for (int i = 0; i < count; i++) {
                String id = b.readUtf(36);
                var icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(b);
                String name = b.readUtf(128);
                int level = b.readVarInt();
                String sex = b.readUtf(16),
                    nature = b.readUtf(128),
                    ability = b.readUtf(128),
                    groups = b.readUtf(256);
                var stats = new ArrayList<Integer>();
                var ivs = new ArrayList<Integer>();
                for (int n = 0; n < 6; n++) stats.add(b.readVarInt());
                for (int n = 0; n < 6; n++) ivs.add(b.readVarInt());
                parents.add(
                    new Parent(
                        id,
                        icon,
                        name,
                        level,
                        sex,
                        nature,
                        ability,
                        groups,
                        stats,
                        ivs,
                        b.readUtf(512)));
              }
              var jobs = new ArrayList<Job>();
              count = bounded(b.readVarInt(), 8);
              for (int i = 0; i < count; i++)
                jobs.add(new Job(b.readUtf(36), b.readUtf(24), b.readLong(), b.readUtf(512)));
              var currencies = new ArrayList<Currency>();
              count = bounded(b.readVarInt(), 128);
              for (int i = 0; i < count; i++)
                currencies.add(new Currency(b.readUtf(128), b.readLong(), b.readLong()));
              return new State(
                  open,
                  session,
                  List.copyOf(parents),
                  List.copyOf(jobs),
                  List.copyOf(currencies),
                  b.readUtf(128),
                  b.readUtf(128),
                  b.readUtf(128),
                  b.readUtf(128),
                  b.readUtf(512),
                  b.readBoolean(),
                  b.readVarInt(),
                  b.readVarInt(),
                  b.readLong(),
                  b.readLong(),
                  b.readUtf(24),
                  b.readUtf(512),
                  b.readUtf(512),
                  b.readLong());
            });

    public Type<? extends CustomPacketPayload> type() {
      return TYPE;
    }
  }

  public record Action(String session, byte kind, String target, boolean maximum)
      implements CustomPacketPayload {
    public static final Type<Action> TYPE =
        new Type<>(
            ResourceLocation.fromNamespaceAndPath(ZianUtilitiesMod.MOD_ID, "breeding_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC =
        StreamCodec.of(
            (b, a) -> {
              text(b, a.session, 36);
              b.writeByte(a.kind);
              text(b, a.target, 128);
              b.writeBoolean(a.maximum);
            },
            b -> new Action(b.readUtf(36), b.readByte(), b.readUtf(128), b.readBoolean()));

    public Type<? extends CustomPacketPayload> type() {
      return TYPE;
    }
  }

  private static int bounded(int n, int max) {
    if (n < 0 || n > max) throw new IllegalArgumentException("Invalid breeding packet length");
    return n;
  }

  private static void text(RegistryFriendlyByteBuf b, String s, int max) {
    b.writeUtf(trim(s, max), max);
  }

  private static String trim(String s, int max) {
    return s.length() > max ? s.substring(0, max) : s;
  }

  private static String id(UUID id) {
    return id == null ? "" : id.toString();
  }

  public static void register(RegisterPayloadHandlersEvent event) {
    var r =
        event
            .registrar("breeding-ui-1")
            .executesOn(net.neoforged.neoforge.network.registration.HandlerThread.MAIN);
    r.playToClient(
        State.TYPE,
        State.CODEC,
        (state, ctx) ->
            com.zianblk.zianutilities.neoforge.breeding.client.BreedingScreen.receive(state));
    r.playToServer(Action.TYPE, Action.CODEC, BreedingNetwork::handle);
  }

  public static void open(ServerPlayer p) {
    try {
      BreedingRuntime.check(p);
      if (!NetworkRegistry.hasChannel(p.connection, State.TYPE.id()))
        throw new IllegalStateException("Instala Zian Utilities beta.11 también en el cliente");
      sessions
          .entrySet()
          .removeIf(e -> BreedingRuntime.server.getPlayerList().getPlayer(e.getKey()) == null);
      var s = new Session();
      s.seen = BreedingRuntime.store.now();
      sessions.put(p.getUUID(), s);
      p.closeContainer();
      send(p, s, true);
    } catch (Exception e) {
      p.sendSystemMessage(Component.literal(e.getMessage()));
    }
  }

  private static void handle(Action a, IPayloadContext ctx) {
    if (!(ctx.player() instanceof ServerPlayer p)) return;
    try {
      BreedingRuntime.check(p);
      var s = sessions.get(p.getUUID());
      long now = BreedingRuntime.store.now();
      if (s == null || !s.token.toString().equals(a.session) || now - s.seen > 120000)
        throw new IllegalStateException("La sesión venció; abre de nuevo Crianza");
      s.seen = now;
      if (a.kind == 0) {
        if (now - s.lastPoll < 1000) return;
        s.lastPoll = now;
        send(p, s, false);
        return;
      }
      s.message = "";
      var party = Cobblemon.INSTANCE.getStorage().getParty(p);
      switch (a.kind) {
        case 1 -> {
          UUID selected = UUID.fromString(a.target);
          var pokemon = party.get(selected);
          if (pokemon == null)
            throw new IllegalArgumentException("Este Pokémon ya no está en tu equipo");
          s.pending = null;
          s.job = null;
          if (selected.equals(s.first)) s.first = null;
          else if (selected.equals(s.second)) s.second = null;
          else if (s.first == null) s.first = selected;
          else s.second = selected;
        }
        case 2 -> {
          s.first = s.second = s.job = null;
          s.pending = null;
        }
        case 3 -> {
          var why =
              BreedingCompatibility.pair(
                  s.first == null ? null : party.get(s.first),
                  s.second == null ? null : party.get(s.second));
          if (!why.isEmpty()) throw new IllegalStateException(why);
          if (BreedingRuntime.mine(p).stream().filter(BreedingJob::occupiesSlot).count()
              >= BreedingPermissions.slots(p, BreedingRuntime.config))
            throw new IllegalStateException("Todas tus ranuras están ocupadas");
          s.pending = new Pending("start", UUID.randomUUID(), null, null, 0, 0, 0);
        }
        case 4 -> {
          s.job = UUID.fromString(a.target);
          BreedingRuntime.owned(p, s.job);
          s.pending = null;
        }
        case 5 -> {
          if (!BreedingRuntime.currencies().contains(a.target))
            throw new IllegalArgumentException("Moneda deshabilitada");
          s.currency = a.target;
          s.pending = null;
        }
        case 6 -> {
          if (s.job == null || s.currency == null)
            throw new IllegalStateException("Selecciona una crianza y una moneda");
          var j = BreedingRuntime.owned(p, s.job);
          long rate =
              Math.multiplyExact(
                  BreedingRuntime.config.currencyReductionsMinutes.get(s.currency), 60000);
          long useful = Math.max(0, j.readyAt() - Math.max(now, j.startedAt() + j.minimumMillis()));
          long units = a.maximum ? useful / rate + (useful % rate == 0 ? 0 : 1) : 1;
          var q = BreedingRuntime.quote(j, s.currency, units);
          s.pending =
              new Pending(
                  "accelerate",
                  UUID.randomUUID(),
                  j.id(),
                  s.currency,
                  units,
                  rate,
                  q.reductionMillis());
        }
        case 7 -> {
          BreedingRuntime.claim(p, s.job);
          s.pending = null;
          s.message = "Entrega solicitada; se comprobará el PC guardado";
        }
        case 8 -> {
          BreedingRuntime.owned(p, s.job);
          s.pending = new Pending("cancel", UUID.randomUUID(), s.job, null, 0, 0, 0);
        }
        case 9 -> {
          var pending = s.pending;
          if (pending == null) return;
          s.pending = null;
          switch (pending.kind) {
            case "start" -> {
              BreedingRuntime.start(p, s.first, s.second, pending.nonce);
              s.first = s.second = null;
              s.message = "Crianza iniciada. Encontrarás el progreso en Mis crianzas";
            }
            case "accelerate" -> {
              if (!Objects.equals(
                  BreedingRuntime.config.currencyReductionsMinutes.get(pending.currency),
                  pending.rate / 60000))
                throw new IllegalStateException("La tarifa cambió; prepara de nuevo la compra");
              BreedingRuntime.accelerate(
                  p, pending.job, pending.currency, pending.units, pending.nonce);
              s.message = "Aceleración aplicada";
            }
            case "cancel" -> {
              BreedingRuntime.cancel(p, pending.job);
              s.job = null;
              s.message = "Crianza cancelada; los padres quedaron libres";
            }
            default -> throw new IllegalStateException("Confirmación inválida");
          }
        }
        case 10 -> {
          s.pending = null;
        }
        case 11 -> {
          sessions.remove(p.getUUID());
          return;
        }
        default -> throw new IllegalArgumentException("Acción inválida");
      }
      send(p, s, false);
    } catch (Exception error) {
      var s = sessions.get(p.getUUID());
      if (s != null) {
        s.pending = null;
        s.message =
            error.getMessage() == null ? "No se pudo completar la acción" : error.getMessage();
        try {
          send(p, s, false);
        } catch (Exception ignored) {
          p.sendSystemMessage(Component.literal(s.message));
        }
      } else p.sendSystemMessage(Component.literal("Abre nuevamente Crianza"));
    }
  }

  static State view(ServerPlayer p, Session s, boolean open) {
    var party = Cobblemon.INSTANCE.getStorage().getParty(p);
    if (s.first != null && party.get(s.first) == null) {
      s.first = null;
      s.pending = null;
      s.message = "El primer Pok�mon sali� del equipo; vuelve a elegirlo";
    }
    if (s.second != null && party.get(s.second) == null) {
      s.second = null;
      s.pending = null;
      s.message = "El segundo Pok�mon sali� del equipo; vuelve a elegirlo";
    }
    var parents = new ArrayList<Parent>();
    var order =
        List.of(
            Stats.HP,
            Stats.ATTACK,
            Stats.DEFENCE,
            Stats.SPECIAL_ATTACK,
            Stats.SPECIAL_DEFENCE,
            Stats.SPEED);
    for (var pokemon : party) {
      String why = BreedingCompatibility.individual(pokemon);
      if (why.isEmpty()
          && s.first != null
          && !pokemon.getUuid().equals(s.first)
          && s.second == null) {
        why = BreedingCompatibility.pair(party.get(s.first), pokemon);
      }
      parents.add(
          new Parent(
              pokemon.getUuid().toString(),
              PokemonItem.Companion.from(pokemon),
              trim(pokemon.getDisplayName(false).getString(), 128),
              pokemon.getLevel(),
              pokemon.getGender().name(),
              pokemon.getNature().getName().getPath(),
              pokemon.getAbility().getName(),
              pokemon.getForm().getEggGroups().toString(),
              order.stream().map(pokemon::getStat).toList(),
              order.stream()
                  .map(stat -> pokemon.getIvs().get(stat) == null ? 0 : pokemon.getIvs().get(stat))
                  .toList(),
              why));
    }
    var jobs =
        BreedingRuntime.mine(p).stream()
            .filter(BreedingJob::occupiesSlot)
            .map(j -> new Job(j.id().toString(), j.status().name(), j.readyAt(), j.reason()))
            .toList();
    if (s.job != null && jobs.stream().noneMatch(j -> j.id.equals(s.job.toString()))) {
      s.job = null;
      s.pending = null;
    }
    var currencies = new ArrayList<Currency>();
    for (String currency : BreedingRuntime.currencies()) {
      long balance = -1;
      try {
        var result = new AvecoinsEconomyPort().balance(p.getUUID(), currency);
        if (result instanceof EconomyBalanceResult.Available value) balance = value.getAmount();
      } catch (Exception ignored) {
      }
      currencies.add(
          new Currency(
              currency, BreedingRuntime.config.currencyReductionsMinutes.get(currency), balance));
    }
    if (s.currency == null || currencies.stream().noneMatch(c -> c.id.equals(s.currency)))
      s.currency = currencies.isEmpty() ? null : currencies.getFirst().id;
    String why =
        BreedingCompatibility.pair(
            s.first == null ? null : party.get(s.first),
            s.second == null ? null : party.get(s.second));
    String confirmation =
        s.pending == null
            ? ""
            : switch (s.pending.kind) {
              case "start" ->
                  "Inicio gratuito · " + BreedingRuntime.config.baseMinutes / 60 + " horas";
              case "cancel" -> "Cancelar sin devolver aceleraciones";
              default ->
                  s.pending.units
                      + " × "
                      + s.pending.currency
                      + " · reduce hasta "
                      + duration(s.pending.cut);
            };
    return new State(
        open,
        s.token.toString(),
        List.copyOf(parents),
        jobs,
        List.copyOf(currencies),
        id(s.first),
        id(s.second),
        id(s.job),
        s.currency == null ? "" : s.currency,
        why,
        why.isEmpty(),
        jobs.size(),
        BreedingPermissions.slots(p, BreedingRuntime.config),
        BreedingRuntime.config.baseMinutes,
        BreedingRuntime.config.minimumMinutes,
        s.pending == null ? "" : s.pending.kind,
        confirmation,
        s.message,
        BreedingRuntime.store.now());
  }

  private static String duration(long millis) {
    long seconds = Math.max(0, (millis + 999) / 1000);
    return String.format("%dh %02dm %02ds", seconds / 3600, seconds / 60 % 60, seconds % 60);
  }

  static State preview(ServerPlayer player) {
    return view(player, new Session(), false);
  }

  private static void send(ServerPlayer p, Session s, boolean open) {
    PacketDistributor.sendToPlayer(p, view(p, s, open));
  }

  public static void action(State state, int kind, String target, boolean maximum) {
    PacketDistributor.sendToServer(new Action(state.session, (byte) kind, target, maximum));
  }
}
