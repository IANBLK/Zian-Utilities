package com.zianblk.zianutilities.neoforge.breeding;

import com.cobblemon.mod.common.Cobblemon;
import com.zianblk.zianutilities.core.breeding.*;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;

/** Vanilla container screen, wholly server-authoritative; display slots never transfer items. */
public final class BreedingMenu extends ChestMenu {
  private final ServerPlayer owner;
  private final SimpleContainer display;
  private UUID startNonce = UUID.randomUUID();
  private UUID first, second, job;
  private List<UUID> parents = List.of(), jobs = List.of();
  private String currency;
  private Pending pending;
  private int refreshTicks;

  private record Pending(
      String action, UUID id, String currency, long units, UUID nonce, long rate, long reduction) {}

  private BreedingMenu(int sync, Inventory inv, ServerPlayer p, SimpleContainer items) {
    super(MenuType.GENERIC_9x6, sync, inv, items, 6);
    owner = p;
    display = items;
    render();
  }

  public static void open(ServerPlayer player) {
    try {
      BreedingRuntime.check(player);
      player.openMenu(
          new SimpleMenuProvider(
              (sync, inv, p) -> new BreedingMenu(sync, inv, player, new SimpleContainer(54)),
              Component.literal("✦ Zian Crianza")));
    } catch (Exception e) {
      player.sendSystemMessage(Component.literal("§c" + e.getMessage()));
    }
  }

  private void icon(int slot, Item item, String name, String... lore) {
    var stack = new ItemStack(item);
    stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
    stack.set(
        DataComponents.LORE,
        new ItemLore(Arrays.stream(lore).<Component>map(Component::literal).toList()));
    display.setItem(slot, stack);
  }

  private void render() {
    display.clearContent();
    var party = Cobblemon.INSTANCE.getStorage().getParty(owner);
    var list = new ArrayList<UUID>();
    for (var p : party) {
      if (list.size() == 6) break;
      list.add(p.getUuid());
      int slot = list.size() - 1;
      icon(
          slot,
          Items.PAPER,
          "§b" + p.getDisplayName(false).getString(),
          "§7" + p.getGender().name(),
          "§7Clic para elegir como padre",
          p.getUuid().equals(first)
              ? "§aPrimer padre seleccionado"
              : p.getUuid().equals(second) ? "§aSegundo padre seleccionado" : "§8Disponible");
    }
    parents = List.copyOf(list);
    var active = BreedingRuntime.mine(owner).stream().filter(BreedingJob::occupiesSlot).toList();
    jobs = active.stream().map(BreedingJob::id).toList();
    for (int i = 0; i < active.size() && i < 8; i++) {
      var j = active.get(i);
      String state =
          j.status() == BreedingJob.Status.ACTIVE && j.readyAt() <= BreedingRuntime.store.now()
              ? "LISTA"
              : j.status().name();
      icon(
          9 + i,
          Items.CLOCK,
          "§dCrianza " + j.id().toString().substring(0, 8),
          "§7Estado: " + state,
          "§eTiempo: " + BreedingRuntime.remaining(j),
          "§7Clic para gestionar",
          "§8" + j.reason());
    }
    icon(
        36,
        Items.BOOK,
        "§6Reglas de crianza",
        "§7Inicio gratuito; base " + BreedingRuntime.config.baseMinutes / 60 + " h",
        "§7Mínimo desde inicio: " + BreedingRuntime.config.minimumMinutes / 60 + " h",
        "§7Los padres siguen contigo y se reservan por UUID",
        "§7El hijo se entrega al PC; no hay huevos físicos");
    icon(
        37,
        Items.EMERALD,
        "§aTus ranuras: "
            + active.size()
            + "/"
            + BreedingPermissions.slots(owner, BreedingRuntime.config));
    icon(38, Items.ARROW, "§bCrear nueva crianza");
    if (job != null) {
      var j = BreedingRuntime.owned(owner, job);
      icon(
          39,
          Items.CHEST,
          "§aEntregar hijo al PC",
          "§7Solo cuando termine; requiere espacio en PC");
      var currencies = BreedingRuntime.currencies();
      if (currency == null || !currencies.contains(currency))
        currency = currencies.isEmpty() ? null : currencies.getFirst();
      icon(
          40,
          Items.GOLD_INGOT,
          "§6Moneda: " + (currency == null ? "No disponible" : currency),
          "§7Clic para cambiar denominación de AVECOINS");
      icon(41, Items.REDSTONE, "§eAcelerar con 1 unidad", "§7Preparar compra; confirmar después");
      icon(
          42,
          Items.DIAMOND,
          "§eAcelerar hasta el límite útil",
          "§7Calcula solo las unidades necesarias",
          "§7La última unidad puede cubrir tiempo parcial");
      icon(
          46,
          Items.BARRIER,
          "§cCancelar crianza",
          "§7No devuelve monedas de aceleraciones",
          "§7Las operaciones inciertas no se pueden cancelar");
    }
    if (pending != null) {
      String detail =
          pending.action().equals("start")
              ? "Inicio gratuito"
              : pending.action().equals("cancel")
                  ? "Sin reembolso"
                  : pending.units() + " × " + pending.currency();
      icon(
          51,
          Items.LIME_DYE,
          "§aCONFIRMAR " + detail,
          "§7La confirmación se procesa una sola vez",
          pending.action().equals("accelerate")
              ? "§eReducción: "
                  + pending.reduction() / 60000
                  + " min (puede disminuir al confirmar)"
              : "§7Sin coste de inicio");
    } else if (first != null && second != null && job == null)
      icon(
          51,
          Items.LIME_DYE,
          "§aPreparar crianza gratuita",
          "§7Clic y confirma para reservar ambos padres");
    icon(53, Items.RED_DYE, "§cCerrar", "§7Cerrar no cancela crianzas ni recompensas");
    super.broadcastChanges();
  }

