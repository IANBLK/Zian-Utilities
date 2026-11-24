package com.zianblk.zianutilities.neoforge.breeding.client;

import com.zianblk.zianutilities.neoforge.Tr;
import com.zianblk.zianutilities.neoforge.breeding.BreedingNetwork;
import com.zianblk.zianutilities.neoforge.hub.ZianHubNetwork;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Same palette/cards/buttons as the quest and gacha panels; no inventory or fabricated items. */
public final class BreedingScreen
    extends com.zianblk.zianutilities.neoforge.client.ResponsiveScreen {
  private static final int GOLD = 0xFFF0C75E,
      WHITE = 0xFFF4F7FA,
      MUTED = 0xFF9FAAB5,
      GREEN = 0xFF78D498,
      RED = 0xFFF19191;
  private BreedingNetwork.State state;
  private boolean jobs;
  private int jobIndex, currencyIndex, ticks, picker, childIndex;
  private boolean childHovered;
  private long received = System.nanoTime();
  private String hovered = "";

  private BreedingScreen(BreedingNetwork.State state) {
    super(Component.translatableWithFallback("ui.zianutilities.d5dbf7a2c5f1", "Zian Utilities | Crianza"));
    this.state = state;
  }

  public static void receive(BreedingNetwork.State state) {
    Minecraft mc = Minecraft.getInstance();
    mc.execute(
        () -> {
          if (state.open()) mc.setScreen(new BreedingScreen(state));
          else if (mc.screen instanceof BreedingScreen old) {
            old.state = state;
            old.received = System.nanoTime();
            old.jobIndex = Math.max(0, Math.min(old.jobIndex, state.jobs().size() - 1));
            for (int i = 0; i < state.jobs().size(); i++)
              if (state.jobs().get(i).id().equals(state.job())) old.jobIndex = i;
            old.rebuildWidgets();
          }
        });
  }

  private int left() {
    return (width - contentWidth()) / 2;
  }

  private int contentWidth() {
    return Math.min(width - 20, 420);
  }

  private int panelHeight() { return jobs && state.pending().isEmpty() ? 306 : 270; }

  private int panelTop() { return Math.max(65, (height - panelHeight()) / 2); }

  private void act(int kind, String target, boolean maximum) {
    BreedingNetwork.action(state, kind, target, maximum);
  }

  private void button(int x, int y, int w, String label, Runnable action, boolean enabled) {
    var b = addRenderableWidget(new StyledButton(x, y, w, 19, label, action));
    b.active = enabled;
  }

  private BreedingNetwork.Job job() {
    return state.jobs().stream().filter(j -> j.id().equals(state.job())).findFirst().orElse(null);
  }

  private BreedingNetwork.Currency currency() {
    return state.currencies().stream()
        .filter(c -> c.id().equals(state.currency()))
        .findFirst()
        .orElse(null);
  }

  @Override
  protected void init() {
    fitViewport(440, 410);
    int x = left(), w = contentWidth();
    button(
        10,
        9,
        55,
        Tr.s("ui.zianutilities.4f70d8c37e6a", "Inicio"),
        () -> {
          act(11, "", false);
          ZianHubNetwork.requestHub();
        },
        true);
    button(width - 65, 9, 55, Tr.s("ui.zianutilities.4b0816bbd5e9", "Cerrar"), this::onClose, true);
    if (!state.pending().isEmpty()) {
      int y = panelTop();
      if (state.pending().equals("start")) {
        var father = addRenderableWidget(new ParentSlot(x + 12, y + 55, (w - 36) / 2, 64, 1));
        var mother = addRenderableWidget(new ParentSlot(x + w / 2 + 6, y + 55, (w - 36) / 2, 64, 2));
        father.active = false;
        mother.active = false;
        addRenderableWidget(new OffspringButton(x + (w - 250) / 2, y + 125, 250, 80));
      }
      button(x + 12, y + 242, (w - 36) / 2, Tr.s("zianutilities.breeding.confirm", "Confirmar"), () -> act(9, "", false), true);
      button(x + w / 2 + 6, y + 242, (w - 36) / 2, Tr.s("zianutilities.common.back", "Volver"), () -> act(10, "", false), true);
      return;
    }
    int tabWidth = Math.min(170, (w - 12) / 2);
    int tabLeft = width / 2 - tabWidth - 6;
    button(
        tabLeft,
        39,
        tabWidth,
        Tr.s("zianutilities.breeding.pick_parents", "Elegir padres"),
        () -> {
          jobs = false;
          picker = 0;
          rebuildWidgets();
        },
        true);
    button(
        width / 2 + 6,
        39,
        tabWidth,
        Tr.s("zianutilities.breeding.my_jobs", "Mis crianzas (%s/%s)", state.used(), state.capacity()),
        () -> {
          jobs = true;
          picker = 0;
          rebuildWidgets();
        },
        true);
    if (!jobs) {
      int gap = 6, ch = cardHeight();
      if (picker != 0) {
        int cw = (w - 2 * gap) / 3;
        for (int i = 0; i < state.parents().size(); i++) {
          var p = state.parents().get(i);
          final String id = p.id();
          final int slot = picker;
          addRenderableWidget(
              new PokemonButton(
                  x + (i % 3) * (cw + gap),
                  79 + (i / 3) * (ch + gap),
                  cw,
                  ch,
                  p,
                  () -> {
                    picker = 0;
                    act(slot == 1 ? 12 : 13, id, false);
                    rebuildWidgets();
                  }));
        }
        button(
            x,
            height - 29,
            w,
            Tr.s("zianutilities.breeding.back_to_pair", "Volver a la pareja"),
            () -> {
              picker = 0;
              rebuildWidgets();
            },
            true);
        return;
      }
      int cw = tabWidth;
      addRenderableWidget(new ParentSlot(tabLeft, 65, cw, ch, 1));
      addRenderableWidget(new ParentSlot(width / 2 + 6, 65, cw, ch, 2));
      int childWidth = Math.min(w, 190);
      addRenderableWidget(
          new OffspringButton((width - childWidth) / 2, 65 + ch + 12, childWidth, ch));
      button(
          tabLeft,
          height - 29,
          tabWidth,
          Tr.s("zianutilities.breeding.clear", "Limpiar selección"),
          () -> act(2, "", false),
          !state.first().isEmpty() || !state.second().isEmpty());
      button(
          width / 2 + 6,
          height - 29,
          tabWidth,
          Tr.s("zianutilities.breeding.start_free", "Iniciar gratis"),
          () -> act(3, "", false),
          state.compatible() && state.used() < state.capacity());
    } else if (!state.jobs().isEmpty()) {
      int top = panelTop();
      button(x + 12, top + 10, 28, "<", () -> selectJob(-1), state.jobs().size() > 1);
      button(x + w - 40, top + 10, 28, ">", () -> selectJob(1), state.jobs().size() > 1);
      var j = job();
      if (j == null) {
        act(4, state.jobs().get(Math.min(jobIndex, state.jobs().size() - 1)).id(), false);
        return;
      }
      int y = top + 160;
      var currency = currency();
      if (currency != null) {
        button(
            x + 12,
            y,
            w - 24,
            Tr.s("zianutilities.breeding.speed_with", "Acelerar con %s", friendly(currency.id())),
            this::nextCurrency,
            true);
      }
      boolean canBuy = j.status().equals("ACTIVE") && j.readyAt() > now() && currency != null && currency.balance() != 0;
      button(x + 12, y + 50, (w - 36) / 2, Tr.s("zianutilities.breeding.speed_one", "Acelerar 1 unidad"), () -> act(6, "", false), canBuy);
      button(x + w / 2 + 6, y + 50, (w - 36) / 2, Tr.s("zianutilities.breeding.speed_min", "Hasta el mínimo"), () -> act(6, "", true), canBuy);
      boolean claim =
          (j.status().equals("ACTIVE") || j.status().equals("BLOCKED")) && j.readyAt() <= now();
      button(x + 12, top + 278, (w - 36) / 2, Tr.s("zianutilities.breeding.deliver", "Entregar al PC"), () -> act(7, "", false), claim);
      button(
          x + w / 2 + 6,
          top + 278,
          (w - 36) / 2,
          Tr.s("zianutilities.breeding.cancel", "Cancelar crianza"),
          () -> act(8, "", false),
          j.status().equals("ACTIVE") || j.status().equals("BLOCKED"));
    }
  }

  private int cardHeight() {
    return Math.max(44, Math.min(80, (height - 150) / 2));
  }

  private long now() {
    return state.serverNow() + Math.max(0, (System.nanoTime() - received) / 1000000);
  }

  private void selectJob(int delta) {
    jobIndex = Math.floorMod(jobIndex + delta, state.jobs().size());
    act(4, state.jobs().get(jobIndex).id(), false);
  }

  private void nextCurrency() {
    if(state.currencies().isEmpty())return;
    minecraft.setScreen(new BreedingCurrencyScreen(this,state.currencies(),id->act(5,id,false)));
  }

  @Override
  public void tick() {
    super.tick();
    if (++ticks % 40 == 0) act(0, "", false);
  }

  @Override
  public void onClose() {
    act(11, "", false);
    if(GuiReturn.returnToMenu())return;
    super.onClose();
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void renderBackground(GuiGraphics g, int mx, int my, float partial) {}

  @Override
  protected void renderCanvas(GuiGraphics g, int mx, int my, float partial) {
    g.fill(0, 0, width, height, 0xE00D1117);
    g.fill(0, 0, width, 4, GOLD);
    g.drawCenteredString(font, title, width / 2, 12, GOLD);
    hovered = "";
    childHovered = false;
    int x = left(), w = contentWidth();
    if (jobs && state.pending().isEmpty()) card(g, x, panelTop(), w, panelHeight(), GOLD);
    if (!state.pending().isEmpty()) {
      int top = panelTop();
      card(g, x, top, w, 270, GOLD);
      g.drawCenteredString(
          font,
          Component.literal(
              switch (state.pending()) {
                case "start" -> Tr.s("zianutilities.breeding.confirm_start", "Confirmar crianza gratuita");
                case "cancel" -> Tr.s("zianutilities.breeding.confirm_cancel", "Confirmar cancelación");
                default -> Tr.s("zianutilities.breeding.confirm_speed", "Confirmar aceleración");
              }),
          width / 2,
          top + 14,
          GOLD);
      wrapCentered(g, state.confirmation(), x + 12, top + 34, w - 24, WHITE, state.pending().equals("start") ? 1 : 5);
      if (!state.pending().equals("start")) {
        pokemonIcon(g, new net.minecraft.world.item.ItemStack(state.pending().equals("cancel")
            ? net.minecraft.world.item.Items.BARRIER : net.minecraft.world.item.Items.CLOCK), width / 2 - 22, top + 105, 44);
      }
      wrap(
          g,
          state.pending().equals("accelerate")
              ? Tr.s("zianutilities.breeding.note_accelerate", "Se cobra la cantidad completa. La última unidad puede cubrir tiempo parcial; no hay devolución parcial.")
              : state.pending().equals("cancel")
                  ? Tr.s("zianutilities.breeding.note_cancel", "Los padres quedarán libres. No se devuelven monedas de aceleraciones.")
                  : Tr.s("zianutilities.breeding.note_start", "Los padres seguirán contigo. La cría se entregará al PC al finalizar."),
          x + 12,
          top + 214,
          w - 24,
          MUTED,
          2);
    } else if (!jobs) {
      if (picker != 0) {
        g.drawCenteredString(
            font,
            picker == 1 ? Tr.s("zianutilities.breeding.pick_father", "Elige el padre de tu equipo") : Tr.s("zianutilities.breeding.pick_mother", "Elige la madre de tu equipo"),
            width / 2,
            65,
            GOLD);
        if (state.parents().isEmpty()) wrap(g, Tr.s("zianutilities.breeding.team_empty", "Tu equipo está vacío"), x, 90, w, MUTED, 2);
      } else {
        int y = 65 + 2 * cardHeight() + 18;
        wrapCentered(
            g,
            !state.message().isEmpty()
                ? state.message()
                : state.compatible()
                    ? Tr.s("zianutilities.breeding.compatible_summary", "Pareja compatible · Gratis · %s h (mínimo %s h)", state.baseMinutes() / 60, state.minimumMinutes() / 60)
                    : state.compatibility(),
            width / 2 - Math.min(170, (w - 12) / 2) - 6,
            y,
            2 * Math.min(170, (w - 12) / 2) + 12,
            state.compatible() ? GREEN : MUTED,
            2);
      }

    } else if (state.jobs().isEmpty())
      wrap(
          g,
          Tr.s("zianutilities.breeding.no_jobs", "Todavía no tienes crianzas pendientes. Elige dos padres para iniciar una."),
          x + 12,
          panelTop() + 40,
          w - 24,
          MUTED,
          3);
    else {
      var j = job();
      if (j != null) {
        int top = panelTop();
        g.drawCenteredString(
            font,
            Component.literal(Tr.s("zianutilities.breeding.job_n", "Crianza %s de %s", jobIndex + 1, state.jobs().size())),
            width / 2,
            top + 15,
            WHITE);
        String label =
            switch (j.status()) {
              case "ACTIVE" -> j.readyAt() <= now() ? Tr.s("zianutilities.breeding.ready", "Lista para entregar") : Tr.s("zianutilities.breeding.in_progress", "En progreso");
              case "BLOCKED" -> Tr.s("zianutilities.breeding.delivery_pending", "Entrega pendiente");
              case "UNCERTAIN" -> Tr.s("zianutilities.breeding.needs_review", "Necesita revisión administrativa");
              case "DELIVERING" -> Tr.s("zianutilities.breeding.checking_pc", "Comprobando guardado del PC");
              default -> Tr.s("zianutilities.breeding.checking_payment", "Comprobando cobro");
            };
        g.drawCenteredString(
            font,
            Component.literal(label),
            width / 2,
            top + 42,
            j.status().equals("UNCERTAIN") ? RED : GOLD);
        pokemonIcon(g, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EGG), width / 2 - 22, top + 60, 44);
        g.drawCenteredString(font, remaining(j.readyAt() - now()), width / 2, top + 110, WHITE);
        String why =
            switch (j.reason()) {
              case "PC_FULL" -> Tr.s("zianutilities.breeding.why_pc_full", "Tu PC está lleno: libera espacio y reclama aquí");
              case "PARENTS_NOT_OWNED" -> Tr.s("zianutilities.breeding.why_parents", "Debes recuperar los padres en tu equipo o PC");
              case "GENERATION_DISABLED" -> Tr.s("zianutilities.breeding.why_generation", "La política de crianza no permite esta generación");
              case "SPECIES_UNAVAILABLE", "SPECIES_OR_GENERATION" -> Tr.s("zianutilities.breeding.why_species", "La especie o su generación no está disponible; la cría sigue reservada");
              default ->
                  j.status().equals("UNCERTAIN")
                      ? Tr.s("zianutilities.breeding.why_uncertain", "Un administrador debe revisar la operación antes de continuar")
                      : Tr.s("zianutilities.breeding.why_reserved", "La cría permanece reservada; no se vuelve a sortear");
            };
        wrap(
            g,
            state.message().isEmpty() ? why : state.message(),
            x + 12,
            top + 126,
            w - 24,
            MUTED,
            2);
        g.fill(x + 12, top + 152, x + w - 12, top + 153, 0xFF3B4148);
        var money = currency();
        if (money != null) g.drawCenteredString(font, Tr.s("zianutilities.breeding.unit_balance", "%s min por unidad · saldo %s", money.minutes(), money.balance() < 0 ? "?" : money.balance()), width / 2, top + 188, WHITE);
        wrapCentered(g, currency() == null ? Tr.s("zianutilities.breeding.no_currencies", "No hay monedas de aceleración configuradas")
            : currency().balance() == 0 ? Tr.s("zianutilities.breeding.no_balance", "Sin saldo para acelerar · puedes esperar sin costo")
            : Tr.s("zianutilities.breeding.speed_hint", "Acelerar reduce la espera hasta el mínimo permitido"), x + 12, top + 241, w - 24, MUTED, 2);
      }
    }
    renderWidgets(g, mx, my, partial);
    if (!hovered.isEmpty()) {
      var p = state.parents().stream().filter(a -> a.id().equals(hovered)).findFirst().orElse(null);
      if (p != null) {
        var lines = new ArrayList<Component>();
        lines.add(Component.literal(Tr.s("zianutilities.breeding.parent_line", "%s · Nv. %s · %s", p.name(), p.level(), sex(p.sex()))));
        lines.add(Component.translatable("cobblemon.nature." + p.nature()));
        lines.add(
            Component.translatableWithFallback("ui.zianutilities.d15ab4bd0f10", "Habilidad: ")
                .append(Component.translatable("cobblemon.ability." + p.ability())));
        String[] labels = statLabels();
        for (int i = 0; i < 6; i++)
          lines.add(
              Component.literal(
                  labels[i] + ": " + p.stats().get(i) + " · IV " + p.ivs().get(i) + "/31"));
        lines.add(Component.literal(Tr.s("zianutilities.breeding.egg_groups", "Grupos huevo: %s", p.groups())));
        lines.add(
            Component.translatableWithFallback("ui.zianutilities.f74d81c04bb3", "Objeto: ")
                .append(
                    p.held().isEmpty() ? Component.translatableWithFallback("ui.zianutilities.f3c24118253b", "Ninguno") : p.held().getHoverName()));
        lines.add(
            Component.literal(p.reason().isEmpty() ? Tr.s("zianutilities.breeding.available", "Disponible para seleccionar") : p.reason()));
        queueTooltip(lines);
      }
    }
    if (childHovered && !state.offspring().isEmpty()) {
      var child = state.offspring().get(Math.floorMod(childIndex, state.offspring().size()));
      var lines = new ArrayList<Component>();
      lines.add(Component.literal(Tr.s("zianutilities.breeding.child_header", "%s · Posible cría · Nv. 1", child.name())));
      lines.add(Component.literal(Tr.s("zianutilities.breeding.form", "Forma: %s", child.form())));
      String[] labels = statLabels();
      for (int i = 0; i < 6; i++)
        lines.add(
            Component.literal(
Tr.s("zianutilities.breeding.child_stat", "%s ≈ %s · IV medio ≈ %s/31", labels[i], child.stats().get(i), child.ivs().get(i))));
      for (String sentence : child.detail().split("\\. ")) lines.add(Component.literal(sentence));
      lines.add(Component.translatableWithFallback("ui.zianutilities.8cc1b8ef0629", "Sexo, naturaleza, habilidad y shiny se determinan al iniciar."));
      if (state.offspring().size() > 1)
        lines.add(Component.translatableWithFallback("ui.zianutilities.fcd5767d2c74", "Haz clic para ver otra especie o forma posible."));
      queueTooltip(lines);
    }
  }

  private void wrap(GuiGraphics g, String text, int x, int y, int w, int color, int rows) {
    var lines = font.split(Component.literal(text), w);
    for (int i = 0; i < Math.min(rows, lines.size()); i++)
      g.drawString(font, lines.get(i), x, y + i * 11, color, false);
  }

  private void wrapCentered(GuiGraphics g, String text, int x, int y, int w, int color, int rows) {
    var lines = font.split(Component.literal(text), w);
    for (int i = 0; i < Math.min(rows, lines.size()); i++)
      g.drawCenteredString(font, lines.get(i), width / 2, y + i * 11, color);
  }

  private String fit(String text, int pixels) {
    return font.width(text) <= pixels
        ? text
        : font.plainSubstrByWidth(text, Math.max(1, pixels - 8)) + "…";
  }

  private static String[] statLabels() {
    return new String[] {Tr.s("zianutilities.breeding.stat_hp", "PS"), Tr.s("zianutilities.breeding.stat_atk", "Ataque"), Tr.s("zianutilities.breeding.stat_def", "Defensa"), Tr.s("zianutilities.breeding.stat_spa", "At. especial"), Tr.s("zianutilities.breeding.stat_spd", "Def. especial"), Tr.s("zianutilities.breeding.stat_spe", "Velocidad")};
  }

  private static String sex(String s) {
    return switch (s) {
      case "MALE" -> Tr.s("zianutilities.breeding.male", "♂ Macho");
      case "FEMALE" -> Tr.s("zianutilities.breeding.female", "♀ Hembra");
      default -> Tr.s("zianutilities.breeding.genderless", "Sin sexo");
    };
  }

  private static String remaining(long ms) {
    long seconds = Math.max(0, (ms + 999) / 1000);
    return String.format("%dh %02dm %02ds", seconds / 3600, seconds / 60 % 60, seconds % 60);
  }

  private static String friendly(String id) {
    String name = id.substring(id.indexOf(':') + 1);
    return switch (name) {
      case "coppercoin" -> Tr.s("zianutilities.item_name.coppercoin", "Moneda de Cobre");
      case "ironcoin" -> Tr.s("zianutilities.item_name.ironcoin", "Moneda de Hierro");
      case "goldcoin" -> Tr.s("zianutilities.item_name.goldcoin", "Moneda de Oro");
      case "diamondcoin" -> Tr.s("zianutilities.item_name.diamondcoin", "Moneda de Diamante");
      case "netheritecoin" -> Tr.s("zianutilities.item_name.netheritecoin", "Moneda de Netherita");
      case "goldticket" -> Tr.s("zianutilities.item_name.goldticket", "Ticket de Oro");
      case "diamondticket" -> Tr.s("zianutilities.item_name.diamondticket", "Ticket de Diamante");
      case "netheriteticket" -> Tr.s("zianutilities.item_name.netheriteticket", "Ticket de Netherita");
      default -> name;
    };
  }

  private static void card(GuiGraphics g, int x, int y, int w, int h, int accent) {
    g.fill(x, y, x + w, y + h, 0xFF59616B);
    g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF181D24);
    g.fill(x + 1, y + 1, x + w - 1, y + 4, accent);
  }

  private void pokemonIcon(
      GuiGraphics g, net.minecraft.world.item.ItemStack icon, int x, int y, int size) {
    g.pose().pushPose();
    g.pose().translate(x, y, 0);
    float scale = size / 16.0f;
    g.pose().scale(scale, scale, 1);
    g.renderItem(icon, 0, 0);
    g.pose().popPose();
  }

  private BreedingNetwork.Parent parent(int slot) {
    String id = slot == 1 ? state.first() : state.second();
    return state.parents().stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
  }

  private final class ParentSlot extends StyledButton {
    private final int slot;

    ParentSlot(int x, int y, int w, int h, int slot) {
      super(
          x,
          y,
          w,
          h,
          slot == 1 ? Tr.s("zianutilities.breeding.father", "Padre") : Tr.s("zianutilities.breeding.mother", "Madre"),
          () -> {
            picker = slot;
            rebuildWidgets();
          });
      this.slot = slot;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
      var p = parent(slot);
      card(g, getX(), getY(), width, height, isHovered ? GOLD : 0xFF526575);
      g.drawString(font, getMessage(), getX() + 8, getY() + 8, GOLD, false);
      if (p == null) {
        g.drawCenteredString(font, "+", getX() + width / 2, getY() + 24, WHITE);
        if (height >= 60)
          g.drawCenteredString(font, Tr.s("zianutilities.breeding.pick_from_team", "Elegir del equipo"), getX() + width / 2, getY() + 44, MUTED);
      } else {
        int iconSize = Math.min(52, height - 28);
        pokemonIcon(g, p.icon(), getX() + 8, getY() + 22, iconSize);
        int textX = getX() + iconSize + 15, textWidth = width - iconSize - 23;
        g.drawString(font, fit(p.name(), textWidth), textX, getY() + 25, WHITE, false);
        if (height >= 60) {
          g.drawString(font, fit(sex(p.sex()), textWidth), textX, getY() + 39, MUTED, false);
          g.drawString(font, Tr.s("zianutilities.breeding.level", "Nv. %s", p.level()), textX, getY() + 51, MUTED, false);
        }
        if (height >= 75)
          g.drawString(font, fit(Tr.s("zianutilities.breeding.change", "Cambiar"), textWidth), textX, getY() + 65, GREEN, false);
        if (isHovered) hovered = p.id();
      }
    }
  }

  private final class OffspringButton extends StyledButton {
    OffspringButton(int x, int y, int w, int h) {
      super(
          x,
          y,
          w,
          h,
          Tr.s("zianutilities.breeding.offspring", "Posible cría"),
          () -> {
            if (!state.offspring().isEmpty())
              childIndex = (childIndex + 1) % state.offspring().size();
          });
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
      card(g, getX(), getY(), width, height, state.compatible() ? GREEN : 0xFF526575);
      g.drawString(font, getMessage(), getX() + 8, getY() + 8, GOLD, false);
      if (state.offspring().isEmpty()) {
        g.drawCenteredString(font, "?", getX() + width / 2, getY() + 24, WHITE);
        if (height >= 60)
          g.drawCenteredString(
              font, Tr.s("zianutilities.breeding.pick_pair", "Elige una pareja compatible"), getX() + width / 2, getY() + 44, MUTED);
        return;
      }
      var child = state.offspring().get(Math.floorMod(childIndex, state.offspring().size()));
      int iconSize = Math.min(52, height - 28);
      pokemonIcon(g, child.icon(), getX() + 8, getY() + 22, iconSize);
      int textX = getX() + iconSize + 15, textWidth = width - iconSize - 23;
      g.drawString(
          font, fit(Tr.s("zianutilities.breeding.child_level1", "%s · Nv. 1", child.name()), textWidth), textX, getY() + 25, WHITE, false);
      if (height >= 65) {
        g.drawString(
            font,
            fit(
Tr.s("zianutilities.breeding.child_stats_a", "≈ PS %s Atq %s Def %s", child.stats().get(0), child.stats().get(1), child.stats().get(2)),
                textWidth),
            textX,
            getY() + 39,
            MUTED,
            false);
        g.drawString(
            font,
            fit(
Tr.s("zianutilities.breeding.child_stats_b", "≈ AE %s DE %s Vel %s", child.stats().get(3), child.stats().get(4), child.stats().get(5)),
                textWidth),
            textX,
            getY() + 51,
            MUTED,
            false);
      }
      if (height >= 75)
        g.drawString(
            font,
            fit(
                state.offspring().size() > 1
                    ? Tr.s("zianutilities.breeding.estimate_n", "Estimación %s/%s", Math.floorMod(childIndex, state.offspring().size()) + 1, state.offspring().size())
                    : Tr.s("zianutilities.breeding.estimate", "Estimación"),
                textWidth),
            textX,
            getY() + 65,
            GREEN,
            false);
      childHovered = isHovered;
    }
  }

  private final class PokemonButton extends StyledButton {
    private final BreedingNetwork.Parent parent;

    PokemonButton(int x, int y, int w, int h, BreedingNetwork.Parent p, Runnable action) {
      super(x, y, w, h, p.name(), action);
      parent = p;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
      boolean chosen = parent.id().equals(state.first()) || parent.id().equals(state.second());
      card(
          g,
          getX(),
          getY(),
          width,
          height,
          chosen ? GOLD : parent.reason().isEmpty() ? 0xFF526575 : RED);
      if (isHovered) {
        g.renderOutline(getX(), getY(), width, height, GOLD);
        hovered = parent.id();
      }
      g.pose().pushPose();
      g.pose().translate(getX() + 6, getY() + 12, 0);
      g.pose().scale(1.5f, 1.5f, 1);
      g.renderItem(parent.icon(), 0, 0);
      g.pose().popPose();
      g.drawString(font, fit(parent.name(), width - 42), getX() + 35, getY() + 9, WHITE, false);
      g.drawString(
          font,
          fit(Tr.s("zianutilities.breeding.sex_level", "%s · Nv. %s", sex(parent.sex()), parent.level()), width - 42),
          getX() + 35,
          getY() + 21,
          parent.sex().equals("MALE")
              ? 0xFF79B9EF
              : parent.sex().equals("FEMALE") ? 0xFFF79BC4 : MUTED,
          false);
      String marker =
          parent.id().equals(state.first())
              ? Tr.s("zianutilities.breeding.parent1", "Padre 1")
              : parent.id().equals(state.second())
                  ? Tr.s("zianutilities.breeding.parent2", "Padre 2")
                  : parent.reason().isEmpty() ? Tr.s("zianutilities.breeding.select", "Seleccionar") : Tr.s("zianutilities.breeding.not_compatible", "No compatible");
      g.drawString(
          font,
          fit(height < 65 ? Tr.s("zianutilities.breeding.hp_marker", "PS %s · %s", parent.stats().get(0), marker) : marker, width - 42),
          getX() + 35,
          getY() + 33,
          chosen ? GOLD : parent.reason().isEmpty() ? GREEN : RED,
          false);
      if (height >= 65) {
        g.drawString(
            font,
            fit(
Tr.s("zianutilities.breeding.parent_stats_a", "PS %s  Atq %s  Def %s", parent.stats().get(0), parent.stats().get(1), parent.stats().get(2)),
                width - 12),
            getX() + 6,
            getY() + 49,
            MUTED,
            false);
      }
      if (height >= 80) {
        g.drawString(
            font,
            fit(
Tr.s("zianutilities.breeding.parent_stats_b", "AtE %s  DfE %s  Vel %s", parent.stats().get(3), parent.stats().get(4), parent.stats().get(5)),
                width - 12),
            getX() + 6,
            getY() + 61,
            MUTED,
            false);
      }
    }
  }

  private static class StyledButton extends AbstractButton {
    private final Runnable action;

    StyledButton(int x, int y, int w, int h, String label, Runnable action) {
      super(x, y, w, h, Component.literal(label));
      this.action = action;
    }

    @Override
    public void onPress() {
      action.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
      defaultButtonNarrationText(output);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
      int border = isHovered && active ? GOLD : active ? 0xFF526575 : 0xFF3B4148;
      g.fill(getX(), getY(), getX() + width, getY() + height, active ? 0xFF20262D : 0xFF161A20);
      g.renderOutline(getX(), getY(), width, height, border);
      var font = Minecraft.getInstance().font;
      String label = getMessage().getString();
      if (font.width(label) > width - 12) label = font.plainSubstrByWidth(label, Math.max(1, width - 20)) + "…";
      g.drawCenteredString(
          Minecraft.getInstance().font,
          label,
          getX() + width / 2,
          getY() + (height - 8) / 2,
          active ? WHITE : 0xFF7C838B);
    }
  }
}