  @Override
  public void clicked(int slot, int button, ClickType type, Player player) {
    if (player != owner
        || owner.containerMenu != this
        || type != ClickType.PICKUP
        || button != 0
        || slot < 0
        || slot >= 54) return;
    try {
      BreedingRuntime.check(owner);
      if (slot < parents.size()) {
        UUID id = parents.get(slot);
        if (id.equals(first)) first = null;
        else if (id.equals(second)) second = null;
        else if (first == null) first = id;
        else second = id;
        job = null;
        pending = null;
      } else if (slot >= 9 && slot < 9 + jobs.size()) {
        job = jobs.get(slot - 9);
        pending = null;
      } else if (slot == 38) {
        job = null;
        first = second = null;
        pending = null;
      } else if (slot == 39 && job != null) {
        BreedingRuntime.claim(owner, job);
        pending = null;
      } else if (slot == 40 && job != null) {
        var cs = BreedingRuntime.currencies();
        if (!cs.isEmpty()) currency = cs.get((cs.indexOf(currency) + 1) % cs.size());
        pending = null;
      } else if ((slot == 41 || slot == 42) && job != null && currency != null) {
        var j = BreedingRuntime.owned(owner, job);
        long units = 1;
        if (slot == 42) {
          long rate =
              Math.multiplyExact(
                  BreedingRuntime.config.currencyReductionsMinutes.get(currency), 60000);
          long useful =
              Math.max(
                  0,
                  j.readyAt()
                      - Math.max(BreedingRuntime.store.now(), j.startedAt() + j.minimumMillis()));
          units = useful / rate + (useful % rate == 0 ? 0 : 1);
        }
        var quote = BreedingRuntime.quote(j, currency, units);
        pending =
            new Pending(
                "accelerate",
                job,
                currency,
                quote.units(),
                UUID.randomUUID(),
                Math.multiplyExact(
                    BreedingRuntime.config.currencyReductionsMinutes.get(currency), 60000),
                quote.reductionMillis());
      } else if (slot == 46 && job != null)
        pending = new Pending("cancel", job, null, 0, UUID.randomUUID(), 0, 0);
      else if (slot == 51) {
        if (pending == null) {
          if (first != null && second != null && job == null)
            pending = new Pending("start", null, null, 0, startNonce, 0, 0);
        } else {
          var p = pending;
          pending = null;
          if (p.action().equals("start")) {
            BreedingRuntime.start(owner, first, second, p.nonce());
            first = second = null;
            startNonce = UUID.randomUUID();
          } else if (p.action().equals("accelerate")) {
            if (!Objects.equals(
                BreedingRuntime.config.currencyReductionsMinutes.get(p.currency()),
                p.rate() / 60000))
              throw new IllegalStateException("La tarifa cambió; vuelve a preparar la compra");
            BreedingRuntime.accelerate(owner, p.id(), p.currency(), p.units(), p.nonce());
          } else BreedingRuntime.cancel(owner, p.id());
        }
      } else if (slot == 53) {
        owner.closeContainer();
        return;
      }
      render();
    } catch (Exception e) {
      pending = null;
      owner.sendSystemMessage(Component.literal("§c" + e.getMessage()));
      try {
        render();
      } catch (Exception ignored) {
        owner.closeContainer();
      }
    }
  }

  @Override
  public void broadcastChanges() {
    if (display != null && ++refreshTicks % 20 == 0) {
      try {
        render();
      } catch (Exception e) {
        owner.closeContainer();
      }
    } else super.broadcastChanges();
  }

  @Override
  public ItemStack quickMoveStack(Player p, int slot) {
    return ItemStack.EMPTY;
  }

  @Override
  public boolean stillValid(Player p) {
    return p == owner && BreedingRuntime.enabled();
  }
}
